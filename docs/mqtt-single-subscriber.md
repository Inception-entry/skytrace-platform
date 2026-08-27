# Java MQTT 单订阅副本拆分方案

> 状态：待实施。本文描述目标方案和落地步骤，不代表仓库已经完成改造。

## 1. 先看结论

当前 `backend-java` 同时承担 API、Temporal Worker 和 MQTT 订阅。只要开启 MQTT 后再扩容 Java，多个实例就会同时消费同一条设备消息，造成遥测重复落库和前端航迹重复。

本次改造只调整部署拓扑，不拆 Java 代码库：

- `backend-java`：继续提供 API，可水平扩容，但必须设置 `MQTT_ENABLED=false`
- `backend-java-mqtt`：复用同一个 Java 镜像，只负责承载 MQTT 订阅入口，固定为 1 个副本，并设置 `MQTT_ENABLED=true`
- Node、Gateway 和负载均衡仍只访问 `backend-java`，不能访问 `backend-java-mqtt`

必须始终满足下面三个约束：

1. API 副本永远不开启 MQTT。
2. MQTT 部署单元永远只有 1 个副本。
3. MQTT 部署单元不对外提供 HTTP 流量，也不加入 API Service 或负载均衡。

相关背景：

- [horizontal-scaling.md](./horizontal-scaling.md)
- [mqtt-device-sim-guide.md](./mqtt-device-sim-guide.md)

## 2. 为什么必须拆分

当前消息链路如下：

```text
device-sim / 真机
        │
        ▼
    Mosquitto
        │
        ▼
backend-java（DeviceMqttSubscriber）
        │
        ├── Redis：设备在线状态和最新遥测
        ├── MySQL：任务执行期间的遥测点
        └── RabbitMQ → Node Socket.IO → Cesium 航迹
```

`DeviceMqttSubscriber` 在 `MQTT_ENABLED=true` 时随 Spring Boot 启动。现在的 `docker-compose.mqtt.yml` 会直接给 `backend-java` 打开这个开关。

单个 Java 容器时没有问题；一旦 API 扩成多个副本，就会发生以下过程：

1. 每个实例都创建自己的 MQTT 客户端。当前 clientId 还会追加随机 UUID，因此 Broker 会把它们视为不同客户端。
2. 每个客户端都订阅相同的 `heartbeat`、`status` 和 `telemetry` Topic。
3. 同一条设备消息会被每个 Java 实例分别处理。
4. Redis、MySQL 和 RabbitMQ 随之发生重复写入或重复发布，最终表现为重复遥测点和重叠航迹。

这属于数据正确性问题，不是简单的性能浪费。因此，在拆出单独的 MQTT 部署单元之前，不能安全地扩容 Java API。

## 3. 本次改造范围

### 3.1 要做什么

- 将 MQTT 订阅从 API 部署单元中隔离出来
- API 和 MQTT 部署单元复用同一份 `backend-java` 镜像
- 本地 Compose、`skytrace.sh`、TLS overlay 和环境变量约定保持一致
- 明确预发、生产环境的副本数和流量边界
- 提供可重复执行的验收与回滚步骤

### 3.2 不做什么

- 不使用 Mosquitto Shared Subscription（`$share/...`）
- 不引入 Redis 锁、ZooKeeper 或其他选主机制
- 不建设 EMQX 集群或设备级证书体系
- 不将 MQTT 代码拆成新的 Maven 模块或独立代码库
- 不在本次改造中调整飞控编排和证据归档流程

共享订阅或选主适合后续建设多活 MQTT 消费能力，但不是本次改造的前提。本轮先通过部署隔离消除“API 一扩容就重复消费”的风险。

## 4. 改造前后对比

### 4.1 当前状态

| 位置 | 当前行为 | 风险 |
| --- | --- | --- |
| `deploy/docker-compose.yml` | `backend-java` 默认 `MQTT_ENABLED=false` | 单独使用主 Compose 时安全 |
| `deploy/docker-compose.mqtt.yml` | 给同一个 `backend-java` 设置 `MQTT_ENABLED=true` | API 和 MQTT 生命周期绑定 |
| `scripts/skytrace.sh mqtt-start` | 启动 Broker 和模拟器后，强制重建 `backend-java` | 启停 MQTT 会中断 API |
| `DeviceMqttSubscriber` | clientId 后追加随机 UUID | 多实例可以同时订阅，重复消费不会暴露为连接冲突 |
| 预发/生产 | 没有独立 MQTT 部署单元 | 开启 MQTT 后无法安全扩容 API |

