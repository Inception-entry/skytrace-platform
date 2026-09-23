import json
from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch
from zoneinfo import ZoneInfo

import anyio
import pytest

from app.config import Settings
from app.detection_publisher import (
    DetectionAlarmPayload,
    publish_detection_alarm,
    stable_detection_id,
    to_legacy_java_local,
    to_wire_event_time,
)


def test_detection_payload_aliases() -> None:
    payload = DetectionAlarmPayload.model_validate(
        {
            "deviceCode": "UAV-1",
            "taskCode": "TASK-1",
            "eventType": "WEAPON_DETECTED",
            "weaponType": "KNIFE",
            "confidence": 0.91,
            "imageObjectKey": "TASK-1/a.jpg",
            "eventTime": "2030-01-01T08:15:00",
        }
    )
    assert payload.device_code == "UAV-1"
    assert payload.task_code == "TASK-1"
    assert payload.image_object_key == "TASK-1/a.jpg"
    assert isinstance(payload.event_time, datetime)


def test_wire_event_time_keeps_utc_offset() -> None:
    source = datetime(2026, 8, 24, 2, 0, tzinfo=timezone.utc)
    assert to_wire_event_time(source) == "2026-08-24T02:00:00Z"
    shanghai = datetime(2026, 8, 24, 10, 0, tzinfo=ZoneInfo("Asia/Shanghai"))
    assert to_wire_event_time(shanghai) == "2026-08-24T02:00:00Z"


def test_legacy_java_local_converts_utc_to_shanghai() -> None:
    source = datetime(2026, 8, 24, 2, 0, tzinfo=timezone.utc)
    assert to_legacy_java_local(source) == "2026-08-24T10:00:00"


def test_legacy_java_local_keeps_shanghai_offset() -> None:
    source = datetime(2026, 8, 24, 10, 0, tzinfo=ZoneInfo("Asia/Shanghai"))
    assert to_legacy_java_local(source) == "2026-08-24T10:00:00"


def test_legacy_java_local_crosses_utc_date() -> None:
    source = datetime(2026, 8, 24, 16, 30, tzinfo=timezone.utc)
    assert to_legacy_java_local(source) == "2026-08-25T00:30:00"


def test_legacy_java_local_rejects_naive_datetime() -> None:
    with pytest.raises(ValueError, match="timezone offset"):
        to_legacy_java_local(datetime(2026, 8, 24, 10, 0))


def test_stable_detection_id_is_deterministic() -> None:
    first = stable_detection_id(
        "req-1",
        frame=0,
        class_name="knife",
        x1=0.52,
        y1=0.40,
        x2=0.70,
        y2=0.62,
    )
    second = stable_detection_id(
        "req-1",
        frame=0,
        class_name="knife",
        x1=0.52,
        y1=0.40,
        x2=0.70,
        y2=0.62,
    )
    other = stable_detection_id(
        "req-1",
        frame=1,
        class_name="knife",
        x1=0.52,
        y1=0.40,
        x2=0.70,
        y2=0.62,
    )
    assert first == second
    assert first != other


def test_detection_payload_keeps_optional_detection_id() -> None:
    payload = DetectionAlarmPayload.model_validate(
        {
            "deviceCode": "UAV-1",
            "eventType": "WEAPON_DETECTED",
            "detectionId": "550e8400-e29b-41d4-a716-446655440000",
        }
    )
    assert payload.detection_id == "550e8400-e29b-41d4-a716-446655440000"
    assert payload.schema_version == 2


def test_publish_waits_for_publisher_confirms() -> None:
    async def _run() -> None:
        exchange = AsyncMock()
        channel = AsyncMock()
        channel.declare_exchange = AsyncMock(return_value=exchange)
        connection = AsyncMock()
        connection.channel = AsyncMock(return_value=channel)
        connection.close = AsyncMock()
        with patch(
            "app.detection_publisher.aio_pika.connect_robust",
            AsyncMock(return_value=connection),
        ):
            await publish_detection_alarm(
                Settings(),
                DetectionAlarmPayload.model_validate(
                    {
                        "deviceCode": "UAV-1",
                        "eventType": "WEAPON_DETECTED",
                    }
                ),
                request_id="req-confirm",
            )
        connection.channel.assert_awaited_with(publisher_confirms=True)
        assert exchange.publish.await_args.kwargs["mandatory"] is True
        assert exchange.publish.await_args.kwargs["routing_key"] == "alarm"
        body = json.loads(exchange.publish.await_args.args[0].body)
        assert body["schemaVersion"] == 2
        assert body["eventTime"].endswith("Z")

    anyio.run(_run)





def test_detection_payload_aliases() -> None:
    payload = DetectionAlarmPayload.model_validate(
        {
            "deviceCode": "UAV-1",
            "taskCode": "TASK-1",
            "eventType": "WEAPON_DETECTED",
            "weaponType": "KNIFE",
            "confidence": 0.91,
            "imageObjectKey": "TASK-1/a.jpg",
            "eventTime": "2030-01-01T08:15:00",
        }
    )
    assert payload.device_code == "UAV-1"
    assert payload.task_code == "TASK-1"
    assert payload.image_object_key == "TASK-1/a.jpg"
    assert isinstance(payload.event_time, datetime)
