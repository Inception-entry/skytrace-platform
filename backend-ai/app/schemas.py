from pydantic import BaseModel, ConfigDict, Field


class ChatRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    session_id: str = Field(
        alias="sessionId",
        min_length=8,
        max_length=64,
        pattern=r"^[A-Za-z0-9_-]+$",
    )
    message: str = Field(min_length=1, max_length=10_000)
    knowledge_query: str | None = Field(
        default=None,
        alias="knowledgeQuery",
        min_length=1,
        max_length=2_000,
    )


class KnowledgeSource(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: str = Field(alias="documentId")
    filename: str
    page: int | None = None
    score: float


class ChatResponse(BaseModel):
    model: str
    answer: str
    sources: list[KnowledgeSource] = Field(default_factory=list)


class KnowledgeDocumentResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: str = Field(alias="documentId")
    filename: str
    content_type: str = Field(alias="contentType")
    chunk_count: int = Field(alias="chunkCount")
    uploaded_at: str = Field(alias="uploadedAt")


class KnowledgeSearchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query: str = Field(min_length=1, max_length=2_000)
    top_k: int | None = Field(default=None, alias="topK", ge=1, le=20)


class KnowledgeSearchResult(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: str = Field(alias="documentId")
    filename: str
    content: str
    page: int | None = None
    chunk_index: int = Field(alias="chunkIndex")
    score: float


class KnowledgeDeleteResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    document_id: str = Field(alias="documentId")
    deleted_chunks: int = Field(alias="deletedChunks")


class HealthResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    status: str
    service: str
    ollama: str
    redis: str
    qdrant: str
    model: str
    embedding_model: str = Field(alias="embeddingModel")
    vision: str = "disabled"


class ServiceInfoResponse(BaseModel):
    service: str
    status: str
    docs: str
    health: str
    chat: str
    knowledge: str
    detections: str = "POST /api/detections/analyze"


class VisionBoxResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    class_name: str = Field(alias="className")
    confidence: float
    x1: float
    y1: float
    x2: float
    y2: float


class VisionAlarmCandidate(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    event_type: str = Field(alias="eventType")
    weapon_type: str | None = Field(default=None, alias="weaponType")
    class_name: str = Field(alias="className")
    confidence: float


class VisionDetectResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    backend: str
    model: str
    detections: list[VisionBoxResponse] = Field(default_factory=list)
    alarm_candidates: list[VisionAlarmCandidate] = Field(
        default_factory=list,
        alias="alarmCandidates",
    )
    published_alarms: list[VisionAlarmCandidate] = Field(
        default_factory=list,
        alias="publishedAlarms",
    )


class VisionFrameDetectResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    frame_index: int = Field(alias="frameIndex")
    backend: str
    model: str
    detections: list[VisionBoxResponse] = Field(default_factory=list)
    alarm_candidates: list[VisionAlarmCandidate] = Field(
        default_factory=list,
        alias="alarmCandidates",
    )
    published_alarms: list[VisionAlarmCandidate] = Field(
        default_factory=list,
        alias="publishedAlarms",
    )


class VisionVideoDetectResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    backend: str
    model: str
    frame_count: int = Field(alias="frameCount")
    frames: list[VisionFrameDetectResponse] = Field(default_factory=list)
    published_alarms: list[VisionAlarmCandidate] = Field(
        default_factory=list,
        alias="publishedAlarms",
    )


class EvidenceDeriveImageRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    evidence_code: str = Field(alias="evidenceCode", min_length=1, max_length=64)
    object_key: str = Field(alias="objectKey", min_length=1, max_length=512)
    bucket: str = Field(min_length=1, max_length=128)


class EvidenceDeriveVideoRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    evidence_code: str = Field(alias="evidenceCode", min_length=1, max_length=64)
    object_key: str = Field(alias="objectKey", min_length=1, max_length=512)
    bucket: str = Field(min_length=1, max_length=128)


class EvidenceDeriveResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    evidence_code: str = Field(alias="evidenceCode")
    status: str
    thumbnail_object_key: str | None = Field(
        default=None,
        alias="thumbnailObjectKey",
    )
    poster_object_key: str | None = Field(default=None, alias="posterObjectKey")
    message: str | None = None