本地 `backend-java` 目前带固定 `container_name`，所以直接执行 `docker compose --scale backend-java=2` 会先发生容器名冲突。这个限制并不能保护生产环境：一旦换成 Kubernetes 或移除固定容器名，多副本仍会重复消费。

### 4.2 目标状态

```text
客户端
  │
  ▼
LB / Gateway ──► Node × N ──► backend-java × N
                                 MQTT_ENABLED=false

设备 / 模拟器 ──► Mosquitto ──► backend-java-mqtt × 1
                                      MQTT_ENABLED=true

两类 Java 部署单元共同访问 MySQL / Redis / RabbitMQ / Temporal，
但只有 backend-java-mqtt 订阅设备 Topic。
```

| 部署单元 | 对外提供 REST | 加入 API 负载均衡 | MQTT 订阅 | 副本数 |
| --- | --- | --- | --- | --- |
| `backend-java` | 是 | 是 | 否 | 1～N |
| `backend-java-mqtt` | 启动了 HTTP Server，但不对外使用 | 否 | 是 | 固定 1 |

这里的 `backend-java-mqtt` 是部署角色，不是精简后的专用应用。因为它仍运行完整的 Spring Boot，所以还会初始化 Temporal Worker、RabbitMQ Listener 等组件。这是“复用同一镜像”的已知代价。它们可以参与原有的竞争消费，但实施时必须确认不会把 MQTT 副本加入 API 流量，也不能误开默认关闭的定时维护任务。

## 5. 环境变量约定

| 变量 | `backend-java` | `backend-java-mqtt` |
| --- | --- | --- |
| `MQTT_ENABLED` | 必须为 `false` | 必须为 `true` |
| `MQTT_BROKER_URL` | 可保留默认值，不会生效 | `tcp://mqtt:1883` 或 `ssl://mqtt:8883` |
| `MQTT_ENV` | 可保留默认值，不会生效 | 与设备发布 Topic 的环境段一致 |
| `MQTT_CLIENT_ID` | 可省略 | 每套环境固定且唯一，例如 `skytrace-local-backend-java-mqtt` |
| `MQTT_USERNAME` / `MQTT_PASSWORD` | 可省略 | 使用现有 backend ACL 账号 |

### 5.1 必须避免的迁移陷阱

如果 `deploy/.env` 中已有 `MQTT_ENABLED=true`，主 Compose 的 API 服务会继续读到这个值。此时即使新增了 `backend-java-mqtt`，仍会变成“API + MQTT 副本”同时订阅。

因此，Compose 服务级配置必须显式覆盖：

```yaml
services:
  backend-java:
    environment:
      MQTT_ENABLED: "false"

  backend-java-mqtt:
    environment:
      MQTT_ENABLED: "true"
```

不要再用全局 `MQTT_ENABLED` 控制两类 Java 部署单元。建议在 `.env.example` 中保留 `MQTT_ENABLED=false` 作为安全默认值，并注明真正的订阅开关由 `backend-java-mqtt` 服务写死。

为避免和 API 的旧变量含义混淆，可新增一个只用于 Compose 插值的变量：

```dotenv
MQTT_SUBSCRIBER_CLIENT_ID=skytrace-local-backend-java-mqtt
```

然后在 MQTT 服务中映射为应用实际读取的变量：

```yaml
MQTT_CLIENT_ID: ${MQTT_SUBSCRIBER_CLIENT_ID:-skytrace-local-backend-java-mqtt}
```

如果多套环境共用一个 Broker，clientId 必须包含环境标识，不能在开发、预发和生产之间重复。

## 6. 实施步骤

建议按以下顺序在同一个 PR 中完成。每一步都以第 1 节的三个核心约束为准。

### 6.1 调整 `deploy/docker-compose.yml`

