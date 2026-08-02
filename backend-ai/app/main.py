import asyncio
import json
import logging
from contextlib import asynccontextmanager
from collections.abc import AsyncIterator
from time import perf_counter

import httpx
from fastapi import FastAPI, File, HTTPException, Request, UploadFile, status
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_ollama import ChatOllama
from redis.asyncio import Redis
from starlette.responses import StreamingResponse

from app.chat_history import RedisChatHistory
from app.config import get_settings
from app.errors import (
    AiServiceError,
    classify_model_exception,
    context_service_error,
)
from app.knowledge_base import KnowledgeBase
from app.model_calls import (
    invoke_model_with_retry,
    stream_model_with_retry,
)
from app.observability import (
    configure_logging,
    log_event,
    normalize_request_id,
    reset_request_id,
    set_request_id,
)
from app.detection_publisher import (
    DetectionAlarmPayload,
    publish_detection_alarm,
)
from app.schemas import (
    ChatRequest,
    ChatResponse,
    HealthResponse,
    KnowledgeDeleteResponse,
    KnowledgeDocumentResponse,
    KnowledgeSearchRequest,
    KnowledgeSearchResult,
    KnowledgeSource,
    ServiceInfoResponse,
    VisionDetectResponse,
)
from app.vision import build_vision_detector
from app.vision.analyze import analyze_image

settings = get_settings()
logger = configure_logging()


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.llm = ChatOllama(
        base_url=settings.ollama_base_url,
        model=settings.ollama_model,
        temperature=0.3,
        num_ctx=8192,
        client_kwargs={"timeout": settings.ollama_timeout_seconds},
    )
    app.state.redis = Redis.from_url(
        settings.redis_url,
        decode_responses=True,
    )
    app.state.chat_history = RedisChatHistory(
        redis=app.state.redis,
        history_turns=settings.chat_history_turns,
        ttl_seconds=settings.chat_session_ttl_seconds,
    )
    app.state.knowledge_base = KnowledgeBase(settings)
    try:
        app.state.vision_detector = build_vision_detector(settings)
    except Exception as exception:
        log_event(
            logger,
            logging.ERROR,
            "vision_detector_init_failed",
            operation="startup",
            error_code="VISION_INIT_FAILED",
            retryable=False,
            exception_type=type(exception).__name__,
        )
        raise
    log_event(
        logger,
        logging.INFO,
        "ai_service_started",
        operation="startup",
        vision_backend=(
            None
            if app.state.vision_detector is None
            else app.state.vision_detector.backend
        ),
        vision_model=(
            None
            if app.state.vision_detector is None
            else app.state.vision_detector.model_name
        ),
    )
    yield
    await app.state.redis.aclose()
    await app.state.knowledge_base.close()
    log_event(
        logger,
        logging.INFO,
        "ai_service_stopped",
        operation="shutdown",
    )


app = FastAPI(
    title="SkyTrace AI Service",
    version="0.1.0",
    lifespan=lifespan,
)


@app.middleware("http")
async def request_context_middleware(
    request: Request,
    call_next,
):
    request_id = normalize_request_id(
        request.headers.get("X-Request-Id")
    )
    request.state.request_id = request_id
    token = set_request_id(request_id)
    started_at = perf_counter()
    response_status = 500
    try:
        response = await call_next(request)
        response_status = response.status_code
        response.headers["X-Request-Id"] = request_id
        return response
    except Exception as exception:
        log_event(
            logger,
            logging.ERROR,
            "http_request_failed",
            request_id=request_id,
            method=request.method,
            path=request.url.path,
            duration_ms=_elapsed_ms(started_at),
            status=response_status,
            exception_type=type(exception).__name__,
        )
        raise
    finally:
        log_event(
            logger,
            logging.INFO,
            "http_response_started",
            request_id=request_id,
            method=request.method,
            path=request.url.path,
            duration_ms=_elapsed_ms(started_at),
            status=response_status,
        )
        reset_request_id(token)


