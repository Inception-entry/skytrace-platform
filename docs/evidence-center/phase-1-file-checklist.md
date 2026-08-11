# 证据中心 Phase 1：逐文件改动清单

> 本清单对应 [Phase 1：查询、安全访问与软删除](./phase-1-foundation.md)。
> 目标是把“任务页里的附件能力”升级成第一版独立证据中心，同时保证现有任务页不被打断。
>
> **需要完整可粘贴代码时，直接看**
> [phase-1-implementation-code.md](./phase-1-implementation-code.md)。

## 使用方式

执行顺序建议：

1. 先做 `backend-java` 的 migration、实体和查询能力。
2. 再做 `backend-node` 的 DTO 与透传。
3. 再做 `frontend` 的 `/evidence` 页面。
4. 最后回头兼容 `DroneView.vue` 的旧证据面板。

每个文件的目标实现以 [phase-1-implementation-code.md](./phase-1-implementation-code.md) 同路径代码为准。

---

## 一、数据库与配置层

### 1. 修改 [backend-java/src/main/resources/application.yml](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/application.yml)

改动目标：

- 在 `app.minio` 下新增 presign 和桶公开策略相关配置。
- 维持现有 `evidence-bucket` 不变。

需要新增的配置项：

- `public-read-enabled`
- `presign-preview-ttl-seconds`
- `presign-download-ttl-seconds`

检查点：

- 默认值应对本地兼容，生产默认不能公开读。

### 2. 新增 [backend-java/src/main/resources/db/migration/V10__upgrade_evidence_asset.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V10__upgrade_evidence_asset.sql)

职责：

- 给 `evidence_asset` 补 Phase 1 必需字段。

至少包含：

- `evidence_code`
- `asset_type`
- `source_type`
- `device_code`
- `uploaded_by`
- `uploaded_by_name`
- `deleted`
- `deleted_at`
- `deleted_by`
- `deleted_by_name`

还要做的事：

- 给历史数据回填 `evidence_code`
- 建唯一索引
- 补 `deleted + created_at` 等必要索引

### 3. 新增 [backend-java/src/main/resources/db/migration/V11__create_evidence_access_log.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V11__create_evidence_access_log.sql)

职责：

- 创建证据访问日志表。

至少包含：

- `evidence_id`
- `evidence_code`
- `action`
- `actor_id`
- `username`
- `roles`
- `request_id`
- `client_ip`
- `created_at`

---

## 二、Java 后端：对象存储与主数据模型

### 4. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/MinioProperties.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/MinioProperties.java)

改动目标：

- 让 Phase 1 的安全访问策略可配置。

新增字段：

- `publicReadEnabled`
- `presignPreviewTtlSeconds`
- `presignDownloadTtlSeconds`

要求：

- 保留现有 getter/setter 风格
- 默认值与 `application.yml` 对齐

### 5. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/MinioConfig.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/MinioConfig.java)

改动目标：

- 如果后续 `EvidenceAccessService` 需要 `MinioClient` 的 presign 能力，这里通常不需要大改。
- 只确认当前构造方式能支持 `getPresignedObjectUrl`。

检查项：

- 不需要额外 Bean 时可只保留现状
- 如果需要公共工具类，可在这里或 `service/` 层补包装

### 6. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java)

改动目标：

- 把当前“对象元数据表映射”升级成 Phase 1 主实体。

新增字段：

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

实现要求：

- `assetType` / `sourceType` 使用枚举存储
- `deleted` 默认 `false`
- 不要破坏现有 `createdAt` / `updatedAt`

### 7. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAssetType.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAssetType.java)

职责：

- 定义 `IMAGE`、`VIDEO`

### 8. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceSourceType.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceSourceType.java)

职责：

- 定义 `MANUAL_UPLOAD`
- 预留 `AI_DETECTION`、`VIDEO_FRAME`、`SYSTEM_GENERATED`

Phase 1 要求：

- 即使前两者暂未完全用到，也要先把枚举固定住

### 9. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAccessLog.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAccessLog.java)

职责：

- 映射 `evidence_access_log`

至少包含：

- `evidenceId`
- `evidenceCode`
- `action`
- `actorId`
- `username`
- `roles`
- `requestId`
- `clientIp`
- `createdAt`

---

## 三、Java 后端：Repository 层

