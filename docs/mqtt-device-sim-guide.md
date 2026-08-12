# MQTT 设备模拟接入：前端程序员开发指南

> Mosquitto、`device-sim` 与 Java MQTT 订阅已在仓库中。
> 本地验证：叠加 `docker-compose.mqtt.yml`，并保证 `MQTT_ENABLED=true`、
> `CACHE_ENABLED=true`。设备页在线状态由 Redis presence（TTL ~90s）驱动。

## 做完以后，你会看到什么

1. Docker 中多出 `skytrace-mqtt` 和 `skytrace-device-sim` 两个容器。
2. 模拟器每 30 秒发布一次 `UAV-001`、`CAMERA-001` 的心跳。
3. Java 服务收到心跳后，复用现有
   [`DevicePresenceService`](../backend-java/src/main/java/com/skytrace/backend/cache/DevicePresenceService.java)
   写入 Redis。
4. 打开 `http://localhost:8888/devices`，刷新页面后设备从 `OFFLINE` 变成
   `ONLINE`。
5. 正常停止模拟器时设备立即离线；模拟器异常消失时，Redis key 最迟在
   90 秒后过期，设备也会变回 `OFFLINE`。

最终链路只有这一条：

```text
device-sim                backend-java                    Vue
   │                           │                           │
   │ publish heartbeat         │                           │
   ├────────► Mosquitto ──────►│                           │
   │                           │ heartbeat(deviceCode)     │
   │                           ├────────► Redis (TTL 90s)   │
   │                           │                           │
   │                           │◄──── GET /api/devices ────┤
   │                           │──── ONLINE / OFFLINE ─────►│
```

## 先把 MQTT 翻译成前端语言

你不需要先系统学习物联网。第一版只要理解下面 6 个词：

| MQTT 词汇 | 可以先这样理解 | 本项目中的例子 |
| --- | --- | --- |
| Broker | 消息中转服务器，类似只负责转发事件的服务端 | Mosquitto |
| Topic | 事件名/频道名，类似 Socket.IO event name | `skytrace/local/device/UAV-001/heartbeat` |
| Publish | 向某个事件名发送数据 | 模拟器发心跳 |
| Subscribe | 监听某类事件 | Java 监听 `device/+/heartbeat` |
| Payload | 事件携带的 JSON | `{"deviceCode":"UAV-001"}` |
| QoS | 消息投递可靠程度 | 心跳用 0，状态用 1 |

这里的 `+` 是单层通配符：

```text
订阅: skytrace/local/device/+/heartbeat
匹配: skytrace/local/device/UAV-001/heartbeat
匹配: skytrace/local/device/CAMERA-001/heartbeat
不匹配: skytrace/prod/device/UAV-001/heartbeat
```

一个容易混淆的点：MQTT 不取代现有 HTTP API。

- 设备/模拟器用 MQTT 报到；
- Java 把“最近报到过”写入 Redis；
- 浏览器仍然通过 `/api/devices` 读取结果；
- 浏览器不直连 Broker，也不保存 MQTT 用户名和密码。

### 准备工作

在 WSL 终端进入仓库的 Linux 路径（也就是 Windows 中
`\\wsl.localhost\...\skytrace-platform` 对应的目录），确认：

```bash
docker compose version
test -f deploy/.env
```

如果第二条没有成功，先按照项目 [README](../README.md) 从
`deploy/.env.example` 创建 `deploy/.env` 并配置本地密码。本文假设原有完整栈至少已经
成功启动过一次；MQTT 只是它上面的可选覆盖层。

## 开发前先看懂仓库现状

当前代码已经完成了后半段，缺的是 MQTT 输入端：

- [`DeviceController`](../backend-java/src/main/java/com/skytrace/backend/device/DeviceController.java)
  已有 `POST /devices/{deviceCode}/heartbeat`；
- [`DeviceService`](../backend-java/src/main/java/com/skytrace/backend/device/service/DeviceService.java)
  会确认设备存在，再调用 `DevicePresenceService.heartbeat`；
- `DevicePresenceService` 会写入
  `skytrace:device:online:{deviceCode}`，默认 TTL 是 90 秒；
- [`DeviceView.vue`](../frontend/src/views/DeviceView.vue) 已经会把 API 返回的
  `ONLINE` / `OFFLINE` 渲染成状态标签；
- 数据库种子中已经有 `UAV-001` 和 `CAMERA-001`。

因此，这次不要重写设备列表，也不要新增一套在线状态表。MQTT Handler 最终只需调用
已经存在的 `heartbeat` 或 `clear`。

### 先体验一次现有行为

如果完整环境已经启动：

1. 打开 `http://localhost:8888/devices`；
2. 找到 `UAV-001`；
3. 点击“心跳”；
4. 页面会显示 `ONLINE`；
5. 约 90 秒不再点击，刷新页面后又会显示 `OFFLINE`。

MQTT 接入只是把“人点击心跳按钮”替换成“模拟器定时发消息”。先建立这个心智模型，
后面的 Java 代码会容易很多。

> 不建议用 `cd backend-java && mvn spring-boot:run` 作为本教程的联调入口。
> 默认 `local` profile 明确关闭了 Redis 自动配置和 `app.cache`，即使 MQTT 收到消息，
> 也没有 Presence 可写。端到端联调请走 Docker Compose。