@app.get("/", response_model=ServiceInfoResponse)
async def service_info() -> ServiceInfoResponse:
    return ServiceInfoResponse(
        service=settings.service_name,
        status="running",
        docs="/docs",
        health="/health",
        chat="POST /api/chat",
        knowledge="/api/knowledge/documents",
        detections="POST /api/detections/analyze",
    )


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    try:
        async with httpx.AsyncClient(
            timeout=settings.dependency_health_timeout_seconds
        ) as client:
            response = await client.get(
                f"{settings.ollama_base_url}/api/tags"
            )
            response.raise_for_status()
            model_names = {
                model["name"].removesuffix(":latest")
                for model in response.json().get("models", [])
            }
        await app.state.redis.ping()
        await app.state.knowledge_base.health()
    except Exception as exc:
        log_event(
            logger,
            logging.ERROR,
            "ai_health_check_failed",
            operation="health",
            error_code="AI_DEPENDENCY_UNAVAILABLE",
            retryable=True,
            exception_type=type(exc).__name__,
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "AI_DEPENDENCY_UNAVAILABLE",
                "message": "AI 依赖服务暂时不可用",
                "retryable": True,
            },
        ) from exc

    model_status = "available" if _has_model(model_names, settings.ollama_model) else "missing"
    embedding_status = (
        "available"
        if _has_model(model_names, settings.ollama_embedding_model)
        else "missing"
    )
    vision_status = "disabled"
    detector = getattr(app.state, "vision_detector", None)
    if detector is not None:
        vision_status = detector.backend

    return HealthResponse(
        status=(
            "ok"
            if model_status == "available" and embedding_status == "available"
            else "degraded"
        ),
        service=settings.service_name,
        ollama="connected",
        redis="connected",
        qdrant="connected",
        model=model_status,
        embeddingModel=embedding_status,
        vision=vision_status,
    )


@app.post("/api/detections/alarms")
async def publish_detection(
    payload: DetectionAlarmPayload,
    request: Request,
) -> dict[str, str]:
    if not settings.messaging_enabled:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "MESSAGING_DISABLED",
                "message": "告警消息通道未启用",
                "retryable": False,
            },
        )
    try:
        await publish_detection_alarm(
            settings,
            payload,
            request_id=request.state.request_id,
        )
    except Exception as exc:
        log_event(
            logger,
            logging.ERROR,
            "detection_alarm_publish_failed",
            request_id=request.state.request_id,
            operation="publish_detection",
            error_code="DETECTION_PUBLISH_FAILED",
            retryable=True,
            exception_type=type(exc).__name__,
        )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail={
                "code": "DETECTION_PUBLISH_FAILED",
                "message": "识别结果投递失败，请稍后重试",
                "retryable": True,
            },
        ) from exc
    return {"status": "queued", "exchange": "uav.detection"}


@app.post(
    "/api/detections/analyze",
    response_model=VisionDetectResponse,
)
async def analyze_detection(
    request: Request,
    file: UploadFile = File(...),
    deviceCode: str = "UAV-001",
    taskCode: str | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
    publishAlarms: bool = True,
    maxAlarms: int | None = None,
) -> VisionDetectResponse:
    detector = getattr(request.app.state, "vision_detector", None)
    if detector is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail={
                "code": "VISION_DISABLED",
                "message": "视觉推理未启用",
                "retryable": False,
            },
        )

    content_type = (file.content_type or "").lower()
    if content_type and not content_type.startswith("image/"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "code": "VISION_INVALID_IMAGE",
                "message": "仅支持图片文件上传",
                "retryable": False,
            },
        )

    image_bytes = await file.read()
    if not image_bytes:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "code": "VISION_EMPTY_IMAGE",
                "message": "上传图片为空",
                "retryable": False,
            },
        )
    if len(image_bytes) > settings.vision_max_upload_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail={
                "code": "VISION_IMAGE_TOO_LARGE",
                "message": "图片超过大小限制",
                "retryable": False,
            },
        )

    started_at = perf_counter()
    try:
        response = await analyze_image(
            detector=detector,
            settings=settings,
            image_bytes=image_bytes,
            device_code=deviceCode.strip() or "UAV-001",
            task_code=taskCode,
            latitude=latitude,
            longitude=longitude,
            publish_alarms=publishAlarms,
            max_alarms=(
                maxAlarms
                if maxAlarms is not None
                else settings.vision_default_max_alarms
            ),
            request_id=request.state.request_id,
        )
    except Exception as exc:
        log_event(
            logger,
            logging.ERROR,
            "vision_analyze_failed",
            request_id=request.state.request_id,
            operation="vision_analyze",
            error_code="VISION_ANALYZE_FAILED",
            retryable=True,
            exception_type=type(exc).__name__,
        )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail={
                "code": "VISION_ANALYZE_FAILED",
                "message": "视觉推理失败，请稍后重试",
                "retryable": True,
            },
        ) from exc

    log_event(
        logger,
        logging.INFO,
        "vision_analyze_completed",
        request_id=request.state.request_id,
        operation="vision_analyze",
        backend=response.backend,
        model=response.model,
        detection_count=len(response.detections),
        published_count=len(response.published_alarms),
        duration_ms=_elapsed_ms(started_at),
    )
    return response


