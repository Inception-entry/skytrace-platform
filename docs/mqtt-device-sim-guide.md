# MQTT 模拟接入自学计划（无真机）

> 指导你自己改代码，风格与「产品 1.1 阶段 A：设备删除」一致。  
> **本文不是已实现功能说明**：仓库默认尚未接入 MQTT；按切片 M1→M3 落地后，把本节「目标」变成现实。

## 目标（验收一句话）

Compose 起 Mosquitto + `device-sim`；Java 订阅心跳/状态后写入现有
[`DevicePresenceService`](../backend-java/src/main/java/com/skytrace/backend/cache/DevicePresenceService.java)；
`GET /api/devices` 能看到 `UAV-001` 变 `ONLINE`，停模拟器（或主动 offline）后变 `OFFLINE`。
HTTP `POST /api/devices/{code}/heartbeat` 仍可用作调试。

## 设计原则

| 原则 | 说明 |
| --- | --- |
| 假设备 + 真协议 | 无真机，但用 MQTT 主题与载荷练总线 |
| Presence 仍用 Redis | 列表 ONLINE/OFFLINE 继续靠 TTL key，MQTT 只是写入来源 |
| 告警继续走 RabbitMQ | 设备遥测与告警检测总线分离 |
| 前端不直连 Broker | 业务端仍走 `/api`（与可选 Socket.IO） |
| 默认关闭 | `MQTT_ENABLED=false`，用 Compose overlay 再开 |

## 与阶段 A（设备删除）的关系

- **不冲突**：A 管删库 + `presence.clear`；MQTT 管「谁来续命 presence」。
- **建议**：先做完阶段 A 再做本指南；若先做 MQTT，订阅时对「库中不存在的 `deviceCode`」直接忽略。
- 删除后模拟器若仍对该 code 发心跳：Handler 忽略即可，避免脏 Redis key；列表读的是 DB，已删设备不会重新出现。

## 总架构

```text
device-sim ──publish──► Mosquitto ──subscribe──► backend-java
                                                      │
                                                      ├─ DevicePresenceService (TTL)
                                                      └─ (可选) status Redis Hash
                                                              │
                                                      GET /api/devices
                                                              │
                                                            Vue
```

```text
推荐顺序:  M1 Mosquitto → M2 device-sim → M3 Java 订阅 → M4 状态 Hash（可选）→ M5 sim HTTP（可选）
```

---

## M1 — 起 Mosquitto

### 改什么

1. 新建 `deploy/mqtt/mosquitto.conf`
2. 新建 `deploy/docker-compose.mqtt.yml`（overlay）
3. 在 `deploy/.env.example` 补充变量说明

### 为什么

独立 overlay，默认全栈不强制依赖 MQTT；演示时再挂上文件即可。

### 配置示例

`deploy/mqtt/mosquitto.conf`：

```conf
listener 1883
allow_anonymous true
persistence false
```

本地匿名即可；预发至少改为用户名密码。

`deploy/docker-compose.mqtt.yml`（示意，需与现有 compose **同一 project / network**，按仓库实际 network 名调整）：

```yaml
services:
  mqtt:
    image: eclipse-mosquitto:2
    container_name: skytrace-mqtt
    ports:
      - "127.0.0.1:${MQTT_HOST_PORT:-1883}:1883"
    volumes:
      - ./mqtt/mosquitto.conf:/mosquitto/config/mosquitto.conf:ro
```

`.env.example` 增加：

```dotenv
MQTT_ENABLED=false
MQTT_BROKER_URL=tcp://mqtt:1883
MQTT_ENV=local
MQTT_HOST_PORT=1883
```

### 验收

MQTTX（或同类客户端）连接 `127.0.0.1:1883`，订阅 `#`，能收发任意消息。

### 好处

Broker 与业务解耦；可先用 MQTTX 玩通再写 Java。

---

## M2 — device-sim 只负责发消息

### 改什么

新建仓库根目录 `device-sim/`（Python + paho 示例；Node 同理）：

```text
device-sim/
  requirements.txt    # paho-mqtt
  sim.py
  Dockerfile          # 可选，供 Compose build
```

### 为什么

模拟器 = 「假无人机」。发布逻辑不要塞进 Java，职责清晰，也可单独重启。

### Topic（冻结）

```text
skytrace/{env}/device/{deviceCode}/heartbeat
skytrace/{env}/device/{deviceCode}/status
```

