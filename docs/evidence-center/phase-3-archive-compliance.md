# 证据中心 Phase 3：归档、导出与合规扩展开发指南

> 这一阶段只应在 Phase 1 和 Phase 2 已稳定上线后推进。
> 它解决的不是“怎么展示证据”，而是“怎么保全、导出和长期管理证据”。

逐文件改动清单与代码级实现见：

- [Phase 3：逐文件改动清单](./phase-3-file-checklist.md)
- [Phase 3：代码级实现参考（可粘贴）](./phase-3-implementation-code.md)
- [证据哈希回填、归档清理与压测 Runbook](./evidence-maintenance-runbook.md)

> 2026-08-11 实现状态：归档、历史哈希回填、受保护的物理清理、前端归档台和真实压测
> 已落地。本文保留设计原理；具体上线开关和操作顺序以 Runbook 为准。

## 做完以后，你会看到什么

1. 可以按任务或告警创建证据归档任务；`CASE` 暂时只保留契约。
2. 系统异步生成 zip 包与清单文件。
3. 清单内包含证据元数据、SHA-256 哈希和导出时间。
4. 证据可标记为 `ACTIVE` / `ARCHIVED`，并为冷热分层预留字段。
5. 可以按策略执行物理清理，而不是永远只做软删除。

最终链路：

```text
Operator / Admin            Spring Boot                    Temporal / archive job           MinIO / MySQL
       │                         │                                     │                           │
       │ create archive job      │                                     │                           │
       ├────────────────────────►│ save archive_job                    │                           │
       │                         ├──── start archive workflow ────────►│                           │
       │                         │                                     ├── collect evidence ─────►│
       │                         │                                     ├── compute manifest ─────►│
       │                         │                                     ├── build zip package ────►│
       │                         │◄──────── save output object key ────┤                           │
       │◄──────── job result ────┤                                     │                           │
```

## 这一阶段的范围

### 必做

- 内容哈希
- 归档任务表
- 归档工作流
- zip 包输出
- manifest 清单
- 归档状态字段
- 清理策略设计

### 可选增强

- Object Lock / WORM
- 签章
- 时间戳保全
- 冷存储桶迁移

## 先冻结 6 条归档契约

### 1. 归档是异步任务，不是同步接口

导出可能涉及：

- 大量对象读取
- 哈希计算
- 清单生成
- zip 打包

因此只能异步执行。

### 2. 归档对象必须有 manifest

导出包至少包含：

- `manifest.json`
- `checksums.sha256`
- `files/...`

不要只给用户一个 zip 而没有清单。

### 3. `contentHash` 必须在证据主表可查询

哈希既服务于导出，也服务于后续保全和查重。

### 4. 软删除不是永久策略

Phase 3 要设计：

- 软删除后多久可清理
- 已归档证据如何处理
- 哪类证据禁止物理删

### 5. 归档范围先支持 2 种

- `TASK`
- `ALARM`
- `CASE` 仅保留枚举，当前接口明确拒绝

不要一开始支持任意复杂条件组合导出。

### 6. 归档产物本身也要是对象存储资产

zip 包和清单文件都存 MinIO，而不是只在本地磁盘生成临时文件。

---

## 关卡 A：补齐哈希与归档主数据

### A1. 新增 migration

建议新增：

```text
backend-java/src/main/resources/db/migration/V16__add_evidence_archive_fields.sql
backend-java/src/main/resources/db/migration/V17__create_evidence_archive_job.sql
```

`V16`：

```sql
ALTER TABLE evidence_asset
  ADD COLUMN content_hash VARCHAR(128) NULL AFTER source_type,
  ADD COLUMN archive_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER review_status,
  ADD COLUMN archive_batch_code VARCHAR(64) NULL AFTER archive_status,
  ADD COLUMN archived_at DATETIME NULL AFTER archive_batch_code;

CREATE INDEX idx_evidence_archive_status_created_at
  ON evidence_asset (archive_status, created_at);
```

`V17`：

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

### A2. 哈希计算策略

哈希来源建议分两步：

1. 新上传对象：衍生 Temporal Activity 异步计算并回写
2. 历史对象：`EvidenceHashBackfillService` 小批量补偿并记录失败原因

第一版不要求上传接口同步算哈希，否则大文件会拖慢主流程。

---

## 关卡 B：引入归档工作流

### B1. 为什么适合用 Temporal

归档任务具备这些特征：

- 执行时间长
- 需要重试
- 需要可视化状态
- 需要失败后可恢复

这正好契合当前仓库已接入的 Temporal。

### B2. 新增工作流建议

新增：

```text
backend-java/src/main/java/com/skytrace/backend/temporal/workflow/EvidenceArchiveWorkflow.java
backend-java/src/main/java/com/skytrace/backend/temporal/activity/EvidenceArchiveActivities.java
backend-java/src/main/java/com/skytrace/backend/evidence/service/EvidenceArchiveService.java
```

工作流步骤建议：

1. 根据 `scopeType + scopeValue` 查询证据集合
2. 过滤已删除或不允许导出的记录
3. 生成 manifest 数据
4. 拉取对象并打包 zip
5. 把 zip 和 manifest 回写 MinIO
6. 更新 `evidence_archive_job`
7. 回写参与归档的 `archiveStatus`

### B2.1 大文件打包的内存边界

当前实现不在 `ByteArrayOutputStream` 中聚合完整 ZIP，而是采用以下生命周期：