@app.post("/api/chat", response_model=ChatResponse)
async def chat(payload: ChatRequest, request: Request) -> ChatResponse:
    request_id = request.state.request_id
    try:
        messages, sources = await _build_chat_context(
            payload,
            request,
            request_id,
        )
        response = await invoke_model_with_retry(
            request.app.state.llm,
            messages,
            timeout_seconds=settings.ollama_timeout_seconds,
            max_attempts=settings.ollama_max_attempts,
            initial_backoff_seconds=(
                settings.ollama_initial_backoff_seconds
            ),
            max_backoff_seconds=settings.ollama_max_backoff_seconds,
            logger=logger,
            request_id=request_id,
            session_id=payload.session_id,
        )
        answer = (
            response.content
            if isinstance(response.content, str)
            else str(response.content)
        )
        answer = _append_source_summary(answer, sources)
        await _append_history_safely(
            request,
            payload.session_id,
            payload.message,
            answer,
            request_id,
        )
    except AiServiceError as error:
        raise _http_exception(error, request_id) from error
    except Exception as exc:
        error = classify_model_exception(exc)
        log_event(
            logger,
            logging.ERROR,
            "ai_chat_failed",
            request_id=request_id,
            operation="chat",
            session_id=payload.session_id,
            error_code=error.code,
            retryable=error.retryable,
            exception_type=type(exc).__name__,
        )
        raise _http_exception(error, request_id) from exc

    return ChatResponse(
        model=settings.ollama_model,
        answer=answer,
        sources=_knowledge_sources(sources),
    )


@app.post("/api/chat/stream")
async def stream_chat(
    payload: ChatRequest,
    request: Request,
) -> StreamingResponse:
    return StreamingResponse(
        _stream_chat_events(payload, request),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "X-Accel-Buffering": "no",
        },
    )


@app.post(
    "/api/knowledge/documents",
    response_model=KnowledgeDocumentResponse,
    status_code=status.HTTP_201_CREATED,
)
async def upload_knowledge_document(
    request: Request,
    file: UploadFile = File(...),
) -> KnowledgeDocumentResponse:
    content = await file.read(settings.knowledge_max_file_size_bytes + 1)
    try:
        return await request.app.state.knowledge_base.import_document(
            file.filename or "unnamed.txt",
            file.content_type or "application/octet-stream",
            content,
        )
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    except Exception as exc:
        log_event(
            logger,
            logging.ERROR,
            "knowledge_import_failed",
            request_id=request.state.request_id,
            operation="knowledge_upload",
            error_code="KNOWLEDGE_IMPORT_FAILED",
            retryable=True,
            exception_type=type(exc).__name__,
        )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail={
                "code": "KNOWLEDGE_IMPORT_FAILED",
                "message": "知识库入库失败，请稍后重试",
                "retryable": True,
                "requestId": request.state.request_id,
            },
        ) from exc
    finally:
        await file.close()


@app.get(
    "/api/knowledge/documents",
    response_model=list[KnowledgeDocumentResponse],
)
async def list_knowledge_documents(request: Request) -> list[KnowledgeDocumentResponse]:
    try:
        return await request.app.state.knowledge_base.list_documents()
    except Exception as exc:
        raise _knowledge_exception(
            request,
            exc,
            operation="knowledge_list",
            event="knowledge_list_failed",
            error_code="KNOWLEDGE_LIST_FAILED",
            safe_message="知识库列表读取失败，请稍后重试",
        ) from exc