### 10. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceAssetRepository.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceAssetRepository.java)

改动目标：

- 同时支持旧任务页数组查询和新证据中心分页查询。

具体改动：

- 继承 `JpaSpecificationExecutor<EvidenceAsset>`
- 新增 `findByEvidenceCode(String evidenceCode)`
- 旧查询方法改成默认过滤 `deleted=false`

建议保留的方法：

- `findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc`
- `findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc`
- `findByTaskCodeAndAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc`

### 11. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceAccessLogRepository.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceAccessLogRepository.java)

职责：

- 保存证据访问日志

Phase 1 最小要求：

- 先继承 `JpaRepository<EvidenceAccessLog, Long>`
- 不必过早加复杂查询方法

---

## 四、Java 后端：DTO 层

### 12. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceAssetResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceAssetResponse.java)

改动目标：

- 这个 DTO 继续服务旧 `GET /evidence` 数组接口

建议改动：

- 增加 `evidenceCode`
- 增加 `assetType`
- 增加 `sourceType`
- 增加 `deviceCode`

要求：

- 保持老页面能兼容使用
- 不要把分页字段塞进这个 DTO

### 13. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceUploadResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceUploadResponse.java)

改动目标：

- 上传结果要返回新业务编号

新增字段建议：

- `evidenceCode`
- `assetType`
- `sourceType`

注意：

- `publicPath` 在 Phase 1 仍可保留给旧页面兼容，但新页面不要继续依赖它

### 14. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceSearchRequest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceSearchRequest.java)

职责：

- 封装分页搜索入参

字段建议：

- `page`
- `size`
- `taskCode`
- `alarmEventCode`
- `deviceCode`
- `assetType`
- `sourceType`
- `startTime`
- `endTime`
- `keyword`
- `includeDeleted`

### 15. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceSummaryResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceSummaryResponse.java)

职责：

- 服务 `/search` 列表项

字段建议：

- `evidenceCode`
- `originalFilename`
- `assetType`
- `sourceType`
- `taskCode`
- `alarmEventCode`
- `deviceCode`
- `sizeBytes`
- `createdAt`
- `deleted`

### 16. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceDetailResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceDetailResponse.java)

职责：

- 服务详情接口

字段建议：

- 列表字段全部包含
- 再补 `objectKey`
- `bucket`
- `contentType`
- `uploadedBy`
- `uploadedByName`

### 17. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidencePageResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidencePageResponse.java)

职责：

- 与后台审计页统一分页结构

字段固定：

- `content`
- `totalElements`
- `totalPages`
- `page`
- `size`

### 18. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceAccessUrlResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceAccessUrlResponse.java)

职责：

- 返回短时效地址

字段：

- `url`
- `expiresAt`

---

## 五、Java 后端：Service 层拆分

### 19. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceStorageService.java)

改动目标：

- 从“全都做”的胖类收缩为“对象存储读写工具 + 上传底层能力”

要移出的职责：

- 分页搜索
- 详情查询
- presign 地址签发
- 软删除/恢复业务语义
- 访问日志记录

要保留或重构的职责：

- 校验文件类型
- `contentType -> assetType` 派生
- 上传对象到 MinIO
- 生成对象键

还要改的点：

- `ensureBucket()` 默认不再自动写公开读策略
- 如果 `publicReadEnabled=true` 才允许写公开策略

### 20. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java)

职责：

- 提供 `search(...)`
- 提供 `detail(evidenceCode)`
- 提供旧任务页的轻量列表包装

要求：

- 新分页查询走 `JpaSpecificationExecutor`
- 旧数组查询继续走轻量 Repository 方法

### 21. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceCommandService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceCommandService.java)

职责：

- `upload(...)`
- `softDelete(evidenceCode)`
- `restore(evidenceCode)`

上传时要补的业务字段：

- `evidenceCode`
- `assetType`
- `sourceType=MANUAL_UPLOAD`
- `uploadedBy`
- `uploadedByName`

### 22. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceAccessService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceAccessService.java)

职责：

- `createPreviewUrl(evidenceCode)`
- `createDownloadUrl(evidenceCode)`

要求：

- 找不到证据时抛 404 语义错误
- `deleted=true` 默认不给签发 URL
- 生成 URL 前先记录访问日志

