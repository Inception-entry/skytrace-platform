from __future__ import annotations

import json
import logging
import uuid
from datetime import datetime, timezone
from zoneinfo import ZoneInfo
from typing import Any

import aio_pika
from pydantic import BaseModel, Field

from app.config import Settings
from app.observability import log_event

logger = logging.getLogger(__name__)

DATABASE_ZONE = ZoneInfo("Asia/Shanghai")
DETECTION_ID_NAMESPACE = uuid.UUID("a8e2c1d0-5b3f-4e9a-9c11-7b4d2f18e601")


def to_legacy_java_local(value: datetime) -> str:
    if value.tzinfo is None:
        raise ValueError("eventTime 必须携带 timezone offset")
    return (
        value.astimezone(DATABASE_ZONE)
        .replace(tzinfo=None)
        .isoformat(timespec="seconds")
    )


def stable_detection_id(
    analysis_id: str,
    *,
    frame: int,
    class_name: str,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
) -> str:
    name = (
        f"{analysis_id}|{frame}|{class_name}"
        f"|{x1:.4f}|{y1:.4f}|{x2:.4f}|{y2:.4f}"
    )
    return str(uuid.uuid5(DETECTION_ID_NAMESPACE, name))


class DetectionAlarmPayload(BaseModel):
    device_code: str = Field(alias="deviceCode")
    task_code: str | None = Field(default=None, alias="taskCode")
    event_type: str = Field(alias="eventType")
    weapon_type: str | None = Field(default=None, alias="weaponType")
    confidence: float | None = None
    latitude: float | None = None
    longitude: float | None = None
    image_object_key: str | None = Field(
        default=None,
        alias="imageObjectKey",
    )
    video_object_key: str | None = Field(
        default=None,
        alias="videoObjectKey",
    )
    event_time: datetime | None = Field(default=None, alias="eventTime")
    detection_id: str | None = Field(default=None, alias="detectionId")
    schema_version: int = Field(default=2, alias="schemaVersion")

    model_config = {"populate_by_name": True}


async def publish_detection_alarm(
    settings: Settings,
    payload: DetectionAlarmPayload,
    *,
    request_id: str,
) -> None:
    event_time = payload.event_time or datetime.now(timezone.utc)
    body: dict[str, Any] = {
        "schemaVersion": payload.schema_version,
        "deviceCode": payload.device_code,
        "taskCode": payload.task_code,
        "eventType": payload.event_type,
        "weaponType": payload.weapon_type,
        "confidence": payload.confidence,
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "imageObjectKey": payload.image_object_key,
        "videoObjectKey": payload.video_object_key,
        "eventTime": to_legacy_java_local(event_time),
    }
    if payload.detection_id:
        body["detectionId"] = payload.detection_id
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    try:
        channel = await connection.channel()
        exchange = await channel.declare_exchange(
            "skytrace.detection",
            aio_pika.ExchangeType.DIRECT,
            durable=True,
        )
        await exchange.publish(
            aio_pika.Message(
                body=json.dumps(body).encode("utf-8"),
                content_type="application/json",
                delivery_mode=aio_pika.DeliveryMode.PERSISTENT,
            ),
            routing_key="alarm",
        )
        log_event(
            logger,
            logging.INFO,
            "detection_alarm_published",
            request_id=request_id,
            operation="publish_detection",
            task_code=payload.task_code,
            event_type=payload.event_type,
            detection_id=payload.detection_id,
        )
    finally:
        await connection.close()
