# 证据中心 Phase 1：查询、安全访问与软删除开发指南

> 这是一份“先做基础闭环”的开发指南，不是最终全量方案。
> 当前仓库的证据能力还停留在任务页附件面板；完成本文的 A → B → C → D
> 四个关卡后，SkyTrace 将拥有第一版独立证据中心。

逐文件改动清单与代码级实现见：

- [Phase 1：逐文件改动清单](./phase-1-file-checklist.md)
- [Phase 1：代码级实现参考（可粘贴）](./phase-1-implementation-code.md)

## 做完以后，你会看到什么

1. 业务端新增 `/evidence` 路由和独立页面。
2. 证据中心可以按任务、告警、设备、时间、来源、类型分页搜索。
3. 点击证据可打开详情抽屉，并看到基础元数据。
4. 图片和视频访问改为通过 `preview-url` / `download-url` 获取短时效地址。
5. 删除证据不会立即删对象，而是软删除并可恢复。
6. 查看、下载、删除、恢复证据会写入 `evidence_access_log`。
7. 任务页里的旧证据面板仍然可用，不会被分页接口直接打断。

这一阶段的目标链路只有这一条：

```text
EvidenceView.vue            backend-node                backend-java                  MySQL / MinIO
      │                          │                           │                              │
      │ GET /api/evidence/search │                           │                              │
      ├──────────────► DTO 校验 ─┼──────────────► search()   ├──── Page query evidence ───►│
      │                          │                           │◄──────── paged result ──────┤
      │◄──────── paged result ───┼◄──────────────────────────┤                              │
      │                          │                           │                              │
      │ POST preview-url         │                           │                              │
      ├──────────────► auth ─────┼──────────────► auth       ├──── log access + presign ──►│
      │◄──────── signed URL ─────┼◄──────────────────────────┤                              │
      │──────────── browser fetch object from MinIO ───────────────────────────────────────►│
```

## 先看懂当前现状

当前仓库的证据能力已经完成了“上传到 MinIO + 写元数据 + 任务页查看”的最小闭环，但也有 4 个明显限制：

1. [EvidenceStorageService](../../backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java)
   同时负责上传、桶创建、对象地址拼接和查询，职责过重。
2. `GET /evidence` 返回 `List<EvidenceAssetResponse>`，不支持分页。
3. MinIO 桶目前在 `ensureBucket()` 中被自动配置为公开读，不适合生产证据。
4. 任务页 [DroneView.vue](../../frontend/src/views/DroneView.vue) 直接假设证据接口返回数组。

第一阶段不要推翻这些现状，而是从外到内渐进演进。

## 这一阶段的范围

### 必做

- 数据模型补齐 `evidenceCode`、来源、类型、上传人、软删除字段
- 新增分页查询接口
- 新增证据详情接口
- 新增预览地址与下载地址接口
- 新增软删除与恢复接口
- 新增证据访问日志表
- 新增证据中心前端页面

### 不做

- 标签
- 审核状态
- 视频封面
- 图片缩略图
- 批量操作
- 归档包导出
- 哈希去重

## 先冻结外部契约

第一阶段最关键的是“兼容老页面”。因此，先把接口层冻结成下面这样。

### 保留旧接口

保留现有：

```text
GET  /api/evidence
POST /api/evidence
```

语义不变：

- `GET /api/evidence` 继续返回数组
- 只供任务页轻量面板使用
- 默认过滤掉软删除记录

### 新增新接口

新增：

```text
GET    /api/evidence/search
GET    /api/evidence/{evidenceCode}
POST   /api/evidence/{evidenceCode}/preview-url
POST   /api/evidence/{evidenceCode}/download-url
DELETE /api/evidence/{evidenceCode}
POST   /api/evidence/{evidenceCode}/restore
```

### 分页查询参数

统一采用：

```text
page=0
size=20
taskCode=
alarmEventCode=
deviceCode=
assetType=IMAGE|VIDEO
sourceType=MANUAL_UPLOAD|AI_DETECTION|VIDEO_FRAME|SYSTEM_GENERATED
startTime=2026-08-10T00:00:00Z
endTime=2026-08-10T23:59:59Z
keyword=
includeDeleted=false
```

### 分页返回体

