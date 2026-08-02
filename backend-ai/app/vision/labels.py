"""Map YOLO/COCO class names to UAV alarm event types."""

from __future__ import annotations

# class_name -> (event_type, weapon_type_or_none)
DEFAULT_ALARM_CLASS_MAP: dict[str, tuple[str, str | None]] = {
    "knife": ("WEAPON_DETECTED", "KNIFE"),
    "scissors": ("WEAPON_DETECTED", "SCISSORS"),
    "baseball bat": ("WEAPON_DETECTED", "BAT"),
    "person": ("PERSON_DETECTED", None),
    "car": ("VEHICLE_DETECTED", "CAR"),
    "truck": ("VEHICLE_DETECTED", "TRUCK"),
    "motorcycle": ("VEHICLE_DETECTED", "MOTORCYCLE"),
    "bus": ("VEHICLE_DETECTED", "BUS"),
    "airplane": ("AIRCRAFT_DETECTED", None),
    "fire hydrant": ("INFRA_DETECTED", "FIRE_HYDRANT"),
    "stop sign": ("INFRA_DETECTED", "STOP_SIGN"),
}


def resolve_alarm(
    class_name: str,
    class_map: dict[str, tuple[str, str | None]] | None = None,
) -> tuple[str, str | None] | None:
    mapping = class_map or DEFAULT_ALARM_CLASS_MAP
    return mapping.get(class_name.strip().lower())
