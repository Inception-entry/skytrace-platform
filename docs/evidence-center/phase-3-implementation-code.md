# 证据中心 Phase 3：代码级实现参考

> 前置：Phase 1 + Phase 2 已稳定。
> 本文给出归档 / 哈希 / Temporal 打包的核心结构。2026-08-11 之后的完整实现还包含
> V18 历史回填、物理清理、包级校验和压测；实际源码优先于本文的教学节选，生产操作见
> [evidence-maintenance-runbook.md](./evidence-maintenance-runbook.md)。

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

### `V18__add_evidence_maintenance_fields.sql`

```sql
ALTER TABLE evidence_asset
  ADD COLUMN hash_backfill_attempted_at DATETIME NULL,
  ADD COLUMN hash_backfill_error VARCHAR(512) NULL,
  ADD COLUMN purge_started_at DATETIME NULL,
  ADD COLUMN purged_at DATETIME NULL,
  ADD COLUMN purge_error VARCHAR(512) NULL;

ALTER TABLE evidence_archive_job
  ADD COLUMN package_content_hash VARCHAR(128) NULL,
  ADD COLUMN package_verified_at DATETIME NULL;
```

迁移文件中还包含回填候选与清理候选索引，完整定义以实际 V18 文件为准。

---

## 2. Domain

```java
public enum EvidenceArchiveStatus {
    ACTIVE,
    ARCHIVED,
    PURGING,
    PURGED
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
        String packageContentHash,
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

当前实现已经改为“磁盘临时 ZIP + 固定缓冲区 + 文件上传”，完整逐句注释见
[`EvidenceArchivePackageService.java`](../../backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceArchivePackageService.java)。核心生命周期如下：

```java
private static final int STREAM_BUFFER_SIZE = 64 * 1024;

public ArchivePackageResult buildAndStore(
        EvidenceArchiveJob job,
        List<ArchivedEvidenceFile> files,
        byte[] manifestBytes,
        byte[] checksumsBytes) {
    if (files == null || files.isEmpty()) {
        throw new IllegalArgumentException("归档文件列表不能为空");
    }

    String jobCode = job.getJobCode();
    Path temporaryZip = null;
    try {
        String packageObjectKey = "archives/" + jobCode
                + "/" + jobCode + ".zip";
        String manifestObjectKey = "archives/" + jobCode
                + "/manifest.json";
        String bucket = files.getFirst().bucket();
        // 每个任务创建唯一的磁盘临时文件。
        temporaryZip = createTemporaryZip();
        // 原始 MinIO 对象逐段写入 ZIP，不生成完整 zipBytes。
        writeZipToFile(
                temporaryZip,
                files,
                manifestBytes,
                checksumsBytes
        );
        // 再用固定缓冲区读取磁盘 ZIP，保存包级完整性摘要。
        String packageContentHash = sha256(temporaryZip);
        // ZipOutputStream 关闭后，SDK 直接读取磁盘文件上传。
        storageService.uploadObject(
                bucket,
                packageObjectKey,
                temporaryZip,
                "application/zip"
        );
        // manifest 是小型元数据，仍单独上传，便于快速查看。
        storageService.putObject(
                bucket,
                manifestObjectKey,
                manifestBytes,
                "application/json"
        );
        long totalBytes = files.stream()
                .mapToLong(ArchivedEvidenceFile::sizeBytes)
                .sum();
        return new ArchivePackageResult(
                bucket,
                packageObjectKey,
                manifestObjectKey,
                packageContentHash,
                files.size(),
                totalBytes
        );
    } catch (Exception exception) {
        throw new IllegalStateException("生成归档压缩包失败", exception);
    } finally {
        // 成功、读取失败或上传失败都会进入这里。
        deleteTemporaryZip(temporaryZip, jobCode);
    }
}

private static void copy(
        InputStream source,
        OutputStream target,
        byte[] transferBuffer) throws IOException {
    int bytesRead;
    while ((bytesRead = source.read(transferBuffer)) != -1) {
        if (bytesRead > 0) {
            target.write(transferBuffer, 0, bytesRead);
        }
    }
}
```

`EvidenceStorageService.uploadObject(...)` 使用 MinIO SDK 的
`UploadObjectArgs.filename(...)` 直接上传本地文件。归档大小增加时，JVM 堆内存不再随
ZIP 总大小线性增长；主要固定开销是 64 KiB 复制缓冲区和
`BufferedOutputStream`。`manifestBytes` 与 `checksumsBytes` 仍在内存中，因为它们只是
与文件数量相关的小型文本元数据。

临时目录由 `app.minio.archive-temp-dir` 配置，环境变量为
`MINIO_ARCHIVE_TEMP_DIR`，默认值是
`${java.io.tmpdir}/skytrace-evidence-archives`。生产环境应把它放到容量受控、可监控的
专用磁盘，并为进程被强制终止后可能遗留的文件设置巡检或清理策略。

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
                            .setHeartbeatTimeout(Duration.ofMinutes(10))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(2))
                                            .setMaximumInterval(Duration.ofSeconds(30))
                                            .setMaximumAttempts(8)
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

## 7. 已落地的回填与清理链路

实现按职责拆成五层：

1. `EvidenceHashBackfillService` 查询缺失哈希，原子认领后流式回填，失败对象按时间退避。
2. `EvidenceArchiveIntegrityService` 检查任务状态和对象存在性，每批重算 ZIP SHA-256，校验独立/包内 manifest 一致，并构建证据级索引。
3. `EvidenceCleanupService` 执行 dry-run、完整条件原子认领、manifest 逐项匹配、原件/派生对象删除、墓碑落库和失败恢复。
4. `EvidenceMaintenanceAuditService` 用独立事务写 STARTED/SUCCESS/FAILURE 审计。
5. `EvidenceMaintenanceScheduler/Controller` 分别提供默认关闭的调度入口和 ADMIN 手动入口。

正式清理只删除在线原件、缩略图和封面；`archives/` 由存储服务硬保护，数据库记录保留为
`PURGED` 墓碑。精确候选条件、90 天默认保留期和确认串见 Runbook。

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

1. V16 / V17 / V18
2. Domain / DTO / Repository  
3. Hash / Manifest / Package / ArchiveService  
4. Temporal Workflow + Activity + Worker  
5. Controller / Node / Frontend  
6. 回填 / 完整性 / 清理 / 审计服务
7. 测试

## 10. 当前实现阅读入口

按下面顺序阅读，比继续复制本文节选更容易理解真实事务边界：

1. `V18__add_evidence_maintenance_fields.sql`
2. `EvidenceAssetRepository` 中的候选查询和原子认领 JPQL
3. `EvidenceHashBackfillService`
4. `EvidenceArchiveIntegrityService`
5. `EvidenceCleanupService`
6. `EvidenceMaintenanceAuditService`
7. `EvidenceMaintenanceScheduler`
8. `EvidenceMaintenanceController`
9. Node `admin.controller.ts` 与 `evidence.controller.ts`
10. 前端 `EvidenceView.vue` 和 `verify-evidence-archive.sh`
