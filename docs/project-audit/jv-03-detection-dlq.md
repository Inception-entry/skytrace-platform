# JV-03（Detection 毒消息进 DLQ）实施说明

适用分支：`fix/detection-rabbit-dlq`  
只给 **detection 队列加 DLX/DLQ，listener 有限重试后 `default-requeue-rejected=false`**。毒消息不再无限 requeue。  
**不要**做 outbox、publisher confirm、`queued` 语义、AI 长连接、整栈回滚、Admin 头像落盘、vitest mocker、打生产 `v1.2.2`。不要改已发布的 V2–V21。

旧编号：JV-03 毒消息半边。上一刀是 detection 幂等（`#191`）和 `v1.2.2-rc.24`。

---

## 1. 一句话

listener 抛错时 Spring AMQP 默认会把消息重新入队。坏 JSON / 持续失败的业务异常会把队列打满。

本分支：`skytrace.detection.alarms` 带死信交换机；进程内最多 3 次后拒绝且不 requeue，消息进 `skytrace.detection.alarms.dlq`。幂等命中仍当场 ACK，不进 DLQ。

---

## 2. 现在怎么坏的

```text
无法反序列化 / DB 持续失败
        │
        ▼
@RabbitListener 抛异常
        │
        ▼
default-requeue-rejected=true（默认）
        │
        ▼
同一条毒消息无限重投
```

审计原文在 [02-java-and-gateway.md](02-java-and-gateway.md) JV-03。消费幂等见 [jv-03-detection-idempotency.md](jv-03-detection-idempotency.md)。

---

## 3. 这个 PR 不要做

| 不要做 | 原因 |
| --- | --- |
| outbox / publisher confirm | 各自独立 |
| 改 realtime 队列 | Node 消费，不是 detection listener |
| AI 每次 publish 新建连接 | AI-07 其余项 |
| 整栈回滚、Admin 头像落盘 | 别的事 |
| 打生产 `v1.2.2` | 别的事 |

无新 Flyway。已有 Rabbit 队列如果参数不同，需要删掉旧 `skytrace.detection.alarms` 让 Java 重新 declare（PRECONDITION_FAILED）。

---

## 4. 要改的文件

| 文件 | 做什么 |
| --- | --- |
| `RabbitMqConfig` / `MessagingProperties` | DLX、DLQ、主队列 `x-dead-letter-*` |
| `application.yml` | `default-requeue-rejected: false` + retry max 3 |
| 对应本文 + 发版说明 | 单测队列参数和 yaml 门禁 |

---

## 5. 怎么确认

```bash
cd backend-java && ../scripts/ci/mvn-with-retry.sh -Dtest=DetectionDeadLetterConfigTest,DetectionAlarmListenerTest,AlarmServiceTest test
```

- 主队列参数指向 `skytrace.detection.dlx` / `alarm.dlq`
- yaml：不 requeue，retry enabled，max-attempts 默认 3
- 已有幂等测试仍绿

---

## 6. 再下一刀

outbox / publisher confirm，或整栈回滚。Admin 头像落盘、vitest mocker 仍各自独立。

合入 `main` 后打 **`v1.2.2-rc.25`**（不要打在本分支上，不要打生产 `v1.2.2`）。
