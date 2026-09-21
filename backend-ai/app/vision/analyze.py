from __future__ import annotations

import asyncio
from datetime import datetime, timezone

from app.config import Settings
from app.detection_publisher import (
    DetectionAlarmPayload,
    publish_detection_alarm,
    stable_detection_id,
)
from app.vision.detector import VisionDetector
from app.vision.image_bounds import assert_image_within_budget
from app.vision.labels import resolve_alarm
from app.vision.video_frames import extract_video_frames
from app.schemas import (
    VisionAlarmCandidate,
    VisionBoxResponse,
    VisionDetectResponse,
    VisionFrameDetectResponse,
    VisionVideoDetectResponse,
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
    assert_image_within_budget(
        image_bytes,
        max_side=settings.vision_max_image_side,
        max_pixels=settings.vision_max_image_pixels,
    )
    if detector.backend == "mock":
        result = detector.detect(image_bytes)
    else:
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
        count = 0
        for box in sorted(
            result.detections,
            key=lambda item: item.confidence,
            reverse=True,
        ):
            mapped = resolve_alarm(box.class_name)
            if mapped is None:
                continue
            if count >= max(1, max_alarms):
                break
            event_type, weapon_type = mapped
            await publish_detection_alarm(
                settings,
                DetectionAlarmPayload.model_validate(
                    {
                        "schemaVersion": 2,
                        "detectionId": stable_detection_id(
                            request_id,
                            frame=0,
                            class_name=box.class_name,
                            x1=box.x1,
                            y1=box.y1,
                            x2=box.x2,
                            y2=box.y2,
                        ),
                        "deviceCode": device_code,
                        "taskCode": task_code,
                        "eventType": event_type,
                        "weaponType": weapon_type,
                        "confidence": box.confidence,
                        "latitude": latitude,
                        "longitude": longitude,
                        "eventTime": event_time,
                    }
                ),
                request_id=request_id,
            )
            published.append(
                VisionAlarmCandidate(
                    event_type=event_type,
                    weapon_type=weapon_type,
                    class_name=box.class_name,
                    confidence=box.confidence,
                )
            )
            count += 1

    return VisionDetectResponse(
        backend=result.backend,
        model=result.model,
        detections=boxes,
        alarm_candidates=candidates,
        published_alarms=published,
    )


async def analyze_video(
    *,
    detector: VisionDetector,
    settings: Settings,
    video_bytes: bytes,
    device_code: str,
    task_code: str | None,
    latitude: float | None,
    longitude: float | None,
    publish_alarms: bool,
    max_alarms: int,
    frame_interval_sec: float,
    max_frames: int,
    request_id: str,
) -> VisionVideoDetectResponse:
    frames = await asyncio.to_thread(
        extract_video_frames,
        video_bytes,
        frame_interval_sec=frame_interval_sec,
        max_frames=max_frames,
        timeout_seconds=settings.vision_ffmpeg_timeout_seconds,
    )

    frame_results: list[VisionFrameDetectResponse] = []
    published_all: list[VisionAlarmCandidate] = []
    remaining_alarms = max(1, max_alarms)

    for index, frame_bytes in enumerate(frames):
        allow_publish = publish_alarms and remaining_alarms > 0
        per_frame = await analyze_image(
            detector=detector,
            settings=settings,
            image_bytes=frame_bytes,
            device_code=device_code,
            task_code=task_code,
            latitude=latitude,
            longitude=longitude,
            publish_alarms=allow_publish,
            max_alarms=remaining_alarms,
            request_id=f"{request_id}:f{index}",
        )
        published_all.extend(per_frame.published_alarms)
        remaining_alarms = max(0, remaining_alarms - len(per_frame.published_alarms))
        frame_results.append(
            VisionFrameDetectResponse(
                frame_index=index,
                backend=per_frame.backend,
                model=per_frame.model,
                detections=per_frame.detections,
                alarm_candidates=per_frame.alarm_candidates,
                published_alarms=per_frame.published_alarms,
            )
        )

    backend = frame_results[0].backend if frame_results else detector.backend
    model = frame_results[0].model if frame_results else detector.model_name
    return VisionVideoDetectResponse(
        backend=backend,
        model=model,
        frame_count=len(frame_results),
        frames=frame_results,
        published_alarms=published_all,
    )