## 这次开发的范围

按三个独立关卡推进。每一关都能单独验收，不要一次写完再一起排错。

| 关卡 | 你要新增什么 | 看到什么才算过关 |
| --- | --- | --- |
| A：消息能走 | Mosquitto 配置、Compose overlay | 手动发布的消息能被订阅端看到 |
| B：设备会说话 | Python `device-sim` | 每 30 秒看到两台设备的 heartbeat |
| C：系统听得懂 | Java 配置、Handler、Subscriber、测试 | 设备页随模拟器变为 ONLINE/OFFLINE |
| D：体验优化（可选） | Vue 静默轮询 | 页面无需手动刷新 |

建议每过一关提交一次，出问题时容易回退和比较。

---

## 先冻结消息协议

写代码前先确定前后端都能读懂的“接口契约”。它相当于 REST API 的 URL、method 和
request body。

### Topic

```text
skytrace/{env}/device/{deviceCode}/heartbeat
skytrace/{env}/device/{deviceCode}/status
skytrace/{env}/device/{deviceCode}/telemetry
```

第一版固定：

- `{env}`：本地为 `local`；
- `{deviceCode}`：必须是数据库已存在的编号；
- `heartbeat`：只表示“我还活着”；
- `status`：主动上线、下线和运行状态变化；
- `telemetry`：实时位置（经纬度），与 heartbeat 并列，不塞进 heartbeat。

### Payload

Heartbeat：

```json
{
  "deviceCode": "UAV-001",
  "ts": "2026-08-06T10:00:00Z",
  "source": "sim"
}
```

Status：

```json
{
  "deviceCode": "UAV-001",
  "ts": "2026-08-06T10:00:00Z",
  "online": true,
  "mode": "IDLE",
  "battery": 87,
  "source": "sim"
}
```

Telemetry：

```json
{
  "deviceCode": "UAV-001",
  "ts": "2026-08-12T01:00:00Z",
  "source": "sim",
  "latitude": 31.2304,
  "longitude": 121.4737,
  "altitude": 120.0,
  "heading": 45.0
}
```

第一版的处理规则：

| 情况 | Java 应该做什么 |
| --- | --- |
| 已知设备的 heartbeat | 调用 `presence.heartbeat(code)` |
| 已知设备的 `online: true` | 调用 `presence.heartbeat(code)` |
| 已知设备的 `online: false` | 调用 `presence.clear(code)`，并清除遥测 Redis key |
| 已知设备的 telemetry（含 lat/lon） | `presence.heartbeat` + Redis 最新点 + Rabbit 实时事件 |
| telemetry 缺 lat/lon | 忽略并记 warning |
| 数据库中不存在的设备 | 忽略，不自动创建设备 |
| Topic 编号与 JSON 编号不同 | 忽略并记 warning 日志 |
| 非法 Topic / 非法 JSON | 忽略并记 warning 日志 |

### QoS、retain 与 TTL

| 消息 | QoS | retained | 原因 |
| --- | --- | --- | --- |
| heartbeat | 0 | false | 高频、下一次很快再来，偶尔丢一条可接受 |
| status | 1 | false | 上下线变化更重要，至少送达一次即可 |
| telemetry | 0 | false | 1～2 秒一条，丢一条可接受，下一帧会补上 |

`HEARTBEAT_INTERVAL_SEC` 默认 30 秒，Presence TTL 默认 90 秒。保持：

```text
心跳间隔 < Presence TTL / 2
```

`TELEMETRY_INTERVAL_SEC` 默认 2 秒。最新坐标写入 Redis Hash
`skytrace:device:telemetry:{deviceCode}`，TTL 与 Presence 同量级（约 90 秒），不落 MySQL。

不要把 `online:true` 设置为 retained。否则 Java 重连时可能收到很久以前的“上线”消息，
让实际已离线的设备短暂复活。设备在线的最终判定仍由 Redis TTL 兜底。

---

## 关卡 A：先让消息能走

这一关不改 Java、不改 Vue，只启动一个最小 Broker。

### A1. 新建 Mosquitto 配置

`deploy/mqtt/mosquitto.conf` 当前要求认证：

```conf
listener 1883
allow_anonymous false
password_file /tmp/mosquitto.passwd
acl_file /mosquitto/config/acl.conf
persistence false
```

容器启动脚本 `docker-entrypoint.sh` 会用环境变量生成 password_file。
默认本地用户：

| 用户 | 默认密码 | ACL |
| --- | --- | --- |
| `backend` | `skytrace-mqtt-backend` | 只读 `skytrace/+/device/+/#` |
| `device-sim` | `skytrace-mqtt-sim` | 只写 heartbeat/status/telemetry |

本地可继续无 TLS（明文 TCP）；真机/预发请再上 `mqtts` + 证书。
`.env` 里用 `MQTT_BACKEND_*` / `MQTT_SIM_*` 覆盖默认密码。

### A2. 新建 Compose overlay

新建 `deploy/docker-compose.mqtt.yml`：