1. 在 `app.minio.archive-temp-dir` 下创建任务级唯一临时文件
2. 用固定 64 KiB 缓冲区把 MinIO 原始对象逐段写入 `ZipOutputStream`
3. 关闭 ZIP，确保中央目录完整写入磁盘
4. 使用 MinIO `uploadObject` 直接从文件路径上传
5. 在 `finally` 中删除临时文件，上传失败时同样执行

因此单个大证据文件或大 ZIP 不会被完整加载到 JVM 堆中。运维侧仍需监控临时目录
容量，并清理进程被强制终止时来不及进入 `finally` 的残留文件。

### B3. Job 状态枚举

第一版只定义：

```text
PENDING
RUNNING
COMPLETED
FAILED
```

不要在第一版就引入 `CANCELLED`、`PARTIAL_SUCCESS` 等复杂状态。

---

## 关卡 C：定义归档包格式

### C1. zip 包内部结构

建议：

```text
EV-ARCHIVE-20260810-000001.zip
├── manifest.json
├── checksums.sha256
└── files/
    ├── EV-20260810-000001.jpg
    ├── EV-20260810-000002.mp4
    └── ...
```

### C2. manifest 字段建议

```json
{
  "jobCode": "EV-ARCHIVE-20260810-000001",
  "scopeType": "TASK",
  "scopeValue": "TASK-001",
  "createdAt": "2026-08-10T12:00:00Z",
  "createdBy": "operator-a",
  "totalFiles": 12,
  "totalBytes": 9182312,
  "evidences": [
    {
      "evidenceCode": "EV-20260810-000001",
      "originalFilename": "uav-001-frame-1.jpg",
      "contentType": "image/jpeg",
      "sizeBytes": 238192,
      "contentHash": "sha256:...",
      "taskCode": "TASK-001",
      "alarmEventCode": null
    }
  ]
}
```

### C3. 为什么要单独输出 `checksums.sha256`

因为：

- shell 环境下方便快速核验
- 外部审计系统可能不读 JSON
- zip 解压后可快速跑校验

---

## 关卡 D：定义清理与生命周期

### D1. 当前已经落地的物理清理策略

默认保留 90 天且自动清理关闭。只有软删除、已归档、哈希存在、归档和删除时间都超过
保留期，并且归档 ZIP 重新计算 SHA-256 通过、独立 manifest 与包内 manifest 一致、
当前证据编号/哈希/大小/归档路径逐项匹配的证据才可清理。正式认领会在原子 UPDATE 中
复核候选条件，`PURGING` 期间禁止并发恢复。

### D2. 安全执行顺序

1. 先回填历史 `contentHash`
2. 先完成归档并验证包哈希
3. 先用 `cleanup-preview` 或 `dryRun=true` 观察候选
4. 再由 ADMIN 提供固定确认串做小批量物理清理
5. 每条删除保留 STARTED/SUCCESS/FAILURE 审计和数据库墓碑

### D3. 真实执行类

```text
EvidenceHashBackfillService
EvidenceArchiveIntegrityService
EvidenceCleanupService
EvidenceMaintenanceScheduler
EvidenceMaintenanceController
```

完整候选条件、配置和恢复流程见
[evidence-maintenance-runbook.md](./evidence-maintenance-runbook.md)。

---

## 从外到内的排错方法

| 检查点 | 怎么检查 | 正常结果 | 常见原因 |
| --- | --- | --- | --- |
| 哈希回填正常 | 查 `content_hash` | 新记录逐步补齐 | 异步任务未触发 |
| 归档任务已创建 | 查 `evidence_archive_job` | 有 `PENDING/RUNNING` 记录 | controller 未落库 |
| 工作流正常执行 | 看 Temporal UI | 任务状态变化 | worker 未注册 |
| zip 包可下载 | 调 archive result | 可获得下载地址 | MinIO 输出路径错误 |
| manifest 正常 | 解压 zip | 包含 JSON 清单 | 打包逻辑只收文件未收清单 |
| checksums 正常 | 校验 sha256 | 与 manifest 一致 | 哈希算法或文件名映射错误 |

### 高频坑

**不要用同步 HTTP 直接导出大包。**

几十个视频就足够把接口拖超时。

**不要只输出 zip 不输出清单。**

没有清单的归档包后期很难审计。

**不要部署后立即打开正式物理清理。**

实现已经存在，但默认关闭且 dry-run；必须先归档、先验收、再小批量启用。

---

## 完成定义（Definition of Done）

- [x] `evidence_asset` 支持 `contentHash` 与 `archiveStatus`
- [x] 新增 `evidence_archive_job`
- [x] 支持按任务 / 告警创建归档任务
- [x] Temporal 中可追踪归档工作流、Heartbeat 和有限指数退避
- [x] 归档产物包含 zip、manifest、checksums 和包级 SHA-256
- [x] 归档完成后可在前端查询并下载产物
- [x] 历史哈希回填、清理预览、物理清理和审计链路落地
- [x] 生命周期、清理策略与压测步骤文档化

## 建议提交顺序

```text
feat(evidence): add archive schema and hash fields
feat(evidence): add archive job api and workflow
feat(evidence): generate manifest and zip output
feat(evidence): add hash backfill and retention cleanup
feat(frontend): integrate evidence archive console
test(evidence): add archive load and recovery verification
```

## 这一阶段先不要做

- 一步到位引入对象锁
- 接第三方签章平台
- 全量 OCR / ASR
- 归档完成即绕过保留期自动物理删除源对象

先把归档任务做稳，再处理合规强化。