给现有 `backend-java` 增加明确的本地镜像名，并将 MQTT 开关写死为关闭：

```yaml
services:
  backend-java:
    build:
      context: ../backend-java
    image: skytrace-backend-java:local
    environment:
      MQTT_ENABLED: "false"
```

其余环境变量和依赖关系保持不变。

这里写死 `false` 的目的，是让 `deploy/.env` 中遗留的 `MQTT_ENABLED=true` 无法再次打开 API 订阅。

### 6.2 调整 `deploy/docker-compose.mqtt.yml`

删除 overlay 中给 `backend-java` 开启 MQTT 的配置，改为新增 `backend-java-mqtt`：

```yaml
services:
  backend-java:
    environment:
      MQTT_ENABLED: "false"

  backend-java-mqtt:
    build:
      context: ../backend-java
    image: skytrace-backend-java:local
    container_name: skytrace-backend-java-mqtt
    restart: unless-stopped
    environment:
      # MYSQL_* / REDIS_* / RABBITMQ_* / TEMPORAL_* / MINIO_* 等配置
      # 必须与主 Compose 中的 backend-java 保持一致。
      SPRING_PROFILES_ACTIVE: docker
      SERVER_PORT: 8080
      MQTT_ENABLED: "true"
      MQTT_BROKER_URL: tcp://mqtt:1883
      MQTT_ENV: ${MQTT_ENV:-local}
      MQTT_CLIENT_ID: ${MQTT_SUBSCRIBER_CLIENT_ID:-skytrace-local-backend-java-mqtt}
      MQTT_USERNAME: ${MQTT_BACKEND_USERNAME:-backend}
      MQTT_PASSWORD: ${MQTT_BACKEND_PASSWORD:-skytrace-mqtt-backend}
    expose:
      - "8080"
    depends_on:
      mqtt:
        condition: service_started
      mysql:
        condition: service_healthy
      redis:
        condition: service_started
      rabbitmq:
        condition: service_started
      temporal:
        condition: service_started
    networks:
      - backend
```

上面的环境变量块是结构示例，不是可直接粘贴的完整配置。实际实施时，应逐项复制主 Compose 中 `backend-java` 的连接配置，再只覆盖 MQTT 相关变量。尤其不能遗漏数据库密码、RabbitMQ 凭证、Temporal 地址和缓存开关。

注意事项：

- 两个服务使用相同的 `image: skytrace-backend-java:local`，避免维护两套镜像身份
- 不要给 `backend-java-mqtt` 配置宿主机 `ports`；`expose` 只用于容器网络
- 不要让 `backend-node`、Gateway、Service 或 Ingress 依赖/选择 `backend-java-mqtt`
- 本地保留固定 `container_name`，可以阻止常规的 `docker compose --scale backend-java-mqtt=2`；这只是本地护栏，不是生产级单例保证
- YAML anchor 不能跨 Compose 文件引用。若 MQTT 服务继续放在 overlay 中，公共环境变量只能在该文件内复用或显式复制，不能直接引用主 Compose 中定义的 anchor
- 如需健康检查，当前应用路径应按 `/api/actuator/health` 配置；还要先确认运行镜像内存在健康检查命令。Kubernetes 可直接使用 `httpGet`，不依赖容器内的 `curl` 或 `wget`

### 6.3 调整 `deploy/docker-compose.mqtt-tls.yml`

TLS overlay 不再修改 API 服务，而是修改 MQTT 副本：

```yaml
services:
  backend-java-mqtt:
    environment:
      MQTT_BROKER_URL: ssl://mqtt:8883
      MQTT_TLS_INSECURE: ${MQTT_TLS_INSECURE:-true}
```

`MQTT_TLS_INSECURE=true` 只用于本地自签证书。预发和生产必须使用受信 CA，并设置为 `false`。

### 6.4 调整 `scripts/skytrace.sh`

`mqtt-start` 和 `mqtt-tls-start` 不再重建 API，只启动 MQTT 相关服务：

```bash
compose_mqtt up -d --build mqtt device-sim backend-java-mqtt
```

TLS 命令使用对应的 `compose_mqtt_tls`。

