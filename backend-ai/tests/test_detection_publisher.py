from datetime import datetime

import pytest

from app.detection_publisher import DetectionAlarmPayload


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
