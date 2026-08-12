# 证据中心 Phase 3：逐文件改动清单

> 本清单对应 [Phase 3：归档、导出与合规扩展](./phase-3-archive-compliance.md)。
> 前提是 Phase 1、Phase 2 已稳定。

> 完整可粘贴代码见 [phase-3-implementation-code.md](./phase-3-implementation-code.md)。

## 推荐迁移编号顺序

如果沿用前两阶段建议编号，Phase 3 建议使用：

- `V16__add_evidence_archive_fields.sql`
- `V17__create_evidence_archive_job.sql`
- `V18__add_evidence_maintenance_fields.sql`

---

## 一、数据库与实体

### 1. 新增 [backend-java/src/main/resources/db/migration/V16__add_evidence_archive_fields.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V16__add_evidence_archive_fields.sql)

职责：

- 为证据主表增加归档与哈希字段

至少包含：

- `content_hash`
- `archive_status`
- `archive_batch_code`
- `archived_at`

### 2. 新增 [backend-java/src/main/resources/db/migration/V17__create_evidence_archive_job.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V17__create_evidence_archive_job.sql)

职责：

- 创建归档任务表

至少包含：

- `job_code`
- `scope_type`
- `scope_value`
- `status`
- `output_bucket`
- `output_object_key`
- `manifest_object_key`
- `total_files`
- `total_bytes`
- `created_by`
- `created_by_name`
- `created_at`
- `completed_at`
- `error_message`

### 2.1 新增 [backend-java/src/main/resources/db/migration/V18__add_evidence_maintenance_fields.sql](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/db/migration/V18__add_evidence_maintenance_fields.sql)

职责：

- 保存哈希回填尝试、失败原因和退避依据
- 保存 `PURGING/PURGED` 物理清理状态
- 保存归档包 SHA-256 和最近复核时间
- 为回填与清理候选查询建立索引

### 3. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java)

新增字段：

- `contentHash`
- `archiveStatus`
- `archiveBatchCode`
- `archivedAt`
- `hashBackfillAttemptedAt`
- `hashBackfillError`
- `purgeStartedAt`
- `purgedAt`
- `purgeError`

### 4. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveStatus.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveStatus.java)

当前枚举：

- `ACTIVE`
- `ARCHIVED`
- `PURGING`
- `PURGED`

### 5. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveJob.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveJob.java)

职责：

- 映射归档任务表

### 6. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveJobStatus.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveJobStatus.java)

第一版枚举：

- `PENDING`
- `RUNNING`
- `COMPLETED`
- `FAILED`

---

## 二、Repository 与 DTO

### 7. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceArchiveJobRepository.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceArchiveJobRepository.java)

职责：

- 查归档任务
- 按 `jobCode` 查询

### 8. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceAssetRepository.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/repository/EvidenceAssetRepository.java)

改动目标：

- 增加按 `taskCode` / `alarmEventCode` / `archiveStatus` 聚合归档所需查询

### 9. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/CreateEvidenceArchiveJobRequest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/CreateEvidenceArchiveJobRequest.java)

字段建议：

- `scopeType`
- `scopeValue`

### 10. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceArchiveJobResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceArchiveJobResponse.java)

字段建议：

- `jobCode`
- `status`
- `scopeType`
- `scopeValue`
- `totalFiles`
- `totalBytes`
- `createdAt`
- `completedAt`
- `errorMessage`

### 11. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceArchiveAccessUrlResponse.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/dto/EvidenceArchiveAccessUrlResponse.java)

职责：

- 返回归档包下载地址或 manifest 下载地址

---

## 三、Java Service 层

### 12. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceHashService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceHashService.java)

职责：

- 负责为对象生成 SHA-256
- 支持新对象异步计算
- 支持历史对象补偿

### 13. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceArchiveService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceArchiveService.java)

职责：

- 创建归档任务
- 查询归档任务状态
- 查询归档包地址

### 14. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceManifestService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceManifestService.java)

职责：

- 构建 `manifest.json`
- 构建 `checksums.sha256`

### 15. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceArchivePackageService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceArchivePackageService.java)

职责：

- 以输入流拉取对象，不把原始文件完整读入内存
- 使用固定 64 KiB 缓冲区把 zip 写入配置的磁盘临时目录
- 关闭完整 zip 后，通过 `EvidenceStorageService.uploadObject(...)` 从文件路径回写 MinIO
- 在成功和普通失败路径的 `finally` 中删除临时 zip

配套修改：

- `MinioProperties` 增加 `archiveTempDir`
- `EvidenceStorageService` 增加基于 `UploadObjectArgs.filename(...)` 的本地文件上传方法

### 16. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java)

新增返回字段：

- `contentHash`
- `archiveStatus`