### 23. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceAccessLogService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceAccessLogService.java)

职责：

- 持久化 `PREVIEW`、`DOWNLOAD`、`DELETE`、`RESTORE` 等动作

建议方法：

- `recordPreview(...)`
- `recordDownload(...)`
- `recordDelete(...)`
- `recordRestore(...)`

### 24. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceActorContextService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceActorContextService.java)

职责：

- 统一从 Spring Security / request 中提取：
  - `actorId`
  - `username`
  - `roles`
  - `requestId`
  - `clientIp`

原因：

- 避免在 `EvidenceCommandService` 和 `EvidenceAccessLogService` 中复制 `AuditInterceptor` 的取值逻辑

---

## 六、Java 后端：Controller 与审计

### 25. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java)

改动目标：

- 保留旧接口
- 新增新接口

最终应包含：

- `GET /evidence`
- `POST /evidence`
- `GET /evidence/search`
- `GET /evidence/{evidenceCode}`
- `POST /evidence/{evidenceCode}/preview-url`
- `POST /evidence/{evidenceCode}/download-url`
- `DELETE /evidence/{evidenceCode}`
- `POST /evidence/{evidenceCode}/restore`

注意：

- 旧 `GET /evidence` 仍返回数组
- 新搜索接口返回分页对象

### 26. 修改 [backend-java/src/main/java/com/skytrace/backend/audit/AuditActionResolver.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/audit/AuditActionResolver.java)

改动目标：

- 让审计中心识别 Phase 1 新增的证据操作

新增动作建议：

- `EVIDENCE_UPLOAD`
- `EVIDENCE_DELETE`
- `EVIDENCE_RESTORE`
- `EVIDENCE_PREVIEW_URL`
- `EVIDENCE_DOWNLOAD_URL`

说明：

- 它审计的是 API 动作
- 不是 MinIO 真正文件内容访问

---

## 七、Java 后端：测试

### 27. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceQueryServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceQueryServiceTest.java)

覆盖重点：

- `includeDeleted=false`
- `includeDeleted=true`
- 任务页轻量查询默认不返回已删记录

### 28. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceCommandServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceCommandServiceTest.java)

覆盖重点：

- 上传时生成 `evidenceCode`
- 上传时正确派生 `assetType`
- 软删除
- 恢复

### 29. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceAccessServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceAccessServiceTest.java)

覆盖重点：

- 预览 URL 签发
- 下载 URL 签发
- 已删除证据不能签发

### 30. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/EvidenceControllerIntegrationTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/EvidenceControllerIntegrationTest.java)

覆盖重点：

- `/search` 返回分页结构
- `/restore` 能恢复
- 旧 `/evidence` 仍返回数组

---

## 八、Node BFF

### 31. 新增 [backend-node/src/evidence/dto/search-evidence.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/dto/search-evidence.dto.ts)

职责：

- 校验分页搜索参数

字段建议：

- `page`
- `size`
- `taskCode`
- `alarmEventCode`
- `deviceCode`
- `assetType`
- `sourceType`
- `startTime`
- `endTime`
- `keyword`
- `includeDeleted`

### 32. 新增 [backend-node/src/evidence/dto/evidence-code.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/dto/evidence-code.dto.ts)

职责：

- 如果你想把路径参数校验抽出来，可用这个 DTO

可选：

- 也可以不单建，用 controller 内简单校验

### 33. 修改 [backend-node/src/evidence/evidence.controller.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/evidence.controller.ts)

改动目标：

- 保留旧列表和上传
- 增加 Phase 1 新接口透传

要新增的方法：

- `search()`
- `detail()`
- `previewUrl()`
- `downloadUrl()`
- `remove()`
- `restore()`

注意：

- `/search` 放在 `/:evidenceCode` 之前，避免路由冲突
- 仍保留 `list()` 给任务页

### 34. 修改 [backend-node/src/evidence/evidence.module.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/evidence.module.ts)

改动目标：

- 如果新增 DTO、拦截器或 provider，这里同步接入

Phase 1 通常只需：

- 保持 `JavaClientModule`
- 确认 controller 新接口已注册

---

## 九、前端 API 与路由

### 35. 新增 [frontend/src/api/evidence.ts](/home/xdeg/workspace/skytrace-platform/frontend/src/api/evidence.ts)

职责：

