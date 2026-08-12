package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJobStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceArchiveIntegrityService {

    // manifest 是受信归档索引，设置硬上限可避免异常对象无限占用 JVM 内存。
    private static final int MAX_MANIFEST_BYTES = 10 * 1024 * 1024;
    // 打包服务把 manifest 固定写成 ZIP 的第一个条目，清理时据此做快速读取。
    private static final String MANIFEST_ENTRY = "manifest.json";

    private final EvidenceStorageService storageService;
    private final EvidenceHashService hashService;
    private final EvidenceArchiveJobRepository jobRepository;
    private final ObjectMapper objectMapper;

    public EvidenceArchiveIntegrityService(
            EvidenceStorageService storageService,
            EvidenceHashService hashService,
            EvidenceArchiveJobRepository jobRepository,
            ObjectMapper objectMapper) {
        this.storageService = storageService;
        this.hashService = hashService;
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
    }

    public VerifiedArchiveManifest verifyBeforePurge(EvidenceArchiveJob job) {
        // 只有完整完成的任务才可能成为物理清理依据。
        if (job.getStatus() != EvidenceArchiveJobStatus.COMPLETED) {
            throw new IllegalStateException("归档任务尚未完成");
        }
        // 老版本任务没有保存 ZIP 哈希，不能把未知状态产物当作删除依据。
        if (blank(job.getPackageContentHash())) {
            throw new IllegalStateException("归档任务缺少归档包 SHA-256");
        }
        // 包、manifest 和桶任一位置缺失都说明归档结果不完整。
        if (blank(job.getOutputBucket())
                || blank(job.getOutputObjectKey())
                || blank(job.getManifestObjectKey())) {
            throw new IllegalStateException("归档任务缺少产物位置");
        }
        // 即使以前校验成功，每次清理批次仍先确认两个对象没有被删除。
        if (!storageService.objectExists(
                job.getOutputBucket(),
                job.getOutputObjectKey()
        )) {
            throw new IllegalStateException("MinIO 中的归档包不存在");
        }
        if (!storageService.objectExists(
                job.getOutputBucket(),
                job.getManifestObjectKey()
        )) {
            throw new IllegalStateException("MinIO 中的归档 manifest 不存在");
        }

        // 每个清理批次都从 MinIO 流式重算 ZIP 哈希，防止已验证对象随后被替换。
        // 同一批次内的重复校验由 EvidenceCleanupService 的批次缓存消除。
        String actualHash;
        try (InputStream source = storageService.getObjectStream(
                job.getOutputBucket(),
                job.getOutputObjectKey()
        )) {
            actualHash = hashService.sha256Hex(source);
        } catch (Exception exception) {
            throw new IllegalStateException("读取归档包进行完整性校验失败", exception);
        }
        if (!job.getPackageContentHash().equalsIgnoreCase(actualHash)) {
            throw new IllegalStateException("归档包 SHA-256 校验失败");
        }

        // ZIP 内 manifest 受归档包哈希保护，是判断证据是否真正入包的信任来源。
        byte[] embeddedManifest = readEmbeddedManifest(job);
        // 单独下载的 manifest 必须与 ZIP 中版本逐字节一致，防止清单与归档包分叉。
        byte[] standaloneManifest = readStandaloneManifest(job);
        if (!MessageDigest.isEqual(embeddedManifest, standaloneManifest)) {
            throw new IllegalStateException("独立 manifest 与归档包内清单不一致");
        }
        // 解析并校验任务号、文件数和总大小，得到供本轮候选复用的只读索引。
        VerifiedArchiveManifest manifest = parseManifest(job, embeddedManifest);

        // 记录最近一次成功复核时间，供审计与运维观察，不把它当作永久信任凭据。
        // 与项目其他 DATETIME 字段保持相同的应用本地时间约定。
        job.setPackageVerifiedAt(LocalDateTime.now());
        jobRepository.save(job);
        return manifest;
    }

    public void verifyContains(
            VerifiedArchiveManifest manifest,
            EvidenceAsset asset) {
        // 每条待删证据都必须在受包哈希保护的 manifest 中拥有唯一条目。
        ManifestEvidence entry = manifest.filesByEvidenceCode().get(
                asset.getEvidenceCode()
        );
        if (entry == null) {
            throw new IllegalStateException(
                    "归档 manifest 不包含证据: " + asset.getEvidenceCode()
            );
        }
        // 数据库哈希与归档时哈希必须一致，后续元数据漂移不能误删当前对象。
        if (!entry.contentHash().equalsIgnoreCase(asset.getContentHash())) {
            throw new IllegalStateException(
                    "归档 manifest 内容哈希不一致: " + asset.getEvidenceCode()
            );
        }
        // 大小也是独立一致性信号，可发现哈希字段或关联记录被错误替换。
        if (entry.sizeBytes() != asset.getSizeBytes()) {
            throw new IllegalStateException(
                    "归档 manifest 文件大小不一致: " + asset.getEvidenceCode()
            );
        }
        // 归档路径必须属于当前证据编号，避免清单条目错误指向其他 ZIP 文件。
        String expectedPrefix = "files/" + asset.getEvidenceCode() + ".";
        if (!entry.archivePath().startsWith(expectedPrefix)) {
            throw new IllegalStateException(
                    "归档 manifest 文件路径不一致: " + asset.getEvidenceCode()
            );
        }
    }

    private byte[] readEmbeddedManifest(EvidenceArchiveJob job) {
        try (InputStream source = storageService.getObjectStream(
                job.getOutputBucket(),
                job.getOutputObjectKey()
        ); ZipInputStream zip = new ZipInputStream(source)) {
            // 只读取第一个小条目，不解压后续大文件；完整 ZIP 已在前一步完成 SHA-256 校验。
            ZipEntry firstEntry = zip.getNextEntry();
            if (firstEntry == null
                    || firstEntry.isDirectory()
                    || !MANIFEST_ENTRY.equals(firstEntry.getName())) {
                throw new IllegalStateException(
                        "归档包首个条目不是 manifest.json"
                );
            }
            return readBounded(zip, "归档包内 manifest");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "读取归档包内 manifest 失败",
                    exception
            );
        }
    }

    private byte[] readStandaloneManifest(EvidenceArchiveJob job) {
        try (InputStream source = storageService.getObjectStream(
                job.getOutputBucket(),
                job.getManifestObjectKey()
        )) {
            // 独立 manifest 同样受大小上限约束，不能直接调用无界 readAllBytes。
            return readBounded(source, "独立 manifest");
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "读取独立 manifest 失败",
                    exception
            );
        }
    }

    private byte[] readBounded(
            InputStream source,
            String label) throws IOException {
        // 多读一个字节即可判断是否越界，不需要先信任对象元数据中的长度。
        byte[] bytes = source.readNBytes(MAX_MANIFEST_BYTES + 1);
        if (bytes.length > MAX_MANIFEST_BYTES) {
            throw new IllegalStateException(
                    label + " 超过 " + MAX_MANIFEST_BYTES + " 字节安全上限"
            );
        }
        return bytes;
    }

    private VerifiedArchiveManifest parseManifest(
            EvidenceArchiveJob job,
            byte[] manifestBytes) {
        try {
            // 使用树模型读取现有 JSON 格式，避免为内部归档结构暴露额外可写 DTO。
            JsonNode root = objectMapper.readTree(manifestBytes);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("归档 manifest 根节点不是对象");
            }
            String manifestJobCode = requiredText(root, "jobCode");
            if (!job.getJobCode().equals(manifestJobCode)) {
                throw new IllegalStateException("归档 manifest 任务号不一致");
            }

            JsonNode files = root.get("files");
            if (files == null || !files.isArray()) {
                throw new IllegalStateException("归档 manifest 缺少 files 数组");
            }
            Map<String, ManifestEvidence> filesByEvidenceCode =
                    new LinkedHashMap<>();
            long calculatedTotalBytes = 0L;
            for (JsonNode file : files) {
                String evidenceCode = requiredText(file, "evidenceCode");
                ManifestEvidence evidence = new ManifestEvidence(
                        requiredText(file, "contentHash"),
                        requiredNonNegativeLong(file, "sizeBytes"),
                        requiredText(file, "archivePath")
                );
                // 同一 evidenceCode 出现两次时拒绝整个清单，避免候选匹配结果不确定。
                if (filesByEvidenceCode.putIfAbsent(
                        evidenceCode,
                        evidence
                ) != null) {
                    throw new IllegalStateException(
                            "归档 manifest 包含重复证据: " + evidenceCode
                    );
                }
                calculatedTotalBytes = Math.addExact(
                        calculatedTotalBytes,
                        evidence.sizeBytes()
                );
            }

            long declaredTotalFiles = requiredNonNegativeLong(
                    root,
                    "totalFiles"
            );
            long declaredTotalBytes = requiredNonNegativeLong(
                    root,
                    "totalBytes"
            );
            // JSON 声明、实际数组和数据库任务统计三方必须完全一致。
            if (declaredTotalFiles != filesByEvidenceCode.size()
                    || job.getTotalFiles() != filesByEvidenceCode.size()) {
                throw new IllegalStateException("归档 manifest 文件数量不一致");
            }
            if (declaredTotalBytes != calculatedTotalBytes
                    || job.getTotalBytes() != calculatedTotalBytes) {
                throw new IllegalStateException("归档 manifest 总字节数不一致");
            }
            return new VerifiedArchiveManifest(
                    manifestJobCode,
                    filesByEvidenceCode
            );
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("归档 manifest 总字节数溢出", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("解析归档 manifest 失败", exception);
        }
    }

    private static String requiredText(JsonNode parent, String fieldName) {
        // 必填文本必须是非空 JSON 字符串，不能把数字或布尔值隐式转换成业务键。
        JsonNode value = parent.get(fieldName);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException(
                    "归档 manifest 缺少有效字段: " + fieldName
            );
        }
        return value.textValue();
    }

    private static long requiredNonNegativeLong(
            JsonNode parent,
            String fieldName) {
        // 数量与大小必须是可表示为 long 的非负整数。
        JsonNode value = parent.get(fieldName);
        if (value == null
                || !value.isIntegralNumber()
                || !value.canConvertToLong()
                || value.longValue() < 0L) {
            throw new IllegalStateException(
                    "归档 manifest 缺少有效字段: " + fieldName
            );
        }
        return value.longValue();
    }

    private static boolean blank(String value) {
        // 所有必填字符串都同时拒绝 null、空串和纯空白。
        return value == null || value.isBlank();
    }

    public record ManifestEvidence(
            String contentHash,
            long sizeBytes,
            String archivePath) {
    }

    public record VerifiedArchiveManifest(
            String jobCode,
            Map<String, ManifestEvidence> filesByEvidenceCode) {

        public VerifiedArchiveManifest {
            // 防御性复制保证一次包级验证结果在整批清理期间不会被调用方修改。
            filesByEvidenceCode = Map.copyOf(filesByEvidenceCode);
        }
    }
}
