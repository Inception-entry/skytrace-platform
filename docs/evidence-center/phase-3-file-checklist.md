# 证据中心 Phase 3：逐文件改动清单

> 本清单对应 [Phase 3：归档、导出与合规扩展](./phase-3-archive-compliance.md)。
> 前提是 Phase 1、Phase 2 已稳定。

> 完整可粘贴代码见 [phase-3-implementation-code.md](./phase-3-implementation-code.md)。

## 推荐迁移编号顺序

如果沿用前两阶段建议编号，Phase 3 建议使用：

- `V16__add_evidence_archive_fields.sql`
- `V17__create_evidence_archive_job.sql`

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

### 3. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceAsset.java)

新增字段：

- `contentHash`
- `archiveStatus`
- `archiveBatchCode`
- `archivedAt`

### 4. 新增 [backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveStatus.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/domain/EvidenceArchiveStatus.java)

第一版枚举：

- `ACTIVE`
- `ARCHIVED`

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

- 拉取对象
- 打包 zip
- 回写 MinIO

### 16. 修改 [backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceQueryService.java)

新增返回字段：

- `contentHash`
- `archiveStatus`

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

---

## 六、Node BFF

### 24. 新增 [backend-node/src/evidence/dto/create-evidence-archive-job.dto.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/dto/create-evidence-archive-job.dto.ts)

### 25. 修改 [backend-node/src/evidence/evidence.controller.ts](/home/xdeg/workspace/skytrace-platform/backend-node/src/evidence/evidence.controller.ts)

新增透传接口：

- `createArchiveJob()`
- `getArchiveJob()`
- `getArchiveDownloadUrl()`
- `getArchiveManifestUrl()`

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

---

## 八、测试

### 30. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceArchiveServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceArchiveServiceTest.java)

### 31. 新增 [backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceManifestServiceTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/evidence/service/EvidenceManifestServiceTest.java)

### 32. 新增 [backend-java/src/test/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivitiesImplTest.java](/home/xdeg/workspace/skytrace-platform/backend-java/src/test/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivitiesImplTest.java)

### 33. 新增 [frontend/test/evidence-center-phase3.test.js](/home/xdeg/workspace/skytrace-platform/frontend/test/evidence-center-phase3.test.js)

覆盖重点：

- 归档接口路径
- 归档状态轮询
- 下载 zip / manifest

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

---

## Phase 3 完成定义

- [ ] `contentHash` 与 `archiveStatus` 落库
- [ ] 可创建归档任务
- [ ] Temporal 中可追踪归档工作流
- [ ] zip、manifest、checksums 都可生成并下载
- [ ] 前端可查看归档任务状态
- [ ] 清理策略有单独文档或已写入 Phase 3 设计稿

