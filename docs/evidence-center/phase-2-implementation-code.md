# 证据中心 Phase 2：代码级实现参考

> 前置：Phase 1 已稳定（分页、presign、软删、`/evidence` 页）。
> 本文给出 Phase 2 关键文件的**可粘贴代码**；与 [phase-2-file-checklist.md](./phase-2-file-checklist.md) 一一对应。

---

## 1. Migration

### `V12__add_evidence_review_fields.sql`

```sql
ALTER TABLE evidence_asset
  ADD COLUMN review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' AFTER source_type,
  ADD COLUMN review_comment VARCHAR(512) NULL AFTER review_status,
  ADD COLUMN reviewed_at DATETIME NULL AFTER review_comment,
  ADD COLUMN reviewed_by VARCHAR(128) NULL AFTER reviewed_at,
  ADD COLUMN reviewed_by_name VARCHAR(128) NULL AFTER reviewed_by,
  ADD COLUMN remark VARCHAR(512) NULL AFTER reviewed_by_name,
  ADD COLUMN analysis_id VARCHAR(64) NULL AFTER remark;

CREATE INDEX idx_evidence_review_status_created_at
  ON evidence_asset (review_status, created_at);
```

> 注意：设计稿里曾写 `AFTER route_code`；当前表无 `route_code`，以上放在 `remark` 后。

### `V13__create_evidence_tag_tables.sql`

```sql
CREATE TABLE evidence_tag (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  color VARCHAR(32) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_evidence_tag_name (name)
);

CREATE TABLE evidence_tag_rel (
  evidence_id BIGINT NOT NULL,
  tag_id BIGINT NOT NULL,
  PRIMARY KEY (evidence_id, tag_id),
  INDEX idx_evidence_tag_rel_tag_id (tag_id)
);

INSERT INTO evidence_tag (name, color) VALUES
  ('可疑目标', '#C45C26'),
  ('已确认', '#2F6F4E'),
  ('误报', '#6B7280'),
  ('待复核', '#B45309');
```

### `V14__add_alarm_evidence_columns.sql`

```sql
ALTER TABLE alarm_event
  ADD COLUMN primary_evidence_code VARCHAR(64) NULL AFTER image_url,
  ADD COLUMN primary_video_evidence_code VARCHAR(64) NULL AFTER video_url;

CREATE INDEX idx_alarm_primary_evidence
  ON alarm_event (primary_evidence_code);
```

> 列位置按实际 `alarm_event` 表结构调整；若无 `image_url`/`video_url`，改为表尾追加。

### `V15__add_evidence_derivative_fields.sql`

```sql
ALTER TABLE evidence_asset
  ADD COLUMN thumbnail_object_key VARCHAR(512) NULL AFTER original_filename,
  ADD COLUMN poster_object_key VARCHAR(512) NULL AFTER thumbnail_object_key,
  ADD COLUMN derivative_status VARCHAR(32) NOT NULL DEFAULT 'NONE' AFTER poster_object_key;

CREATE INDEX idx_evidence_derivative_status
  ON evidence_asset (derivative_status);
```

---

## 2. Domain / Enum

### `EvidenceReviewStatus.java`

```java
package com.skytrace.backend.evidence.domain;

public enum EvidenceReviewStatus {
    PENDING,
    APPROVED,
    REJECTED
}
```

### `EvidenceDerivativeStatus.java`

```java
package com.skytrace.backend.evidence.domain;

public enum EvidenceDerivativeStatus {
    NONE,
    PENDING,
    READY,
    FAILED
}
```

### `EvidenceTag.java`

```java
package com.skytrace.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_tag")
public class EvidenceTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(length = 32)
    private String color;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
```

### `EvidenceTagRel.java`

```java
package com.skytrace.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "evidence_tag_rel")
@IdClass(EvidenceTagRel.PK.class)
public class EvidenceTagRel {

    @Id
    @Column(name = "evidence_id")
    private Long evidenceId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    public EvidenceTagRel() {}

    public EvidenceTagRel(Long evidenceId, Long tagId) {
        this.evidenceId = evidenceId;
        this.tagId = tagId;
    }

    public Long getEvidenceId() { return evidenceId; }
    public Long getTagId() { return tagId; }

    public static class PK implements Serializable {
        private Long evidenceId;
        private Long tagId;
        // equals/hashCode 按 IDE 生成
    }
}
```