### 16.1 新增维护与完整性服务

- [EvidenceHashBackfillService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceHashBackfillService.java)：小批量认领历史缺口、流式计算哈希、失败退避
- [EvidenceArchiveIntegrityService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceArchiveIntegrityService.java)：物理清理前重算 ZIP SHA-256，比较独立/包内 manifest，并建立证据级校验索引
- [EvidenceCleanupService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceCleanupService.java)：dry-run、完整条件原子认领、manifest 逐项匹配、对象删除、墓碑状态与失败恢复
- [EvidenceMaintenanceAuditService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceMaintenanceAuditService.java)：用独立事务记录每条物理清理
- [EvidenceMaintenanceScheduler.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceMaintenanceScheduler.java)：默认关闭的回填与清理调度

### 16.2 修改 MinIO 与归档完整性边界

- `EvidenceArchivePackageService` 使用固定缓冲区计算 ZIP 自身 SHA-256
- `EvidenceArchiveJob` 保存 `packageContentHash/packageVerifiedAt`
- `EvidenceStorageService` 增加对象存在检查和受保护删除，拒绝删除 `archives/`
- `MinioConfig` 将连接超时收紧到 5 秒，读写大对象仍保留 5 分钟
- `EvidenceArchiveActivitiesImpl` 发送 Heartbeat 并回写包级 SHA-256
- `EvidenceArchiveWorkflowImpl` 使用 10 分钟 Heartbeat timeout 和最多 8 次有限指数退避

---

## 四、Controller 与接口

### 17. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceController.java)

新增接口：

- `POST /evidence/archive-jobs`
- `GET /evidence/archive-jobs/{jobCode}`
- `POST /evidence/archive-jobs/{jobCode}/download-url`
- `POST /evidence/archive-jobs/{jobCode}/manifest-url`

### 18. 修改 [backend-java/src/main/java/com/skytrace/backend/audit/AuditActionResolver.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/audit/AuditActionResolver.java)

新增动作建议：

- `EVIDENCE_ARCHIVE_JOB_CREATE`
- `EVIDENCE_ARCHIVE_DOWNLOAD_URL`
- `EVIDENCE_ARCHIVE_MANIFEST_URL`

### 18.1 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceMaintenanceController.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/EvidenceMaintenanceController.java)

新增 ADMIN 接口：

- `GET /admin/evidence-maintenance/policy`
- `POST /admin/evidence-maintenance/hash-backfill`
- `GET /admin/evidence-maintenance/cleanup-preview`
- `POST /admin/evidence-maintenance/cleanup`

正式清理必须同时提供 `dryRun=false` 与 `confirmation=PURGE_ARCHIVED_EVIDENCE`。

---

## 五、Temporal 工作流

### 19. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceArchiveWorkflow.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceArchiveWorkflow.java)

职责：

- 定义归档工作流接口

### 20. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceArchiveWorkflowImpl.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceArchiveWorkflowImpl.java)

职责：

- 组织清单生成、文件打包、结果回写

### 21. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivities.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivities.java)

### 22. 新增 [backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivitiesImpl.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivitiesImpl.java)

实现建议拆成活动：

- 取归档证据集合
- 计算 / 校验哈希
- 生成 manifest
- 生成 zip
- 回写任务状态

### 23. 修改 [backend-java/src/main/resources/application.yml](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/resources/application.yml)

改动目标：

- 注册 `EvidenceArchiveWorkflowImpl`
- 注册 `evidenceArchiveActivities`
- 增加 `app.minio.archive-temp-dir`
- 可通过 `MINIO_ARCHIVE_TEMP_DIR` 指向容量受控的归档临时磁盘
- 默认使用 `${java.io.tmpdir}/skytrace-evidence-archives`

---

## 六、Node BFF

### 24. 新增 [backend-node/src/evidence/dto/create-evidence-archive-job.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/dto/create-evidence-archive-job.dto.ts)

### 25. 修改 [backend-node/src/evidence/evidence.controller.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/evidence.controller.ts)

新增透传接口：

- `createArchiveJob()`
- `getArchiveJob()`
- `getArchiveDownloadUrl()`
- `getArchiveManifestUrl()`

### 25.1 修改 [backend-node/src/admin/admin.controller.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/admin/admin.controller.ts)

- ADMIN 维护接口完整透传到 Java
- DTO 严格校验批次和 `dryRun=true/false`
- 拼写错误不能被转换成正式清理

---

## 七、前端

### 26. 修改 [frontend/src/api/evidence.ts](/home/xdeg/workspace/skytrace-platform/frontend/src/api/evidence.ts)

新增接口：

- `createEvidenceArchiveJob()`
- `getEvidenceArchiveJob()`
- `getEvidenceArchiveDownloadUrl()`
- `getEvidenceArchiveManifestUrl()`

