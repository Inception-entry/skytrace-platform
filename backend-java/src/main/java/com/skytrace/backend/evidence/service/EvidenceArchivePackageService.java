package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.MinioProperties;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceArchivePackageService {

    // 日志只记录归档任务编号和临时文件清理结果，不记录证据内容。
    private static final Logger log = LoggerFactory.getLogger(
            EvidenceArchivePackageService.class
    );
    // ZIP 根目录中的 manifest 文件名必须与下载接口保存的对象名保持一致。
    private static final String MANIFEST_NAME = "manifest.json";
    // checksums 文件保存每个 ZIP 条目的 SHA-256，供离线核验使用。
    private static final String CHECKSUMS_NAME = "checksums.sha256";
    // 所有证据对象共用一个固定缓冲区，因此内存占用不会随归档包大小线性增长。
    private static final int STREAM_BUFFER_SIZE = 64 * 1024;

    // 存储服务负责读取原始对象，并把最终临时文件上传回 MinIO。
    private final EvidenceStorageService storageService;
    // 临时目录由配置提供，生产环境可以把它指向容量受控的专用磁盘。
    private final Path archiveTempDirectory;

    public EvidenceArchivePackageService(
            EvidenceStorageService storageService,
            MinioProperties properties) {
        // 保存依赖，后续所有对象读写都通过统一的存储边界完成。
        this.storageService = storageService;
        // 启动时就把配置转换成绝对规范路径，避免每个任务重复解析相对路径。
        this.archiveTempDirectory = resolveTempDirectory(
                properties.getArchiveTempDir()
        );
    }

    // 这个 record 是打包完成后的不可变结果，Activity 用它回写归档任务表。
    public record ArchivePackageResult(
            String bucket,
            String packageObjectKey,
            String manifestObjectKey,
            String packageContentHash,
            int totalFiles,
            long totalBytes
    ) {
    }

    @FunctionalInterface
    public interface ArchiveProgressListener {

        // Activity 用这个回调发送 Heartbeat，打包服务本身不依赖 Temporal SDK。
        void onProgress(
                String stage,
                int completedFiles,
                int totalFiles,
                long processedBytes
        );
    }

    public ArchivePackageResult buildAndStore(
            EvidenceArchiveJob job,
            List<EvidenceManifestService.ArchivedEvidenceFile> files,
            byte[] manifestBytes,
            byte[] checksumsBytes) {
        // 普通调用方不关心进度时使用空回调，保持原有方法签名兼容。
        return buildAndStore(
                job,
                files,
                manifestBytes,
                checksumsBytes,
                (stage, completed, total, bytes) -> {
                }
        );
    }

    public ArchivePackageResult buildAndStore(
            EvidenceArchiveJob job,
            List<EvidenceManifestService.ArchivedEvidenceFile> files,
            byte[] manifestBytes,
            byte[] checksumsBytes,
            ArchiveProgressListener progressListener) {
        // 归档范围必须至少包含一个文件，因为输出桶从首个证据对象中取得。
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("归档文件列表不能为空");
        }
        // 回调由内部 Activity 提供，拒绝 null 可以尽早暴露调用错误。
        if (progressListener == null) {
            throw new IllegalArgumentException("归档进度监听器不能为空");
        }

        // 在 try 外保存任务编号，确保 finally 清理时不需要再次访问可变实体。
        String jobCode = job.getJobCode();
        // 临时路径初始为空；只有创建成功后，finally 才需要尝试删除它。
        Path temporaryZip = null;

        try {
            // 最终 ZIP 使用稳定业务任务号作为对象路径，Activity 重试时会覆盖同一目标。
            String packageObjectKey = "archives/" + jobCode
                    + "/" + jobCode + ".zip";
            // manifest 同时单独保存，调用方无需下载整个 ZIP 就能查看清单。
            String manifestObjectKey = "archives/" + jobCode
                    + "/" + MANIFEST_NAME;
            // 当前归档范围内的证据属于同一个 MinIO 桶，归档产物也写入这个桶。
            String bucket = files.getFirst().bucket();
            // 先在配置目录创建唯一临时文件，避免并发归档任务互相覆盖。
            temporaryZip = createTemporaryZip();
            // 原始对象按固定缓冲区逐段写入磁盘 ZIP，不再构造完整 zipBytes。
            writeZipToFile(
                    temporaryZip,
                    files,
                    manifestBytes,
                    checksumsBytes,
                    progressListener
            );
            // ZIP 哈希同样按固定缓冲区读取磁盘，不会把完整归档重新加载到内存。
            String packageContentHash = sha256(temporaryZip);
            // 哈希完成意味着临时 ZIP 已具备清理原件前所需的完整性依据。
            progressListener.onProgress(
                    "PACKAGE_HASHED",
                    files.size(),
                    files.size(),
                    files.stream()
                            .mapToLong(EvidenceManifestService.ArchivedEvidenceFile::sizeBytes)
                            .sum()
            );

            // ZipOutputStream 已关闭并写完中央目录，此时文件是完整可读的 ZIP。
            progressListener.onProgress(
                    "PACKAGE_UPLOAD_STARTED",
                    files.size(),
                    files.size(),
                    Files.size(temporaryZip)
            );
            storageService.uploadObject(
                    bucket,
                    packageObjectKey,
                    temporaryZip,
                    "application/zip"
            );
            // 上传调用同步返回后，MinIO 已接收完整文件，可以继续上传 manifest。
            progressListener.onProgress(
                    "PACKAGE_UPLOADED",
                    files.size(),
                    files.size(),
                    Files.size(temporaryZip)
            );
            // manifest 很小，继续通过 byte[] 单独上传不会形成与归档大小相关的内存风险。
            storageService.putObject(
                    bucket,
                    manifestObjectKey,
                    manifestBytes,
                    "application/json"
            );

            // totalBytes 表示原始证据总字节数，而不是经过 ZIP 压缩后的文件大小。
            long totalBytes = files.stream()
                    .mapToLong(EvidenceManifestService.ArchivedEvidenceFile::sizeBytes)
                    .sum();

            // 返回产物位置和统计数据，让 Activity 在全部上传成功后更新数据库。
            return new ArchivePackageResult(
                    bucket,
                    packageObjectKey,
                    manifestObjectKey,
                    packageContentHash,
                    files.size(),
                    totalBytes
            );
        } catch (Exception exception) {
            // 保留原始异常作为 cause，Temporal 日志和测试仍可看到真正失败位置。
            throw new IllegalStateException("生成归档压缩包失败", exception);
        } finally {
            // 无论打包、读取或上传是否成功，都尽力释放临时磁盘空间。
            deleteTemporaryZip(temporaryZip, jobCode);
        }
    }

    private Path createTemporaryZip() throws IOException {
        // createDirectories 是幂等操作，目录已存在时不会报错。
        Files.createDirectories(archiveTempDirectory);
        // JDK 自动追加随机字符，保证同一目录下并发任务的文件名唯一。
        return Files.createTempFile(
                archiveTempDirectory,
                "evidence-archive-",
                ".zip"
        );
    }

    private void writeZipToFile(
            Path targetFile,
            List<EvidenceManifestService.ArchivedEvidenceFile> files,
            byte[] manifestBytes,
            byte[] checksumsBytes,
            ArchiveProgressListener progressListener) throws IOException {
        // 只分配一次固定大小的缓冲区，所有大文件依次复用它。
        byte[] transferBuffer = new byte[STREAM_BUFFER_SIZE];
        // Files.newOutputStream 把字节写到临时文件，而不是 JVM 堆内存。
        try (OutputStream fileOutput = Files.newOutputStream(targetFile);
             // BufferedOutputStream 减少小块磁盘写入次数，缓冲区大小仍是固定值。
             BufferedOutputStream bufferedOutput = new BufferedOutputStream(
                     fileOutput,
                     STREAM_BUFFER_SIZE
             );
             // 关闭 ZipOutputStream 时会写入 ZIP 中央目录，上传前必须完成这一步。
             ZipOutputStream zip = new ZipOutputStream(bufferedOutput)) {
            // 清单放在 ZIP 根目录，下载后可以直接找到归档描述。
            writeBytesEntry(zip, MANIFEST_NAME, manifestBytes);
            // 校验文件也放在根目录，常见 sha256sum 工具可以直接读取。
            writeBytesEntry(zip, CHECKSUMS_NAME, checksumsBytes);
            // 每次只处理一个原始对象，前一个 InputStream 会在进入下一个对象前关闭。
            long processedBytes = 0L;
            for (int index = 0; index < files.size(); index++) {
                // 下标转换成业务可读的已完成数量，第一项写完后为 1。
                EvidenceManifestService.ArchivedEvidenceFile file = files.get(index);
                // 把一个 MinIO 对象按固定缓冲区复制成对应的 ZIP entry。
                writeEvidenceEntry(zip, file, transferBuffer);
                // 使用清单中的原始大小累计进度，不把压缩后大小误当业务数据量。
                processedBytes += file.sizeBytes();
                // 每写完一个对象回调一次，避免按 64 KiB 高频调用 Temporal Heartbeat。
                progressListener.onProgress(
                        "FILE_PACKAGED",
                        index + 1,
                        files.size(),
                        processedBytes
                );
            }
        }
    }

    private static String sha256(Path sourceFile) throws IOException {
        // MessageDigest 只保留固定大小摘要状态，不保存已经读取的文件内容。
        MessageDigest digest = sha256Digest();
        // 哈希计算复用一个固定 64 KiB 缓冲区。
        byte[] buffer = new byte[STREAM_BUFFER_SIZE];
        // Files.newInputStream 从磁盘逐段读取刚刚完成的 ZIP。
        try (InputStream source = Files.newInputStream(sourceFile)) {
            // 每轮只把实际读取到的字节加入摘要。
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                if (bytesRead > 0) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
        }
        // 使用与证据 contentHash 相同的前缀格式，调用方无需猜测算法。
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            // SHA-256 是所有标准 JDK 都必须提供的算法。
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            // 如果运行时缺失必选算法，属于无法恢复的 JVM 配置错误。
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private void writeEvidenceEntry(
            ZipOutputStream zip,
            EvidenceManifestService.ArchivedEvidenceFile file,
            byte[] transferBuffer) throws IOException {
        // archivePath 同时出现在 manifest、checksums 和 ZIP 中，三处必须完全一致。
        zip.putNextEntry(new ZipEntry(file.archivePath()));
        // MinIO 返回的流只在当前 entry 生命周期内保持打开。
        try (InputStream source = storageService.getObjectStream(
                file.bucket(),
                file.objectKey()
        )) {
            // 循环复制固定大小数据块，文件再大也不会一次读入内存。
            copy(source, zip, transferBuffer);
        }
        // 只在复制成功后结束条目；失败时由外层 try-with-resources 关闭整个 ZIP。
        zip.closeEntry();
    }

    private static void writeBytesEntry(
            ZipOutputStream zip,
            String name,
            byte[] content) throws IOException {
        // manifest 和 checksums 体积很小，可以直接写入当前 ZIP entry。
        zip.putNextEntry(new ZipEntry(name));
        // 写入调用方已经生成好的 UTF-8 元数据字节。
        zip.write(content);
        // 成功写完的 entry 必须显式结束，后续条目才能拥有独立边界。
        zip.closeEntry();
    }

    private static void copy(
            InputStream source,
            OutputStream target,
            byte[] transferBuffer) throws IOException {
        // read 返回本轮实际字节数，返回 -1 表示源对象已经读取完毕。
        int bytesRead;
        // 循环次数取决于文件大小，但 transferBuffer 始终复用同一块 64 KiB 内存。
        while ((bytesRead = source.read(transferBuffer)) != -1) {
            // 某些 InputStream 允许暂时返回 0；这种情况下无需向 ZIP 写空数据。
            if (bytesRead > 0) {
                // 只写缓冲区中本轮有效的部分，最后一轮通常不足 64 KiB。
                target.write(transferBuffer, 0, bytesRead);
            }
        }
    }

    private void deleteTemporaryZip(Path temporaryZip, String jobCode) {
        // 临时文件尚未创建时没有任何清理工作。
        if (temporaryZip == null) {
            return;
        }

        try {
            // deleteIfExists 同时兼容正常删除和文件已被外部清理的情况。
            boolean deleted = Files.deleteIfExists(temporaryZip);
            // 调试日志帮助确认生命周期，但默认 INFO 级别不会产生噪声。
            log.debug(
                    "event=evidence_archive_temp_deleted jobCode={} deleted={}",
                    jobCode,
                    deleted
            );
        } catch (IOException | SecurityException cleanupException) {
            // 归档已经上传成功时，清理失败不应把任务改成 FAILED；运维可根据日志处理残留文件。
            log.warn(
                    "event=evidence_archive_temp_delete_failed jobCode={} path={} exceptionType={}",
                    jobCode,
                    temporaryZip,
                    cleanupException.getClass().getSimpleName()
            );
        }
    }

    private static Path resolveTempDirectory(String configuredDirectory) {
        // 空目录会让文件落点不可预测，因此在 Bean 创建阶段直接拒绝错误配置。
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            throw new IllegalArgumentException("归档临时目录不能为空");
        }
        // 绝对化和规范化只处理路径文本，不会提前创建目录或访问磁盘。
        return Path.of(configuredDirectory).toAbsolutePath().normalize();
    }
}