- `{env}` 默认 `local`
- `{deviceCode}` 与种子一致：`UAV-001`、`CAMERA-001`

### 载荷

**Heartbeat**

```json
{
  "deviceCode": "UAV-001",
  "ts": "2026-08-05T09:00:00Z",
  "source": "sim"
}
```

**Status**

```json
{
  "deviceCode": "UAV-001",
  "ts": "2026-08-05T09:00:00Z",
  "online": true,
  "mode": "IDLE",
  "battery": 87,
  "source": "sim"
}
```

`mode` 建议枚举（先少后多）：`OFFLINE` | `IDLE` | `READY` | `FLYING` | `CHARGING` | `FAULT`。

### QoS 与 TTL

| Topic | QoS |
| --- | --- |
| heartbeat | 0 或 1 |
| status | 1 |

`HEARTBEAT_INTERVAL_SEC` 建议 `30`；现有
`CACHE_DEVICE_PRESENCE_TTL_SECONDS` 默认 `90`。  
须满足：**心跳间隔 &lt; Presence TTL / 2**，否则会假离线。

### `sim.py` 核心逻辑（可改着用）

```python
import json
import os
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

BROKER = os.getenv("MQTT_HOST", "127.0.0.1")
PORT = int(os.getenv("MQTT_PORT", "1883"))
ENV = os.getenv("MQTT_ENV", "local")
CODES = [
    c.strip()
    for c in os.getenv("DEVICE_CODES", "UAV-001,CAMERA-001").split(",")
    if c.strip()
]
INTERVAL = int(os.getenv("HEARTBEAT_INTERVAL_SEC", "30"))


def topic(code: str, kind: str) -> str:
    return f"skytrace/{ENV}/device/{code}/{kind}"


def now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def main() -> None:
    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id="skytrace-device-sim",
    )
    client.connect(BROKER, PORT, keepalive=60)
    client.loop_start()

    for code in CODES:
        client.publish(
            topic(code, "status"),
            json.dumps({
                "deviceCode": code,
                "online": True,
                "mode": "IDLE",
                "battery": 100,
                "source": "sim",
                "ts": now(),
            }),
            qos=1,
        )

    try:
        while True:
            for code in CODES:
                client.publish(
                    topic(code, "heartbeat"),
                    json.dumps({
                        "deviceCode": code,
                        "ts": now(),
                        "source": "sim",
                    }),
                    qos=0,
                )
            time.sleep(INTERVAL)
    except KeyboardInterrupt:
        for code in CODES:
            client.publish(
                topic(code, "status"),
                json.dumps({
                    "deviceCode": code,
                    "online": False,
                    "mode": "OFFLINE",
                    "source": "sim",
                    "ts": now(),
                }),
                qos=1,
            )
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
```

**关于 LWT：** Paho 单 client 通常只有一个 will。一期更简单：正常退出时主动发 `online:false`（如上）。多设备可靠 LWT 可二期「每设备一个 client」。

Compose 中 sim 示意（写入 `docker-compose.mqtt.yml`）：

```yaml
  device-sim:
    build: ../device-sim
    container_name: skytrace-device-sim
    environment:
      MQTT_HOST: mqtt
      MQTT_PORT: "1883"
      MQTT_ENV: local
      DEVICE_CODES: UAV-001,CAMERA-001
      HEARTBEAT_INTERVAL_SEC: "30"
    depends_on:
      - mqtt
```

### 验收

MQTTX 订阅 `skytrace/local/device/+/+`，周期性看到 heartbeat，启动时有 status。

### 好处

协议在进 Java 前就可视、可调；Java 挂了也能单独验证 Broker。

---

## M3 — Java 订阅 → Presence（核心）

### 改什么

建议新建：

```text
backend-java/src/main/java/com/skytrace/backend/device/mqtt/
  MqttProperties.java
  DeviceMqttConfig.java
  DeviceMqttSubscriber.java
  DeviceMqttMessageHandler.java
  DeviceMqttPayload.java          # 可选 record
```

[`backend-java/pom.xml`](../backend-java/pom.xml) 增加依赖，例如：

```xml
<dependency>
  <groupId>org.eclipse.paho</groupId>
  <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
  <version>1.2.5</version>
</dependency>
```

[`application.yml`](../backend-java/src/main/resources/application.yml) 增加：

