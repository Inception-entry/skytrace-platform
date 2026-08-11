from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    service_name: str = "skytrace-backend-ai"
    ollama_base_url: str = "http://127.0.0.1:11434"
    ollama_model: str = "my-drone-expert"
    ollama_embedding_model: str = "nomic-embed-text"
    ollama_timeout_seconds: float = Field(default=120.0, gt=0)
    ollama_max_attempts: int = Field(default=2, ge=1, le=5)
    ollama_initial_backoff_seconds: float = Field(
        default=0.5,
        ge=0,
    )
    ollama_max_backoff_seconds: float = Field(default=2.0, ge=0)
    dependency_health_timeout_seconds: float = Field(
        default=5.0,
        gt=0,
    )
    redis_url: str = "redis://127.0.0.1:6380/0"
    chat_history_turns: int = 6
    chat_session_ttl_seconds: int = 86_400
    qdrant_url: str = "http://127.0.0.1:6333"
    qdrant_collection: str = "skytrace_knowledge"
    knowledge_chunk_size: int = 800
    knowledge_chunk_overlap: int = 120
    knowledge_top_k: int = 4
    knowledge_score_threshold: float = 0.25
    knowledge_max_file_size_bytes: int = 10 * 1024 * 1024
    rabbitmq_url: str = "amqp://admin:admin123@127.0.0.1:5672/"
    messaging_enabled: bool = True
    vision_enabled: bool = True
    # mock | yolo26 | ultralytics
    vision_backend: str = "mock"
    vision_model: str = "yolo26n.pt"
    vision_device: str = "cpu"
    vision_confidence_threshold: float = Field(
        default=0.35,
        ge=0.05,
        le=0.99,
    )
    vision_max_upload_bytes: int = Field(
        default=10 * 1024 * 1024,
        ge=1024,
    )
    vision_default_max_alarms: int = Field(default=3, ge=1, le=20)
    evidence_thumb_max_size: int = Field(default=320, ge=64, le=1024)
    evidence_video_poster_second: float = Field(default=1.0, ge=0)
    evidence_derive_max_upload_bytes: int = Field(
        default=20 * 1024 * 1024,
        ge=1024,
    )

    model_config = SettingsConfigDict(
        env_prefix="AI_",
        env_file=".env",
        extra="ignore",
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
