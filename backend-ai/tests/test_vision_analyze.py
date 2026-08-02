from unittest.mock import AsyncMock, patch

import anyio

from app.config import Settings
from app.vision.analyze import analyze_image
from app.vision.detector import MockVisionDetector


def test_analyze_image_publishes_mapped_alarms() -> None:
    settings = Settings(
        messaging_enabled=True,
        vision_enabled=True,
        vision_backend="mock",
    )

    async def _run() -> None:
        with patch(
            "app.vision.analyze.publish_detection_alarm",
            new_callable=AsyncMock,
        ) as publish:
            result = await analyze_image(
                detector=MockVisionDetector(),
                settings=settings,
                image_bytes=b"frame",
                device_code="UAV-1",
                task_code="TASK-1",
                latitude=1.0,
                longitude=2.0,
                publish_alarms=True,
                max_alarms=2,
                request_id="req-1",
            )

        assert result.backend == "mock"
        assert len(result.detections) == 2
        assert len(result.alarm_candidates) == 2
        assert len(result.published_alarms) == 2
        assert publish.await_count == 2

    anyio.run(_run)


def test_analyze_image_can_skip_publish() -> None:
    settings = Settings(messaging_enabled=True)

    async def _run() -> None:
        with patch(
            "app.vision.analyze.publish_detection_alarm",
            new_callable=AsyncMock,
        ) as publish:
            result = await analyze_image(
                detector=MockVisionDetector(),
                settings=settings,
                image_bytes=b"frame",
                device_code="UAV-1",
                task_code=None,
                latitude=None,
                longitude=None,
                publish_alarms=False,
                max_alarms=3,
                request_id="req-2",
            )

        assert result.published_alarms == []
        publish.assert_not_awaited()

    anyio.run(_run)
