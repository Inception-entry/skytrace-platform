# Temporal / Nexus 实现与演进

Temporal 已在 Spring Boot 中接入，用于巡检工作流和可靠的同步 AI 分析；
Nexus 仍是未来的跨服务演进选项。

## 当前接入点

```text
Vue -> Nginx -> Gateway -> Node BFF -> Spring Boot -> Temporal Workflow
                                                  ├── MySQL：任务、分析历史、证据元数据和审计
                                                  ├── RabbitMQ：识别告警 Signal
                                                  ├── MinIO：证据 object key
                                                  └── FastAPI AI：流式/同步模型调用与检测投递
```

Spring Boot 是业务入口，Temporal 不直接暴露给前端，也不替代数据库。它负责
可靠编排、重试、超时和工作流状态追踪。识别告警已通过 RabbitMQ 转为
`alarmDetected` Signal；证据文件保存在 MinIO，数据库只存 object key。

## Compose 服务

`deploy/docker-compose.yml` 已加入：

- `temporal`: Temporal Server，gRPC 端口 `7233`。
- `temporal-ui`: Temporal UI，默认宿主机端口 `8088`。

启动后访问：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

Temporal UI：

```text
http://localhost:8088
```

Java Docker 环境会获得这些变量：

```text
TEMPORAL_TARGET_ENDPOINT=temporal:7233
TEMPORAL_NAMESPACE=default
TEMPORAL_TASK_QUEUE=uav-inspection-task-queue
```

本地开发默认连接：

```text
localhost:7233
```

## 已实现 Workflow

Java Worker 使用 `uav-inspection-task-queue`，已注册：

- `InspectionWorkflow`：启动、查询状态、完成、取消，以及 `alarmDetected` Signal；
- `InspectionAnalysisWorkflow`：可靠的完整 AI 分析；
- `InspectionChatWorkflow`：同步聊天分析。

可用业务接口：

```text
POST /api/inspection-workflows/{taskCode}
GET  /api/inspection-workflows/{taskCode}/status
POST /api/inspection-workflows/{taskCode}/complete
POST /api/inspection-workflows/{taskCode}/cancel
POST /api/inspection-workflows/{taskCode}/analysis
POST /api/inspection-workflows/{taskCode}/analysis/stream
POST /api/alarms/detections
POST /api/evidence
```

其中 `/analysis` 的完整结果由 Workflow 编排；`/analysis/stream` 直接透传 SSE，
避免将每个模型 Token 记录到 Temporal 历史。两条通道均在成功完成后保存分析
记录。`/status` 会返回 `lastAlarmEventCode`。

## RabbitMQ 与 Temporal 的关系

识别结果已接入：

```text
AI/API -> RabbitMQ uav.detection -> Java DetectionAlarmListener
       -> AlarmService.create
       -> InspectionWorkflow.alarmDetected(eventCode)
       -> RabbitMQ uav.alarm.realtime -> Node Socket.IO alarm.created
```

这样即使 Java 服务重启，Workflow 的等待状态和历史仍由 Temporal 维护。

## 什么时候用 Nexus

Temporal Nexus 适合跨 Temporal 应用的可靠服务调用。官方文档说明，Nexus 通过 Endpoint 暴露服务，Endpoint 会把请求路由到目标 Namespace 和 Task Queue；操作可以同步或异步执行，底层具备重试、限流、负载均衡和 Worker 恢复后的继续处理能力。

在本项目中，建议先不要把 Nexus 作为第一步。等这些能力变成独立服务后再抽象 Nexus：

- `flight-control.dispatchRoute`: 飞控/无人机调度服务。
- `ai-detection.startAnalysis`: AI 视频分析服务。
- `evidence.archiveCase`: 证据归档服务。
- `notification.broadcastAlarm`: 告警通知服务。

到那时，每个团队或服务可以拥有自己的 Namespace、Task Queue 和 Worker，Spring Boot 的巡检 Workflow 通过 Nexus 调用它们。

## 下一步

1. 将真实 YOLO/视觉推理结果接入现有检测投递通道。
2. 在 Workflow Activity 中补充证据归档与通知编排。
3. 生产环境规划 Namespace、mTLS/API Key、权限、归档和可观测性。
