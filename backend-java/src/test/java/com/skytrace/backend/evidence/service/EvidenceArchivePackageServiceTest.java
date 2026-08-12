package com.skytrace.backend.evidence.service;

import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.evidence.MinioProperties;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceArchivePackageServiceTest {

    // JUnit 为每个测试创建独立目录，测试结束后自动清理外层目录。
    @TempDir
    Path tempDirectory;

    // 存储服务使用 Mock，测试不需要启动真实 MinIO。
    private final EvidenceStorageService storageService = mock(
            EvidenceStorageService.class
    );
    // 被测服务在 setUp 中使用测试临时目录完成构造。
    private EvidenceArchivePackageService service;
    // 每个测试都复用同一个最小归档任务对象。
    private EvidenceArchiveJob job;
    // 每个测试都复用一条证据文件描述。
    private EvidenceManifestService.ArchivedEvidenceFile archivedFile;
    // 原始证据内容故意超过一个短字符串，便于验证字节没有被改变。
    private byte[] evidenceBytes;
    // manifest 是 ZIP 根目录中的第一个元数据条目。
    private byte[] manifestBytes;
    // checksums 是 ZIP 根目录中的第二个元数据条目。
    private byte[] checksumsBytes;

    @BeforeEach
    void setUp() {
        // 配置对象只设置本测试关心的归档临时目录。
        MinioProperties properties = new MinioProperties();
        // 使用 @TempDir，确保测试不会在开发机留下临时归档包。
        properties.setArchiveTempDir(tempDirectory.toString());
        // 手动构造服务，使测试只覆盖归档打包逻辑。
        service = new EvidenceArchivePackageService(
                storageService,
                properties
        );

        // 任务号同时决定 MinIO 中 ZIP 和 manifest 的对象路径。
        job = new EvidenceArchiveJob();
        // 固定任务号使断言稳定且容易阅读。
        job.setJobCode("ARCHIVE-001");

        // 构造超过 64 KiB 的模拟证据，确保固定缓冲区复制循环会执行多轮。
        evidenceBytes = new byte[150_000];
        // 使用可重复的字节模式，解压后可以精确核对是否丢失或错位。
        for (int index = 0; index < evidenceBytes.length; index++) {
            // 251 小于无符号 byte 的范围，并可产生足够多的不同字节值。
            evidenceBytes[index] = (byte) (index % 251);
        }
        // 构造最小 manifest 内容，测试只关心它是否原样进入 ZIP。
        manifestBytes = "{\"jobCode\":\"ARCHIVE-001\"}".getBytes(
                StandardCharsets.UTF_8
        );
        // 构造最小校验清单，测试只关心文件名和内容是否正确。
        checksumsBytes = "abc123  files/EV-001.jpg\n".getBytes(
                StandardCharsets.UTF_8
        );
        // 描述对象连接 MinIO 源对象、ZIP 内部路径和业务元数据。
        archivedFile = new EvidenceManifestService.ArchivedEvidenceFile(
                "EV-001",
                "camera.jpg",
                "image/jpeg",
                evidenceBytes.length,
                "sha256:abc123",
                "TASK-001",
                "ALARM-001",
                "evidence",
                "TASK-001/source.jpg",
                "files/EV-001.jpg"
        );
    }

    @Test
    void shouldWriteCompleteZipToDiskUploadItAndDeleteTemporaryFile()
            throws Exception {
        // 模拟 MinIO 下载流，归档服务会把它按固定缓冲区写进 ZIP。
        when(storageService.getObjectStream(
                "evidence",
                "TASK-001/source.jpg"
        )).thenReturn(new ByteArrayInputStream(evidenceBytes));

        // 保存 SDK 上传时收到的路径，方法返回后用于验证文件已被删除。
        AtomicReference<Path> uploadedPath = new AtomicReference<>();
        // 在临时文件删除前读取 ZIP 条目，证明上传时拿到的是完整文件。
        AtomicReference<Map<String, byte[]>> uploadedEntries =
                new AtomicReference<>();
        // uploadObject 是 void 方法，因此使用 doAnswer 拦截调用参数。
        doAnswer(invocation -> {
            // 第三个参数就是归档服务创建的磁盘临时文件。
            Path sourceFile = invocation.getArgument(2);
            // 保存路径本身，稍后检查 finally 是否完成删除。
            uploadedPath.set(sourceFile);
            // 模拟 MinIO SDK 读取文件，并把 ZIP 内容保存给后续断言。
            uploadedEntries.set(readZipEntries(sourceFile));
            // void Mock 调用必须返回 null。
            return null;
        }).when(storageService).uploadObject(
                eq("evidence"),
                eq("archives/ARCHIVE-001/ARCHIVE-001.zip"),
                any(Path.class),
                eq("application/zip")
        );

        // 执行完整的打包、归档上传和独立 manifest 上传流程。
        EvidenceArchivePackageService.ArchivePackageResult result =
                service.buildAndStore(
                        job,
                        List.of(archivedFile),
                        manifestBytes,
                        checksumsBytes
                );

        // ZIP 根目录必须包含 manifest，并保持调用方提供的原始字节。
        assertThat(uploadedEntries.get().get("manifest.json"))
                .isEqualTo(manifestBytes);
        // ZIP 根目录必须包含 checksums，并保持调用方提供的原始字节。
        assertThat(uploadedEntries.get().get("checksums.sha256"))
                .isEqualTo(checksumsBytes);
        // 原始证据必须出现在 manifest 描述的 archivePath 下。
        assertThat(uploadedEntries.get().get("files/EV-001.jpg"))
                .isEqualTo(evidenceBytes);
        // 当前示例只应产生两个元数据条目和一个证据条目。
        assertThat(uploadedEntries.get()).hasSize(3);

        // 返回值中的桶名供 Activity 回写归档任务表。
        assertThat(result.bucket()).isEqualTo("evidence");
        // 返回值中的 ZIP 路径必须与实际上传路径一致。
        assertThat(result.packageObjectKey())
                .isEqualTo("archives/ARCHIVE-001/ARCHIVE-001.zip");
        // 独立 manifest 使用同一个任务目录。
        assertThat(result.manifestObjectKey())
                .isEqualTo("archives/ARCHIVE-001/manifest.json");
        // ZIP 自身必须生成标准 sha256 前缀和 64 位十六进制摘要。
        assertThat(result.packageContentHash())
                .matches("sha256:[0-9a-f]{64}");
        // 文件总数来自归档描述列表。
        assertThat(result.totalFiles()).isEqualTo(1);
        // 总字节数统计原始证据大小，不统计 ZIP 压缩后大小。
        assertThat(result.totalBytes()).isEqualTo(evidenceBytes.length);

        // ZIP 上传完成后，manifest 还必须作为独立对象写入 MinIO。
        verify(storageService).putObject(
                "evidence",
                "archives/ARCHIVE-001/manifest.json",
                manifestBytes,
                "application/json"
        );
        // finally 必须删除刚才传给 MinIO SDK 的临时 ZIP。
        assertThat(uploadedPath.get()).doesNotExist();
        // 临时目录中不应留下同一任务创建的其他文件。
        assertTemporaryDirectoryIsEmpty();
    }

    @Test
    void shouldDeleteTemporaryFileWhenArchiveUploadFails() throws Exception {
        // 打包阶段仍然需要一个可读取的原始证据流。
        when(storageService.getObjectStream(
                "evidence",
                "TASK-001/source.jpg"
        )).thenReturn(new ByteArrayInputStream(evidenceBytes));

        // 保存失败上传收到的路径，异常返回后检查 finally 清理结果。
        AtomicReference<Path> uploadedPath = new AtomicReference<>();
        // 模拟 MinIO 在读取本地文件并上传时失败。
        doAnswer(invocation -> {
            // 先记录路径，证明异常发生前临时 ZIP 已经创建完成。
            uploadedPath.set(invocation.getArgument(2));
            // 抛出存储层业务异常，模拟网络或 MinIO 服务故障。
            throw new ConflictException("模拟归档上传失败");
        }).when(storageService).uploadObject(
                eq("evidence"),
                eq("archives/ARCHIVE-001/ARCHIVE-001.zip"),
                any(Path.class),
                eq("application/zip")
        );

        // 上层应把打包链路异常统一包装，同时保留原始 cause。
        assertThatThrownBy(() -> service.buildAndStore(
                job,
                List.of(archivedFile),
                manifestBytes,
                checksumsBytes
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("生成归档压缩包失败")
                .hasCauseInstanceOf(ConflictException.class);

        // ZIP 上传失败后不能继续写 manifest，避免产生误导性的半成品。
        verify(storageService, never()).putObject(
                any(String.class),
                any(String.class),
                any(byte[].class),
                any(String.class)
        );
        // 即使上传抛出异常，finally 仍必须删除临时 ZIP。
        assertThat(uploadedPath.get()).doesNotExist();
        // 临时目录最终也必须恢复为空。
        assertTemporaryDirectoryIsEmpty();
    }

    @Test
    void shouldPreserveSourceReadFailureAndDeleteTemporaryFile()
            throws Exception {
        // Mock 输入流允许我们精确制造“对象已打开但读取失败”的场景。
        InputStream failingSource = mock(InputStream.class);
        // 固定缓冲区第一次读取时就抛出 IOException，模拟 MinIO 连接中断。
        when(failingSource.read(any(byte[].class)))
                .thenThrow(new IOException("模拟证据对象读取失败"));
        // 归档服务会通过正常存储边界取得这个失败流。
        when(storageService.getObjectStream(
                "evidence",
                "TASK-001/source.jpg"
        )).thenReturn(failingSource);

        // closeEntry 或 ZipOutputStream.close 不能覆盖最先发生的读取异常。
        assertThatThrownBy(() -> service.buildAndStore(
                job,
                List.of(archivedFile),
                manifestBytes,
                checksumsBytes
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("生成归档压缩包失败")
                .hasCauseInstanceOf(IOException.class)
                .hasRootCauseMessage("模拟证据对象读取失败");

        // 打包没有成功完成时，不允许向 MinIO 上传不完整 ZIP。
        verify(storageService, never()).uploadObject(
                any(String.class),
                any(String.class),
                any(Path.class),
                any(String.class)
        );
        // 读取阶段失败同样必须由 finally 清理已经创建的临时文件。
        assertTemporaryDirectoryIsEmpty();
    }

    private void assertTemporaryDirectoryIsEmpty() throws IOException {
        // Files.list 返回需要关闭的 Stream，所以使用 try-with-resources。
        try (Stream<Path> remainingFiles = Files.list(tempDirectory)) {
            // 没有任何元素说明当前测试创建的临时 ZIP 已经被清理。
            assertThat(remainingFiles).isEmpty();
        }
    }

    private static Map<String, byte[]> readZipEntries(Path zipFile)
            throws IOException {
        // LinkedHashMap 保留 ZIP 原始条目顺序，失败时输出更容易阅读。
        Map<String, byte[]> entries = new LinkedHashMap<>();
        // 测试在 uploadObject 回调中读取此文件，此时 finally 尚未删除它。
        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(zipFile)
        )) {
            // getNextEntry 返回 null 表示已经遍历完 ZIP 中的所有条目。
            ZipEntry entry;
            // 逐条读取测试数据；这里只处理几十字节，不代表生产实现的内存策略。
            while ((entry = zip.getNextEntry()) != null) {
                // 测试把小条目读成 byte[]，便于精确比较内容。
                entries.put(entry.getName(), zip.readAllBytes());
                // 明确结束当前条目，再读取下一个条目。
                zip.closeEntry();
            }
        }
        // 返回上传瞬间观察到的完整 ZIP 内容。
        return entries;
    }
}