```yaml
services:
  mqtt:
    image: eclipse-mosquitto:2
    container_name: skytrace-mqtt
    restart: unless-stopped
    ports:
      - "127.0.0.1:${MQTT_HOST_PORT:-1883}:1883"
    volumes:
      - ./mqtt/mosquitto.conf:/mosquitto/config/mosquitto.conf:ro
    networks:
      - backend
```

它叫 overlay，是因为必须和主文件一起使用：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mqtt.yml \
  config
```

注意两件事：

1. `deploy/docker-compose.yml` 必须放在前面；
2. 不要单独运行 `docker-compose.mqtt.yml`，它引用了主文件中的 `backend` network。

### A3. 补充环境变量说明

在 `deploy/.env.example` 末尾加入：

```dotenv
# Local MQTT device simulation (disabled unless the overlay is used)
MQTT_ENABLED=false
MQTT_BROKER_URL=tcp://mqtt:1883
MQTT_ENV=local
MQTT_HOST_PORT=1883
MQTT_CLIENT_ID=skytrace-backend-java
```

如果本机已经有程序占用 1883，只改宿主机端口即可：

```dotenv
MQTT_HOST_PORT=1884
```

容器之间仍然使用 `mqtt:1883`，不需要跟着改。

### A4. 启动并做“回声测试”

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mqtt.yml \
  up -d mqtt
```

终端 1 持续订阅：

```bash
docker exec skytrace-mqtt mosquitto_sub \
  -h 127.0.0.1 \
  -u backend -P skytrace-mqtt-backend \
  -t 'skytrace/local/device/+/+' \
  -v
```

终端 2 手动发布：

```bash
docker exec skytrace-mqtt mosquitto_pub \
  -h 127.0.0.1 \
  -u device-sim -P skytrace-mqtt-sim \
  -t 'skytrace/local/device/UAV-001/heartbeat' \
  -q 0 \
  -m '{"deviceCode":"UAV-001","source":"manual"}'
```

终端 1 能立即看到 Topic 和 JSON，这一关就结束。此时设备页仍然不会变化，因为 Java
还没有订阅消息，这是正常现象。

---

## 关卡 B：让模拟设备定时发消息

模拟器是一台“假的无人机”，职责只有生成消息。不要把定时发布逻辑写进 Java 服务，
否则无法单独判断问题在发送端还是消费端。

### B1. 新建目录

```text
device-sim/
├── Dockerfile
├── requirements.txt
└── sim.py
```

`requirements.txt`：

```text
paho-mqtt==2.1.0
```

`Dockerfile`：

```dockerfile
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --create-home --uid 10001 app

COPY sim.py .
USER app

CMD ["python", "sim.py"]
```

### B2. 编写 `sim.py`

下面这个版本处理了 `docker stop` 的 SIGTERM，因此正常停止时能先发
`online:false`；如果进程被强杀，Redis TTL 仍会负责最终离线。

```python
import json
import os
import signal
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt


MQTT_HOST = os.getenv("MQTT_HOST", "127.0.0.1")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_ENV = os.getenv("MQTT_ENV", "local")
DEVICE_CODES = [
    code.strip()
    for code in os.getenv(
        "DEVICE_CODES",
        "UAV-001,CAMERA-001",
    ).split(",")
    if code.strip()
]
INTERVAL = int(os.getenv("HEARTBEAT_INTERVAL_SEC", "30"))

stopping = threading.Event()
connected = threading.Event()


def now() -> str:
    return (
        datetime.now(timezone.utc)
        .isoformat(timespec="seconds")
        .replace("+00:00", "Z")
    )


def topic(code: str, kind: str) -> str:
    return f"skytrace/{MQTT_ENV}/device/{code}/{kind}"


def payload(code: str, **extra: object) -> str:
    return json.dumps(
        {
            "deviceCode": code,
            "ts": now(),
            "source": "sim",
            **extra,
        },
        ensure_ascii=False,
    )


def on_connect(client, userdata, flags, reason_code, properties) -> None:
    if reason_code == 0:
        connected.set()
        print(f"connected to mqtt://{MQTT_HOST}:{MQTT_PORT}", flush=True)
    else:
        print(f"MQTT connect failed: {reason_code}", flush=True)


def request_stop(signum, frame) -> None:
    stopping.set()


def publish(client: mqtt.Client, code: str, kind: str, body: str, qos: int) -> None:
    info = client.publish(topic(code, kind), body, qos=qos, retain=False)
    if info.rc != mqtt.MQTT_ERR_SUCCESS:
        print(f"publish failed: {code}/{kind}, rc={info.rc}", flush=True)


def connect_with_retry(client: mqtt.Client) -> None:
    for attempt in range(1, 31):
        try:
            client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
            return
        except OSError as error:
            print(f"waiting for MQTT ({attempt}/30): {error}", flush=True)
            time.sleep(1)
    raise RuntimeError("MQTT was not ready within 30 seconds")


def main() -> None:
    signal.signal(signal.SIGINT, request_stop)
    signal.signal(signal.SIGTERM, request_stop)

    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=os.getenv("MQTT_CLIENT_ID", "skytrace-device-sim"),
    )
    client.on_connect = on_connect
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    connect_with_retry(client)
    client.loop_start()

    if not connected.wait(timeout=10):
        client.loop_stop()
        raise RuntimeError("MQTT connection acknowledgement timed out")

    try:
        for code in DEVICE_CODES:
            publish(
                client,
                code,
                "status",
                payload(code, online=True, mode="IDLE", battery=100),
                qos=1,
            )

        while not stopping.is_set():
            for code in DEVICE_CODES:
                publish(client, code, "heartbeat", payload(code), qos=0)
                print(f"heartbeat -> {code}", flush=True)
            stopping.wait(INTERVAL)
    finally:
        for code in DEVICE_CODES:
            info = client.publish(
                topic(code, "status"),
                payload(code, online=False, mode="OFFLINE"),
                qos=1,
                retain=False,
            )
            info.wait_for_publish(timeout=3)
        client.disconnect()
        client.loop_stop()


if __name__ == "__main__":
    main()
```

