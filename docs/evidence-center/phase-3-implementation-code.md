# 证据中心 Phase 3：代码级实现参考

> 前置：Phase 1 + Phase 2 已稳定。
> 本文给出归档 / 哈希 / Temporal 打包的**可粘贴核心代码**。

---

## 1. Migration

### `V16__add_evidence_archive_fields.sql`

```sql
ALTER TABLE evidence_asset
  ADD COLUMN content_hash VARCHAR(128) NULL AFTER source_type,
  ADD COLUMN archive_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER review_status,
  ADD COLUMN archive_batch_code VARCHAR(64) NULL AFTER archive_status,
  ADD COLUMN archived_at DATETIME NULL AFTER archive_batch_code;

CREATE INDEX idx_evidence_archive_status_created_at
  ON evidence_asset (archive_status, created_at);
CREATE INDEX idx_evidence_content_hash
  ON evidence_asset (content_hash);
```

### `V17__create_evidence_archive_job.sql`

```sql
CREATE TABLE evidence_archive_job (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  job_code VARCHAR(64) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  scope_value VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  output_bucket VARCHAR(128) NULL,
  output_object_key VARCHAR(512) NULL,
  manifest_object_key VARCHAR(512) NULL,
  total_files INT NOT NULL DEFAULT 0,
  total_bytes BIGINT NOT NULL DEFAULT 0,
  created_by VARCHAR(128) NOT NULL,
  created_by_name VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  completed_at DATETIME NULL,
  error_message VARCHAR(512) NULL,
  UNIQUE KEY uk_evidence_archive_job_code (job_code)
);
```

---

## 2. Domain

```java
public enum EvidenceArchiveStatus {
    ACTIVE,
    ARCHIVED
}

public enum EvidenceArchiveJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

public enum EvidenceArchiveScopeType {
    TASK,
    ALARM,
    CASE
}
```

### `EvidenceArchiveJob.java`

```java
package com.skytrace.backend.evidence.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_archive_job")
public class EvidenceArchiveJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_code", nullable = false, unique = true, length = 64)
    private String jobCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 32)
    private EvidenceArchiveScopeType scopeType;

    @Column(name = "scope_value", nullable = false, length = 128)
    private String scopeValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EvidenceArchiveJobStatus status = EvidenceArchiveJobStatus.PENDING;

    @Column(name = "output_bucket", length = 128)
    private String outputBucket;

    @Column(name = "output_object_key", length = 512)
    private String outputObjectKey;

    @Column(name = "manifest_object_key", length = 512)
    private String manifestObjectKey;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_by_name", nullable = false, length = 128)
    private String createdByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    // getters/setters ...
}
```

`EvidenceAsset` 追加：`contentHash`、`archiveStatus`、`archiveBatchCode`、`archivedAt`。

---

## 3. DTO

```java
public record CreateEvidenceArchiveJobRequest(
        String scopeType,
        String scopeValue
) {
}

public record EvidenceArchiveJobResponse(
        String jobCode,
        String scopeType,
        String scopeValue,
        String status,
        String outputObjectKey,
        String manifestObjectKey,
        int totalFiles,
        long totalBytes,
        Instant createdAt,
        Instant completedAt,
        String errorMessage
) {
}

public record EvidenceArchiveAccessUrlResponse(
        String url,
        Instant expiresAt
) {
}
```

---

## 4. Hash / Manifest / Package

### `EvidenceHashService.java`

```java
package com.skytrace.backend.evidence.service;

import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class EvidenceHashService {

    public String sha256Hex(InputStream inputStream) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream digests = new DigestInputStream(inputStream, digest)) {
                digests.transferTo(OutputStream.nullOutputStream());
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("计算内容哈希失败", ex);
        }
    }
}
```

需要 `import java.io.OutputStream;`。

### `EvidenceManifestService.java`

