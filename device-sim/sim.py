import json
import math
import os
import signal
import threading
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt


MQTT_HOST = os.getenv("MQTT_HOST", "127.0.0.1")
MQTT_PORT = int(os.getenv("MQTT_PORT", "1883"))
MQTT_ENV = os.getenv("MQTT_ENV", "local")
MQTT_USERNAME = os.getenv("MQTT_USERNAME", "").strip()
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "")
MQTT_TLS = os.getenv("MQTT_TLS", "false").lower() in ("1", "true", "yes")
MQTT_TLS_INSECURE = os.getenv("MQTT_TLS_INSECURE", "false").lower() in (
    "1",
    "true",
    "yes",
)
DEVICE_CODES = [
    code.strip()
    for code in os.getenv(
        "DEVICE_CODES",
        "UAV-001,CAMERA-001",
    ).split(",")
    if code.strip()
]
INTERVAL = int(os.getenv("HEARTBEAT_INTERVAL_SEC", "30"))
TELEMETRY_INTERVAL = float(os.getenv("TELEMETRY_INTERVAL_SEC", "2"))
TELEMETRY_DEVICE = os.getenv("TELEMETRY_DEVICE_CODE", "UAV-001")
CENTER_LAT = float(os.getenv("TELEMETRY_CENTER_LAT", "31.2304"))
CENTER_LON = float(os.getenv("TELEMETRY_CENTER_LON", "121.4737"))
ORBIT_RADIUS_DEG = float(os.getenv("TELEMETRY_ORBIT_RADIUS_DEG", "0.008"))
ORBIT_ALTITUDE = float(os.getenv("TELEMETRY_ALTITUDE_M", "120"))
# 约 90 秒一圈（半径 0.008°）
ORBIT_OMEGA = float(os.getenv("TELEMETRY_ORBIT_OMEGA", "0.07"))

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


def orbit_position(elapsed: float) -> tuple[float, float, float, float]:
    """上海附近小环线：返回 lat, lon, altitude, heading(度)。"""
    angle = elapsed * ORBIT_OMEGA
    lat = CENTER_LAT + ORBIT_RADIUS_DEG * math.sin(angle)
    lon = CENTER_LON + ORBIT_RADIUS_DEG * math.cos(angle)
    # 切线方向（北向为 0°）：d(lat)/dt ∝ cos, d(lon)/dt ∝ -sin
    heading = (math.degrees(math.atan2(
        -math.sin(angle),
        math.cos(angle),
    )) + 360.0) % 360.0
    return lat, lon, ORBIT_ALTITUDE, heading


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
    if MQTT_USERNAME:
        client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)
    if MQTT_TLS:
        # 本地自签可用 insecure；生产应挂载 CA 并关闭 insecure。
        client.tls_set()
        if MQTT_TLS_INSECURE:
            client.tls_insecure_set(True)
            print("MQTT TLS insecure mode enabled", flush=True)
    client.on_connect = on_connect
    client.reconnect_delay_set(min_delay=1, max_delay=30)

    connect_with_retry(client)
    client.loop_start()

    if not connected.wait(timeout=10):
        client.loop_stop()
        raise RuntimeError("MQTT connection acknowledgement timed out")

    started = time.monotonic()
    next_heartbeat = 0.0
    next_telemetry = 0.0

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
            now_mono = time.monotonic()

            if now_mono >= next_heartbeat:
                for code in DEVICE_CODES:
                    publish(client, code, "heartbeat", payload(code), qos=0)
                    print(f"heartbeat -> {code}", flush=True)
                next_heartbeat = now_mono + INTERVAL

            if (
                TELEMETRY_DEVICE in DEVICE_CODES
                and now_mono >= next_telemetry
            ):
                lat, lon, alt, heading = orbit_position(now_mono - started)
                publish(
                    client,
                    TELEMETRY_DEVICE,
                    "telemetry",
                    payload(
                        TELEMETRY_DEVICE,
                        latitude=round(lat, 7),
                        longitude=round(lon, 7),
                        altitude=round(alt, 1),
                        heading=round(heading, 1),
                    ),
                    qos=0,
                )
                print(
                    f"telemetry -> {TELEMETRY_DEVICE} "
                    f"lat={lat:.5f} lon={lon:.5f} hdg={heading:.0f}",
                    flush=True,
                )
                next_telemetry = now_mono + TELEMETRY_INTERVAL

            stopping.wait(0.2)
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