```json
{
  "success": true,
  "message": "success",
  "data": {
    "content": [
      {
        "evidenceCode": "EV-20260810-000001",
        "originalFilename": "uav-001-frame-1.jpg",
        "assetType": "IMAGE",
        "sourceType": "MANUAL_UPLOAD",
        "taskCode": "TASK-001",
        "alarmEventCode": null,
        "deviceCode": "UAV-001",
        "sizeBytes": 238192,
        "createdAt": "2026-08-10T09:11:42Z",
        "deleted": false
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "page": 0,
    "size": 20
  },
  "timestamp": "2026-08-10T09:11:43Z"
}
```

### 详情返回体

```json
{
  "success": true,
  "message": "success",
  "data": {
    "evidenceCode": "EV-20260810-000001",
    "objectKey": "TASK-001/4f3f5f1a-27bc-4c71-b8bb-bf0f5f4d2335.jpg",
    "bucket": "skytrace-evidence",
    "assetType": "IMAGE",
    "sourceType": "MANUAL_UPLOAD",
    "contentType": "image/jpeg",
    "originalFilename": "uav-001-frame-1.jpg",
    "sizeBytes": 238192,
    "taskCode": "TASK-001",
    "alarmEventCode": null,
    "deviceCode": "UAV-001",
    "uploadedBy": "0f53f4a1-...",
    "uploadedByName": "operator-a",
    "createdAt": "2026-08-10T09:11:42Z",
    "deleted": false
  },
  "timestamp": "2026-08-10T09:11:43Z"
}
```

### 预览 / 下载地址返回体

```json
{
  "success": true,
  "message": "success",
  "data": {
    "url": "http://localhost:9011/skytrace-evidence/....",
    "expiresAt": "2026-08-10T09:16:43Z"
  },
  "timestamp": "2026-08-10T09:11:43Z"
}
```

`preview-url` 与 `download-url` 分开是为了后续支持：

- 不同过期时间
- 不同 `response-content-disposition`
- 不同访问审计动作

---

## 关卡 A：先把数据模型立住

这一关只做 DB、实体、Repository 和 Service 骨架，不动前端页面。

### A1. 新增 DB migration

新增：

```text
backend-java/src/main/resources/db/migration/V10__upgrade_evidence_asset.sql
backend-java/src/main/resources/db/migration/V11__create_evidence_access_log.sql
```

`V10__upgrade_evidence_asset.sql` 建议内容：

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

`V11__create_evidence_access_log.sql`：

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

第一阶段不要给 `evidence_asset` 一次性加几十个字段，先把真正要用到的补齐。

### A2. 升级实体与枚举

建议新增：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAssetType.java
backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceSourceType.java
backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAccessLog.java
```

并升级：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java
```

`EvidenceAsset` 第一阶段至少新增这些字段：

- `evidenceCode`
- `assetType`
- `sourceType`
- `deviceCode`
- `uploadedBy`
- `uploadedByName`
- `deleted`
- `deletedAt`
- `deletedBy`
- `deletedByName`

建议 `assetType` 不由前端自由传值，而由后端根据 `contentType` 派生：

```text
image/* -> IMAGE
video/* -> VIDEO
```

这样能避免上传参数和实际文件内容不一致。

### A3. 升级 Repository

当前 `EvidenceAssetRepository` 只支持固定条件数组查询，不够分页筛选。

建议改成：

```java
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
}
```

说明：

- 旧任务页继续使用 `findBy...AndDeletedFalseOrderByCreatedAtDesc`
- 新证据中心使用 `JpaSpecificationExecutor`

### A4. 新建 Query / Command / Access 服务

不要继续把所有逻辑塞进 `EvidenceStorageService`。

