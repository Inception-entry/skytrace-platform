from __future__ import annotations

import asyncio
from datetime import datetime, timezone

from app.config import Settings
from app.detection_publisher import (
    DetectionAlarmPayload,
    publish_detection_alarm,
)
from app.vision.detector import VisionDetector
from app.vision.labels import resolve_alarm
from app.schemas import (
    VisionAlarmCandidate,
    VisionBoxResponse,
    VisionDetectResponse,
)


async def analyze_image(
    *,
    detector: VisionDetector,
    settings: Settings,
    image_bytes: bytes,
    device_code: str,
    task_code: str | None,
    latitude: float | None,
    longitude: float | None,
    publish_alarms: bool,
    max_alarms: int,
    request_id: str,
) -> VisionDetectResponse:
    result = await asyncio.to_thread(detector.detect, image_bytes)
    boxes = [
        VisionBoxResponse(
            class_name=box.class_name,
            confidence=box.confidence,
            x1=box.x1,
            y1=box.y1,
            x2=box.x2,
            y2=box.y2,
        )
        for box in result.detections
    ]

    candidates: list[VisionAlarmCandidate] = []
    for box in sorted(
        result.detections,
        key=lambda item: item.confidence,
        reverse=True,
    ):
        mapped = resolve_alarm(box.class_name)
        if mapped is None:
            continue
        event_type, weapon_type = mapped
        candidates.append(
            VisionAlarmCandidate(
                event_type=event_type,
                weapon_type=weapon_type,
                class_name=box.class_name,
                confidence=box.confidence,
            )
        )

    published: list[VisionAlarmCandidate] = []
    if (
        publish_alarms
        and settings.messaging_enabled
        and candidates
    ):
        event_time = datetime.now(timezone.utc)
        for candidate in candidates[: max(1, max_alarms)]:
            await publish_detection_alarm(
                settings,
                DetectionAlarmPayload.model_validate(
                    {
                        "deviceCode": device_code,
                        "taskCode": task_code,
                        "eventType": candidate.event_type,
                        "weaponType": candidate.weapon_type,
                        "confidence": candidate.confidence,
                        "latitude": latitude,
                        "longitude": longitude,
                        "eventTime": event_time,
                    }
                ),
                request_id=request_id,
            )
            published.append(candidate)

    return VisionDetectResponse(
        backend=result.backend,
        model=result.model,
        detections=boxes,
        alarm_candidates=candidates,
        published_alarms=published,
    )