- 封装证据中心新接口

至少包含：

- `searchEvidence()`
- `getEvidenceDetail()`
- `getEvidencePreviewUrl()`
- `getEvidenceDownloadUrl()`
- `deleteEvidence()`
- `restoreEvidence()`

类型建议：

- `EvidenceSummary`
- `EvidenceDetail`
- `EvidencePage`
- `EvidenceSearchQuery`

### 36. 修改 [frontend/src/api/alarm-evidence.ts](/home/xdeg/workspace/skytrace-platform/frontend/src/api/alarm-evidence.ts)

改动目标：

- 继续服务任务页旧证据面板
- 不要再承担新证据中心的全部接口

Phase 1 需要做的事：

- 保留 `getEvidence()` 和 `uploadEvidence()`
- 可把类型命名整理得更明确
- 任务页若要切换到 presign 下载，可补轻量包装，但不要一次塞满新接口

### 37. 修改 [frontend/src/router/index.ts](/home/xdeg/workspace/skytrace-platform/frontend/src/router/index.ts)

改动目标：

- 新增 `/evidence` 路由

建议：

- 所有角色都可查看时，不必加 `roles` 限制
- 如果要与任务页一致，可允许 `ADMIN` / `OPERATOR` / `VIEWER`

### 38. 修改 [frontend/src/components/st-menu-aside/index.vue](/home/xdeg/workspace/skytrace-platform/frontend/src/components/st-menu-aside/index.vue)

改动目标：

- 在侧边导航中加入“证据中心”

要新增的导航项：

- `to: '/evidence'`
- `labelKey: 'nav.evidenceCenter'`
- `descKey: 'nav.evidenceCenterDesc'`

### 39. 修改 [frontend/src/locales/zh.js](/home/xdeg/workspace/skytrace-platform/frontend/src/locales/zh.js)

改动目标：

- 增加证据中心页面文案

至少新增：

- `nav.evidenceCenter`
- `nav.evidenceCenterDesc`
- `evidenceCenter.title`
- `evidenceCenter.filter.*`
- `evidenceCenter.table.*`
- `evidenceCenter.actions.*`
- `evidenceCenter.detail.*`

### 40. 修改 [frontend/src/locales/en.js](/home/xdeg/workspace/skytrace-platform/frontend/src/locales/en.js)

改动目标：

- 与 `zh.js` 对齐新增英文文案

### 41. 新增 [frontend/src/views/EvidenceView.vue](/home/xdeg/workspace/skytrace-platform/frontend/src/views/EvidenceView.vue)

职责：

- Phase 1 独立证据中心页面

页面最小结构：

- 筛选区
- 列表表格
- 分页器
- 详情抽屉

要实现的交互：

- 查询
- 翻页
- 打开详情
- 预览
- 下载
- 删除
- 恢复

### 42. 修改 [frontend/src/views/DroneView.vue](/home/xdeg/workspace/skytrace-platform/frontend/src/views/DroneView.vue)

改动目标：

- 旧证据面板继续可用
- 但链接打开方式要开始向新安全访问模型过渡

Phase 1 最小改动建议：

- 维持 `getEvidence({ taskCode })`
- 如果保留 `publicPath`，可先不改
- 如果切到 presign，给“打开”按钮接入新 `preview/download` 逻辑

更稳妥的做法：

- 第一刀先不改任务页行为
- 等 `/evidence` 页面稳定后再切换旧面板链接

---

## 十、联调与验收文件

### 43. 修改 [docs/evidence-center/phase-1-foundation.md](/home/xdeg/workspace/skytrace-platform/docs/evidence-center/phase-1-foundation.md)

改动目标：

- 在设计稿中链接到本逐文件清单

### 44. 修改 [docs/evidence-center/README.md](/home/xdeg/workspace/skytrace-platform/docs/evidence-center/README.md)

改动目标：

- 在总览中增加本清单入口

---

## Phase 1 完成定义

- [ ] `V10` / `V11` migration 落库
- [ ] 旧 `GET /api/evidence` 保持数组返回
- [ ] 新增 `/api/evidence/search`
- [ ] 新增 `/api/evidence/{code}`
- [ ] 新增 `preview-url` / `download-url`
- [ ] 新增软删除 / 恢复
- [ ] `/evidence` 页面可用
- [ ] 任务页旧证据面板未回归

