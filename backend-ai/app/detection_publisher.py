from __future__ import annotations

import json
import logging
from datetime import datetime, timezone
from typing import Any

import aio_pika
from pydantic import BaseModel, Field

from app.config import Settings
from app.observability import log_event

logger = logging.getLogger(__name__)


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

    model_config = {"populate_by_name": True}


async def publish_detection_alarm(
    settings: Settings,
    payload: DetectionAlarmPayload,
    *,
    request_id: str,
) -> None:
    event_time = payload.event_time or datetime.now(timezone.utc)
    body: dict[str, Any] = {
        "deviceCode": payload.device_code,
        "taskCode": payload.task_code,
        "eventType": payload.event_type,
        "weaponType": payload.weapon_type,
        "confidence": payload.confidence,
        "latitude": payload.latitude,
        "longitude": payload.longitude,
        "imageObjectKey": payload.image_object_key,
        "videoObjectKey": payload.video_object_key,
        "eventTime": event_time.replace(tzinfo=None).isoformat(
            timespec="seconds"
        ),
    }
    connection = await aio_pika.connect_robust(settings.rabbitmq_url)
    try:
        channel = await connection.channel()
        exchange = await channel.declare_exchange(
            "uav.detection",
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
        )
    finally:
        await connection.close()
