from datetime import datetime, timezone
from zoneinfo import ZoneInfo

import pytest

from app.detection_publisher import (
    DetectionAlarmPayload,
    stable_detection_id,
    to_legacy_java_local,
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
