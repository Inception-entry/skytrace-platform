# JV-04（告警 Temporal/realtime outbox）实施说明

适用分支：`fix/alarm-outbox`  
只把 **告警落库与 Temporal Signal / Java realtime 投递拆开**：同一事务写 `alarm_outbox`，提交后再投。  
**不要**做 Evidence MinIO/workflow outbox、realtime publisher-confirm、AI 长连接复用、整栈回滚、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。不要改已发布的 V2–V21。

旧编号：JV-04 告警半边。上一刀是 detection publisher confirm（`#206`）和 `v1.2.2-rc.26`。

---

## 1. 一句话

`AlarmService` 在事务提交前 Signal Temporal；detection listener 在 `createResult` 返回后直接发 realtime。事务回滚后 Temporal 可能已经看到不存在的告警；进程在提交后、realtime 前崩溃会丢通知。

本分支：HTTP 建告警只入队 `TEMPORAL_SIGNAL`；detection 消费同事务入队 `TEMPORAL_SIGNAL` + `REALTIME`。dispatcher 在 `afterCommit` 和定时 drain 里投递。

---

## 2. 现在怎么坏的

```text
AlarmService.createResult  @Transactional
        │
        ▼
INSERT alarm_event
        │
        ▼
Temporal Signal（提交前）
        │
        ▼
事务回滚 → workflow 已收到不存在的 eventCode
```

detection 路径：`createResult(..., false realtime)` 返回后 listener 再 `publishCreated`。Temporal 仍在事务内；realtime 在事务外但没有持久化重试。

审计原文在 [02-java-and-gateway.md](02-java-and-gateway.md) JV-04。Evidence MinIO/workflow 仍走原路径，本刀不做。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| Evidence MinIO / archive / derivative outbox | JV-04 其余项 |
| realtime 等 broker confirm | 不是告警落库边界 |
| 改 `001_init.sql` / 已发布 V2–V21 | Flyway 只加 V22 |
| 整栈回滚、Admin 头像落盘 | 别的事 |
| 打生产 `v1.2.2` | 别的事 |

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `V22__create_alarm_outbox.sql` | `alarm_outbox` + `(event_code, kind)` 唯一 + drain 索引 |
| `AlarmService` | 同事务 `saveAndFlush` outbox，不再直接 Signal / publish |
| `AlarmOutboxDispatcher` | `afterCommit` + `@Scheduled` drain；失败退避，满次 `FAILED` |
| `DetectionAlarmListener` | `createResult(..., true, true)`，去掉 listener 内 realtime |
| `InspectionAlarmSignaler` | 非 `WorkflowNotFoundException` 重新抛出，给 drain 重试 |
| 对应本文 + 发版说明 | 单测 enqueue / drain；Flyway 烟测 V22 |

HTTP 路径仍不入队 `REALTIME`：Node BFF 自己 Socket.IO 广播。

---

## 5. 怎么确认

```bash
cd backend-java && ../scripts/ci/mvn-with-retry.sh -Dtest=AlarmServiceTest,AlarmOutboxDispatcherTest,InspectionAlarmSignalerTest,DetectionAlarmListenerTest,FlywayEmptyMysqlSchemaTest test
```

- HTTP `create` 只写 `TEMPORAL_SIGNAL`，不立刻调 signaler
- detection 首次插入写两种 outbox；重复 `detectionId` 不写
- Temporal 失败会 `attempts++` 并推迟 `available_at`；缺 workflow 算成功
- 空 MySQL Flyway 有 V22 和 `alarm_outbox`

---

## 6. 再下一刀

整栈回滚，或 Evidence MinIO/workflow outbox。Admin 头像落盘、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.27`**（不要打在本分支上，不要打生产 `v1.2.2`）。