@app.post(
    "/api/knowledge/search",
    response_model=list[KnowledgeSearchResult],
)
async def search_knowledge(
    payload: KnowledgeSearchRequest,
    request: Request,
) -> list[KnowledgeSearchResult]:
    try:
        return await request.app.state.knowledge_base.search(
            payload.query,
            payload.top_k,
        )
    except Exception as exc:
        raise _knowledge_exception(
            request,
            exc,
            operation="knowledge_search",
            event="knowledge_search_failed",
            error_code="KNOWLEDGE_SEARCH_FAILED",
            safe_message="知识库检索失败，请稍后重试",
        ) from exc


@app.delete(
    "/api/knowledge/documents/{document_id}",
    response_model=KnowledgeDeleteResponse,
)
async def delete_knowledge_document(
    document_id: str,
    request: Request,
) -> KnowledgeDeleteResponse:
    try:
        deleted_chunks = await request.app.state.knowledge_base.delete_document(
            document_id
        )
    except Exception as exc:
        raise _knowledge_exception(
            request,
            exc,
            operation="knowledge_delete",
            event="knowledge_delete_failed",
            error_code="KNOWLEDGE_DELETE_FAILED",
            safe_message="知识库删除失败，请稍后重试",
        ) from exc
    if deleted_chunks == 0:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="知识库文档不存在",
        )
    return KnowledgeDeleteResponse(
        documentId=document_id,
        deletedChunks=deleted_chunks,
    )


def _has_model(model_names: set[str], expected: str) -> bool:
    normalized = {name.removesuffix(":latest") for name in model_names}
    return expected.removesuffix(":latest") in normalized


def _rag_system_prompt(sources: list[KnowledgeSearchResult]) -> str:
    context = "\n\n".join(
        f"[资料{index}] 文件：{item.filename}"
        f"{'，第 ' + str(item.page) + ' 页' if item.page else ''}\n"
        f"{item.content}"
        for index, item in enumerate(sources, start=1)
    )
    return f"""
你是无人机巡检知识助手。下面是从内部知识库检索到的资料。
只把资料作为专业知识依据；业务任务事实仍以用户消息中的 MySQL 数据为准。
引用资料时使用 [资料1]、[资料2] 标记。若资料不足，请明确说明，不要编造。

【知识库检索结果】
{context}
""".strip()


def _append_source_summary(
    answer: str,
    sources: list[KnowledgeSearchResult],
) -> str:
    return f"{answer.rstrip()}{_source_summary(sources)}"


async def _stream_chat_events(
    payload: ChatRequest,
    request: Request,
) -> AsyncIterator[str]:
    request_id = request.state.request_id
    try:
        messages, sources = await _build_chat_context(
            payload,
            request,
            request_id,
        )

        yield _sse_event(
            "meta",
            {
                "model": settings.ollama_model,
                "sources": [
                    source.model_dump(by_alias=True)
                    for source in _knowledge_sources(sources)
                ],
            },
        )

        answer_parts: list[str] = []
        async for chunk in stream_model_with_retry(
            request.app.state.llm,
            messages,
            timeout_seconds=settings.ollama_timeout_seconds,
            max_attempts=settings.ollama_max_attempts,
            initial_backoff_seconds=(
                settings.ollama_initial_backoff_seconds
            ),
            max_backoff_seconds=settings.ollama_max_backoff_seconds,
            logger=logger,
            request_id=request_id,
            session_id=payload.session_id,
        ):
            content = _message_content(chunk.content)
            if not content:
                continue
            answer_parts.append(content)
            yield _sse_event("token", {"content": content})

        answer = "".join(answer_parts).rstrip()
        source_summary = _source_summary(sources)
        if source_summary:
            answer += source_summary
            yield _sse_event("token", {"content": source_summary})

        await _append_history_safely(
            request,
            payload.session_id,
            payload.message,
            answer,
            request_id,
        )
        yield _sse_event(
            "done",
            {
                "model": settings.ollama_model,
                "answerLength": len(answer),
            },
        )
    except asyncio.CancelledError:
        log_event(
            logger,
            logging.INFO,
            "ai_stream_cancelled",
            request_id=request_id,
            operation="chat_stream",
            session_id=payload.session_id,
        )
        raise
    except AiServiceError as error:
        yield _sse_event(
            "error",
            error.payload(request_id),
        )
    except Exception as exc:
        error = classify_model_exception(exc)
        log_event(
            logger,
            logging.ERROR,
            "ai_stream_failed",
            request_id=request_id,
            operation="chat_stream",
            session_id=payload.session_id,
            error_code=error.code,
            retryable=error.retryable,
            exception_type=type(exc).__name__,
        )
        yield _sse_event(
            "error",
            error.payload(request_id),
        )


