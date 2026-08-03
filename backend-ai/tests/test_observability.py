import json
import logging

from app.observability import JsonLogFormatter, normalize_request_id


def test_structured_log_contains_correlation_fields() -> None:
    record = logging.LogRecord(
        name="skytrace.ai",
        level=logging.WARNING,
        pathname=__file__,
        lineno=10,
        msg="ai_model_call_retry",
        args=(),
        exc_info=None,
    )
    record.event = "ai_model_call_retry"
    record.request_id = "request-123"
    record.operation = "chat"
    record.attempt = 1
    record.error_code = "AI_TIMEOUT"

    payload = json.loads(JsonLogFormatter().format(record))

    assert payload["event"] == "ai_model_call_retry"
    assert payload["request_id"] == "request-123"
    assert payload["operation"] == "chat"
    assert payload["attempt"] == 1
    assert payload["error_code"] == "AI_TIMEOUT"


def test_replaces_invalid_inbound_request_id() -> None:
    request_id = normalize_request_id("bad request id with spaces")

    assert request_id != "bad request id with spaces"
    assert len(request_id) == 36