```java
package com.skytrace.backend.evidence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EvidenceManifestService {

    private final ObjectMapper objectMapper;

    public EvidenceManifestService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record ManifestFile(
            String evidenceCode,
            String originalFilename,
            String contentType,
            long sizeBytes,
            String contentHash,
            String taskCode,
            String alarmEventCode
    ) {
    }

    public byte[] toJson(
            EvidenceArchiveJob job,
            List<EvidenceAsset> assets) throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("jobCode", job.getJobCode());
        root.put("scopeType", job.getScopeType().name());
        root.put("scopeValue", job.getScopeValue());
        root.put("createdAt", Instant.now().toString());
        root.put("createdBy", job.getCreatedByName());
        root.put("totalFiles", assets.size());
        root.put(
                "totalBytes",
                assets.stream().mapToLong(EvidenceAsset::getSizeBytes).sum()
        );
        List<ManifestFile> files = new ArrayList<>();
        for (EvidenceAsset asset : assets) {
            files.add(new ManifestFile(
                    asset.getEvidenceCode(),
                    asset.getOriginalFilename(),
                    asset.getContentType(),
                    asset.getSizeBytes(),
                    asset.getContentHash(),
                    asset.getTaskCode(),
                    asset.getAlarmEventCode()
            ));
        }
        root.put("evidences", files);
        return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(root);
    }

    public byte[] toChecksums(List<EvidenceAsset> assets) {
        StringBuilder builder = new StringBuilder();
        for (EvidenceAsset asset : assets) {
            String hash = asset.getContentHash() == null
                    ? "sha256:UNKNOWN"
                    : asset.getContentHash();
            String name = asset.getEvidenceCode()
                    + extensionOf(asset.getOriginalFilename());
            builder.append(hash.replace("sha256:", ""))
                    .append("  files/")
                    .append(name)
                    .append('\n');
        }
        return builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
```