其他命令同步调整：

| 命令 | 调整后行为 |
| --- | --- |
| `mqtt-start` | 启动 `mqtt`、`device-sim`、`backend-java-mqtt` |
| `mqtt-tls-start` | 生成本地证书，并以 TLS overlay 启动上述三个服务 |
| `mqtt-stop` | 停止 `backend-java-mqtt`、`device-sim`、`mqtt`，不停止 API |
| `mqtt-logs` | 同时查看 `mqtt`、`device-sim`、`backend-java-mqtt` 日志 |

同时删除 `--force-recreate --no-deps backend-java`，并将帮助文本中的“启用 Java MQTT”改成“启动独立 MQTT 订阅副本”。

### 6.5 调整 `deploy/.env.example`

- 保留 `MQTT_ENABLED=false`，注明 API 不允许开启 MQTT
- 新增 `MQTT_SUBSCRIBER_CLIENT_ID=skytrace-local-backend-java-mqtt`
- 保留现有 Broker、账号、密码和 TLS 变量
- 提醒预发/生产使用各自唯一的 clientId

### 6.6 建议增强：固定 MQTT clientId

当前 `DeviceMqttSubscriber` 会无条件执行：

```java
String clientId = properties.getClientId() + "-" + UUID.randomUUID();
```

建议在完成部署拆分后，改为直接使用已经配置好的固定 clientId。这样，如果有人误启动第二个 MQTT 副本，Broker 会因为 clientId 冲突断开旧连接，日志中也会出现明显的反复重连，而不是静默地同时消费。

需要明确：固定 clientId 不是高可用方案，也不能替代 `replicas: 1`。两个实例争抢同一 clientId 时会连接抖动；而 telemetry 使用 QoS 0，抖动期间还可能丢消息。它只是一道暴露误配置的保护措施。

不要在部署拆分前单独上线这项修改，否则多个 API 副本会互相踢连接，造成 MQTT 链路间歇性中断。

## 7. 预发和生产部署要求

仓库目前没有 Kubernetes 清单，因此下面是部署约束，不是可直接应用的现成文件。

### 7.1 Compose 预发

- 使用与本地相同的 `backend-java-mqtt` 独立服务
- 只定义一个 MQTT 服务实例，不执行 `--scale`
- API 服务保持 `MQTT_ENABLED=false`
- 为 MQTT JVM 单独设置内存限制，不能依赖 API 服务的限额自动继承

### 7.2 Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-java-api
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: java
          env:
            - name: MQTT_ENABLED
              value: "false"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-java-mqtt
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: java
          env:
            - name: MQTT_ENABLED
              value: "true"
            - name: MQTT_CLIENT_ID
              value: skytrace-prod-backend-java-mqtt
