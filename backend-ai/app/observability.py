import json
import logging
import re
from contextvars import ContextVar
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4


_REQUEST_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")
_request_id: ContextVar[str] = ContextVar(
    "ai_request_id",
    default="-",
)


class JsonLogFormatter(logging.Formatter):
    _structured_fields = (
        "event",
        "request_id",
        "operation",
        "session_id",
        "attempt",
        "next_attempt",
        "duration_ms",
        "backoff_ms",
        "error_code",
        "retryable",
        "exception_type",
        "method",
        "path",
        "status",
        "stream_started",
    )

    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }
        for field in self._structured_fields:
            value = getattr(record, field, None)
            if value is not None:
                payload[field] = value
        payload.setdefault("request_id", current_request_id())
        return json.dumps(
            payload,
            ensure_ascii=False,
            separators=(",", ":"),
        )


def configure_logging() -> logging.Logger:
    logger = logging.getLogger("skytrace.ai")
    logger.setLevel(logging.INFO)
    logger.propagate = False
    if not any(
        getattr(handler, "_skytrace_json_handler", False)
        for handler in logger.handlers
    ):
        handler = logging.StreamHandler()
        handler.setFormatter(JsonLogFormatter())
        handler._skytrace_json_handler = True  # type: ignore[attr-defined]
        logger.addHandler(handler)
    return logger


def normalize_request_id(value: str | None) -> str:
    if value and _REQUEST_ID_PATTERN.fullmatch(value):
        return value
    return str(uuid4())


def set_request_id(value: str):
    return _request_id.set(value)


def reset_request_id(token) -> None:
    _request_id.reset(token)


def current_request_id() -> str:
    return _request_id.get()


def log_event(
    logger: logging.Logger,
    level: int,
    event: str,
    **fields: Any,
) -> None:
    logger.log(
        level,
        event,
        extra={"event": event, **fields},
    )