```yaml
app:
  mqtt:
    enabled: ${MQTT_ENABLED:false}
    broker-url: ${MQTT_BROKER_URL:tcp://localhost:1883}
    env: ${MQTT_ENV:local}
    client-id: ${MQTT_CLIENT_ID:skytrace-backend-java}
    username: ${MQTT_USERNAME:}
    password: ${MQTT_PASSWORD:}
```

mqtt overlay 中给 `backend-java`：

```yaml
  backend-java:
    environment:
      MQTT_ENABLED: "true"
      MQTT_BROKER_URL: tcp://mqtt:1883
      MQTT_ENV: local
```

### 为什么

在线真相继续是 Redis TTL；MQTT 只是写入来源，列表/任务旁「是否在线」不用重写。

### 先具备 `DevicePresenceService.clear`

阶段 A 步骤 1 会加 `clear(deviceCode)`。若尚未做 A，M3 处理 offline 时需要先补上，例如：

```java
public void clear(String deviceCode) {
    String code = normalize(deviceCode);
    if (code.isEmpty()) {
        return;
    }
    try {
        redisTemplate.delete(KEY_PREFIX + code);
    } catch (Exception ex) {
        log.warn("清除设备在线状态失败: {}", ex.getMessage());
    }
}
```

### Handler（抽成可单测类，推荐）

```java
@Component
public class DeviceMqttMessageHandler {
    private final DeviceRepository deviceRepository;
    private final ObjectProvider<DevicePresenceService> presenceService;
    private final ObjectMapper objectMapper;

    public void onMessage(String topic, String payloadJson) {
        // topic: skytrace/{env}/device/{code}/heartbeat|status
        String[] parts = topic.split("/");
        if (parts.length < 5) {
            return;
        }
        String deviceCode = parts[3];
        String kind = parts[4];

        if (!deviceRepository.existsByDeviceCode(deviceCode)) {
            // 已删或不存在：忽略，避免脏 presence（衔接阶段 A）
            return;
        }

        DevicePresenceService presence = presenceService.getIfAvailable();
        if (presence == null) {
            return;
        }

        try {
            if ("heartbeat".equals(kind)) {
                presence.heartbeat(deviceCode);
                return;
            }
            if ("status".equals(kind)) {
                JsonNode node = objectMapper.readTree(payloadJson);
                boolean online = node.path("online").asBoolean(true);
                if (online) {
                    presence.heartbeat(deviceCode);
                } else {
                    presence.clear(deviceCode);
                }
            }
        } catch (Exception ex) {
            // 打日志，勿拖垮订阅线程
        }
    }
}
```

### Subscriber 启动骨架

使用 `@ConditionalOnProperty(name = "app.mqtt.enabled", havingValue = "true")`。

```java
@PostConstruct
public void start() throws MqttException {
    MqttConnectOptions opts = new MqttConnectOptions();
    opts.setAutomaticReconnect(true);
    opts.setCleanSession(true);
    if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
        opts.setUserName(properties.getUsername());
        opts.setPassword(properties.getPassword().toCharArray());
    }

    MqttClient client = new MqttClient(
            properties.getBrokerUrl(),
            properties.getClientId() + "-" + UUID.randomUUID()
    );
    client.connect(opts);

    String env = properties.getEnv();
    String filterHb = "skytrace/" + env + "/device/+/heartbeat";
    String filterSt = "skytrace/" + env + "/device/+/status";

    client.subscribe(filterHb, 0, (topic, msg) ->
            handler.onMessage(topic, new String(msg.getPayload(), StandardCharsets.UTF_8)));
    client.subscribe(filterSt, 1, (topic, msg) ->
            handler.onMessage(topic, new String(msg.getPayload(), StandardCharsets.UTF_8)));
}

@PreDestroy
public void stop() {
    // disconnect / close，避免泄漏
}
```

`clientId` 加随机后缀，避免多实例冲突。生产代码补齐异常与日志。

### 与 HTTP heartbeat 共存

| 来源 | 行为 |
| --- | --- |
| MQTT | 主路径（演示/模拟） |
| `POST .../heartbeat` | 同样调 `presence.heartbeat`，便于单测与 Postman |

不要做成两套互斥的在线模型。

### 单测示例（只测 Handler，不连 Broker）