第一阶段建议拆成：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/service/
├── EvidenceCommandService.java
├── EvidenceQueryService.java
├── EvidenceAccessService.java
├── EvidenceStorageService.java
└── EvidenceAccessLogService.java
```

职责建议：

- `EvidenceStorageService`：只负责 MinIO 上传与对象操作
- `EvidenceCommandService`：上传、删除、恢复
- `EvidenceQueryService`：列表、详情
- `EvidenceAccessService`：presign 预览/下载
- `EvidenceAccessLogService`：显式记录证据访问日志

`EvidenceStorageService` 可以保留原类名，但应缩窄职责。

### A5. 先写后端单测

先验证下列行为：

```text
legacy task query      -> 只返回未删除记录
search includeDeleted=false -> 不返回 deleted=true
search includeDeleted=true  -> 返回 deleted=true
soft delete            -> deleted=true, deletedAt != null
restore                -> deleted=false, deletedAt == null
preview/download url   -> deleted evidence 默认不可访问
```

建议新增：

```text
backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceQueryServiceTest.java
backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceCommandServiceTest.java
backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceAccessServiceTest.java
```

---

## 关卡 B：再把查询接口搭出来

这一关先打通 Java -> Node -> 前端的查询链路。

### B1. Java DTO 设计

建议新增：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/dto/
├── EvidenceSearchRequest.java
├── EvidenceSummaryResponse.java
├── EvidenceDetailResponse.java
├── EvidencePageResponse.java
└── EvidenceAccessUrlResponse.java
```

第一阶段查询请求示例：