### `EvidenceArchivePackageService.java`（打包核心）

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceArchiveJob;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class EvidenceArchivePackageService {

    private final MinioClient minioClient;
    private final EvidenceManifestService manifestService;
    private final EvidenceHashService hashService;

    public EvidenceArchivePackageService(
            MinioClient minioClient,
            EvidenceManifestService manifestService,
            EvidenceHashService hashService) {
        this.minioClient = minioClient;
        this.manifestService = manifestService;
        this.hashService = hashService;
    }

    public record PackageResult(
            String outputObjectKey,
            String manifestObjectKey,
            int totalFiles,
            long totalBytes
    ) {
    }

    public PackageResult buildAndUpload(
            EvidenceArchiveJob job,
            List<EvidenceAsset> assets,
            String archiveBucket) throws Exception {
        // 1) 补齐缺失哈希
        for (EvidenceAsset asset : assets) {
            if (asset.getContentHash() == null || asset.getContentHash().isBlank()) {
                try (var in = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(asset.getBucket())
                                .object(asset.getObjectKey())
                                .build())) {
                    asset.setContentHash(hashService.sha256Hex(in));
                }
            }
        }

        byte[] manifest = manifestService.toJson(job, assets);
        byte[] checksums = manifestService.toChecksums(assets);

        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        long totalBytes = 0;
        try (ZipOutputStream zip = new ZipOutputStream(zipBuffer)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifest);
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("checksums.sha256"));
            zip.write(checksums);
            zip.closeEntry();

            for (EvidenceAsset asset : assets) {
                String entryName = "files/" + asset.getEvidenceCode()
                        + extensionOf(asset.getOriginalFilename());
                zip.putNextEntry(new ZipEntry(entryName));
                try (var in = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(asset.getBucket())
                                .object(asset.getObjectKey())
                                .build())) {
                    totalBytes += in.transferTo(zip);
                }
                zip.closeEntry();
            }
        }

        String zipKey = "archives/" + job.getJobCode() + ".zip";
        String manifestKey = "archives/" + job.getJobCode() + ".manifest.json";
        byte[] zipBytes = zipBuffer.toByteArray();

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(archiveBucket)
                        .object(zipKey)
                        .stream(new ByteArrayInputStream(zipBytes), zipBytes.length, -1)
                        .contentType("application/zip")
                        .build()
        );
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(archiveBucket)
                        .object(manifestKey)
                        .stream(new ByteArrayInputStream(manifest), manifest.length, -1)
                        .contentType("application/json")
                        .build()
        );

        return new PackageResult(zipKey, manifestKey, assets.size(), totalBytes);
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
```

> 生产环境大归档应改为临时文件流式 zip，避免全量进内存；Phase 3 第一版可用内存实现，并在文档中标注限制。

---

## 5. Archive Service + Temporal

### `EvidenceArchiveService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.*;
import com.skytrace.backend.evidence.dto.CreateEvidenceArchiveJobRequest;
import com.skytrace.backend.evidence.dto.EvidenceArchiveJobResponse;
import com.skytrace.backend.evidence.repository.EvidenceArchiveJobRepository;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import com.skytrace.backend.temporal.workflow.EvidenceArchiveWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EvidenceArchiveService {

    private final EvidenceArchiveJobRepository jobRepository;
    private final EvidenceAssetRepository assetRepository;
    private final EvidenceActorContextService actorContextService;
    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public EvidenceArchiveService(
            EvidenceArchiveJobRepository jobRepository,
            EvidenceAssetRepository assetRepository,
            EvidenceActorContextService actorContextService,
            WorkflowClient workflowClient,
            @Value("${app.temporal.task-queue:skytrace-inspection-task-queue}")
            String taskQueue) {
        this.jobRepository = jobRepository;
        this.assetRepository = assetRepository;
        this.actorContextService = actorContextService;
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    @Transactional
    public EvidenceArchiveJobResponse create(CreateEvidenceArchiveJobRequest request) {
        EvidenceArchiveScopeType scopeType = EvidenceArchiveScopeType.valueOf(
                request.scopeType().trim().toUpperCase(Locale.ROOT)
        );
        String scopeValue = request.scopeValue().trim();
        List<EvidenceAsset> assets = findScopeAssets(scopeType, scopeValue);
        if (assets.isEmpty()) {
            throw new IllegalArgumentException("归档范围内没有可导出的证据");
        }

        EvidenceActorContext actor = actorContextService.current();
        EvidenceArchiveJob job = new EvidenceArchiveJob();
        job.setJobCode(nextJobCode());
        job.setScopeType(scopeType);
        job.setScopeValue(scopeValue);
        job.setStatus(EvidenceArchiveJobStatus.PENDING);
        job.setCreatedBy(actor.actorId());
        job.setCreatedByName(actor.username());
        jobRepository.save(job);

        EvidenceArchiveWorkflow workflow = workflowClient.newWorkflowStub(
                EvidenceArchiveWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId("evidence-archive-" + job.getJobCode())
                        .build()
        );
        WorkflowClient.start(workflow::run, job.getJobCode());
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public EvidenceArchiveJobResponse get(String jobCode) {
        return toResponse(requireJob(jobCode));
    }

    public List<EvidenceAsset> findScopeAssets(
            EvidenceArchiveScopeType scopeType,
            String scopeValue) {
        return switch (scopeType) {
            case TASK -> assetRepository
                    .findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(scopeValue);
            case ALARM -> assetRepository
                    .findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(scopeValue);
            case CASE -> throw new IllegalArgumentException(
                    "CASE 归档需后续案件模块；Phase 3 先支持 TASK/ALARM"
            );
        };
    }

    public EvidenceArchiveJob requireJob(String jobCode) {
        return jobRepository.findByJobCode(jobCode)
                .orElseThrow(() -> new NoSuchElementException("归档任务不存在"));
    }

    private String nextJobCode() {
        String day = LocalDate.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        return "EV-ARCHIVE-" + day + "-"
                + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 6).toUpperCase();
    }

    private EvidenceArchiveJobResponse toResponse(EvidenceArchiveJob job) {
        return new EvidenceArchiveJobResponse(
                job.getJobCode(),
                job.getScopeType().name(),
                job.getScopeValue(),
                job.getStatus().name(),
                job.getOutputObjectKey(),
                job.getManifestObjectKey(),
                job.getTotalFiles(),
                job.getTotalBytes(),
                job.getCreatedAt().atZone(ZoneOffset.UTC).toInstant(),
                job.getCompletedAt() == null
                        ? null
                        : job.getCompletedAt().atZone(ZoneOffset.UTC).toInstant(),
                job.getErrorMessage()
        );
    }
}
```

### Temporal

```java
@WorkflowInterface
public interface EvidenceArchiveWorkflow {
    @WorkflowMethod
    void run(String jobCode);
}

public class EvidenceArchiveWorkflowImpl implements EvidenceArchiveWorkflow {
    private final EvidenceArchiveActivities activities =
            Workflow.newActivityStub(
                    EvidenceArchiveActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(30))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setMaximumAttempts(3)
                                            .build()
                            )
                            .build()
            );

    @Override
    public void run(String jobCode) {
        activities.executeArchive(jobCode);
    }
}

