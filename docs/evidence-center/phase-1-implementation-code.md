# 证据中心 Phase 1：代码级实现参考

> 本文是 [phase-1-foundation.md](./phase-1-foundation.md) / [phase-1-file-checklist.md](./phase-1-file-checklist.md) 的**可粘贴实现稿**。
> 目标：按路径复制到仓库后，Phase 1 能编译、联调、通过主链路测试。
>
> 约定：
> - 与现有 SkyTrace 风格对齐（`ApiResponse`、`NoSuchElementException`→404、JWT 取法同 `AuditInterceptor`）
> - 旧 `GET /evidence` **仍返回数组**；新能力走 `/search`、`/{code}`、`preview-url` 等
> - 时间：实体层继续用 `LocalDateTime`；新 API DTO 对外用 `Instant`（UTC）
> - 仓库里已有部分空壳 / 半成品文件时，以本文为准整体覆盖

关联：Phase 2/3 代码级见 [phase-2-implementation-code.md](./phase-2-implementation-code.md)、[phase-3-implementation-code.md](./phase-3-implementation-code.md)。

---

## 0. 粘贴顺序

1. `application.yml` + `V10` / `V11`（若已存在且内容一致可跳过）
2. domain / repository / dto
3. service（先 Storage，再 Query / Command / Access / AccessLog / ActorContext）
4. Controller + `AuditActionResolver`
5. 单测
6. Node BFF
7. 前端 API / 路由 / 页面 / i18n / `DroneView` 兼容

---

## 1. 配置

### 1.1 `backend-java/src/main/resources/application.yml`

在现有 `app.minio` 下追加：

```yaml
  minio:
    enabled: ${MINIO_ENABLED:true}
    endpoint: ${MINIO_ENDPOINT:http://localhost:9011}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin123}
    evidence-bucket: ${MINIO_EVIDENCE_BUCKET:skytrace-evidence}
    public-read-enabled: ${MINIO_PUBLIC_READ_ENABLED:false}
    presign-preview-ttl-seconds: ${MINIO_PRESIGN_PREVIEW_TTL_SECONDS:300}
    presign-download-ttl-seconds: ${MINIO_PRESIGN_DOWNLOAD_TTL_SECONDS:300}
```

本地若暂时需要旧公开桶行为，可临时设 `MINIO_PUBLIC_READ_ENABLED=true`；生产必须为 `false`。

### 1.2 `deploy/.env.example`（可选同步）

```dotenv
MINIO_PUBLIC_READ_ENABLED=false
MINIO_PRESIGN_PREVIEW_TTL_SECONDS=300
MINIO_PRESIGN_DOWNLOAD_TTL_SECONDS=300
```

---

## 2. Migration（完整 SQL）

### 2.1 `V10__upgrade_evidence_asset.sql`

```sql
ALTER TABLE evidence_asset
  ADD COLUMN evidence_code VARCHAR(64) NULL AFTER id,
  ADD COLUMN asset_type VARCHAR(32) NOT NULL DEFAULT 'IMAGE' AFTER bucket,
  ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL_UPLOAD' AFTER asset_type,
  ADD COLUMN device_code VARCHAR(64) NULL AFTER alarm_event_code,
  ADD COLUMN uploaded_by VARCHAR(128) NULL AFTER device_code,
  ADD COLUMN uploaded_by_name VARCHAR(128) NULL AFTER uploaded_by,
  ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER uploaded_by_name,
  ADD COLUMN deleted_at DATETIME NULL AFTER deleted,
  ADD COLUMN deleted_by VARCHAR(128) NULL AFTER deleted_at,
  ADD COLUMN deleted_by_name VARCHAR(128) NULL AFTER deleted_by;

UPDATE evidence_asset
SET evidence_code = CONCAT('EV-LEGACY-', LPAD(id, 8, '0'))
WHERE evidence_code IS NULL;

ALTER TABLE evidence_asset
  MODIFY COLUMN evidence_code VARCHAR(64) NOT NULL,
  ADD CONSTRAINT uk_evidence_asset_code UNIQUE (evidence_code);

CREATE INDEX idx_evidence_created_at ON evidence_asset (created_at);
CREATE INDEX idx_evidence_device_code ON evidence_asset (device_code);
CREATE INDEX idx_evidence_asset_type ON evidence_asset (asset_type);
CREATE INDEX idx_evidence_source_type ON evidence_asset (source_type);
CREATE INDEX idx_evidence_deleted_created_at ON evidence_asset (deleted, created_at);
```

### 2.2 `V11__create_evidence_access_log.sql`

```sql
CREATE TABLE IF NOT EXISTS evidence_access_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evidence_id BIGINT NOT NULL,
  evidence_code VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  actor_id VARCHAR(128) NOT NULL,
  username VARCHAR(128) NOT NULL,
  roles VARCHAR(256) NOT NULL,
  request_id VARCHAR(128) NULL,
  client_ip VARCHAR(64) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_evidence_access_code_created_at (evidence_code, created_at),
  INDEX idx_evidence_access_actor_created_at (actor_id, created_at)
);
```

---

## 3. MinIO 属性与配置

### 3.1 `MinioProperties.java`（完整替换）

```java
package com.skytrace.backend.evidence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.minio")
public class MinioProperties {

    private boolean enabled = true;
    private String endpoint = "http://localhost:9011";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin123";
    private String evidenceBucket = "skytrace-evidence";
    private boolean publicReadEnabled = false;
    private int presignPreviewTtlSeconds = 300;
    private int presignDownloadTtlSeconds = 300;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEvidenceBucket() {
        return evidenceBucket;
    }

    public void setEvidenceBucket(String evidenceBucket) {
        this.evidenceBucket = evidenceBucket;
    }

    public boolean isPublicReadEnabled() {
        return publicReadEnabled;
    }

    public void setPublicReadEnabled(boolean publicReadEnabled) {
        this.publicReadEnabled = publicReadEnabled;
    }

    public int getPresignPreviewTtlSeconds() {
        return presignPreviewTtlSeconds;
    }

    public void setPresignPreviewTtlSeconds(int presignPreviewTtlSeconds) {
        this.presignPreviewTtlSeconds = presignPreviewTtlSeconds;
    }

    public int getPresignDownloadTtlSeconds() {
        return presignDownloadTtlSeconds;
    }

    public void setPresignDownloadTtlSeconds(int presignDownloadTtlSeconds) {
        this.presignDownloadTtlSeconds = presignDownloadTtlSeconds;
    }
}
```

### 3.2 `MinioConfig.java`

保持现有 Bean 即可；**不要**在 Config 里改桶策略。桶策略改动集中在 `EvidenceStorageService.ensureBucket()`。

---

## 4. Domain

### 4.1 `EvidenceAssetType.java`

```java
package com.skytrace.backend.evidence.domain;

public enum EvidenceAssetType {
    IMAGE,
    VIDEO;

    public static EvidenceAssetType fromContentType(String contentType) {
        if (contentType != null && contentType.startsWith("video/")) {
            return VIDEO;
        }
        return IMAGE;
    }
}
```

### 4.2 `EvidenceSourceType.java`

```java
package com.skytrace.backend.evidence.domain;

public enum EvidenceSourceType {
    MANUAL_UPLOAD,
    AI_DETECTION,
    VIDEO_FRAME,
    SYSTEM_GENERATED
}
```

### 4.3 `EvidenceAccessAction.java`（建议新增，避免字符串散落）

```java
package com.skytrace.backend.evidence.domain;

public enum EvidenceAccessAction {
    PREVIEW,
    DOWNLOAD,
    DELETE,
    RESTORE,
    UPLOAD
}
```

