# SkyTrace 水平扩展指南

说明各组件在「多副本」下的能力边界与推荐做法。本地默认仍是单实例 Compose；
本文面向预发/生产扩容。

## 总览

| 组件 | 能否水平扩展 | 关键前提 |
| --- | --- | --- |
| Gateway | 可以 | 无状态；限流靠 Redis |
| backend-node（HTTP） | 可以 | 无状态 BFF |
| backend-node（Socket.IO） | 可以 | **必须** `SOCKETIO_REDIS_ADAPTER=true` + 共享 Redis |
| backend-java（HTTP/API） | 可以 | 无状态；会话不在 JVM |
| backend-java（MQTT 订阅） | **慎扩** | 多订阅者会重复消费同一消息；保持 **1 个 MQTT 消费者** 或改共享订阅/选主 |
| Temporal Worker（同 Java 进程） | 可以 | 多 Worker 同 Task Queue 由 Temporal 调度 |
| Mosquitto | 单节点够用演示 | 生产建议 EMQX 集群 + TLS |
| MySQL / Redis / RabbitMQ / MinIO | 各自集群方案 | 超出本文；业务侧已按服务名访问 |

## 1. Socket.IO 多实例（已实现）

实现：`backend-node/src/realtime/redis-io.adapter.ts`，在 `main.ts` 启动时挂载。

```bash
# deploy/.env
SOCKETIO_REDIS_ADAPTER=true   # 默认
REDIS_HOST=redis
REDIS_PORT=6379
```

行为：

- 任一 Node 实例从 Rabbit fanout 消费后 `server.emit('alarm.created'|'device.telemetry')`
- Redis pub/sub 把事件同步到其它 Node 实例上的浏览器连接
- Redis 不可用时**回退内存 adapter**（仅单实例安全）；日志会打 warn

扩容示例（概念）：

```bash
docker compose up -d --scale backend-node=2
# 注意：当前 compose 固定 container_name，生产镜像部署请用无固定名的编排
```

验证：起两个 Node，只让其中一个连上 Rabbit 消费队列；连到另一个 Node 的前端仍应收到 socket 事件。

## 2. backend-java

- **REST / Temporal Activity / Rabbit 告警消费**：可多副本（Rabbit 竞争消费，Temporal 由服务端分片）。
- **MQTT `DeviceMqttSubscriber`**：默认 **只跑 1 个** 带 `MQTT_ENABLED=true` 的实例。
  - 若两个 Java 都订阅 `device/+/telemetry`，会各自写 Redis、各推一条 Rabbit → 前端双点。
  - 真要多活：改用 Mosquitto Shared Subscription（`$share/skytrace/...`）或选主（只有 leader 订阅）。本仓库尚未内置选主。

建议：StatefulSet / Deployment 中把 MQTT 订阅放到单独 Deployment `replicas: 1`，API Worker 另扩。

## 3. Temporal 工作流（已加厚一档）

`InspectionWorkflow` 现行为：

1. `createTaskIfAbsent` + **`assertStartable`**（终态拒绝再启）
2. 置 `RUNNING`，记录 `startedAtEpochMs`
3. `Workflow.await(24h)` 等待 `complete` / `cancel` Signal；超时 → **`TIMED_OUT`**
4. Signal **幂等**（已结束后忽略重复 complete/cancel）
5. Query：`status` / `lastAlarmEventCode` / `startedAtEpochMs` / `finishReason`

扩 Worker：多个 Java 进程连同一 `TEMPORAL_TASK_QUEUE` 即可；不要把业务状态只放在本地内存。

仍未做（有意留白）：飞控逐步 Activity、证据归档编排、按任务计划时长动态超时——见
[temporal-integration.md](./temporal-integration.md)。

## 4. MQTT 安全与接入（认证已有，TLS 可选）

| 能力 | 状态 |
| --- | --- |
| 禁止匿名 | `allow_anonymous false` |
| 用户名密码 | entrypoint 生成 `password_file` |
| ACL | `backend` 只读上行；`device-sim` 只写 heartbeat/status/telemetry |
| TLS mqtts | 可选 overlay：`./scripts/skytrace.sh mqtt-tls-start` |

真机下一阶段：每设备独立账号、按 `deviceCode` ACL、受信 CA、密钥托管。

## 5. 推荐生产拓扑（简图）

```text
                  ┌─ gateway ×N
客户端 ─ LB ──────┼─ node ×N  (Socket.IO ↔ Redis adapter)
                  └─ java-api ×N
                        │
           java-mqtt ×1 ┼─ MQTT broker (TLS)
                        │
                   temporal workers ⊆ java-api 或独立 ×N
                        │
              MySQL / Redis / Rabbit / MinIO / Qdrant
```

## 6. 相关文档

- [mqtt-device-sim-guide.md](./mqtt-device-sim-guide.md) — 认证 ACL、TLS、遥测
- [temporal-integration.md](./temporal-integration.md)
- [ops.md](./ops.md) / [data-governance.md](./data-governance.md)