```java
@Test
void heartbeatForKnownDeviceTouchesPresence() {
    when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);
    when(presenceProvider.getIfAvailable()).thenReturn(presence);

    handler.onMessage(
            "skytrace/local/device/UAV-001/heartbeat",
            "{\"deviceCode\":\"UAV-001\",\"source\":\"sim\"}"
    );

    verify(presence).heartbeat("UAV-001");
}

@Test
void unknownDeviceIsIgnored() {
    when(deviceRepository.existsByDeviceCode("GONE")).thenReturn(false);

    handler.onMessage("skytrace/local/device/GONE/heartbeat", "{}");

    verifyNoInteractions(presence);
}

@Test
void statusOfflineClearsPresence() {
    when(deviceRepository.existsByDeviceCode("UAV-001")).thenReturn(true);
    when(presenceProvider.getIfAvailable()).thenReturn(presence);

    handler.onMessage(
            "skytrace/local/device/UAV-001/status",
            "{\"deviceCode\":\"UAV-001\",\"online\":false,\"mode\":\"OFFLINE\"}"
    );

    verify(presence).clear("UAV-001");
}
```

### 验收

1. `MQTT_ENABLED=true`，起 mqtt + sim + java  
2. `GET /api/devices` → `UAV-001` 为 `ONLINE`  
3. 停 `device-sim`（主动发 offline）→ 刷新列表为 `OFFLINE`  
4. Redis：`EXISTS skytrace:device:online:UAV-001` 与列表一致  
5. 若已做阶段 A：删无引用设备后，sim 仍发该 code 时列表无此行；Handler 不再写入该 code 的 presence

### 好处

- 列表逻辑几乎不动  
- 与 HTTP heartbeat、阶段 A `clear`、后续 DB 对账共用 Presence  
- 未知设备忽略，与硬删策略兼容  

---

## M4 —（可选）状态进 Redis Hash + 详情返回

### 为什么

仅有 ONLINE/OFFLINE 不够演示 `IDLE` / `FLYING` / `battery`；先不要改 MySQL `device` 表。

### 做什么

收到 `status` 且 `online=true` 时，例如：

```text
HSET skytrace:device:status:{code} mode IDLE battery 87 ts ...
EXPIRE 与 presence TTL 同级或略长
```

`DeviceResponse` 增加可选字段 `mode`、`battery`（或单独 DTO）；详情/列表从 Hash 读取，没有则 `null`。

### 好处

前端可展示舱态；DB 结构保持干净。

---

## M5 —（可选）sim 控制 HTTP

```text
POST /sim/devices/{code}/start
POST /sim/devices/{code}/stop
POST /sim/devices/{code}/mode   body: {"mode":"FLYING"}
GET  /sim/devices
```

绑定 `127.0.0.1:8095`，勿对公网暴露。演示用 curl 切模式，不必改 Java。

---

## 前端（一期最小）

- **不要**用 MQTT.js 直连 Broker  
- DeviceView 继续 `GET /api/devices`；可加 5–10s 轮询或手动刷新以看到 ONLINE 变化  
- 原「心跳」按钮可标为调试，或用环境变量控制是否显示  

二期再考虑经现有 Socket.IO 推 `device.status`（非本指南必做）。

---

## 环境与开关矩阵

| 环境 | MQTT_ENABLED | device-sim | 说明 |
| --- | --- | --- | --- |
| 默认 CI / 单测 | false | 不起 | Handler 单测 mock Presence |
| 本地做业务 1.1 | false | 不起 | 免干扰 |
| 演示设备在线 | true | true | 使用 mqtt overlay |

---

## 明确不做

- 浏览器直连 MQTT  
- 用 RabbitMQ 替代设备遥测（告警继续走 Rabbit）  
- EMQX 集群、指令下行、真飞控协议  
- 自动把未知 `deviceCode` 写入 MySQL  

---

## 建议提交（中文 message）

1. `chore(deploy): 增加 Mosquitto MQTT overlay`  
2. `feat(device-sim): 无真机设备心跳与状态模拟器`  
3. `feat(backend-java): 订阅 MQTT 并写入设备在线 Presence`  

---

## 推进清单

| 顺序 | 切片 | 完成标准 |
| --- | --- | --- |
| 1 | M1 | MQTTX 连上 `1883` |
| 2 | M2 | 订阅看到 heartbeat / status |
| 3 | M3 | 列表 ONLINE/OFFLINE 跟 sim 走；Handler 单测绿 |
| 4 | M4 / M5 | 有余力再做 |

相关文档：[架构说明](architecture.md)、[v1.0.0 发版说明](releases/v1.0.0.md)（设备在线仍为 Redis presence 的已知限制语境）。