### 4.4 `EvidenceAccessLog.java`

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
@Table(name = "evidence_access_log")
public class EvidenceAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "evidence_code", nullable = false, length = 64)
    private String evidenceCode;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(nullable = false, length = 128)
    private String username;

    @Column(nullable = false, length = 256)
    private String roles;

    @Column(name = "request_id", length = 128)
    private String requestId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public EvidenceAccessLog() {
    }

    public EvidenceAccessLog(
            Long evidenceId,
            String evidenceCode,
            String action,
            String actorId,
            String username,
            String roles,
            String requestId,
            String clientIp) {
        this.evidenceId = evidenceId;
        this.evidenceCode = evidenceCode;
        this.action = action;
        this.actorId = actorId;
        this.username = username;
        this.roles = roles;
        this.requestId = requestId;
        this.clientIp = clientIp;
    }

    public Long getId() {
        return id;
    }

    public Long getEvidenceId() {
        return evidenceId;
    }

    public String getEvidenceCode() {
        return evidenceCode;
    }

    public String getAction() {
        return action;
    }

    public String getActorId() {
        return actorId;
    }

    public String getUsername() {
        return username;
    }

    public String getRoles() {
        return roles;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
```

### 4.5 `EvidenceAsset.java`（完整替换）

```java
package com.skytrace.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence_asset")
public class EvidenceAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evidence_code", nullable = false, unique = true, length = 64)
    private String evidenceCode;

    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;

    @Column(nullable = false, length = 128)
    private String bucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 32)
    private EvidenceAssetType assetType = EvidenceAssetType.IMAGE;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private EvidenceSourceType sourceType = EvidenceSourceType.MANUAL_UPLOAD;

    @Column(name = "content_type", nullable = false, length = 128)
    private String contentType;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "task_code", length = 64)
    private String taskCode;

    @Column(name = "alarm_event_code", length = 64)
    private String alarmEventCode;

    @Column(name = "device_code", length = 64)
    private String deviceCode;

    @Column(name = "uploaded_by", length = 128)
    private String uploadedBy;

    @Column(name = "uploaded_by_name", length = 128)
    private String uploadedByName;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 128)
    private String deletedBy;

    @Column(name = "deleted_by_name", length = 128)
    private String deletedByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEvidenceCode() {
        return evidenceCode;
    }

    public void setEvidenceCode(String evidenceCode) {
        this.evidenceCode = evidenceCode;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public EvidenceAssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(EvidenceAssetType assetType) {
        this.assetType = assetType;
    }

    public EvidenceSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(EvidenceSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getAlarmEventCode() {
        return alarmEventCode;
    }

    public void setAlarmEventCode(String alarmEventCode) {
        this.alarmEventCode = alarmEventCode;
    }

    public String getDeviceCode() {
        return deviceCode;
    }

    public void setDeviceCode(String deviceCode) {
        this.deviceCode = deviceCode;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public String getUploadedByName() {
        return uploadedByName;
    }

    public void setUploadedByName(String uploadedByName) {
        this.uploadedByName = uploadedByName;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getDeletedByName() {
        return deletedByName;
    }

    public void setDeletedByName(String deletedByName) {
        this.deletedByName = deletedByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
```

---

## 5. Repository

### 5.1 `EvidenceAssetRepository.java`

```java
package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EvidenceAssetRepository
        extends JpaRepository<EvidenceAsset, Long>,
                JpaSpecificationExecutor<EvidenceAsset> {

    Optional<EvidenceAsset> findByEvidenceCode(String evidenceCode);

    List<EvidenceAsset> findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(
            String taskCode
    );

    List<EvidenceAsset> findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
            String alarmEventCode
    );

    List<EvidenceAsset>
            findByTaskCodeAndAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
                    String taskCode,
                    String alarmEventCode
            );
}
```

### 5.2 `EvidenceAccessLogRepository.java`

```java
package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceAccessLogRepository
        extends JpaRepository<EvidenceAccessLog, Long> {
}
```

---

## 6. DTO（完整）

### 6.1 `EvidenceAssetResponse.java`（旧列表兼容，补 evidenceCode）

```java
package com.skytrace.backend.evidence.dto;

import java.time.LocalDateTime;

public record EvidenceAssetResponse(
        String evidenceCode,
        String objectKey,
        String bucket,
        String contentType,
        long sizeBytes,
        String originalFilename,
        String taskCode,
        String alarmEventCode,
        String publicPath,
        LocalDateTime createdAt
) {
}
```

### 6.2 `EvidenceUploadResponse.java`

```java
package com.skytrace.backend.evidence.dto;

public record EvidenceUploadResponse(
        String evidenceCode,
        String objectKey,
        String bucket,
        String contentType,
        long sizeBytes,
        String taskCode,
        String alarmEventCode,
        String publicPath
) {
}
```

### 6.3 `EvidenceSearchRequest.java`

```java
package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceSearchRequest(
        Integer page,
        Integer size,
        String taskCode,
        String alarmEventCode,
        String deviceCode,
        String assetType,
        String sourceType,
        Instant startTime,
        Instant endTime,
        String keyword,
        Boolean includeDeleted
) {
}
```

### 6.4 `EvidenceSummaryResponse.java`

```java
package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceSummaryResponse(
        String evidenceCode,
        String originalFilename,
        String assetType,
        String sourceType,
        String taskCode,
        String alarmEventCode,
        String deviceCode,
        String uploadedByName,
        long sizeBytes,
        Instant createdAt,
        boolean deleted
) {
}
```

### 6.5 `EvidenceDetailResponse.java`

```java
package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceDetailResponse(
        String evidenceCode,
        String objectKey,
        String bucket,
        String assetType,
        String sourceType,
        String contentType,
        String originalFilename,
        long sizeBytes,
        String taskCode,
        String alarmEventCode,
        String deviceCode,
        String uploadedBy,
        String uploadedByName,
        Instant createdAt,
        boolean deleted
) {
}
```

### 6.6 `EvidencePageResponse.java`

```java
package com.skytrace.backend.evidence.dto;

import java.util.List;

public record EvidencePageResponse(
        List<EvidenceSummaryResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
```

### 6.7 `EvidenceAccessUrlResponse.java`

```java
package com.skytrace.backend.evidence.dto;

import java.time.Instant;

public record EvidenceAccessUrlResponse(
        String url,
        Instant expiresAt
) {
}
```

---

## 7. Service 层（完整实现）

### 7.1 `EvidenceActorContext.java` + `EvidenceActorContextService.java`

```java
package com.skytrace.backend.evidence.service;

public record EvidenceActorContext(
        String actorId,
        String username,
        String roles,
        String requestId,
        String clientIp
) {
}
```

```java
package com.skytrace.backend.evidence.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EvidenceActorContextService {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("^[A-Za-z0-9._:-]{8,128}$");

    public EvidenceActorContext current() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();
        HttpServletRequest request = currentRequest();
        return new EvidenceActorContext(
                actorId(authentication),
                username(authentication),
                roles(authentication),
                requestId(request),
                clientIp(request)
        );
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            return servlet.getRequest();
        }
        return null;
    }

    private String actorId(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof Jwt jwt) {
            return safe(jwt.getSubject(), 128);
        }
        return "anonymous";
    }

    private String username(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("preferred_username");
            return safe(
                    username == null ? jwt.getSubject() : username,
                    128
            );
        }
        return "anonymous";
    }

    private String roles(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return safe(
                authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority())
                        .filter(authority -> authority.startsWith("ROLE_"))
                        .map(authority -> authority.substring(5))
                        .sorted()
                        .collect(Collectors.joining(",")),
                256
        );
    }

    private String requestId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }
        String incoming = request.getHeader(REQUEST_ID_HEADER);
        return incoming != null
                && SAFE_REQUEST_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
        return safe(value, 64);
    }

    private String safe(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String sanitized = value
                .replace("\r", "")
                .replace("\n", "")
                .trim();
        return sanitized.length() > maxLength
                ? sanitized.substring(0, maxLength)
                : sanitized;
    }
}
```

### 7.2 `EvidenceAccessLogService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAccessAction;
import com.skytrace.backend.evidence.domain.EvidenceAccessLog;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceAccessLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceAccessLogService {

    private final EvidenceAccessLogRepository repository;
    private final EvidenceActorContextService actorContextService;

    public EvidenceAccessLogService(
            EvidenceAccessLogRepository repository,
            EvidenceActorContextService actorContextService) {
        this.repository = repository;
        this.actorContextService = actorContextService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(EvidenceAsset asset, EvidenceAccessAction action) {
        EvidenceActorContext actor = actorContextService.current();
        repository.save(new EvidenceAccessLog(
                asset.getId(),
                asset.getEvidenceCode(),
                action.name(),
                actor.actorId(),
                actor.username(),
                actor.roles() == null ? "" : actor.roles(),
                actor.requestId(),
                actor.clientIp()
        ));
    }

    public void recordPreview(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.PREVIEW);
    }

    public void recordDownload(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.DOWNLOAD);
    }

    public void recordDelete(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.DELETE);
    }

    public void recordRestore(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.RESTORE);
    }

    public void recordUpload(EvidenceAsset asset) {
        record(asset, EvidenceAccessAction.UPLOAD);
    }
}
```

### 7.3 `EvidenceStorageService.java`（收缩为对象存储工具）

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.common.ConflictException;
import com.skytrace.backend.evidence.MinioProperties;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnBean(MinioClient.class)
public class EvidenceStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "video/mp4",
            "video/webm"
    );

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public EvidenceStorageService(
            MinioClient minioClient,
            MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    public record StoredObject(
            String objectKey,
            String bucket,
            String contentType,
            long sizeBytes,
            String originalFilename,
            EvidenceAssetType assetType
    ) {
    }

    public StoredObject store(
            MultipartFile file,
            String taskCode) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("未上传证据文件");
        }
        String contentType = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                    "仅支持 jpg/png/webp 截图或 mp4/webm 视频"
            );
        }

        try {
            ensureBucket();
            String extension = extensionFor(
                    contentType,
                    file.getOriginalFilename()
            );
            String prefix = (taskCode == null || taskCode.isBlank())
                    ? "unassigned"
                    : taskCode.trim();
            String objectKey = prefix + "/" + UUID.randomUUID() + extension;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(properties.getEvidenceBucket())
                                .object(objectKey)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
            }

            return new StoredObject(
                    objectKey,
                    properties.getEvidenceBucket(),
                    contentType,
                    file.getSize(),
                    file.getOriginalFilename(),
                    EvidenceAssetType.fromContentType(contentType)
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ConflictException(
                    "证据上传失败: " + exception.getMessage()
            );
        }
    }

    public String createPresignedGetUrl(
            String bucket,
            String objectKey,
            int ttlSeconds,
            String contentDisposition) {
        try {
            GetPresignedObjectUrlArgs.Builder builder =
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(ttlSeconds, TimeUnit.SECONDS);
            if (contentDisposition != null && !contentDisposition.isBlank()) {
                Map<String, String> extra = new LinkedHashMap<>();
                extra.put(
                        "response-content-disposition",
                        contentDisposition
                );
                builder.extraQueryParams(extra);
            }
            return minioClient.getPresignedObjectUrl(builder.build());
        } catch (Exception exception) {
            throw new ConflictException(
                    "生成证据访问地址失败: " + exception.getMessage()
            );
        }
    }

    public Instant expiresAt(int ttlSeconds) {
        return Instant.now().plusSeconds(ttlSeconds);
    }

    public int previewTtlSeconds() {
        return properties.getPresignPreviewTtlSeconds();
    }

    public int downloadTtlSeconds() {
        return properties.getPresignDownloadTtlSeconds();
    }

    public String legacyPublicPath(String bucket, String objectKey) {
        return "/files/" + bucket + "/" + objectKey;
    }

    private void ensureBucket() throws Exception {
        String bucket = properties.getEvidenceBucket();
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucket).build()
            );
        }
        if (properties.isPublicReadEnabled()) {
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [{
                        "Effect": "Allow",
                        "Principal": {"AWS": ["*"]},
                        "Action": ["s3:GetObject"],
                        "Resource": ["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(bucket);
            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(policy)
                            .build()
            );
        }
    }

    private static String extensionFor(String contentType, String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            default -> ".jpg";
        };
    }
}
```

