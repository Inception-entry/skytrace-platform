# JV-03（Detection 消费幂等第一阶段）实施说明

适用分支：`fix/detection-idempotency-key`  
只做 **可选 `detectionId` + `alarm_event.source_detection_id` 唯一键去重**。重投同一条消息不再插入第二条告警，也不再发第二条实时通知。  
**不要**做 outbox、publisher confirm、DLQ、Rabbit 长连接、整栈回滚、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。不要改已发布的 V2–V20。

旧编号：JV-03 / AI-07 的 consumer 半边。上一刀是 Flyway 空库（`#190`）和 `v1.2.2-rc.23`。

---

## 1. 一句话

Detection 消息没有业务幂等键。DB 提交后、ACK 前崩溃会让 Rabbit 重投，再生成一条告警和实时推送。

本分支：AI 按分析/框生成稳定 `detectionId`；Java 写入 `source_detection_id` 唯一键；重复消费直接 ACK，不发 realtime。没有 `detectionId` 的旧消息行为与现在一样。

---

## 2. 现在怎么坏的

```text
AI publish 检测告警（无 ID）
        │
        ▼
Java 每次 new ALARM-<时间>-<随机>
        │
        ▼
commit 成功，ACK 前进程挂了
        │
        ▼
Rabbit 重投 → 第二条告警 + 第二条实时事件
```

审计原文在 [02-java-and-gateway.md](02-java-and-gateway.md) JV-03。AI 侧部分成功重试见 [05-ai-service.md](05-ai-service.md) AI-07，本刀不做连接复用。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| outbox / publisher confirm / `queued` 语义 | 下一阶段 |
| DLQ、有限重试、`default-requeue-rejected` | 毒消息是另一刀 |
| AI 每次 publish 新建连接 | AI-07 其余项 |
| 整栈回滚、Admin 头像落盘 | 别的事 |
| 打生产 `v1.2.2` | 别的事 |

V21 是 additive。相对 `v1.2.1` 现在是 Flyway **V21**。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `V21__alarm_source_detection_id.sql` | 可空列 + 唯一索引（多个 NULL 合法） |
| `AlarmEvent` / `AlarmService` / Listener | 命中唯一键则跳过插入和 realtime |
| `DetectionAlarmPayload` / `analyze.py` | `UUIDv5(analysisId, frame, class, bbox)` |
| Node `CreateAlarmDto` | 透传可选 `detectionId` |
| 对应本文 + 发版说明 | 单测和挂链接 |

---

## 5. 怎么确认

```bash
cd backend-java && ../scripts/ci/mvn-with-retry.sh test
cd backend-ai && uv run pytest tests/test_detection_publisher.py tests/test_vision_analyze.py -q
```

- 同一 `detectionId` 第二次消费不 `save`、不发 realtime
- 没有 `detectionId` 的消息仍可各插一行
- 空 MySQL Flyway 到 V21，且有 `source_detection_id`

---

## 6. 再下一刀

Detection 毒消息 DLQ 已单独实施，见 [jv-03-detection-dlq.md](jv-03-detection-dlq.md)。publisher confirm 见 [jv-03-detection-publisher-confirm.md](jv-03-detection-publisher-confirm.md)。告警 outbox 见 [jv-04-alarm-outbox.md](jv-04-alarm-outbox.md)。下一刀是整栈回滚，或 Evidence MinIO/workflow outbox。Admin 头像落盘、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.24`**（不要打在本分支上，不要打生产 `v1.2.2`）。
