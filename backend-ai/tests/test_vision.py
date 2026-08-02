from app.vision.detector import MockVisionDetector
from app.vision.labels import resolve_alarm


def test_resolve_alarm_maps_weapon_and_person() -> None:
    assert resolve_alarm("Knife") == ("WEAPON_DETECTED", "KNIFE")
    assert resolve_alarm("person") == ("PERSON_DETECTED", None)
    assert resolve_alarm("unknown-class") is None


def test_mock_detector_returns_normalized_boxes() -> None:
    detector = MockVisionDetector()
    result = detector.detect(b"fake-image")
    assert result.backend == "mock"
    assert len(result.detections) == 2
    knife = next(
        item for item in result.detections if item.class_name == "knife"
    )
    assert 0.0 <= knife.x1 < knife.x2 <= 1.0
    assert knife.confidence > 0.5


def test_mock_detector_empty_bytes() -> None:
    detector = MockVisionDetector()
    assert detector.detect(b"").detections == []