### 7.4 `EvidenceQueryService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.dto.EvidenceDetailResponse;
import com.skytrace.backend.evidence.dto.EvidencePageResponse;
import com.skytrace.backend.evidence.dto.EvidenceSearchRequest;
import com.skytrace.backend.evidence.dto.EvidenceSummaryResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
@Transactional(readOnly = true)
public class EvidenceQueryService {

    private final EvidenceAssetRepository repository;
    private final EvidenceStorageService storageService;

    public EvidenceQueryService(
            EvidenceAssetRepository repository,
            EvidenceStorageService storageService) {
        this.repository = repository;
        this.storageService = storageService;
    }

    public List<EvidenceAssetResponse> findLegacy(
            String taskCode,
            String alarmEventCode) {
        String task = blankToNull(taskCode);
        String alarm = blankToNull(alarmEventCode);
        if (task == null && alarm == null) {
            throw new IllegalArgumentException(
                    "请至少提供 taskCode 或 alarmEventCode"
            );
        }

        List<EvidenceAsset> assets;
        if (task != null && alarm != null) {
            assets = repository
                    .findByTaskCodeAndAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
                            task,
                            alarm
                    );
        } else if (task != null) {
            assets = repository
                    .findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(task);
        } else {
            assets = repository
                    .findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
                            alarm
                    );
        }
        return assets.stream().map(this::toLegacyResponse).toList();
    }

    public EvidencePageResponse search(EvidenceSearchRequest request) {
        int page = request.page() == null ? 0 : Math.max(request.page(), 0);
        int size = request.size() == null
                ? 20
                : Math.min(Math.max(request.size(), 1), 100);
        boolean includeDeleted = Boolean.TRUE.equals(request.includeDeleted());

        Page<EvidenceAsset> result = repository.findAll(
                buildSpec(request, includeDeleted),
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );

        return new EvidencePageResponse(
                result.getContent().stream()
                        .map(this::toSummary)
                        .toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    public EvidenceDetailResponse detail(String evidenceCode) {
        EvidenceAsset asset = requireActive(evidenceCode);
        return toDetail(asset);
    }

    public EvidenceAsset requireActive(String evidenceCode) {
        EvidenceAsset asset = requireAny(evidenceCode);
        if (asset.isDeleted()) {
            throw new NoSuchElementException("证据不存在或已删除");
        }
        return asset;
    }

    public EvidenceAsset requireAny(String evidenceCode) {
        String code = blankToNull(evidenceCode);
        if (code == null) {
            throw new IllegalArgumentException("evidenceCode 不能为空");
        }
        return repository.findByEvidenceCode(code)
                .orElseThrow(() -> new NoSuchElementException(
                        "证据不存在：" + code
                ));
    }

    private Specification<EvidenceAsset> buildSpec(
            EvidenceSearchRequest request,
            boolean includeDeleted) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeDeleted) {
                predicates.add(cb.isFalse(root.get("deleted")));
            }
            String taskCode = blankToNull(request.taskCode());
            if (taskCode != null) {
                predicates.add(cb.equal(root.get("taskCode"), taskCode));
            }
            String alarm = blankToNull(request.alarmEventCode());
            if (alarm != null) {
                predicates.add(cb.equal(root.get("alarmEventCode"), alarm));
            }
            String device = blankToNull(request.deviceCode());
            if (device != null) {
                predicates.add(cb.equal(root.get("deviceCode"), device));
            }
            EvidenceAssetType assetType = parseAssetType(request.assetType());
            if (assetType != null) {
                predicates.add(cb.equal(root.get("assetType"), assetType));
            }
            EvidenceSourceType sourceType = parseSourceType(request.sourceType());
            if (sourceType != null) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            if (request.startTime() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("createdAt"),
                        toLocal(request.startTime())
                ));
            }
            if (request.endTime() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("createdAt"),
                        toLocal(request.endTime())
                ));
            }
            String keyword = blankToNull(request.keyword());
            if (keyword != null) {
                String like = "%" + keyword.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("evidenceCode")), like),
                        cb.like(cb.lower(root.get("originalFilename")), like),
                        cb.like(cb.lower(root.get("taskCode")), like),
                        cb.like(cb.lower(root.get("alarmEventCode")), like),
                        cb.like(cb.lower(root.get("deviceCode")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private EvidenceAssetResponse toLegacyResponse(EvidenceAsset asset) {
        return new EvidenceAssetResponse(
                asset.getEvidenceCode(),
                asset.getObjectKey(),
                asset.getBucket(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getOriginalFilename(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                storageService.legacyPublicPath(
                        asset.getBucket(),
                        asset.getObjectKey()
                ),
                asset.getCreatedAt()
        );
    }

    private EvidenceSummaryResponse toSummary(EvidenceAsset asset) {
        return new EvidenceSummaryResponse(
                asset.getEvidenceCode(),
                asset.getOriginalFilename(),
                asset.getAssetType().name(),
                asset.getSourceType().name(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                asset.getDeviceCode(),
                asset.getUploadedByName(),
                asset.getSizeBytes(),
                toInstant(asset.getCreatedAt()),
                asset.isDeleted()
        );
    }

    private EvidenceDetailResponse toDetail(EvidenceAsset asset) {
        return new EvidenceDetailResponse(
                asset.getEvidenceCode(),
                asset.getObjectKey(),
                asset.getBucket(),
                asset.getAssetType().name(),
                asset.getSourceType().name(),
                asset.getContentType(),
                asset.getOriginalFilename(),
                asset.getSizeBytes(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                asset.getDeviceCode(),
                asset.getUploadedBy(),
                asset.getUploadedByName(),
                toInstant(asset.getCreatedAt()),
                asset.isDeleted()
        );
    }

    private static EvidenceAssetType parseAssetType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return EvidenceAssetType.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private static EvidenceSourceType parseSourceType(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        return EvidenceSourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
    }

    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneOffset.UTC).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```


### 7.5 `EvidenceCommandService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.dto.EvidenceUploadResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EvidenceCommandService {

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final EvidenceAssetRepository repository;
    private final EvidenceStorageService storageService;
    private final EvidenceActorContextService actorContextService;
    private final EvidenceAccessLogService accessLogService;
    private final EvidenceQueryService queryService;

    public EvidenceCommandService(
            EvidenceAssetRepository repository,
            EvidenceStorageService storageService,
            EvidenceActorContextService actorContextService,
            EvidenceAccessLogService accessLogService,
            EvidenceQueryService queryService) {
        this.repository = repository;
        this.storageService = storageService;
        this.actorContextService = actorContextService;
        this.accessLogService = accessLogService;
        this.queryService = queryService;
    }

    @Transactional
    public EvidenceUploadResponse upload(
            MultipartFile file,
            String taskCode,
            String alarmEventCode,
            String deviceCode) {
        EvidenceStorageService.StoredObject stored =
                storageService.store(file, taskCode);
        EvidenceActorContext actor = actorContextService.current();

        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode(nextEvidenceCode());
        asset.setObjectKey(stored.objectKey());
        asset.setBucket(stored.bucket());
        asset.setAssetType(stored.assetType());
        asset.setSourceType(EvidenceSourceType.MANUAL_UPLOAD);
        asset.setContentType(stored.contentType());
        asset.setOriginalFilename(stored.originalFilename());
        asset.setSizeBytes(stored.sizeBytes());
        asset.setTaskCode(blankToNull(taskCode));
        asset.setAlarmEventCode(blankToNull(alarmEventCode));
        asset.setDeviceCode(blankToNull(deviceCode));
        asset.setUploadedBy(actor.actorId());
        asset.setUploadedByName(actor.username());
        repository.save(asset);
        accessLogService.recordUpload(asset);

        return new EvidenceUploadResponse(
                asset.getEvidenceCode(),
                asset.getObjectKey(),
                asset.getBucket(),
                asset.getContentType(),
                asset.getSizeBytes(),
                asset.getTaskCode(),
                asset.getAlarmEventCode(),
                storageService.legacyPublicPath(
                        asset.getBucket(),
                        asset.getObjectKey()
                )
        );
    }

    @Transactional
    public void softDelete(String evidenceCode) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        EvidenceActorContext actor = actorContextService.current();
        asset.setDeleted(true);
        asset.setDeletedAt(LocalDateTime.now());
        asset.setDeletedBy(actor.actorId());
        asset.setDeletedByName(actor.username());
        repository.save(asset);
        accessLogService.recordDelete(asset);
    }

    @Transactional
    public void restore(String evidenceCode) {
        EvidenceAsset asset = queryService.requireAny(evidenceCode);
        if (!asset.isDeleted()) {
            throw new IllegalArgumentException("证据未被删除，无需恢复");
        }
        asset.setDeleted(false);
        asset.setDeletedAt(null);
        asset.setDeletedBy(null);
        asset.setDeletedByName(null);
        repository.save(asset);
        accessLogService.recordRestore(asset);
    }

    private String nextEvidenceCode() {
        String day = LocalDate.now(ZoneOffset.UTC).format(DAY);
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "EV-" + day + "-" + suffix;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
```

> 编号格式示例：`EV-20260810-A1B2C3D4`。若必须严格 `EV-yyyyMMdd-000001` 序号，可后续改成表序列；Phase 1 优先保证唯一与可读。

### 7.6 `EvidenceAccessService.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceAccessUrlResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceAccessService {

    private final EvidenceQueryService queryService;
    private final EvidenceStorageService storageService;
    private final EvidenceAccessLogService accessLogService;

    public EvidenceAccessService(
            EvidenceQueryService queryService,
            EvidenceStorageService storageService,
            EvidenceAccessLogService accessLogService) {
        this.queryService = queryService;
        this.storageService = storageService;
        this.accessLogService = accessLogService;
    }

    @Transactional
    public EvidenceAccessUrlResponse createPreviewUrl(String evidenceCode) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        accessLogService.recordPreview(asset);
        int ttl = storageService.previewTtlSeconds();
        String url = storageService.createPresignedGetUrl(
                asset.getBucket(),
                asset.getObjectKey(),
                ttl,
                null
        );
        return new EvidenceAccessUrlResponse(
                url,
                storageService.expiresAt(ttl)
        );
    }

    @Transactional
    public EvidenceAccessUrlResponse createDownloadUrl(String evidenceCode) {
        EvidenceAsset asset = queryService.requireActive(evidenceCode);
        accessLogService.recordDownload(asset);
        int ttl = storageService.downloadTtlSeconds();
        String filename = asset.getOriginalFilename() == null
                ? asset.getEvidenceCode()
                : asset.getOriginalFilename().replace("\"", "");
        String disposition = "attachment; filename=\"" + filename + "\"";
        String url = storageService.createPresignedGetUrl(
                asset.getBucket(),
                asset.getObjectKey(),
                ttl,
                disposition
        );
        return new EvidenceAccessUrlResponse(
                url,
                storageService.expiresAt(ttl)
        );
    }
}
```

---

## 8. Controller 与审计

### 8.1 `EvidenceController.java`（完整替换）

```java
package com.skytrace.backend.evidence;

import com.skytrace.backend.common.ApiResponse;
import com.skytrace.backend.evidence.dto.EvidenceAccessUrlResponse;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.dto.EvidenceDetailResponse;
import com.skytrace.backend.evidence.dto.EvidencePageResponse;
import com.skytrace.backend.evidence.dto.EvidenceSearchRequest;
import com.skytrace.backend.evidence.dto.EvidenceUploadResponse;
import com.skytrace.backend.evidence.service.EvidenceAccessService;
import com.skytrace.backend.evidence.service.EvidenceCommandService;
import com.skytrace.backend.evidence.service.EvidenceQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/evidence")
@ConditionalOnProperty(name = "app.minio.enabled", havingValue = "true")
public class EvidenceController {

    private final EvidenceQueryService queryService;
    private final EvidenceCommandService commandService;
    private final EvidenceAccessService accessService;

    public EvidenceController(
            EvidenceQueryService queryService,
            EvidenceCommandService commandService,
            EvidenceAccessService accessService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.accessService = accessService;
    }

    @GetMapping
    public ApiResponse<List<EvidenceAssetResponse>> list(
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode) {
        return ApiResponse.ok(queryService.findLegacy(taskCode, alarmEventCode));
    }

    @GetMapping("/search")
    public ApiResponse<EvidencePageResponse> search(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode,
            @RequestParam(value = "deviceCode", required = false)
            String deviceCode,
            @RequestParam(value = "assetType", required = false)
            String assetType,
            @RequestParam(value = "sourceType", required = false)
            String sourceType,
            @RequestParam(value = "startTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant startTime,
            @RequestParam(value = "endTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant endTime,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "includeDeleted", required = false)
            Boolean includeDeleted) {
        return ApiResponse.ok(queryService.search(new EvidenceSearchRequest(
                page,
                size,
                taskCode,
                alarmEventCode,
                deviceCode,
                assetType,
                sourceType,
                startTime,
                endTime,
                keyword,
                includeDeleted
        )));
    }

    @GetMapping("/{evidenceCode}")
    public ApiResponse<EvidenceDetailResponse> detail(
            @PathVariable String evidenceCode) {
        return ApiResponse.ok(queryService.detail(evidenceCode));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<EvidenceUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "taskCode", required = false) String taskCode,
            @RequestParam(value = "alarmEventCode", required = false)
            String alarmEventCode,
            @RequestParam(value = "deviceCode", required = false)
            String deviceCode) {
        return ApiResponse.ok(commandService.upload(
                file,
                taskCode,
                alarmEventCode,
                deviceCode
        ));
    }

    @PostMapping("/{evidenceCode}/preview-url")
    public ApiResponse<EvidenceAccessUrlResponse> previewUrl(
            @PathVariable String evidenceCode) {
        return ApiResponse.ok(accessService.createPreviewUrl(evidenceCode));
    }

    @PostMapping("/{evidenceCode}/download-url")
    public ApiResponse<EvidenceAccessUrlResponse> downloadUrl(
            @PathVariable String evidenceCode) {
        return ApiResponse.ok(accessService.createDownloadUrl(evidenceCode));
    }

    @DeleteMapping("/{evidenceCode}")
    public ApiResponse<Void> delete(@PathVariable String evidenceCode) {
        commandService.softDelete(evidenceCode);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{evidenceCode}/restore")
    public ApiResponse<Void> restore(@PathVariable String evidenceCode) {
        commandService.restore(evidenceCode);
        return ApiResponse.ok(null);
    }
}
```

### 8.2 `AuditActionResolver.java` 增量片段

在 `resolve(...)` 中、默认 `API_MUTATION` 之前插入：

```java
        private static final Pattern EVIDENCE_CODE = Pattern.compile(
                "^/evidence/([^/]+)(?:/(preview-url|download-url|restore))?$"
        );

        // ... inside resolve():
        if ("/evidence".equals(path) && HttpMethod.POST.matches(method)) {
            return new AuditDescriptor(
                    "EVIDENCE_UPLOAD",
                    "EVIDENCE",
                    null
            );
        }

        Matcher evidence = EVIDENCE_CODE.matcher(path);
        if (evidence.matches()) {
            String operation = evidence.group(2);
            String action = switch (operation == null ? "" : operation) {
                case "preview-url" -> "EVIDENCE_PREVIEW_URL";
                case "download-url" -> "EVIDENCE_DOWNLOAD_URL";
                case "restore" -> "EVIDENCE_RESTORE";
                default -> HttpMethod.DELETE.matches(method)
                        ? "EVIDENCE_DELETE"
                        : "EVIDENCE_MUTATION";
            };
            return new AuditDescriptor(
                    action,
                    "EVIDENCE",
                    evidence.group(1)
            );
        }
```

记得在类顶部补 `EVIDENCE_CODE` 常量与现有 `Pattern` 字段放一起。

---

## 9. Java 单测（最小主链路）

### 9.1 `EvidenceQueryServiceTest.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.domain.EvidenceAssetType;
import com.skytrace.backend.evidence.domain.EvidenceSourceType;
import com.skytrace.backend.evidence.dto.EvidenceAssetResponse;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvidenceQueryServiceTest {

    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private EvidenceQueryService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceQueryService(repository, storageService);
        when(storageService.legacyPublicPath("evidence", "TASK-001/demo.jpg"))
                .thenReturn("/files/evidence/TASK-001/demo.jpg");
    }

    @Test
    void shouldRequireFilterWhenListingEvidence() {
        assertThatThrownBy(() -> service.findLegacy(null, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少提供");
    }

    @Test
    void shouldListEvidenceByTaskCode() {
        EvidenceAsset asset = sample();
        when(repository.findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(
                "TASK-001"
        )).thenReturn(List.of(asset));

        List<EvidenceAssetResponse> responses =
                service.findLegacy("TASK-001", null);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().evidenceCode())
                .isEqualTo("EV-20260810-DEMO0001");
        assertThat(responses.getFirst().taskCode()).isEqualTo("TASK-001");
    }

    private EvidenceAsset sample() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-20260810-DEMO0001");
        asset.setObjectKey("TASK-001/demo.jpg");
        asset.setBucket("evidence");
        asset.setAssetType(EvidenceAssetType.IMAGE);
        asset.setSourceType(EvidenceSourceType.MANUAL_UPLOAD);
        asset.setContentType("image/jpeg");
        asset.setOriginalFilename("demo.jpg");
        asset.setSizeBytes(128);
        asset.setTaskCode("TASK-001");
        return asset;
    }
}
```

### 9.2 `EvidenceCommandServiceTest.java`（软删/恢复）

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.repository.EvidenceAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceCommandServiceTest {

    private final EvidenceAssetRepository repository =
            mock(EvidenceAssetRepository.class);
    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private final EvidenceActorContextService actorContextService =
            mock(EvidenceActorContextService.class);
    private final EvidenceAccessLogService accessLogService =
            mock(EvidenceAccessLogService.class);
    private final EvidenceQueryService queryService =
            mock(EvidenceQueryService.class);
    private EvidenceCommandService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceCommandService(
                repository,
                storageService,
                actorContextService,
                accessLogService,
                queryService
        );
        when(actorContextService.current()).thenReturn(
                new EvidenceActorContext(
                        "user-1",
                        "operator-a",
                        "OPERATOR",
                        "req-1",
                        "127.0.0.1"
                )
        );
    }

    @Test
    void shouldSoftDeleteEvidence() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        when(queryService.requireActive("EV-1")).thenReturn(asset);

        service.softDelete("EV-1");

        assertThat(asset.isDeleted()).isTrue();
        assertThat(asset.getDeletedBy()).isEqualTo("user-1");
        verify(repository).save(asset);
        verify(accessLogService).recordDelete(asset);
    }

    @Test
    void shouldRestoreEvidence() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        asset.setDeleted(true);
        when(queryService.requireAny("EV-1")).thenReturn(asset);

        service.restore("EV-1");

        assertThat(asset.isDeleted()).isFalse();
        assertThat(asset.getDeletedAt()).isNull();
        verify(accessLogService).recordRestore(asset);
    }
}
```

### 9.3 `EvidenceAccessServiceTest.java`

```java
package com.skytrace.backend.evidence.service;

import com.skytrace.backend.evidence.domain.EvidenceAsset;
import com.skytrace.backend.evidence.dto.EvidenceAccessUrlResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvidenceAccessServiceTest {

    private final EvidenceQueryService queryService =
            mock(EvidenceQueryService.class);
    private final EvidenceStorageService storageService =
            mock(EvidenceStorageService.class);
    private final EvidenceAccessLogService accessLogService =
            mock(EvidenceAccessLogService.class);
    private EvidenceAccessService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceAccessService(
                queryService,
                storageService,
                accessLogService
        );
    }

    @Test
    void shouldCreatePreviewUrlAndLog() {
        EvidenceAsset asset = new EvidenceAsset();
        asset.setEvidenceCode("EV-1");
        asset.setBucket("b");
        asset.setObjectKey("k");
        when(queryService.requireActive("EV-1")).thenReturn(asset);
        when(storageService.previewTtlSeconds()).thenReturn(300);
        when(storageService.createPresignedGetUrl("b", "k", 300, null))
                .thenReturn("http://signed");
        Instant expires = Instant.parse("2026-08-10T09:16:43Z");
        when(storageService.expiresAt(300)).thenReturn(expires);

        EvidenceAccessUrlResponse response =
                service.createPreviewUrl("EV-1");

        assertThat(response.url()).isEqualTo("http://signed");
        assertThat(response.expiresAt()).isEqualTo(expires);
        verify(accessLogService).recordPreview(asset);
    }
}
```

> 原 `EvidenceStorageServiceTest` 依赖已迁走的 `findEvidence`，请改测 `store` / `legacyPublicPath`，或删除后由上面三个测试覆盖。

---

## 10. Node BFF（完整）

### 10.1 `backend-node/src/evidence/dto/search-evidence.dto.ts`

```ts
import { Type } from 'class-transformer'
import {
  IsBoolean,
  IsIn,
  IsInt,
  IsISO8601,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator'

export class SearchEvidenceDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  page = 0

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(100)
  size = 20

  @IsOptional()
  @IsString()
  @MaxLength(64)
  taskCode?: string

  @IsOptional()
  @IsString()
  @MaxLength(64)
  alarmEventCode?: string

  @IsOptional()
  @IsString()
  @MaxLength(64)
  deviceCode?: string

  @IsOptional()
  @IsIn(['IMAGE', 'VIDEO'])
  assetType?: 'IMAGE' | 'VIDEO'

  @IsOptional()
  @IsIn([
    'MANUAL_UPLOAD',
    'AI_DETECTION',
    'VIDEO_FRAME',
    'SYSTEM_GENERATED',
  ])
  sourceType?:
    | 'MANUAL_UPLOAD'
    | 'AI_DETECTION'
    | 'VIDEO_FRAME'
    | 'SYSTEM_GENERATED'

  @IsOptional()
  @IsISO8601()
  startTime?: string

  @IsOptional()
  @IsISO8601()
  endTime?: string

  @IsOptional()
  @IsString()
  @MaxLength(128)
  keyword?: string

  @IsOptional()
  @Type(() => Boolean)
  @IsBoolean()
  includeDeleted?: boolean
}
```

### 10.2 `backend-node/src/evidence/dto/evidence-code.dto.ts`

```ts
import { IsString, Matches, MaxLength } from 'class-validator'

export class EvidenceCodeParamDto {
  @IsString()
  @MaxLength(64)
  @Matches(/^[A-Za-z0-9_-]+$/)
  evidenceCode!: string
}
```

### 10.3 `backend-node/src/evidence/evidence.controller.ts`（完整替换）

```ts
import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Post,
  Query,
  UploadedFile,
  UseInterceptors,
} from '@nestjs/common'
import { FileInterceptor } from '@nestjs/platform-express'
import { Roles } from '../auth/http-auth.decorators'
import { JavaClientService } from '../common/java-client/java-client.service'
import { EvidenceCodeParamDto } from './dto/evidence-code.dto'
import { SearchEvidenceDto } from './dto/search-evidence.dto'

interface UploadedEvidenceFile {
  buffer: Buffer
  originalname: string
  mimetype: string
}

@Controller('evidence')
export class EvidenceController {
  constructor(private readonly javaClient: JavaClientService) {}

  @Get()
  list(
    @Query('taskCode') taskCode?: string,
    @Query('alarmEventCode') alarmEventCode?: string,
  ) {
    if (!taskCode?.trim() && !alarmEventCode?.trim()) {
      throw new BadRequestException(
        '请至少提供 taskCode 或 alarmEventCode',
      )
    }
    const parameters = new URLSearchParams()
    if (taskCode?.trim()) {
      parameters.set('taskCode', taskCode.trim())
    }
    if (alarmEventCode?.trim()) {
      parameters.set('alarmEventCode', alarmEventCode.trim())
    }
    return this.javaClient.get(`/evidence?${parameters.toString()}`)
  }

  @Get('search')
  search(@Query() query: SearchEvidenceDto) {
    const parameters = new URLSearchParams()
    parameters.set('page', String(query.page ?? 0))
    parameters.set('size', String(query.size ?? 20))
    if (query.taskCode?.trim()) {
      parameters.set('taskCode', query.taskCode.trim())
    }
    if (query.alarmEventCode?.trim()) {
      parameters.set('alarmEventCode', query.alarmEventCode.trim())
    }
    if (query.deviceCode?.trim()) {
      parameters.set('deviceCode', query.deviceCode.trim())
    }
    if (query.assetType) {
      parameters.set('assetType', query.assetType)
    }
    if (query.sourceType) {
      parameters.set('sourceType', query.sourceType)
    }
    if (query.startTime) {
      parameters.set('startTime', query.startTime)
    }
    if (query.endTime) {
      parameters.set('endTime', query.endTime)
    }
    if (query.keyword?.trim()) {
      parameters.set('keyword', query.keyword.trim())
    }
    if (query.includeDeleted != null) {
      parameters.set('includeDeleted', String(query.includeDeleted))
    }
    return this.javaClient.get(`/evidence/search?${parameters.toString()}`)
  }

  @Get(':evidenceCode')
  detail(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.get(
      `/evidence/${encodeURIComponent(params.evidenceCode)}`,
    )
  }

  @Post()
  @Roles('ADMIN', 'OPERATOR')
  @UseInterceptors(
    FileInterceptor('file', {
      limits: { fileSize: 20 * 1024 * 1024 },
    }),
  )
  upload(
    @UploadedFile() file?: UploadedEvidenceFile,
    @Body('taskCode') taskCode?: string,
    @Body('alarmEventCode') alarmEventCode?: string,
    @Body('deviceCode') deviceCode?: string,
  ) {
    if (!file) {
      throw new BadRequestException('请选择需要上传的证据文件')
    }
    return this.javaClient.postMultipart('/evidence', file, {
      taskCode,
      alarmEventCode,
      deviceCode,
    })
  }

  @Post(':evidenceCode/preview-url')
  @Roles('ADMIN', 'OPERATOR')
  previewUrl(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.post(
      `/evidence/${encodeURIComponent(params.evidenceCode)}/preview-url`,
      {},
    )
  }

  @Post(':evidenceCode/download-url')
  @Roles('ADMIN', 'OPERATOR')
  downloadUrl(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.post(
      `/evidence/${encodeURIComponent(params.evidenceCode)}/download-url`,
      {},
    )
  }

  @Delete(':evidenceCode')
  @Roles('ADMIN', 'OPERATOR')
  remove(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.delete(
      `/evidence/${encodeURIComponent(params.evidenceCode)}`,
    )
  }

  @Post(':evidenceCode/restore')
  @Roles('ADMIN', 'OPERATOR')
  restore(@Param() params: EvidenceCodeParamDto) {
    return this.javaClient.post(
      `/evidence/${encodeURIComponent(params.evidenceCode)}/restore`,
      {},
    )
  }
}
```

> **路由顺序**：`search` 必须写在 `:evidenceCode` 之前（上面已满足）。

`evidence.module.ts` 无需改动（仍只注册 Controller）。

`JavaClientService` 已具备 `get` / `post` / `delete` / `postMultipart`，Phase 1 可直接调用。

---

## 11. 前端（完整）

### 11.1 `frontend/src/api/evidence.ts`

```ts
import { authorizedFetch } from '@/api/http'

interface ApiResponse<T> {
  success: boolean
  message: string
  data: T
}

export interface EvidenceSummary {
  evidenceCode: string
  originalFilename: string | null
  assetType: string
  sourceType: string
  taskCode: string | null
  alarmEventCode: string | null
  deviceCode: string | null
  uploadedByName: string | null
  sizeBytes: number
  createdAt: string
  deleted: boolean
}

export interface EvidenceDetail extends EvidenceSummary {
  objectKey: string
  bucket: string
  contentType: string
  uploadedBy: string | null
}

export interface EvidencePage {
  content: EvidenceSummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface EvidenceAccessUrl {
  url: string
  expiresAt: string
}

export interface EvidenceSearchParams {
  page?: number
  size?: number
  taskCode?: string
  alarmEventCode?: string
  deviceCode?: string
  assetType?: string
  sourceType?: string
  startTime?: string
  endTime?: string
  keyword?: string
  includeDeleted?: boolean
}

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await authorizedFetch(url, options)
  const result: ApiResponse<T> = await response.json()
  if (!response.ok || !result.success) {
    throw new Error(result.message || '请求失败')
  }
  return result.data
}

export function searchEvidence(params: EvidenceSearchParams = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return
    query.set(key, String(value))
  })
  return request<EvidencePage>(`/api/evidence/search?${query.toString()}`)
}

export function getEvidenceDetail(evidenceCode: string) {
  return request<EvidenceDetail>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}`,
  )
}

export function createEvidencePreviewUrl(evidenceCode: string) {
  return request<EvidenceAccessUrl>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}/preview-url`,
    { method: 'POST' },
  )
}

export function createEvidenceDownloadUrl(evidenceCode: string) {
  return request<EvidenceAccessUrl>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}/download-url`,
    { method: 'POST' },
  )
}

export function deleteEvidence(evidenceCode: string) {
  return request<null>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}`,
    { method: 'DELETE' },
  )
}

export function restoreEvidence(evidenceCode: string) {
  return request<null>(
    `/api/evidence/${encodeURIComponent(evidenceCode)}/restore`,
    { method: 'POST' },
  )
}
```

### 11.2 `alarm-evidence.ts` 类型增量

给 `EvidenceUpload` / `EvidenceAsset` 增加可选字段，避免任务页报错：

```ts
export interface EvidenceUpload {
  evidenceCode?: string
  objectKey: string
  // ...其余保持
}

export interface EvidenceAsset extends EvidenceUpload {
  evidenceCode?: string
  originalFilename: string | null
  createdAt: string
}
```

### 11.3 路由

在 `frontend/src/router/index.ts` 增加：

```ts
import EvidenceView from '../views/EvidenceView.vue'

// routes 内：
{
  path: '/evidence',
  name: 'evidence',
  component: EvidenceView,
},
```

### 11.4 侧栏

`st-menu-aside/index.vue` 的 `navItems` 中，建议插在设备之后：

```ts
{ to: '/evidence', labelKey: 'nav.evidence', descKey: 'nav.evidenceDesc', roles: [] as string[] },
```

### 11.5 i18n（`zh.js` / `en.js`）

`zh.js`：

```js
nav: {
  // ...
  evidence: '证据中心',
  evidenceDesc: '检索、预览与管理巡检证据',
},
evidence: {
  eyebrow: 'Evidence Center',
  title: '证据中心',
  subtitle: '按任务、告警、设备检索证据，并安全预览与下载。',
  keyword: '关键词',
  taskCode: '任务编号',
  alarmEventCode: '告警编号',
  deviceCode: '设备编号',
  assetType: '类型',
  sourceType: '来源',
  search: '搜索',
  reset: '重置',
  code: '证据编号',
  filename: '文件名',
  uploadedBy: '上传人',
  createdAt: '时间',
  actions: '操作',
  preview: '预览',
  download: '下载',
  delete: '删除',
  restore: '恢复',
  detail: '详情',
  empty: '暂无证据',
  includeDeleted: '包含已删除',
},
```

`en.js` 对应英文字段即可。

### 11.6 `EvidenceView.vue`（完整可运行骨架）

```vue
<template>
  <main class="evidence-page st-page">
    <section class="evidence-panel">
      <header class="panel-header">
        <div>
          <p class="eyebrow">{{ $t('evidence.eyebrow') }}</p>
          <h1>{{ $t('evidence.title') }}</h1>
          <p class="subtitle">{{ $t('evidence.subtitle') }}</p>
        </div>
      </header>

      <form class="filter-form st-panel" @submit.prevent="loadPage(0)">
        <div class="form-grid">
          <label>
            <span>{{ $t('evidence.keyword') }}</span>
            <input v-model.trim="filters.keyword" />
          </label>
          <label>
            <span>{{ $t('evidence.taskCode') }}</span>
            <input v-model.trim="filters.taskCode" />
          </label>
          <label>
            <span>{{ $t('evidence.alarmEventCode') }}</span>
            <input v-model.trim="filters.alarmEventCode" />
          </label>
          <label>
            <span>{{ $t('evidence.deviceCode') }}</span>
            <input v-model.trim="filters.deviceCode" />
          </label>
          <label>
            <span>{{ $t('evidence.assetType') }}</span>
            <select v-model="filters.assetType">
              <option value="">全部</option>
              <option value="IMAGE">IMAGE</option>
              <option value="VIDEO">VIDEO</option>
            </select>
          </label>
          <label class="checkbox">
            <input v-model="filters.includeDeleted" type="checkbox" />
            <span>{{ $t('evidence.includeDeleted') }}</span>
          </label>
        </div>
        <div class="form-actions">
          <button class="primary-button" type="submit" :disabled="loading">
            {{ $t('evidence.search') }}
          </button>
          <button class="secondary-button" type="button" :disabled="loading" @click="resetFilters">
            {{ $t('evidence.reset') }}
          </button>
        </div>
      </form>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

      <div class="table-wrap st-panel">
        <table>
          <thead>
            <tr>
              <th>{{ $t('evidence.code') }}</th>
              <th>{{ $t('evidence.filename') }}</th>
              <th>{{ $t('evidence.assetType') }}</th>
              <th>{{ $t('evidence.sourceType') }}</th>
              <th>{{ $t('evidence.taskCode') }}</th>
              <th>{{ $t('evidence.alarmEventCode') }}</th>
              <th>{{ $t('evidence.deviceCode') }}</th>
              <th>{{ $t('evidence.uploadedBy') }}</th>
              <th>{{ $t('evidence.createdAt') }}</th>
              <th>{{ $t('evidence.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && rows.length === 0">
              <td colspan="10">{{ $t('evidence.empty') }}</td>
            </tr>
            <tr v-for="row in rows" :key="row.evidenceCode">
              <td>{{ row.evidenceCode }}</td>
              <td>{{ row.originalFilename || '-' }}</td>
              <td>{{ row.assetType }}</td>
              <td>{{ row.sourceType }}</td>
              <td>{{ row.taskCode || '-' }}</td>
              <td>{{ row.alarmEventCode || '-' }}</td>
              <td>{{ row.deviceCode || '-' }}</td>
              <td>{{ row.uploadedByName || '-' }}</td>
              <td>{{ formatTime(row.createdAt) }}</td>
              <td class="actions">
                <button type="button" @click="openDetail(row.evidenceCode)">
                  {{ $t('evidence.detail') }}
                </button>
                <button
                  v-if="canOperate && !row.deleted"
                  type="button"
                  @click="preview(row.evidenceCode)"
                >
                  {{ $t('evidence.preview') }}
                </button>
                <button
                  v-if="canOperate && !row.deleted"
                  type="button"
                  @click="download(row.evidenceCode)"
                >
                  {{ $t('evidence.download') }}
                </button>
                <button
                  v-if="canOperate && !row.deleted"
                  type="button"
                  @click="remove(row.evidenceCode)"
                >
                  {{ $t('evidence.delete') }}
                </button>
                <button
                  v-if="canOperate && row.deleted"
                  type="button"
                  @click="restore(row.evidenceCode)"
                >
                  {{ $t('evidence.restore') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pager">
        <button type="button" :disabled="page <= 0 || loading" @click="loadPage(page - 1)">
          Prev
        </button>
        <span>{{ page + 1 }} / {{ Math.max(totalPages, 1) }}</span>
        <button
          type="button"
          :disabled="page + 1 >= totalPages || loading"
          @click="loadPage(page + 1)"
        >
          Next
        </button>
      </div>
    </section>

    <aside v-if="detail" class="detail-drawer st-panel">
      <header>
        <h2>{{ detail.evidenceCode }}</h2>
        <button type="button" @click="detail = null">×</button>
      </header>
      <dl>
        <dt>{{ $t('evidence.filename') }}</dt>
        <dd>{{ detail.originalFilename || '-' }}</dd>
        <dt>{{ $t('evidence.assetType') }}</dt>
        <dd>{{ detail.assetType }}</dd>
        <dt>{{ $t('evidence.sourceType') }}</dt>
        <dd>{{ detail.sourceType }}</dd>
        <dt>{{ $t('evidence.taskCode') }}</dt>
        <dd>{{ detail.taskCode || '-' }}</dd>
        <dt>{{ $t('evidence.deviceCode') }}</dt>
        <dd>{{ detail.deviceCode || '-' }}</dd>
      </dl>
      <div v-if="previewUrl" class="preview">
        <img
          v-if="detail.assetType === 'IMAGE'"
          :src="previewUrl"
          :alt="detail.originalFilename || detail.evidenceCode"
        />
        <video v-else :src="previewUrl" controls />
      </div>
    </aside>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { authenticationState } from '@/auth/keycloak'
import {
  createEvidenceDownloadUrl,
  createEvidencePreviewUrl,
  deleteEvidence,
  getEvidenceDetail,
  restoreEvidence,
  searchEvidence,
  type EvidenceDetail,
  type EvidenceSummary,
} from '@/api/evidence'

const loading = ref(false)
const errorMessage = ref('')
const rows = ref<EvidenceSummary[]>([])
const page = ref(0)
const size = ref(20)
const totalPages = ref(0)
const detail = ref<EvidenceDetail | null>(null)
const previewUrl = ref('')

const filters = reactive({
  keyword: '',
  taskCode: '',
  alarmEventCode: '',
  deviceCode: '',
  assetType: '',
  includeDeleted: false,
})

const canOperate = computed(() =>
  authenticationState.roles.some((role) =>
    ['ADMIN', 'OPERATOR'].includes(role),
  ),
)

async function loadPage(nextPage: number) {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await searchEvidence({
      page: nextPage,
      size: size.value,
      keyword: filters.keyword || undefined,
      taskCode: filters.taskCode || undefined,
      alarmEventCode: filters.alarmEventCode || undefined,
      deviceCode: filters.deviceCode || undefined,
      assetType: filters.assetType || undefined,
      includeDeleted: filters.includeDeleted || undefined,
    })
    rows.value = result.content
    page.value = result.page
    totalPages.value = result.totalPages
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '加载失败'
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.taskCode = ''
  filters.alarmEventCode = ''
  filters.deviceCode = ''
  filters.assetType = ''
  filters.includeDeleted = false
  loadPage(0)
}

async function openDetail(evidenceCode: string) {
  detail.value = await getEvidenceDetail(evidenceCode)
  previewUrl.value = ''
}

async function preview(evidenceCode: string) {
  const access = await createEvidencePreviewUrl(evidenceCode)
  if (!detail.value || detail.value.evidenceCode !== evidenceCode) {
    detail.value = await getEvidenceDetail(evidenceCode)
  }
  previewUrl.value = access.url
}

async function download(evidenceCode: string) {
  const access = await createEvidenceDownloadUrl(evidenceCode)
  window.open(access.url, '_blank', 'noopener')
}

async function remove(evidenceCode: string) {
  await deleteEvidence(evidenceCode)
  await loadPage(page.value)
}

async function restore(evidenceCode: string) {
  await restoreEvidence(evidenceCode)
  await loadPage(page.value)
}

function formatTime(value: string) {
  return new Date(value).toLocaleString()
}

onMounted(() => loadPage(0))
</script>

<style scoped>
.evidence-page {
  display: grid;
  gap: 1rem;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 0.75rem;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}
.detail-drawer {
  position: fixed;
  right: 1rem;
  top: 6rem;
  width: min(420px, 92vw);
  max-height: 80vh;
  overflow: auto;
  z-index: 20;
}
.preview img,
.preview video {
  width: 100%;
  margin-top: 0.75rem;
}
.pager {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
</style>
```

### 11.7 `DroneView.vue` 兼容要点

1. 旧 `getEvidence()` 仍可用（返回数组）；响应多了 `evidenceCode`。
2. 预览/下载不要再依赖 `publicPath` 直链；改为：

```ts
import { createEvidencePreviewUrl, createEvidenceDownloadUrl } from '@/api/evidence'

async function openEvidence(item: EvidenceAsset) {
  if (!item.evidenceCode) {
    // 兼容历史数据：仍可临时走 publicPath
    window.open(item.publicPath, '_blank')
    return
  }
  const access = await createEvidencePreviewUrl(item.evidenceCode)
  window.open(access.url, '_blank', 'noopener')
}
```

3. 上传成功后刷新列表，无需改 FormData 结构。

---

## 12. 联调检查清单（代码落地后）

```bash
# Java
cd backend-java && mvn -q test -Dtest=EvidenceQueryServiceTest,EvidenceCommandServiceTest,EvidenceAccessServiceTest

# Node
cd backend-node && npm test   # 或至少 tsc

# Frontend
cd frontend && npm test && npx vue-tsc --noEmit
```

手工：

1. `GET /api/evidence?taskCode=TASK-001` → 数组
2. `GET /api/evidence/search?page=0&size=20` → 分页
3. `POST /api/evidence/{code}/preview-url` → 短链可打开
4. `DELETE` 后普通搜索不可见；`includeDeleted=true` 可见；`restore` 后恢复
5. 查 `evidence_access_log` 有 PREVIEW/DOWNLOAD/DELETE/RESTORE/UPLOAD

---

## 13. 已知与现仓库半成品的差异

当前仓库可能已有：

- 空壳 `EvidenceQueryService.java` 等（0 字节）
- `EvidenceAssetRepository` 已声明 `JpaSpecificationExecutor`，但实体/Service 尚未对齐
- `EvidenceStorageServiceTest` 仍调用旧 `findEvidence`

**以本文完整代码覆盖为准**，不要在空壳上“局部补丁”导致编译不过。