### B3. 把模拟器加入 overlay

在 `deploy/docker-compose.mqtt.yml` 的 `services` 下追加：

```yaml
  device-sim:
    build:
      context: ../device-sim
    container_name: skytrace-device-sim
    restart: unless-stopped
    environment:
      MQTT_HOST: mqtt
      MQTT_PORT: "1883"
      MQTT_ENV: ${MQTT_ENV:-local}
      MQTT_CLIENT_ID: skytrace-device-sim
      DEVICE_CODES: UAV-001,CAMERA-001
      HEARTBEAT_INTERVAL_SEC: "30"
    depends_on:
      - mqtt
    networks:
      - backend
```

### B4. 验收模拟器

保持 A4 的订阅终端打开，然后执行：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mqtt.yml \
  up -d --build device-sim
```

查看模拟器日志：

```bash
docker logs -f skytrace-device-sim
```

通过标准：

- 启动时每台设备各有一条 `status`；
- 启动后立刻有 heartbeat；
- 此后约每 30 秒一条 heartbeat；
- `docker stop skytrace-device-sim` 时各有一条 `online:false`。

到这里仍然不要求设备页变化。先确认生产者和 Broker 完全正常，再进入 Java。

---

## 关卡 C：让 Java 听懂消息

推荐新建下面这些文件：

```text
backend-java/src/main/java/com/skytrace/backend/device/mqtt/
├── MqttProperties.java
├── DeviceMqttConfig.java
├── DeviceMqttMessageHandler.java
└── DeviceMqttSubscriber.java

backend-java/src/test/java/com/skytrace/backend/device/mqtt/
└── DeviceMqttMessageHandlerTest.java
```

把“解析并处理消息”和“连接 Broker”拆成两个类非常重要：Handler 可以像普通函数一样
快速单测，测试时不需要启动 Docker。

### C1. 加 Java MQTT 客户端

在 [`backend-java/pom.xml`](../backend-java/pom.xml) 的 `dependencies` 中加入：

```xml
<dependency>
    <groupId>org.eclipse.paho</groupId>
    <artifactId>org.eclipse.paho.client.mqttv3</artifactId>
    <version>1.2.5</version>
