# SkyTrace 测试地图

目标：证据中心之外，把**任务状态机、航线、遥测链路、前端关键路径**也拉到可持续回归的水位。
本页只列「该跑什么 / 覆盖什么」，不重复用例细节。

## 1. 分层

| 层 | 位置 | 命令 | 覆盖重点 |
| --- | --- | --- | --- |
| Java 单测 | `backend-java/src/test/...` | `mvn -pl . -Dtest='...' test` 或全量 `mvn test` | 领域规则、Service、MQTT Handler、遥测落库 |
| 前端契约测 | `frontend/test/*.test.js` | `cd frontend && npm test` | 航点解析、Route/Drone/Replay 接线存在性 |
| Node 单测 | `backend-node/test/*.test.js` | `cd backend-node && npm test` | JWT、DTO、构建冒烟 |
| Playwright E2E | `e2e/tests/*.spec.ts` | `cd e2e && npm test` | 登录后任务/航线/证据/回放 UI |

## 2. Java：任务 / 航线 / 遥测

| 测试类 | 覆盖 |
| --- | --- |
| `task/domain/InspectionTaskStateMachineTest` | CREATED→RUNNING→COMPLETED/CANCELLED；终态 `isTerminal` |
| `task/service/InspectionTaskServiceTest` | 创建校验设备/航线、拒绝终态编辑、RUNNING 可改航线绑定、重复编号 |
| `temporal/activity/InspectionTaskActivitiesImplTest` | Temporal Activity 写回任务状态、缺任务报错 |
| `route/service/InspectionRouteServiceTest` | 创建/更新 `waypointsJson`、重复编号、查找失败 |
| `device/mqtt/DeviceMqttMessageHandlerTest` | heartbeat / status / telemetry → Presence + Redis + Rabbit + 历史落库钩子 |
| `telemetry/service/DeviceTelemetryHistoryServiceTest` | 仅 RUNNING 任务落库；无 RUNNING 跳过；回放查询映射 |

建议本地抽测：

```bash
cd backend-java
mvn -Dmaven.repo.local="$HOME/.m2/repository" \
  -Dtest=InspectionTaskStateMachineTest,InspectionTaskServiceTest,InspectionTaskActivitiesImplTest,InspectionRouteServiceTest,DeviceMqttMessageHandlerTest,DeviceTelemetryHistoryServiceTest \
  test
```

## 3. 前端契约

`frontend/test/waypoints.test.js`：

- `parseWaypointsJson` / `serializeWaypoints` 存在
- `/routes` 使用地图编辑器 + 缩略图（非纯 textarea）
- `/drone` 有航线缩略图、实时地图、轨迹回放面板
- `getTaskTelemetryTrack` + Replay 使用 Primitive 折线

```bash
cd frontend && npm test
```

## 4. Playwright E2E

| 文件 | 覆盖 |
| --- | --- |
| `e2e/tests/smoke.spec.ts` | 入口可达、任务创建→启动→证据上传→告警落地 |
| `e2e/tests/routes-and-replay.spec.ts` | 航线页缩略图；任务绑航线→启动→完成→打开「轨迹回放」 |
| `e2e/tests/map-live.spec.ts` | 登录后 `/map` Cesium 壳可用 |

说明：回放用例在无 MQTT 落点时断言空态文案；有 telemetry 时断言播放控件。两者都算 UI 接通。

```bash
cd e2e && npm test
# 或：npx playwright test tests/routes-and-replay.spec.ts tests/map-live.spec.ts
```

## 5. 仍偏薄（刻意）

- 真 Temporal 服务端到端（依赖 Worker 时序，CI 成本高）——用 Activity / 状态机单测兜底
- 多 Node 实例下 Socket.IO Redis adapter 的联调——见运维文档，非默认 E2E
- Cesium 像素级截图对比——不稳定，不做

## 6. 相关文档

- [data-governance.md](./data-governance.md) — 备份 / MinIO / Qdrant
- [ops.md](./ops.md) — 值班备份入口
- [mqtt-device-sim-guide.md](./mqtt-device-sim-guide.md) — 遥测与回放验收