### `EvidenceAsset` 增量字段

在 Phase 1 实体上追加：

```java
@Enumerated(EnumType.STRING)
@Column(name = "review_status", nullable = false, length = 32)
private EvidenceReviewStatus reviewStatus = EvidenceReviewStatus.PENDING;

@Column(name = "review_comment", length = 512)
private String reviewComment;

@Column(name = "reviewed_at")
private LocalDateTime reviewedAt;

@Column(name = "reviewed_by", length = 128)
private String reviewedBy;

@Column(name = "reviewed_by_name", length = 128)
private String reviewedByName;

@Column(length = 512)
private String remark;

@Column(name = "analysis_id", length = 64)
private String analysisId;

@Column(name = "thumbnail_object_key", length = 512)
private String thumbnailObjectKey;

@Column(name = "poster_object_key", length = 512)
private String posterObjectKey;

@Enumerated(EnumType.STRING)
@Column(name = "derivative_status", nullable = false, length = 32)
private EvidenceDerivativeStatus derivativeStatus = EvidenceDerivativeStatus.NONE;
```

---

## 3. DTO

```java
package com.skytrace.backend.evidence.dto;

import java.util.List;

public record UpdateEvidenceMetadataRequest(
        String remark,
        String reviewStatus,
        String reviewComment,
        List<Long> tagIds
) {
}

public record BatchReviewEvidenceRequest(
        List<String> evidenceCodes,
        String reviewStatus,
        String reviewComment
) {
}

public record BatchTagEvidenceRequest(
        List<String> evidenceCodes,
        List<Long> tagIds,
        boolean replace
) {
}

public record EvidenceTagResponse(Long id, String name, String color) {
}
```

在 `EvidenceSummaryResponse` / `EvidenceDetailResponse` 追加：

```java
// Summary
String reviewStatus,
List<EvidenceTagResponse> tags,
String thumbnailUrl,   // 可选：由 AccessService 临时签发，或前端再请求
String posterUrl

// Detail 额外
String remark,
String reviewComment,
String reviewedByName,
Instant reviewedAt,
String analysisId,
String derivativeStatus
```

---

## 4. Service 核心实现