</dependency>
```

先确认依赖能解析：

```bash
cd backend-java
mvn -DskipTests compile
```

### C2. 加配置，但默认关闭

在
[`application.yml`](../backend-java/src/main/resources/application.yml)
的 `app` 下加入：

```yaml
  mqtt:
    enabled: ${MQTT_ENABLED:false}
    broker-url: ${MQTT_BROKER_URL:tcp://localhost:1883}
    env: ${MQTT_ENV:local}
    client-id: ${MQTT_CLIENT_ID:skytrace-backend-java}
    username: ${MQTT_USERNAME:}
    password: ${MQTT_PASSWORD:}
```

`MqttProperties.java` 只负责把 YAML 映射成 Java 字段：

```java
@ConfigurationProperties(prefix = "app.mqtt")
public class MqttProperties {
    private boolean enabled = false;
    private String brokerUrl = "tcp://localhost:1883";
    private String env = "local";
    private String clientId = "skytrace-backend-java";
    private String username = "";
    private String password = "";

    // 用 IDE 生成标准 getter / setter。
    // 写法可直接参考同仓库的 CacheProperties。
}
```

`DeviceMqttConfig.java` 在开关打开时注册配置属性：

```java
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(
        name = "app.mqtt.enabled",
        havingValue = "true"
)
public class DeviceMqttConfig {
}
```

类所在包已经位于 `com.skytrace.backend` 下，不需要修改启动类的扫描范围。后面的
`DeviceMqttSubscriber` 也要加相同的 `@ConditionalOnProperty`；这样开关关闭时不会创建
MQTT client 或尝试连接 Broker。Handler 本身可以保持普通 `@Component`，便于单测和
复用。

### C3. 先写 Handler，不要急着连接 Broker

`DeviceMqttMessageHandler` 的职责应限制在下面这条流水线：

```text
检查 Topic
  → 解析 JSON
  → Topic 中的 code 必须等于 payload.deviceCode
  → 查询数据库确认设备存在
  → heartbeat / clear Presence
```

核心结构可以写成：

```java
@Component
public class DeviceMqttMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(
            DeviceMqttMessageHandler.class
    );

    private final DeviceRepository deviceRepository;
    private final ObjectProvider<DevicePresenceService> presenceProvider;
    private final ObjectMapper objectMapper;

    public DeviceMqttMessageHandler(
            DeviceRepository deviceRepository,
            ObjectProvider<DevicePresenceService> presenceProvider,
            ObjectMapper objectMapper) {
        this.deviceRepository = deviceRepository;
        this.presenceProvider = presenceProvider;
        this.objectMapper = objectMapper;
    }

    public void onMessage(String topic, String payloadJson) {
        try {
            String[] parts = topic.split("/", -1);
            if (parts.length != 5
                    || !"skytrace".equals(parts[0])
                    || !"device".equals(parts[2])) {
                log.warn("忽略非法 MQTT topic: {}", topic);
                return;
            }

            String deviceCode = parts[3];
            String kind = parts[4];
            if (!"heartbeat".equals(kind) && !"status".equals(kind)) {
                return;
            }

            JsonNode payload = objectMapper.readTree(payloadJson);
            if (!deviceCode.equals(payload.path("deviceCode").asText())) {
                log.warn("MQTT topic 与 payload 的 deviceCode 不一致: {}", topic);
                return;
            }

            if (!deviceRepository.existsByDeviceCode(deviceCode)) {
                log.warn("忽略未知设备的 MQTT 消息: {}", deviceCode);
                return;
            }

            DevicePresenceService presence = presenceProvider.getIfAvailable();
            if (presence == null) {
                log.warn("Presence 未启用，忽略 MQTT 消息: {}", deviceCode);
                return;
            }

            if ("heartbeat".equals(kind)) {
                presence.heartbeat(deviceCode);
                return;
            }

            JsonNode online = payload.get("online");
            if (online == null || !online.isBoolean()) {
                log.warn("status 消息缺少 boolean online: {}", deviceCode);
                return;
            }
            if (online.booleanValue()) {
                presence.heartbeat(deviceCode);
            } else {
                presence.clear(deviceCode);
            }
        } catch (Exception error) {
            // MQTT 回调线程不能因为一条坏消息退出。
            log.warn("处理 MQTT 设备消息失败: {}", error.getMessage());
        }
    }
}
```

需要补齐的 imports 都来自现有依赖：Jackson、SLF4J、Spring `ObjectProvider` 和项目中的
`DeviceRepository`、`DevicePresenceService`。

这里故意不调用 `DeviceService.heartbeat`。那个方法会先查完整实体并生成 HTTP response；
MQTT Handler 只需要“确认存在 + 更新 Presence”，保持消息回调足够轻。

### C4. 先用单测证明 Handler 正确

在 `DeviceMqttMessageHandlerTest` 至少覆盖 5 个行为：

```text
known heartbeat       → verify(presence).heartbeat("UAV-001")
online status         → verify(presence).heartbeat("UAV-001")
offline status        → verify(presence).clear("UAV-001")
unknown device        → verifyNoInteractions(presence)
topic/payload mismatch → verifyNoInteractions(presence)
```

测试用 Mockito mock `DeviceRepository`、`ObjectProvider<DevicePresenceService>` 和
`DevicePresenceService`；`ObjectMapper` 可以直接 `new ObjectMapper()`。测试方法直接调用
`handler.onMessage(...)`，不要启动 Spring，也不要连接真实 Broker。

运行：

```bash
cd backend-java
mvn -Dtest=DeviceMqttMessageHandlerTest test
```

Handler 测试全绿以后再写 Subscriber。否则 Broker、线程和 JSON 解析问题会混在一起。

### C5. 编写 Subscriber

`DeviceMqttSubscriber` 只负责生命周期，不放业务判断：

```text
Spring 启动
  → 创建唯一 clientId
  → 连接 tcp://mqtt:1883
  → 订阅两个 Topic filter
  → 每条消息交给 handler.onMessage
Spring 停止
  → disconnect
  → close
```

实现时使用这些关键设置：

```java
MqttConnectOptions options = new MqttConnectOptions();
options.setAutomaticReconnect(true);
options.setCleanSession(true);
options.setConnectionTimeout(10);
options.setKeepAliveInterval(30);

String clientId = properties.getClientId() + "-" + UUID.randomUUID();
MqttClient client = new MqttClient(
        properties.getBrokerUrl(),
        clientId,
        new MemoryPersistence()
);
```

用户名非空时再设置凭证，避免把空字符串当真实账号：

```java
if (!properties.getUsername().isBlank()) {
    options.setUserName(properties.getUsername());
    options.setPassword(properties.getPassword().toCharArray());
}
```

连接成功后订阅：

```java
String prefix = "skytrace/" + properties.getEnv() + "/device/+/";

client.subscribe(prefix + "heartbeat", 0, (topic, message) ->
        handler.onMessage(
                topic,
                new String(message.getPayload(), StandardCharsets.UTF_8)
        )
);