```java
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

返回分页结构建议与后台审计页保持一致：

```java
public record EvidencePageResponse(
        List<EvidenceSummaryResponse> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
```

### B2. Java Controller 改造

升级：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java
```

目标接口：

```java
@GetMapping
public ApiResponse<List<EvidenceAssetResponse>> list(...legacy...)

@GetMapping("/search")
public ApiResponse<EvidencePageResponse> search(...)

@GetMapping("/{evidenceCode}")
public ApiResponse<EvidenceDetailResponse> detail(...)

@PostMapping("/{evidenceCode}/preview-url")
public ApiResponse<EvidenceAccessUrlResponse> previewUrl(...)

@PostMapping("/{evidenceCode}/download-url")
public ApiResponse<EvidenceAccessUrlResponse> downloadUrl(...)

@DeleteMapping("/{evidenceCode}")
public ApiResponse<Void> delete(...)

@PostMapping("/{evidenceCode}/restore")
public ApiResponse<Void> restore(...)
```

说明：

- `preview-url` / `download-url` 采用 `POST`，与现有 `AuditInterceptor` 的行为更贴近
- 但真正的证据访问日志仍需单独写 `evidence_access_log`

### B3. Java 安全规则

当前 [ApiSecurityConfig](../../backend-java/src/main/java/com/skytrace/backend/security/ApiSecurityConfig.java)
对 `GET` 默认放给 `ADMIN`、`OPERATOR`、`VIEWER`，而 `POST` / `DELETE`
默认只给 `ADMIN`、`OPERATOR`。

第一阶段证据接口正好可以复用这个边界：

- `GET /evidence/search`：`ADMIN|OPERATOR|VIEWER`
- `GET /evidence/{code}`：`ADMIN|OPERATOR|VIEWER`
- `POST /evidence/{code}/preview-url`：`ADMIN|OPERATOR`
- `POST /evidence/{code}/download-url`：`ADMIN|OPERATOR`
- `DELETE /evidence/{code}`：`ADMIN|OPERATOR`
- `POST /evidence/{code}/restore`：`ADMIN|OPERATOR`

如果要让 `VIEWER` 也能下载，建议单独在 `ApiSecurityConfig` 为
`/evidence/*/download-url` 增加显式放行规则，不要等 Phase 2 再回头拆。

### B4. Node BFF DTO 与透传

建议新增：

```text
backend-node/src/evidence/dto/
├── search-evidence.dto.ts
└── evidence-action.dto.ts
```

`SearchEvidenceDto` 风格参考
[backend-node/src/admin/dto/list-audit-log.dto.ts](../../backend-node/src/admin/dto/list-audit-log.dto.ts)：

```ts
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
}
```

`backend-node/src/evidence/evidence.controller.ts` 目标形态：

```ts
@Get()
list(...)

@Get('search')
search(@Query() query: SearchEvidenceDto)

@Get(':evidenceCode')
detail(...)

@Post(':evidenceCode/preview-url')
previewUrl(...)

@Post(':evidenceCode/download-url')
downloadUrl(...)

@Delete(':evidenceCode')
remove(...)

@Post(':evidenceCode/restore')
restore(...)
```

### B5. 前端路由和 API

新增：

```text
frontend/src/views/EvidenceView.vue
frontend/src/api/evidence.ts
```

改动：

```text
frontend/src/router/index.ts
frontend/src/components/st-menu-aside/index.vue
frontend/src/locales/en.js
frontend/src/locales/zh.js
```

建议不要继续把新接口放在 `alarm-evidence.ts`。第一阶段就拆出：

```text
frontend/src/api/evidence.ts
```

旧任务页可暂时继续使用 `alarm-evidence.ts` 中的 `getEvidence()` / `uploadEvidence()`。

### B6. `/evidence` 页面最小结构

第一阶段页面只做 4 个区域：

1. 筛选表单
2. 列表表格
3. 分页器
4. 详情抽屉

建议列表字段：

- 证据编号
- 文件名
- 类型
- 来源
- 任务编号
- 告警编号
- 设备编号
- 上传人
- 时间
- 操作

建议页面先做“管理优先”，Phase 1 不必强求花哨卡片流。

---

## 关卡 C：把公开对象访问改成私有 + presign

这一关的重点不是“能下载”，而是“带权限和审计地下载”。

### C1. 先改 MinIO 桶策略

当前 [EvidenceStorageService](../../backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java)
在 `ensureBucket()` 中会自动设置公开读策略：

```text
Action: s3:GetObject
Principal: *
```

第一阶段应改成：

- 桶存在性仍可自动检查
- 但默认不再自动写公开读策略
- 是否公开由显式配置控制

建议在 `MinioProperties` 中新增：

```java
private boolean publicReadEnabled = false;
private int presignPreviewTtlSeconds = 300;
private int presignDownloadTtlSeconds = 300;
```

对应 `application.yml`：

```yaml
app:
  minio:
    public-read-enabled: ${MINIO_PUBLIC_READ_ENABLED:false}
    presign-preview-ttl-seconds: ${MINIO_PRESIGN_PREVIEW_TTL_SECONDS:300}
    presign-download-ttl-seconds: ${MINIO_PRESIGN_DOWNLOAD_TTL_SECONDS:300}
```

### C2. 新建 AccessService

建议新增：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceAccessService.java
```

职责只有三件事：

1. 按 `evidenceCode` 取证据
2. 校验未删除、存在且有访问权限
3. 生成 presigned URL 并写访问日志

方法建议：

```java
public EvidenceAccessUrlResponse createPreviewUrl(String evidenceCode)
public EvidenceAccessUrlResponse createDownloadUrl(String evidenceCode)
```

下载 URL 可额外设置：

```text
response-content-disposition=attachment; filename="original-filename.jpg"
```

### C3. 新建显式访问日志服务

新增：

```text
backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceAccessLogService.java
backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceAccessLogRepository.java
```

写日志时不要只记录 `actorId`，至少要记录：

- `evidenceId`
- `evidenceCode`
- `action`
- `actorId`
- `username`
- `roles`
- `requestId`
- `clientIp`

请求上下文可参考
[AuditInterceptor](../../backend-java/src/main/java/com/skytrace/backend/audit/AuditInterceptor.java)
里取 JWT / requestId / clientIp 的方式。

建议把“当前访问人提取”封装成一个小组件，避免在证据服务里复制粘贴审计逻辑。

### C4. 更新 `AuditActionResolver`

当前 [AuditActionResolver](../../backend-java/src/main/java/com/skytrace/backend/audit/AuditActionResolver.java)
还不认识证据接口。

Phase 1 至少补这些动作：

- `EVIDENCE_UPLOAD`
- `EVIDENCE_DELETE`
- `EVIDENCE_RESTORE`
- `EVIDENCE_PREVIEW_URL`
- `EVIDENCE_DOWNLOAD_URL`

说明：

- 这里审计的是“URL 签发动作”
- `evidence_access_log` 记录的是“证据访问意图”

两者不是重复，而是不同粒度。

### C5. 前端预览交互

前端不要再直接把 `publicPath` 塞到 `<a href>`。

新交互建议：

1. 用户点击“预览”
2. 前端请求 `/api/evidence/{code}/preview-url`
3. 拿到短时效 URL
4. 抽屉中展示图片或视频

下载同理：

1. 用户点击“下载”
2. 请求 `/api/evidence/{code}/download-url`
3. 浏览器打开返回的短时效地址

这样后续加水印代理时前端契约仍然稳定。

---

## 关卡 D：最后补软删除与恢复

这一关要确保“删掉的不影响主流程，但也不会立刻丢失数据”。

### D1. 删除语义

`DELETE /api/evidence/{evidenceCode}` 的第一阶段行为：

- 校验当前用户有权限
- 标记 `deleted=true`
- 设置 `deletedAt`、`deletedBy`、`deletedByName`
- 不删除 MinIO 对象

### D2. 恢复语义

`POST /api/evidence/{evidenceCode}/restore`：

- 校验当前用户有权限
- `deleted=false`
- 清空删除元数据

### D3. 查询默认过滤

所有旧查询默认要加：

```text
deleted = false
```

包括：

- 任务页轻量列表
- 告警轻量列表
- 新的分页搜索接口

只有明确传入 `includeDeleted=true` 时，分页搜索才允许返回软删除记录。

### D4. 删除后的预览 / 下载规则

第一阶段建议：

- 默认不允许对 `deleted=true` 证据签发预览和下载地址
- 只有恢复后才可继续访问

这能降低“被删证据仍被分享链接访问”的灰色行为。

---

## 从外到内的排错方法

按请求链路逐层看，第一处不符合预期的地方就是当前故障层。

| 检查点 | 怎么检查 | 正常结果 | 常见原因 |
| --- | --- | --- | --- |
| DB migration 成功 | 看 Java 启动日志或查表结构 | 新字段和 `evidence_access_log` 存在 | Flyway 版本号冲突 |
| 搜索接口正常 | 调 `/api/evidence/search?page=0&size=20` | 返回分页结构 | Node DTO 未放行、Java controller 未挂新路径 |
| 旧任务页正常 | 进入任务页查看证据 | 仍能展示数组 | 误把 `GET /evidence` 改成分页 |
| 预览地址可签发 | 调 `preview-url` | 返回短时效 URL | MinIO 私有桶配置后仍在拼公开路径 |
| 预览内容可打开 | 浏览器访问 presigned URL | 能看到图或视频 | presign 过期时间太短、对象键错误 |
| 删除后不可见 | 普通列表查询 | 默认不返回已删记录 | Repository 查询漏了 `deleted=false` |
| 恢复后可见 | restore 后再查 | 记录重新出现 | 删除元数据未清理干净 |
| 访问日志入库 | 查 `evidence_access_log` | 有 PREVIEW/DOWNLOAD 行 | AccessService 没有显式 record |

### 高频坑

**不要一上来把 `GET /api/evidence` 改成分页。**

当前任务页的 `getEvidence()` 明确假设返回数组。直接改接口，最先坏的是 `DroneView.vue`。

**不要把 `evidenceCode` 和 `objectKey` 混用。**

- `evidenceCode`：业务编号
- `objectKey`：MinIO 内部路径

前端和用户沟通只认 `evidenceCode`。

**不要把证据访问审计完全寄托在 `AuditInterceptor` 上。**

真正内容下载发生在 MinIO，不在 Java controller 内。

**删除先软删，不要第一阶段就删对象。**

一旦误删对象，恢复就无从谈起。

---

## 完成定义（Definition of Done）

- [ ] 新增 `V10`、`V11` migration 并通过启动验证
- [ ] `evidence_asset` 增加 `evidenceCode`、类型、来源、上传人、软删除字段
- [ ] 新增 `evidence_access_log`
- [ ] 保留旧 `GET /api/evidence` 数组接口
- [ ] 新增 `GET /api/evidence/search`
- [ ] 新增 `GET /api/evidence/{evidenceCode}`
- [ ] 新增 `preview-url` / `download-url`
- [ ] 新增软删除 / 恢复接口
- [ ] 前端新增 `/evidence` 页面
- [ ] 旧任务页证据面板继续可用
- [ ] MinIO 不再依赖公开桶策略
- [ ] 查看 / 下载 / 删除 / 恢复有审计记录
- [ ] Java、Node、前端测试至少覆盖主链路

## 建议提交顺序

```text
feat(evidence): phase 1 schema and repository foundation
feat(evidence): add search detail and access-log apis
feat(evidence): switch evidence access to presigned urls
feat(frontend): add evidence center page and detail drawer
refactor(frontend): migrate task page evidence links to preview/download apis
```

## 这一阶段先不要做

- 标签系统
- 视频封面和图片缩略图
- 批量操作
- 案件导出
- 内容哈希
- Object Lock

这些全部留给后续阶段。
