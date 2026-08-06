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