client.subscribe(prefix + "status", 1, (topic, message) ->
        handler.onMessage(
                topic,
                new String(message.getPayload(), StandardCharsets.UTF_8)
        )
);
```

还要补上四件容易漏的事：

1. 类上添加与配置相同的 `@ConditionalOnProperty`；
2. 初次连接失败时每秒重试，最多约 30 秒，处理 Compose 启动竞态；
3. 使用 `MqttCallbackExtended.connectComplete` 在自动重连后重新订阅；
4. `@PreDestroy` 中依次 `disconnect()` 和 `close()`，并吞掉关闭阶段的次要异常。

> MQTT clientId 必须唯一。两个实例使用相同 ID 时，Broker 会不断把旧连接踢下线。
> 随机后缀解决多实例冲突；同一个 `MqttClient` 自动重连时仍会复用这个 ID。

启动日志至少打印“连接成功”和两个已订阅的 filter。排错时这两行非常有用，但不要打印
用户名、密码或完整敏感 payload。

### C6. 在 overlay 中打开 Java MQTT

在 `deploy/docker-compose.mqtt.yml` 的 `services` 下再追加：

```yaml
  backend-java:
    environment:
      MQTT_ENABLED: "true"
      MQTT_BROKER_URL: tcp://mqtt:1883
      MQTT_ENV: ${MQTT_ENV:-local}
      MQTT_CLIENT_ID: ${MQTT_CLIENT_ID:-skytrace-backend-java}
    depends_on:
      mqtt:
        condition: service_started
```

Compose 会把这里的 environment 和主文件中已有的 MySQL、Redis 等 environment 合并，
不是把它们全部覆盖。

### C7. 端到端启动

先检查合并后的配置：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mqtt.yml \
  config
```

再构建并启动完整环境：

```bash
docker compose --env-file deploy/.env \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mqtt.yml \
  up -d --build
```

分别观察两端：

```bash
docker logs -f skytrace-device-sim
```

```bash
docker logs -f skytrace-backend-java
```

最后打开 `http://localhost:8888/devices` 并刷新。`UAV-001` 与 `CAMERA-001` 应显示
`ONLINE`。

### C8. 验证离线的两条路径

正常停止：

```bash
docker stop skytrace-device-sim
```

模拟器会先发 `online:false`。刷新设备页，应立即看到 `OFFLINE`。

异常消失：重新启动模拟器，确认 ONLINE 后强制结束进程或断开网络，使它来不及发送
offline。Redis key 会自然过期：

```bash
docker exec skytrace-redis redis-cli \
  TTL skytrace:device:online:UAV-001
```

TTL 应从不超过 90 的正数逐渐减少，心跳到达时又回到约 90。超时后刷新页面，应显示
`OFFLINE`。

---

## 关卡 D（可选）：让 Vue 自动刷新

MQTT 主链路完成时，前端可以一行代码都不改；当前页面只在首次进入和用户操作后请求
设备列表，所以手动刷新即可看到变化。

如果想让演示更自然，可以给
[`DeviceView.vue`](../frontend/src/views/DeviceView.vue)
增加 5 秒静默轮询。先把 `refresh` 改成支持 `silent`：

```ts
async function refresh(silent = false) {
  if (!silent) loading.value = true
  errorMessage.value = ''
  try {
    devices.value = await getDevices()
  } catch (error) {
    errorMessage.value =
      error instanceof Error ? error.message : t('devices.loadFailed')
  } finally {
    if (!silent) loading.value = false
  }
}
```

然后引入 `onBeforeUnmount` 并管理 timer：

```ts
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'

let pollingId: number | undefined

onMounted(() => {
  void refresh()
  pollingId = window.setInterval(() => {
    if (!document.hidden && !loading.value) {
      void refresh(true)
    }
  }, 5_000)
})

onBeforeUnmount(() => {
  if (pollingId !== undefined) {
    window.clearInterval(pollingId)
  }
})
```

先用轮询完成演示，不要在这一期让浏览器直连 MQTT。以后如果确实需要秒级状态更新，
应由后端把设备状态转换为现有 Socket.IO 事件，再由 Vue 订阅。

---

## 从外到内的排错方法

不要一上来读 Java 堆栈。按消息流方向逐层确认，第一处不符合预期的地方就是当前故障层。

| 检查点 | 怎么检查 | 正常结果 | 常见原因 |
| --- | --- | --- | --- |
| Broker 在运行 | `docker ps --filter name=skytrace-mqtt` | 状态为 Up | overlay 没有带上 |
| Broker 收到消息 | `mosquitto_sub` 订阅 `device/+/+` | 每 30 秒有 heartbeat；约每 2 秒有 UAV-001 telemetry | sim 未连接、Topic 环境不一致 |
| 模拟器正常 | `docker logs skytrace-device-sim` | 有 connected、heartbeat、telemetry | 容器内误用了 localhost |
| Java 已订阅 | `docker logs skytrace-backend-java` | 有 connected/subscribed（含 telemetry） | `MQTT_ENABLED` 仍是 false |
| Java 接受设备 | 看 warning 日志 | 没有 unknown/mismatch | 数据库没有该 code、JSON 不一致 |
| Redis 有 Presence | `redis-cli TTL skytrace:device:online:UAV-001` | 返回正数 | cache 未启用、Handler 未调用 heartbeat |
| Redis 有遥测 | `redis-cli HGETALL skytrace:device:telemetry:UAV-001` | 有 latitude/longitude | telemetry 未订阅或设备未知 |
| API 返回 ONLINE | 浏览器 Network 看 `/api/devices` | `status: "ONLINE"` | BFF/Java 未重建或 Redis 已过期 |
| Socket 推送 | 前端 Network / 控制台 | 收到 `device.telemetry` | Node 未消费 Rabbit、未登录 WS |
| 地图轨迹 | 打开 `http://localhost:8888/map` | UAV-001 移动且有短尾迹 | 前端未重建、Cesium 未订阅 |

