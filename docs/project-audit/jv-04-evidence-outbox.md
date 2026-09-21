# JV-04（Evidence Temporal/MinIO outbox）实施说明

适用分支：`fix/v122-remaining`  
只把 **证据落库与 Temporal / MinIO 补偿拆开**：同一事务写 `evidence_outbox`，提交后再起 workflow；上传先写 MinIO，落库失败再删对象，删失败再记 `MINIO_DELETE`。  
**不要**做蓝绿 / Caddy、改已发布 V2–V22、打生产 `v1.2.2`。不要上完整 `PENDING_UPLOAD` 证据状态机。

旧编号：JV-04 证据半边。上一刀是不可变 IMAGE_TAG（`#209`）和 `v1.2.2-rc.29`。告警半边见 [jv-04-alarm-outbox.md](jv-04-alarm-outbox.md)。

---

## 1. 一句话

`EvidenceCommandService.upload` / `EvidenceRegistrationService.createNew` / `EvidenceArchiveService.createJob` 在事务提交前 `WorkflowClient.start`。DB 一回滚，Temporal 已经看到不存在的 evidenceCode / jobCode；MinIO 成功但 INSERT 失败会留下孤儿对象。

本分支：同事务入队 `DERIVATIVE` / `ARCHIVE`。dispatcher 在 `afterCommit` 和定时 drain 里启动 workflow。`WorkflowExecutionAlreadyStartedException` 算成功。归档创建不再在同一事务里标 `FAILED`；drain 满次才改 job。上传：MinIO 在事务外，persist 失败最好努力 `removeObject`，删不掉再写 `MINIO_DELETE`。

---

## 2. 现在怎么坏的

```text
upload @Transactional
        │
        ▼
MinIO putObject
        │
        ▼
INSERT evidence_asset
        │
        ▼
Temporal start（提交前）
        │
        ▼
事务回滚 → workflow 已看到不存在的 code；MinIO 对象孤儿
```

归档 `createJob` 捕获 Temporal 异常后在同一事务标 `FAILED`，瞬时故障没有重试。衍生 `start()` 吞掉所有异常，outbox 无法重试。

审计原文在 [02-java-and-gateway.md](02-java-and-gateway.md) JV-04。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| `PENDING_UPLOAD → AVAILABLE \| FAILED` 证据状态机 | 要改查询/可见性，另开协议 |
| 改 `001_init.sql` / 已发布 V2–V22 | Flyway 只加 V23 |
| 蓝绿 / Caddy | 1.3.0 |
| 打生产 `v1.2.2` | 合入 main 后再打 RC30 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `V23__create_evidence_outbox.sql` | `evidence_outbox` + `(aggregate_code, kind)` 唯一 + drain 索引 |
| `EvidenceCommandService` | MinIO 事务外；persist + `DERIVATIVE`；失败补偿删除 |
| `EvidenceRegistrationService` | 新对象入队 `DERIVATIVE`，不再直接 start |
| `EvidenceArchiveService` | 入队 `ARCHIVE`，创建事务保持 PENDING |
| `EvidenceOutboxDispatcher` | `afterCommit` + `@Scheduled`；满次 ARCHIVE 标 job FAILED |
| `EvidenceDerivativeJobService` | 已存在算成功，其它失败重抛 |
| 对应本文 + 发版说明 | 单测 enqueue / drain / 补偿；Flyway 烟测 V23 |

---

## 5. 怎么确认

```bash
cd backend-java && ../scripts/ci/mvn-with-retry.sh -Dtest=EvidenceCommandServiceTest,EvidenceRegistrationServiceTest,EvidenceArchiveServiceTest,EvidenceOutboxDispatcherTest,FlywayEmptyMysqlSchemaTest test
```

- 上传成功只 enqueue，不直接 start；persist 失败会 `removeObject`
- 登记新 objectKey 写 `DERIVATIVE`；重复 objectKey 不写
- 建归档 job 保持 PENDING，只写 `ARCHIVE` outbox
- Temporal 失败会 `attempts++`；已 started 算 SENT；ARCHIVE 满次改 job FAILED
- 空 MySQL Flyway 有 V23 和 `evidence_outbox`

---

## 6. 再下一刀

本 RC 已同时做 Admin 头像落盘、vitest mocker、digest manifest。剩下是运维（Keycloak 旧账号、操作日志历史）和 1.3.0 项（蓝绿、Redis 跨副本限流）。不要打生产 `v1.2.2`，合入 `main` 后打 **`v1.2.2-rc.30`**。