@ActivityInterface
public interface EvidenceArchiveActivities {
    @ActivityMethod
    void executeArchive(String jobCode);
}
```

### `EvidenceArchiveActivitiesImpl` 主流程

```java
@Override
@Transactional
public void executeArchive(String jobCode) {
    EvidenceArchiveJob job = archiveService.requireJob(jobCode);
    job.setStatus(EvidenceArchiveJobStatus.RUNNING);
    jobRepository.save(job);
    try {
        List<EvidenceAsset> assets = archiveService.findScopeAssets(
                job.getScopeType(),
                job.getScopeValue()
        );
        var result = packageService.buildAndUpload(
                job,
                assets,
                minioProperties.getEvidenceBucket()
        );
        for (EvidenceAsset asset : assets) {
            asset.setArchiveStatus(EvidenceArchiveStatus.ARCHIVED);
            asset.setArchiveBatchCode(job.getJobCode());
            asset.setArchivedAt(LocalDateTime.now());
            assetRepository.save(asset);
        }
        job.setOutputBucket(minioProperties.getEvidenceBucket());
        job.setOutputObjectKey(result.outputObjectKey());
        job.setManifestObjectKey(result.manifestObjectKey());
        job.setTotalFiles(result.totalFiles());
        job.setTotalBytes(result.totalBytes());
        job.setStatus(EvidenceArchiveJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
    } catch (Exception ex) {
        job.setStatus(EvidenceArchiveJobStatus.FAILED);
        job.setErrorMessage(ex.getMessage() == null
                ? ex.getClass().getSimpleName()
                : ex.getMessage().substring(0, Math.min(512, ex.getMessage().length())));
        jobRepository.save(job);
        throw ex instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException(ex);
    }
}
```

---

## 6. Controller / Node / Frontend

```java
@PostMapping("/archive-jobs")
public ApiResponse<EvidenceArchiveJobResponse> createArchive(
        @RequestBody CreateEvidenceArchiveJobRequest request) {
    return ApiResponse.ok(archiveService.create(request));
}

@GetMapping("/archive-jobs/{jobCode}")
public ApiResponse<EvidenceArchiveJobResponse> getArchive(
        @PathVariable String jobCode) {
    return ApiResponse.ok(archiveService.get(jobCode));
}

@PostMapping("/archive-jobs/{jobCode}/download-url")
public ApiResponse<EvidenceArchiveAccessUrlResponse> archiveDownload(
        @PathVariable String jobCode) {
    // load job → storageService.createPresignedGetUrl(outputBucket, outputObjectKey, ...)
}
```

Node 透传对应路径；前端在证据页增加「按任务/告警创建归档」与任务状态轮询。

---

## 7. 清理策略（文档位，非必须立刻跑 Job）

建议 `docs/evidence-center/retention-policy.md`：

```text
- deleted=true 且未归档：保留 30 天后允许物理删对象
- ARCHIVED：归档包保留期与对象保留期分离配置
- 物理清理必须先校验 archive_job COMPLETED 且 checksum 可核验
```

第一版可只文档化，不做自动清理 Worker。

---

## 8. 测试最小集

```java
@Test
void shouldBuildManifestAndChecksums() { ... }

@Test
void shouldFailWhenScopeEmpty() { ... }

@Test
void shouldMarkAssetsArchivedOnSuccess() { ... }
```

---

## 9. 粘贴顺序

1. V16 / V17  
2. Domain / DTO / Repository  
3. Hash / Manifest / Package / ArchiveService  
4. Temporal Workflow + Activity + Worker  
5. Controller / Node / Frontend  
6. retention-policy 文档  
7. 测试