```

生产要求：

- 不给 `backend-java-mqtt` 配置 HPA
- API Service / Ingress 的 selector 只匹配 `backend-java-api`
- MQTT Deployment 不创建对外 Service；探针直接访问 Pod 的 `/api/actuator/health`
- 为 MQTT JVM 单独设置 requests/limits，例如先以 512 MiB 为起点，再根据实际堆内存和负载调整
- 对 MQTT Deployment 的副本数变更、反复重连和订阅中断设置监控告警

`replicas: 1` 能防止正常扩容造成重复消费，但无法提供无损高可用。若未来必须容忍 MQTT 消费实例故障，应单独设计共享订阅、选主或消息幂等，本方案不覆盖该问题。

## 8. 验收步骤

### 8.1 启动

先启动完整基础栈，再启动 MQTT：

```bash
./scripts/skytrace.sh start
./scripts/skytrace.sh mqtt-start
```

如果基础栈已经运行，只执行第二条命令即可。

### 8.2 核心检查

| 检查项 | 期望结果 |
| --- | --- |
| `docker ps` | 同时存在 `skytrace-backend-java` 和 `skytrace-backend-java-mqtt` |
| API 日志 | 不出现 `MQTT 已连接` 或 `MQTT 已订阅` |
| MQTT 副本日志 | 出现 `MQTT 已连接`，并订阅 `heartbeat`、`status`、`telemetry` |
| `GET /api/devices` | `UAV-001` 最终为 `ONLINE` |
| `/map` 航迹 | 每个采样周期只有一个新点，不出现重叠双轨迹 |
| 停止 MQTT 副本 | 设备在 TTL 到期后变为 `OFFLINE`，航迹停止更新，但 API 仍可用 |
| 重新启动 MQTT 副本 | 心跳和遥测恢复，航迹仍保持单份 |

建议同时用数据库查询核对 `device_telemetry_point`：在模拟器固定 2 秒发送间隔下，新增记录数应与单订阅者的理论数量接近，不应稳定地翻倍。

### 8.3 验证副本边界

1. 确认 `docker compose config` 的最终结果中，`backend-java.MQTT_ENABLED=false`。
2. 确认 `backend-java-mqtt.MQTT_ENABLED=true`，且没有宿主机端口映射。
3. 如果已实施固定 clientId，可在隔离环境临时启动第二个 MQTT 实例，确认日志出现连接冲突或反复重连。
4. 测试结束后立即恢复单副本，不能把冲突状态留在默认配置中。

不要把“第二个实例被踢下线”当成业务验收成功；正式状态仍必须只有一个 MQTT 副本稳定连接。

### 8.4 CI 检查

- 默认 Compose 不应启动 `mqtt`、`device-sim` 或 `backend-java-mqtt`
- 现有 full-stack job 不应因为本次改造额外拉起 MQTT 服务
- 如有 MQTT 专项用例，应断言订阅日志只出现在 `backend-java-mqtt`
- 增加配置断言，防止后续 PR 把 API 的 `MQTT_ENABLED` 改回 `true`

## 9. 回滚方案

本次改造不涉及 Flyway 和表结构，回滚只发生在编排层：

1. 停止并移除 `backend-java-mqtt`
2. 恢复 MQTT overlay 中对 `backend-java` 的 `MQTT_ENABLED=true` 配置
3. 恢复 `mqtt-start` 对 `backend-java` 的强制重建逻辑
4. 重新创建单实例 `backend-java`

回滚后只能保持单个 API 实例；在旧架构下扩容 API 会重新引入重复消费问题。

## 10. 需要同步的文档

实施 PR 中同步修改以下内容，避免仓库里出现两套说法：

- [horizontal-scaling.md](./horizontal-scaling.md)：将“建议单独 Deployment”改成已经采用 `backend-java-mqtt`，并链接本文
- [mqtt-device-sim-guide.md](./mqtt-device-sim-guide.md)：将链路图中的订阅者改为 `backend-java-mqtt`，同步启停和日志命令
- [architecture.md](./architecture.md)：将 MQTT 订阅标记为 Java MQTT 副本 × 1
- [README.md](../README.md)：补充本文入口，并说明 API 与 MQTT 部署单元已经分离

如果本轮只提交方案文档、尚未改代码和 Compose，README 仍应写“Java MQTT 当前按单实例设计”，并把本文标记为待实施方案，不能提前描述为已完成。

## 11. 实施检查清单

- [ ] 主 Compose 中 API 的 `MQTT_ENABLED` 已写死为 `false`
- [ ] MQTT overlay 已新增 `backend-java-mqtt`，且 `MQTT_ENABLED=true`
- [ ] 两个 Java 服务复用同一个镜像名
- [ ] MQTT 副本已补齐数据库、Redis、RabbitMQ、Temporal 等运行配置
- [ ] MQTT 副本没有宿主机端口，也没有加入 API 流量入口
- [ ] TLS overlay 修改的是 `backend-java-mqtt`
- [ ] `mqtt-start`、`mqtt-tls-start`、`mqtt-stop`、`mqtt-logs` 和帮助文本已同步
- [ ] `.env.example` 已补充安全默认值和环境唯一的 clientId
- [ ] 已评估完整 Spring Boot 副本带来的 Worker、Listener 和定时任务影响
- [ ] 已完成单轨迹、停副本、重启副本和数据库记录数验收
- [ ] 预发/生产明确固定为 1 个 MQTT 副本，且未配置 HPA
- [ ] 交叉文档已经同步

完成以上检查后，`backend-java` 才具备安全水平扩容的前提。
