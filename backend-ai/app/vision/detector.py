from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Protocol

from app.config import Settings
from app.observability import log_event

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class DetectionBox:
    class_name: str
    confidence: float
    x1: float
    y1: float
    x2: float
    y2: float


@dataclass(frozen=True, slots=True)
class VisionDetectResult:
    backend: str
    model: str
    detections: list[DetectionBox]


class VisionDetector(Protocol):
    @property
    def backend(self) -> str: ...

    @property
    def model_name(self) -> str: ...

    def detect(self, image_bytes: bytes) -> VisionDetectResult: ...


class MockVisionDetector:
    """Deterministic detections for CI and local without ultralytics."""

    def __init__(self, model_name: str = "mock-yolo26n") -> None:
        self._model_name = model_name

    @property
    def backend(self) -> str:
        return "mock"

    @property
    def model_name(self) -> str:
        return self._model_name

    def detect(self, image_bytes: bytes) -> VisionDetectResult:
        if not image_bytes:
            return VisionDetectResult(
                backend=self.backend,
                model=self.model_name,
                detections=[],
            )
        return VisionDetectResult(
            backend=self.backend,
            model=self.model_name,
            detections=[
                DetectionBox(
                    class_name="person",
                    confidence=0.92,
                    x1=0.12,
                    y1=0.18,
                    x2=0.45,
                    y2=0.88,
                ),
                DetectionBox(
                    class_name="knife",
                    confidence=0.81,
                    x1=0.52,
                    y1=0.40,
                    x2=0.70,
                    y2=0.62,
                ),
            ],
        )


class Yolo26VisionDetector:
    def __init__(
        self,
        model_name: str,
        *,
        confidence_threshold: float,
        device: str,
    ) -> None:
        try:
            from ultralytics import YOLO
        except ImportError as exc:  # pragma: no cover - optional dep
            raise RuntimeError(
                "ultralytics 未安装。请使用 `uv sync --group vision` "
                "或构建时设置 INSTALL_VISION=1。"
            ) from exc
        self._model_name = model_name
        self._confidence_threshold = confidence_threshold
        self._device = device
        self._model = YOLO(model_name)
        log_event(
            logger,
            logging.INFO,
            "yolo26_model_loaded",
            operation="vision_startup",
            model=model_name,
            device=device,
        )

    @property
    def backend(self) -> str:
        return "yolo26"

    @property
    def model_name(self) -> str:
        return self._model_name

    def detect(self, image_bytes: bytes) -> VisionDetectResult:
        from io import BytesIO

        from PIL import Image

        image = Image.open(BytesIO(image_bytes)).convert("RGB")
        results = self._model.predict(
            source=image,
            conf=self._confidence_threshold,
            device=self._device,
            verbose=False,
        )
        detections: list[DetectionBox] = []
        if not results:
            return VisionDetectResult(
                backend=self.backend,
                model=self.model_name,
                detections=detections,
            )
        result = results[0]
        names = result.names or {}
        boxes = result.boxes
        if boxes is None:
            return VisionDetectResult(
                backend=self.backend,
                model=self.model_name,
                detections=detections,
            )
        width, height = image.size
        for box in boxes:
            cls_id = int(box.cls.item())
            class_name = str(names.get(cls_id, cls_id))
            confidence = float(box.conf.item())
            x1, y1, x2, y2 = (float(v) for v in box.xyxy[0].tolist())
            detections.append(
                DetectionBox(
                    class_name=class_name,
                    confidence=confidence,
                    x1=x1 / width,
                    y1=y1 / height,
                    x2=x2 / width,
                    y2=y2 / height,
                )
            )
        return VisionDetectResult(
            backend=self.backend,
            model=self.model_name,
            detections=detections,
        )


def build_vision_detector(settings: Settings) -> VisionDetector | None:
    if not settings.vision_enabled:
        return None
    backend = settings.vision_backend.strip().lower()
    if backend == "mock":
        return MockVisionDetector(model_name=settings.vision_model)
    if backend in {"yolo26", "ultralytics"}:
        return Yolo26VisionDetector(
            settings.vision_model,
            confidence_threshold=settings.vision_confidence_threshold,
            device=settings.vision_device,
        )
    raise ValueError(f"不支持的视觉后端: {settings.vision_backend}")