### 高频坑

**容器里不能用 `localhost` 找另一个容器。**

- 宿主机工具连接 Broker：`127.0.0.1:1883`；
- `device-sim` 容器连接 Broker：`mqtt:1883`；
- `backend-java` 容器连接 Broker：`tcp://mqtt:1883`。

**Topic 的环境必须一致。**

模拟器发布 `skytrace/local/...`，Java 却订阅 `skytrace/dev/...` 时，双方都不会报错，
但永远收不到消息。检查两边的 `MQTT_ENV`。

**只改代码但没有重建镜像。**

Java `pom.xml`、Java 源码、`device-sim` 源码变化后，用 `up -d --build`，单纯 restart
不会把新代码放入镜像。

**设备编号不存在。**

第一版不会自动创建设备。默认可用 `UAV-001`、`CAMERA-001`；如果改
`DEVICE_CODES`，请先通过设备页创建同编号设备。

**把在线状态写回 MySQL。**

当前 API 的 `status` 是运行时合成字段：数据库存设备主数据，Redis TTL 表示在线状态。
不要让 MQTT Handler 更新 `device.status`，否则会出现 DB 与 TTL 两个真相源。

---

## 扩展：地图实时轨迹（telemetry）

在心跳链路稳定后，可演示「飞机在飞」而无需真机。数据流：

```text
device-sim → MQTT .../telemetry
  → Java 订阅 → Redis 最新点 + Rabbit fanout
  → Node 消费 → Socket.IO `device.telemetry`
  → `/map` Cesium Entity + 短折线（内存约 200 点）
```

### 本地启用

代码变更后先重建相关镜像，再启 MQTT overlay：

```bash
./scripts/skytrace.sh rebuild backend-java backend-node frontend
./scripts/skytrace.sh mqtt-start
```

确认：

1. `docker logs skytrace-device-sim` 有 `telemetry -> UAV-001 lat=...`；
2. `mosquitto_sub -h 127.0.0.1 -u backend -P skytrace-mqtt-backend -t 'skytrace/local/device/+/telemetry' -v` 能看到 JSON；
3. Redis：`HGETALL skytrace:device:telemetry:UAV-001` 有坐标；
4. 登录后打开 `http://localhost:8888/map`，可见 UAV-001 移动与青色尾迹。

模拟器相关环境变量（`docker-compose.mqtt.yml`）：

| 变量 | 默认 | 含义 |
| --- | --- | --- |
| `TELEMETRY_INTERVAL_SEC` | `2` | 上报周期（秒） |
| `TELEMETRY_DEVICE_CODE` | `UAV-001` | 仅该机发 telemetry |
| `TELEMETRY_CENTER_LAT` / `LON` | 上海附近 | 环线中心 |
| `TELEMETRY_ORBIT_RADIUS_DEG` | `0.008` | 环线半径（度） |

本扩展不做：历史轨迹表、航线编辑地图化、多机编队、完整飞控状态机。

航线地图编辑与任务缩略图见前端 `/routes`、`/drone`：点选/拖拽航点写回 `waypointsJson`；任务列表展示绑定航线缩略图；RUNNING 任务可跳转 `/map` 看实时位置（需 `mqtt-start`）。

## 扩展：任务级飞行轨迹回放

`device_telemetry_point`（Flyway V19）落库任务期间的遥测坐标，用于事后回放：

- **落库条件**：仅当设备存在一个 `RUNNING` 状态的巡检任务时才写入（`DeviceTelemetryHistoryService.recordIfTaskRunning`），避免把所有心跳期坐标无限期保留；`task_code` 取当前 RUNNING 任务
- **API**：`GET /inspection-tasks/{taskCode}/telemetry`（Java）→ Node 同路径代理 → 前端 `getTaskTelemetryTrack`
- **前端**：`/drone` 任务列表，RUNNING/COMPLETED 任务显示「轨迹回放」按钮，打开 `TelemetryReplay` 组件：Cesium Primitive 折线 + 无人机模型，播放/暂停/拖动进度条
- **不做**：跨任务轨迹合并、地图历史图层、轨迹导出

验收：任务 `start` 后运行 `mqtt-start`，等 telemetry 上报几次，`complete` 任务后打开「轨迹回放」应看到完整折线与可播放动画。

---

## 扩展：MQTT 认证与 ACL

本地 overlay 已关闭匿名访问：

- `allow_anonymous false` + `password_file` + `acl.conf`
- Java 用 `MQTT_USERNAME` / `MQTT_PASSWORD`（overlay 注入为 backend 凭据）
- device-sim 用 `MQTT_USERNAME` / `MQTT_PASSWORD`（overlay 注入为 device-sim 凭据）
- 无用户名密码的 `mosquitto_pub/sub` 会被拒绝（CONNACK 鉴权失败）

仍未默认开启（真机下一阶段）：

- 生产级证书轮换与密钥托管（本地可用自签演练，见下）
- 每台真机独立账号与按 `deviceCode` 细粒度 ACL
- EMQX 集群