async def _build_chat_context(
    payload: ChatRequest,
    request: Request,
    request_id: str,
) -> tuple[list, list[KnowledgeSearchResult]]:
    try:
        history = await request.app.state.chat_history.get_messages(
            payload.session_id
        )
        sources = await request.app.state.knowledge_base.search(
            payload.knowledge_query or payload.message
        )
    except asyncio.CancelledError:
        raise
    except Exception as exception:
        error = context_service_error()
        log_event(
            logger,
            logging.ERROR,
            "ai_context_load_failed",
            request_id=request_id,
            operation="load_context",
            session_id=payload.session_id,
            error_code=error.code,
            retryable=error.retryable,
            exception_type=type(exception).__name__,
        )
        raise error from exception

    messages = [*history]
    if sources:
        messages.append(SystemMessage(content=_rag_system_prompt(sources)))
    messages.append(HumanMessage(content=payload.message))
    return messages, sources


async def _append_history_safely(
    request: Request,
    session_id: str,
    user_message: str,
    answer: str,
    request_id: str,
) -> None:
    try:
        await request.app.state.chat_history.append_exchange(
            session_id,
            user_message,
            answer,
        )
    except asyncio.CancelledError:
        raise
    except Exception as exception:
        # The model response is already complete. A Redis history failure
        # must not trigger another model call and duplicate the answer.
        log_event(
            logger,
            logging.WARNING,
            "ai_history_write_failed",
            request_id=request_id,
            operation="save_history",
            session_id=session_id,
            error_code="AI_HISTORY_UNAVAILABLE",
            retryable=True,
            exception_type=type(exception).__name__,
        )


def _knowledge_sources(
    sources: list[KnowledgeSearchResult],
) -> list[KnowledgeSource]:
    return [
        KnowledgeSource(
            documentId=item.document_id,
            filename=item.filename,
            page=item.page,
            score=item.score,
        )
        for item in sources
    ]


def _message_content(content: object) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            str(block.get("text", ""))
            for block in content
            if isinstance(block, dict) and block.get("type") == "text"
        )
    return str(content) if content is not None else ""


def _sse_event(event: str, data: dict[str, object]) -> str:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event}\ndata: {payload}\n\n"


def _source_summary(sources: list[KnowledgeSearchResult]) -> str:
    if not sources:
        return ""
    seen: set[tuple[str, int | None]] = set()
    lines = []
    for item in sources:
        key = (item.filename, item.page)
        if key in seen:
            continue
        seen.add(key)
        page = f"（第 {item.page} 页）" if item.page else ""
        lines.append(f"- {item.filename}{page}")
    return "\n\n参考资料：\n" + "\n".join(lines)


def _http_exception(
    error: AiServiceError,
    request_id: str,
) -> HTTPException:
    return HTTPException(
        status_code=error.http_status,
        detail=error.payload(request_id),
    )


def _knowledge_exception(
    request: Request,
    exception: Exception,
    *,
    operation: str,
    event: str,
    error_code: str,
    safe_message: str,
) -> HTTPException:
    request_id = request.state.request_id
    log_event(
        logger,
        logging.ERROR,
        event,
        request_id=request_id,
        operation=operation,
        error_code=error_code,
        retryable=True,
        exception_type=type(exception).__name__,
    )
    return HTTPException(
        status_code=status.HTTP_502_BAD_GATEWAY,
        detail={
            "code": error_code,
            "message": safe_message,
            "retryable": True,
            "requestId": request_id,
        },
    )


def _elapsed_ms(started_at: float) -> int:
    return round((perf_counter() - started_at) * 1000)
