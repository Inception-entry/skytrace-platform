# JV-03（Detection 发布等 broker confirm）实施说明

适用分支：`fix/detection-publisher-confirm`  
只让 **detection 发布在 broker ack 之后才返回 `queued`**。nack / 超时 / 无法路由不再假装已入队。  
**不要**做 outbox 表、Temporal/MinIO 事务外移、AI 长连接复用、整栈回滚、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。不要改已发布的 V2–V21。

旧编号：JV-03 publisher confirm。上一刀是 detection DLQ（`#205`）和 `v1.2.2-rc.25`。

---

## 1. 一句话

Java `convertAndSend` 和 AI `exchange.publish` 在 broker 确认前就返回 `queued`。磁盘满、路由失败时调用方以为已经入队。

本分支：Java 开 correlated confirm + returns + mandatory，`DetectionAlarmPublisher` 等到 ack；AI channel `publisher_confirms=True` 且 `mandatory=True`。失败返回 502，不返回 queued。

---

## 2. 现在怎么坏的

```text
HTTP /detections/alarms
        │
        ▼
convertAndSend 写入 client buffer
        │
        ▼
立刻 200 queued
        │
        ▼
broker nack / 不可路由 → 消息其实没进队列
```

审计原文在 [02-java-and-gateway.md](02-java-and-gateway.md) JV-03。消费幂等见 [jv-03-detection-idempotency.md](jv-03-detection-idempotency.md)，DLQ 见 [jv-03-detection-dlq.md](jv-03-detection-dlq.md)。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| outbox 表 / dispatcher | JV-04 |
| AI 每次 publish 复用长连接 | AI-07 其余项 |
| realtime / telemetry 等待 confirm | 不是 detection API |
| 整栈回滚、Admin 头像落盘 | 别的事 |
| 打生产 `v1.2.2` | 别的事 |

无新 Flyway。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `application.yml` | `publisher-confirm-type: correlated`、returns、mandatory |
| `DetectionAlarmPublisher` | 等待 `CorrelationData` ack，检查 returned |
| `BrokerPublishException` + 全局 502 | nack 不返回 queued |
| `detection_publisher.py` | `publisher_confirms=True` + `mandatory=True` |
| 对应本文 + 发版说明 | 单测 ack/nack 和 yaml |

---

## 5. 怎么确认

```bash
cd backend-java && ../scripts/ci/mvn-with-retry.sh -Dtest=DetectionPublisherConfirmTest,DetectionDeadLetterConfigTest,DetectionAlarmListenerTest test
cd backend-ai && uv run pytest tests/test_detection_publisher.py -q
```

- ack 才算 publish 成功
- nack 抛 `BrokerPublishException`
- AI publish 调用带 `publisher_confirms=True` 和 `mandatory=True`

---

## 6. 再下一刀

outbox，或整栈回滚。Admin 头像落盘、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.26`**（不要打在本分支上，不要打生产 `v1.2.2`）。