新增类型：

- `EvidenceArchiveJob`
- `EvidenceArchiveScopeType`

### 27. 修改 [frontend/src/views/EvidenceView.vue](/home/xdeg/workspace/skytrace-platform/frontend/src/views/EvidenceView.vue)

新增能力：

- 按任务/告警/案件发起归档
- 查看归档任务状态
- 下载 zip
- 下载 manifest

### 28. 修改 [frontend/src/locales/zh.js](/home/xdeg/workspace/skytrace-platform/frontend/src/locales/zh.js)

新增文案：

- 归档任务
- 归档状态
- 导出包
- manifest
- 校验文件

### 29. 修改 [frontend/src/locales/en.js](/home/xdeg/workspace/skytrace-platform/frontend/src/locales/en.js)

与 `zh.js` 对齐。

### 29.1 修改 [frontend/nginx.conf](/home/xdeg/workspace/skytrace-platform/frontend/nginx.conf)

- 代理 `/<evidence-bucket>/...` 到 MinIO
- 保留浏览器 Host，确保 SigV4 预签名 URL 可验证
- 本地下载链接不再暴露容器内 `minio:9000` 地址

---

## 八、测试

### 30. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceArchiveServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceArchiveServiceTest.java)

### 31. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceManifestServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceManifestServiceTest.java)

### 32. 新增 [backend-java/src/test/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivitiesImplTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivitiesImplTest.java)

### 32.1 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceArchivePackageServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceArchivePackageServiceTest.java)

覆盖重点：

- 上传时临时 zip 已完整包含 manifest、checksums 和原始证据
- 上传成功后删除临时 zip
- 上传失败后仍删除临时 zip，且不继续上传独立 manifest
- 原始对象读取失败时保留根因、删除临时 zip，且不上传不完整产物

### 32.2 修改 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceStorageServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceStorageServiceTest.java)

覆盖重点：

- MinIO 接收 `UploadObjectArgs` 文件路径
- 桶、对象 key 和 `application/zip` 内容类型正确

### 33. 新增 [frontend/test/evidence-center-phase3.test.js](/home/xdeg/workspace/skytrace-platform/frontend/test/evidence-center-phase3.test.js)

覆盖重点：

- 归档接口路径
- 归档状态轮询
- 下载 zip / manifest

### 33.1 新增维护与权限测试

- `EvidenceHashBackfillServiceTest`
- `EvidenceArchiveIntegrityServiceTest`
- `EvidenceCleanupServiceTest`
- `EvidenceMaintenanceSecurityIntegrationTest`
- `backend-node/test/evidence-maintenance.dto.test.js`

覆盖并发认领、单对象失败隔离、包哈希不一致阻断、物理删除保护、管理员权限和严格布尔输入。

### 33.2 新增 [scripts/ci/verify-evidence-archive.sh](/home/xdeg/workspace/skytrace-platform/scripts/ci/verify-evidence-archive.sh)

- 可调任务证据数量和单文件大小
- 使用不可压缩负载验证真实大 ZIP
- 下载并核对包级与逐文件 SHA-256
- 可选短停 MinIO，要求 Temporal Activity `attempt > 1` 后恢复

---

## 九、策略与文档

### 34. 修改 [docs/evidence-center/phase-3-archive-compliance.md](/home/xdeg/workspace/skytrace-platform/docs/evidence-center/phase-3-archive-compliance.md)

改动目标：

- 链接到本逐文件清单
- 同步迁移编号到 `V16` / `V17`

### 35. 视情况新增 [docs/evidence-center/retention-policy.md](/home/xdeg/workspace/skytrace-platform/docs/evidence-center/retention-policy.md)

职责：

- 单独沉淀保留期、清理与物理删除策略

如果你不想再拆文档，也可以把这部分仍留在 Phase 3 设计稿内。

实际已新增 [evidence-maintenance-runbook.md](./evidence-maintenance-runbook.md)，统一记录候选条件、
保留期、权限、确认串、审计、失败恢复、上线步骤和真实压测结果。

---

## Phase 3 完成定义

- [x] `contentHash`、归档、回填和物理清理状态落库
- [x] 可按任务或告警创建归档任务
- [x] Temporal 中可追踪 Workflow、Heartbeat、有限重试和故障恢复
- [x] zip、manifest、checksums 和包级哈希都可生成并下载
- [x] 大归档不在 JVM 堆中聚合，临时 zip 在成功和失败后都能清理
- [x] 前端可创建、轮询、查询和下载归档任务
- [x] 历史哈希回填支持批次、认领、失败退避
- [x] 物理清理具备保留期、完整性门槛、二次确认、墓碑和审计
- [x] Runbook 记录上线顺序与真实压测结果