### `EvidenceTagService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceTag;
import com.skytrace.backend.evidence.domain.EvidenceTagRel;
import com.skytrace.backend.evidence.dto.EvidenceTagResponse;
import com.skytrace.backend.evidence.repository.EvidenceTagRelRepository;
import com.skytrace.backend.evidence.repository.EvidenceTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EvidenceTagService {

    private final EvidenceTagRepository tagRepository;
    private final EvidenceTagRelRepository relRepository;

    public EvidenceTagService(
            EvidenceTagRepository tagRepository,
            EvidenceTagRelRepository relRepository) {
        this.tagRepository = tagRepository;
        this.relRepository = relRepository;
    }

    @Transactional(readOnly = true)
    public List<EvidenceTagResponse> listAll() {
        return tagRepository.findAll().stream()
                .map(t -> new EvidenceTagResponse(t.getId(), t.getName(), t.getColor()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EvidenceTagResponse> tagsOf(Long evidenceId) {
        List<Long> tagIds = relRepository.findByEvidenceId(evidenceId).stream()
                .map(EvidenceTagRel::getTagId)
                .toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllById(tagIds).stream()
                .map(t -> new EvidenceTagResponse(t.getId(), t.getName(), t.getColor()))
                .toList();
    }

    @Transactional
    public void replaceTags(Long evidenceId, List<Long> tagIds) {
        relRepository.deleteByEvidenceId(evidenceId);
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            relRepository.save(new EvidenceTagRel(evidenceId, tagId));
        }
    }

    @Transactional
    public void addTags(Long evidenceId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds) {
            if (!relRepository.existsByEvidenceIdAndTagId(evidenceId, tagId)) {
                relRepository.save(new EvidenceTagRel(evidenceId, tagId));
            }
        }
    }
}
```

Repository 方法：

```java
public interface EvidenceTagRepository extends JpaRepository<EvidenceTag, Long> {}

public interface EvidenceTagRelRepository extends JpaRepository<EvidenceTagRel, EvidenceTagRel.PK> {
    List<EvidenceTagRel> findByEvidenceId(Long evidenceId);
    void deleteByEvidenceId(Long evidenceId);
    boolean existsByEvidenceIdAndTagId(Long evidenceId, Long tagId);
}
```

### `EvidenceMetadataService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceReviewStatus;
import com.skytrace.backend.evidence.dto.BatchReviewEvidenceRequest;
import com.skytrace.backend.evidence.dto.BatchTagEvidenceRequest;
import com.skytrace.backend.evidence.dto.UpdateEvidenceMetadataRequest;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class EvidenceMetadataService {

    private final EvidenceQueryService queryService;
    private final EvidenceAssetRepository repository;
    private final EvidenceTagService tagService;
    private final EvidenceActorContextService actorContextService;

    public EvidenceMetadataService(
            EvidenceQueryService queryService,
            EvidenceAssetRepository repository,
            EvidenceTagService tagService,
            EvidenceActorContextService actorContextService) {
        this.queryService = queryService;
        this.repository = repository;
        this.tagService = tagService;
        this.actorContextService = actorContextService;
    }

    @Transactional
    public void updateMetadata(String evidenceCode, UpdateEvidenceMetadataRequest request) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        if (request.remark() != null) {
            asset.setRemark(blankToNull(request.remark()));
        }
        if (request.reviewStatus() != null) {
            applyReview(asset, request.reviewStatus(), request.reviewComment());
        }
        if (request.tagIds() != null) {
            tagService.replaceTags(asset.getId(), request.tagIds());
        }
        repository.save(asset);
    }

    @Transactional
    public void batchReview(BatchReviewEvidenceRequest request) {
        if (request.evidenceCodes() == null || request.evidenceCodes().isEmpty()) {
            throw new IllegalArgumentException("evidenceCodes 不能为空");
        }
        for (String code : request.evidenceCodes()) {
            EvidenceAsset asset = queryService.requireActive(code);
            applyReview(asset, request.reviewStatus(), request.reviewComment());
            repository.save(asset);
        }
    }

    @Transactional
    public void batchTags(BatchTagEvidenceRequest request) {
        if (request.evidenceCodes() == null || request.evidenceCodes().isEmpty()) {
            throw new IllegalArgumentException("evidenceCodes 不能为空");
        }
        for (String code : request.evidenceCodes()) {
            EvidenceAsset asset = queryService.requireActive(code);
            if (request.replace()) {
                tagService.replaceTags(asset.getId(), request.tagIds());
            } else {
                tagService.addTags(asset.getId(), request.tagIds());
            }
        }
    }

    private void applyReview(
            EvidenceAsset asset,
            String reviewStatus,
            String reviewComment) {
        EvidenceReviewStatus status = EvidenceReviewStatus.valueOf(
                reviewStatus.trim().toUpperCase(Locale.ROOT)
        );
        EvidenceActorContext actor = actorContextService.current();
        asset.setReviewStatus(status);
        asset.setReviewComment(blankToNull(reviewComment));
        asset.setReviewedAt(LocalDateTime.now());
        asset.setReviewedBy(actor.actorId());
        asset.setReviewedByName(actor.username());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

### `EvidenceRegistrationService.java`（AI / 抽帧入库）

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class EvidenceRegistrationService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final EvidenceAssetRepository repository;
    private final EvidenceDerivativeJobService derivativeJobService;

    public EvidenceRegistrationService(
            EvidenceAssetRepository repository,
            EvidenceDerivativeJobService derivativeJobService) {
        this.repository = repository;
        this.derivativeJobService = derivativeJobService;
    }

    public record RegisterCommand(
            String objectKey,
            String bucket,
            String contentType,
            String originalFilename,
            long sizeBytes,
            EvidenceSourceType sourceType,
            String taskCode,
            String alarmEventCode,
            String deviceCode,
            String analysisId,
            String uploadedBy,
            String uploadedByName
    ) {
    }

    @Transactional
    public EvidenceAsset register(RegisterCommand command) {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode(nextEvidenceCode());
        asset.setObjectKey(command.objectKey());
        asset.setBucket(command.bucket());
        asset.setContentType(command.contentType());
        asset.setOriginalFilename(command.originalFilename());
        asset.setSizeBytes(command.sizeBytes());
        asset.setAssetType(EvidenceAssetType.fromContentType(command.contentType()));
        asset.setSourceType(command.sourceType());
        asset.setTaskCode(command.taskCode());
        asset.setAlarmEventCode(command.alarmEventCode());
        asset.setDeviceCode(command.deviceCode());
        asset.setAnalysisId(command.analysisId());
        asset.setUploadedBy(command.uploadedBy() == null ? "system" : command.uploadedBy());
        asset.setUploadedByName(
                command.uploadedByName() == null ? "system" : command.uploadedByName()
        );
        asset.setDerivativeStatus(EvidenceDerivativeStatus.PENDING);
        repository.save(asset);
        derivativeJobService.start(asset.getEvidenceCode());
        return asset;
    }

    private String nextEvidenceCode() {
        String day = LocalDate.now(ZoneOffset.UTC).format(DAY);
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase();
        return "EV-" + day + "-" + suffix;
    }
}
```

### `EvidenceDerivativeJobService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.temporal.workflow.EvidenceDerivativeWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EvidenceDerivativeJobService {

    private final WorkflowClient workflowClient;
    private final String taskQueue;

    public EvidenceDerivativeJobService(
            WorkflowClient workflowClient,
            @Value("${app.temporal.task-queue:skytrace-inspection-task-queue}")
            String taskQueue) {
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
    }

    public void start(String evidenceCode) {
        EvidenceDerivativeWorkflow workflow = workflowClient.newWorkflowStub(
                EvidenceDerivativeWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(taskQueue)
                        .setWorkflowId("evidence-derivative-" + evidenceCode)
                        .build()
        );
        WorkflowClient.start(workflow::enrich, evidenceCode);
    }
}
```

> `WorkflowClient` Bean 名称以仓库现有 Temporal 配置为准；若注入方式不同，对齐现有 `Inspection*` 启动代码。

---

## 5. Temporal 衍生工作流

### `EvidenceDerivativeWorkflow.java`

```java
package com.skytrace.backend.temporal.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface EvidenceDerivativeWorkflow {
    @WorkflowMethod
    void enrich(String evidenceCode);
}
```

### `EvidenceDerivativeWorkflowImpl.java`

```java
package com.skytrace.backend.temporal.workflow;

import com.skytrace.backend.temporal.activity.EvidenceDerivativeActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class EvidenceDerivativeWorkflowImpl implements EvidenceDerivativeWorkflow {

    private final EvidenceDerivativeActivities activities =
            Workflow.newActivityStub(
                    EvidenceDerivativeActivities.class,
                    ActivityOptions.newBuilder()
                            .setStartToCloseTimeout(Duration.ofMinutes(5))
                            .setRetryOptions(
                                    RetryOptions.newBuilder()
                                            .setInitialInterval(Duration.ofSeconds(2))
                                            .setMaximumAttempts(3)
                                            .build()
                            )
                            .build()
            );

    @Override
    public void enrich(String evidenceCode) {
        activities.generateDerivatives(evidenceCode);
    }
}
```

### `EvidenceDerivativeActivities.java` / Impl 骨架

```java
package com.skytrace.backend.temporal.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface EvidenceDerivativeActivities {
    @ActivityMethod
    void generateDerivatives(String evidenceCode);
}
```

```java
package com.skytrace.backend.temporal.activity;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceDerivativeStatus;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import com.skytrace.backend.evidence.service.EvidenceStorageService;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
public class EvidenceDerivativeActivitiesImpl
        implements EvidenceDerivativeActivities {

    private final EvidenceAssetRepository repository;
    private final EvidenceStorageService storageService;

    public EvidenceDerivativeActivitiesImpl(
            EvidenceAssetRepository repository,
            EvidenceStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    @Override
    public void generateDerivatives(String evidenceCode) {
        EvidenceAsset asset = repository.findByEvidenceCode(evidenceCode)
                .orElseThrow(() -> new NoSuchElementException(evidenceCode));
        try {
            if (asset.getAssetType() == EvidenceAssetType.IMAGE) {
                // 1) 从 MinIO 拉原图
                // 2) 生成缩略图字节（可用 Thumbnailator / ImageIO）
                // 3) storageService 上传到 derivatives/{code}/thumb.jpg
                // 4) asset.setThumbnailObjectKey(...)
            } else {
                // 视频：抽取封面帧 → poster
                // asset.setPosterObjectKey(...)
            }
            asset.setDerivativeStatus(EvidenceDerivativeStatus.READY);
            repository.save(asset);
        } catch (Exception ex) {
            asset.setDerivativeStatus(EvidenceDerivativeStatus.FAILED);
            repository.save(asset);
            throw ex instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException(ex);
        }
    }
}
```

> 缩略图/抽帧的具体图像库实现可按环境选型；Activity 必须**幂等**（重复跑覆盖同一 object key）。

Worker 注册：与现有 `Inspection*` Workflow/Activity 同一 Worker 配置处追加接口与实现类。

---

## 6. Controller 增量

```java
@PatchMapping("/{evidenceCode}/metadata")
public ApiResponse<Void> updateMetadata(
        @PathVariable String evidenceCode,
        @RequestBody UpdateEvidenceMetadataRequest request) {
    metadataService.updateMetadata(evidenceCode, request);
    return ApiResponse.ok(null);
}

@PostMapping("/batch/review")
public ApiResponse<Void> batchReview(@RequestBody BatchReviewEvidenceRequest request) {
    metadataService.batchReview(request);
    return ApiResponse.ok(null);
}

@PostMapping("/batch/tags")
public ApiResponse<Void> batchTags(@RequestBody BatchTagEvidenceRequest request) {
    metadataService.batchTags(request);
    return ApiResponse.ok(null);
}

@GetMapping("/tags")
public ApiResponse<List<EvidenceTagResponse>> tags() {
    return ApiResponse.ok(tagService.listAll());
}
```

告警侧：在 Detection / Vision 入库成功后调用 `EvidenceRegistrationService.register(...)`，并把返回的 `evidenceCode` 写入 `alarm_event.primary_evidence_code`。

---

## 7. Node / 前端增量（摘要代码）

Node DTO 示例：

```ts
export class UpdateEvidenceMetadataDto {
  @IsOptional() @IsString() @MaxLength(512) remark?: string
  @IsOptional() @IsIn(['PENDING', 'APPROVED', 'REJECTED']) reviewStatus?: string
  @IsOptional() @IsString() @MaxLength(512) reviewComment?: string
  @IsOptional() @IsArray() tagIds?: number[]
}
```

Controller：

```ts
@Patch(':evidenceCode/metadata')
@Roles('ADMIN', 'OPERATOR')
updateMetadata(@Param() params: EvidenceCodeParamDto, @Body() body: UpdateEvidenceMetadataDto) {
  return this.javaClient.patch(`/evidence/${encodeURIComponent(params.evidenceCode)}/metadata`, body)
}
```

`JavaClientService` 目前没有 `patch`，需按 `put` 同款补上：

```ts
async patch<T>(path: string, body: unknown, timeout = 5000): Promise<T> {
  try {
    const response = await firstValueFrom(
      this.httpService.patch<T>(`${this.baseUrl}/api${path}`, body, {
        timeout,
        headers: this.downstreamHeaders(),
      }),
    )
    return response.data
  } catch (error) {
    this.rethrowUpstreamError(error)
  }
}
```

前端 `EvidenceView.vue` Phase 2：

- 列表增加审核状态、标签列
- 详情抽屉增加备注编辑、审核按钮、标签多选
- 批量操作：勾选行 → `batch/review` / `batch/tags`
- 缩略图列：优先显示 `thumbnailUrl`，否则占位

---

## 8. 测试最小集

```java
@Test
void shouldUpdateReviewStatus() { /* MetadataService */ }

@Test
void shouldReplaceTags() { /* TagService */ }

@Test
void shouldRegisterAiEvidenceAndStartDerivative() {
    // verify WorkflowClient.start called
}
```

---

## 9. 粘贴顺序

1. V12–V15  
2. Domain / Repository / DTO  
3. Tag / Metadata / Registration / DerivativeJob  
4. Temporal Workflow + Activity + Worker 注册  
5. Controller / Alarm 联动  
6. Node / Frontend  
7. 测试