### 可选：本地 mqtts（TLS）

```bash
./scripts/skytrace.sh mqtt-tls-start
```

会生成 `deploy/mqtt/certs/`（已 gitignore），叠加 `docker-compose.mqtt-tls.yml`：

- 仍监听 `1883`（明文，方便工具）
- 新增 `8883`（TLS）；Java / device-sim 改连 `ssl://mqtt:8883`
- 默认 `MQTT_TLS_INSECURE=true` 信任自签；生产改为受信 CA 并设 `false`

手工验证：

```bash
mosquitto_sub -h 127.0.0.1 -p 8883 \
  --cafile deploy/mqtt/certs/ca.crt \
  -u backend -P "$MQTT_BACKEND_PASSWORD" \
  -t 'skytrace/local/device/+/telemetry' -v
```

---

## 扩展：Socket.IO 多实例（Redis adapter）

Node 启动时默认启用 `@socket.io/redis-adapter`（`SOCKETIO_REDIS_ADAPTER=true`）：

- 任一 Node 实例消费 Rabbit fanout 后 `server.emit`，其它实例上的浏览器客户端也能收到
- Redis 不可用时回退内存 adapter（仅适合单实例调试）
- 设 `SOCKETIO_REDIS_ADAPTER=false` 可强制关闭

已知限制（本扩展不解决）：

- 多个 `backend-java` 同时订阅同一 MQTT topic 会重复写 Redis / 重复投递 Rabbit；Java MQTT 侧仍按单订阅者设计（见 [horizontal-scaling.md](./horizontal-scaling.md)）
- Temporal 巡检工作流已含启动校验、24h 超时与 Signal 幂等，仍非飞控编排

---

## 完成定义（Definition of Done）

全部勾选后，这个功能才算完成：

- [ ] 不带 MQTT overlay 时，原有完整栈仍可启动；
- [ ] `MQTT_ENABLED` 默认是 `false`；
- [ ] Broker 拒绝匿名连接；backend / device-sim 凭据可连通；
- [ ] ACL 拒绝 device-sim 订阅或跨权限 publish（抽测）；
- [ ] Broker 能通过带账号的手工 publish / subscribe 验证；
- [ ] 模拟器定时发布两台已知设备的 heartbeat；
- [ ] 模拟器对 `UAV-001` 定时发布 telemetry（含 lat/lon）；
- [ ] Handler 单测覆盖 heartbeat、telemetry、上线、下线、未知设备和缺坐标；
- [ ] 一条坏 JSON 不会让订阅线程退出；
- [ ] Java 重连后会重新订阅；
- [ ] `GET /api/devices` 能看到 ONLINE；
- [ ] Redis 有 `skytrace:device:telemetry:UAV-001`；
- [ ] 前端收到 `device.telemetry`，`/map` 上可见移动与短轨迹；
- [ ] Node 日志可见 Socket.IO Redis adapter connected（除非显式关闭）；
- [ ] 正常停止模拟器会立即 OFFLINE；
- [ ] 异常停止后会在 Presence TTL 内 OFFLINE；
- [ ] 现有 HTTP heartbeat 仍然可用；
- [ ] 浏览器没有直连 Broker；
- [ ] `mvn test` 通过；
- [ ] `docker compose ... config` 通过。

## 建议提交顺序

```text
chore(deploy): 增加本地 Mosquitto MQTT overlay
feat(device-sim): 增加无真机设备心跳模拟器
feat(backend-java): 订阅 MQTT 并更新设备 Presence
feat(frontend): 轮询刷新设备在线状态       # 可选
feat(map): MQTT 模拟坐标并在 Cesium 实时绘制轨迹
feat(mqtt): Mosquitto 用户名密码与 ACL
feat(node): Socket.IO Redis adapter 支持多实例广播
```

## 这一期先不要做

- 浏览器通过 MQTT.js 直连 Broker；
- EMQX 集群、生产级 TLS 证书轮换与密钥托管；
- 用 RabbitMQ 代替设备接入协议（RabbitMQ 继续服务现有告警 / 实时推送链路）；
- 把未知 `deviceCode` 自动写入 MySQL；
- 多 Java 实例共用 MQTT 订阅的去重 / 选主；
- 指令下行、真飞控协议、多设备独立 LWT；
- 把 Temporal 巡检工作流做成完整飞控编排。

## 官方资料

- [Eclipse Paho Java `MqttClient` API](https://eclipse.dev/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttClient.html)
- [Eclipse Paho Java MQTT 包说明](https://eclipse.dev/paho/files/javadoc/org/eclipse/paho/client/mqttv3/package-summary.html)
- [Maven Central：Paho Java 1.2.5](https://central.sonatype.com/artifact/org.eclipse.paho/org.eclipse.paho.client.mqttv3)
- [Eclipse Paho Python Client](https://eclipse.dev/paho/files/paho.mqtt.python/html/index.html)
- [Mosquitto 配置手册](https://mosquitto.org/man/mosquitto-conf-5.html)

项目内相关资料：

- [架构说明](architecture.md)
- [运维速查](ops.md)
- [设备 API 与 Redis Presence 实现](../backend-java/src/main/java/com/skytrace/backend/device/service/DeviceService.java)
