import asyncio
import hashlib
import io
import uuid
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

from langchain_ollama import OllamaEmbeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter
from pypdf import PdfReader
from pypdf.errors import PdfReadError
from qdrant_client import AsyncQdrantClient, models

from app.config import Settings
from app.schemas import (
    KnowledgeDocumentResponse,
    KnowledgeSearchResult,
)


@dataclass(frozen=True)
class ParsedSection:
    text: str
    page: int | None


class KnowledgeBase:
    supported_extensions = {".pdf", ".md", ".markdown", ".txt"}

    def __init__(
        self,
        settings: Settings,
        client: AsyncQdrantClient | None = None,
        embeddings: OllamaEmbeddings | None = None,
    ) -> None:
        self.settings = settings
        self.client = client or AsyncQdrantClient(url=settings.qdrant_url)
        self.embeddings = embeddings or OllamaEmbeddings(
            base_url=settings.ollama_base_url,
            model=settings.ollama_embedding_model,
            client_kwargs={"timeout": settings.ollama_timeout_seconds},
        )
        self.splitter = RecursiveCharacterTextSplitter(
            chunk_size=settings.knowledge_chunk_size,
            chunk_overlap=settings.knowledge_chunk_overlap,
            separators=["\n\n", "\n", "。", "！", "？", ". ", " "],
        )
        self._collection_lock = asyncio.Lock()

    async def close(self) -> None:
        await self.client.close()

    async def health(self) -> None:
        await self.client.get_collections()

    async def import_document(
        self,
        filename: str,
        content_type: str,
        content: bytes,
    ) -> KnowledgeDocumentResponse:
        safe_filename = Path(filename.replace("\\", "/")).name
        extension = Path(safe_filename).suffix.lower()
        if extension not in self.supported_extensions:
            raise ValueError("仅支持 PDF、Markdown 和 TXT 文档")
        if not content:
            raise ValueError("上传的文档为空")
        if len(content) > self.settings.knowledge_max_file_size_bytes:
            max_megabytes = self.settings.knowledge_max_file_size_bytes / 1024 / 1024
            raise ValueError(f"文档不能超过 {max_megabytes:g} MB")

        try:
            sections = self._parse_document(extension, content)
        except PdfReadError as exc:
            raise ValueError("PDF 文件损坏或无法解析") from exc
        chunks = self._split_sections(sections)
        if not chunks:
            raise ValueError("文档中没有可提取的文字内容")

        texts = [text for text, _ in chunks]
        vectors = await self.embeddings.aembed_documents(texts)
        if not vectors:
            raise RuntimeError("嵌入模型未返回向量")
        await self._ensure_collection(len(vectors[0]))

        sha256 = hashlib.sha256(content).hexdigest()
        document_id = sha256
        uploaded_at = datetime.now(UTC).isoformat()
        await self._delete_by_document_id(document_id)

        points = []
        for chunk_index, ((text, page), vector) in enumerate(
            zip(chunks, vectors, strict=True)
        ):
            point_id = str(
                uuid.uuid5(
                    uuid.NAMESPACE_URL,
                    f"skytrace-knowledge:{document_id}:{chunk_index}",
                )
            )
            points.append(
                models.PointStruct(
                    id=point_id,
                    vector=vector,
                    payload={
                        "document_id": document_id,
                        "filename": safe_filename,
                        "content_type": content_type,
                        "chunk_index": chunk_index,
                        "page": page,
                        "text": text,
                        "uploaded_at": uploaded_at,
                    },
                )
            )

        await self.client.upsert(
            collection_name=self.settings.qdrant_collection,
            points=points,
            wait=True,
        )
        return KnowledgeDocumentResponse(
            documentId=document_id,
            filename=safe_filename,
            contentType=content_type,
            chunkCount=len(points),
            uploadedAt=uploaded_at,
        )

    async def list_documents(self) -> list[KnowledgeDocumentResponse]:
        if not await self._collection_exists():
            return []

        documents: dict[str, dict[str, object]] = {}
        offset: int | str | None = None
        while True:
            points, offset = await self.client.scroll(
                collection_name=self.settings.qdrant_collection,
                limit=256,
                offset=offset,
                with_payload=True,
                with_vectors=False,
            )
            for point in points:
                payload = point.payload or {}
                document_id = str(payload.get("document_id", ""))
                if not document_id:
                    continue
                document = documents.setdefault(
                    document_id,
                    {
                        "filename": str(payload.get("filename", "未知文档")),
                        "content_type": str(
                            payload.get("content_type", "application/octet-stream")
                        ),
                        "uploaded_at": str(payload.get("uploaded_at", "")),
                        "chunk_count": 0,
                    },
                )
                document["chunk_count"] = int(document["chunk_count"]) + 1
            if offset is None:
                break

        result = [
            KnowledgeDocumentResponse(
                documentId=document_id,
                filename=str(document["filename"]),
                contentType=str(document["content_type"]),
                chunkCount=int(document["chunk_count"]),
                uploadedAt=str(document["uploaded_at"]),
            )
            for document_id, document in documents.items()
        ]
        return sorted(result, key=lambda item: item.uploaded_at, reverse=True)

    async def search(
        self,
        query: str,
        top_k: int | None = None,
    ) -> list[KnowledgeSearchResult]:
        if not await self._collection_exists():
            return []

        vector = await self.embeddings.aembed_query(query)
        response = await self.client.query_points(
            collection_name=self.settings.qdrant_collection,
            query=vector,
            limit=top_k or self.settings.knowledge_top_k,
            score_threshold=self.settings.knowledge_score_threshold,
            with_payload=True,
            with_vectors=False,
        )
        results = []
        for point in response.points:
            payload = point.payload or {}
            results.append(
                KnowledgeSearchResult(
                    documentId=str(payload.get("document_id", "")),
                    filename=str(payload.get("filename", "未知文档")),
                    content=str(payload.get("text", "")),
                    page=self._optional_int(payload.get("page")),
                    chunkIndex=int(payload.get("chunk_index", 0)),
                    score=float(point.score),
                )
            )
        return results

    async def delete_document(self, document_id: str) -> int:
        if not await self._collection_exists():
            return 0
        count = await self.client.count(
            collection_name=self.settings.qdrant_collection,
            count_filter=self._document_filter(document_id),
            exact=True,
        )
        if count.count:
            await self._delete_by_document_id(document_id)
        return count.count

    async def _ensure_collection(self, vector_size: int) -> None:
        async with self._collection_lock:
            if await self._collection_exists():
                collection = await self.client.get_collection(
                    self.settings.qdrant_collection
                )
                configured_size = collection.config.params.vectors.size
                if configured_size != vector_size:
                    raise RuntimeError(
                        "知识库向量维度与当前嵌入模型不一致；"
                        "请清空 Qdrant 数据或恢复原嵌入模型"
                    )
                return
            await self.client.create_collection(
                collection_name=self.settings.qdrant_collection,
                vectors_config=models.VectorParams(
                    size=vector_size,
                    distance=models.Distance.COSINE,
                ),
            )

    async def _collection_exists(self) -> bool:
        return await self.client.collection_exists(
            self.settings.qdrant_collection
        )

    async def _delete_by_document_id(self, document_id: str) -> None:
        await self.client.delete(
            collection_name=self.settings.qdrant_collection,
            points_selector=models.FilterSelector(
                filter=self._document_filter(document_id)
            ),
            wait=True,
        )

    @staticmethod
    def _document_filter(document_id: str) -> models.Filter:
        return models.Filter(
            must=[
                models.FieldCondition(
                    key="document_id",
                    match=models.MatchValue(value=document_id),
                )
            ]
        )

    def _parse_document(
        self,
        extension: str,
        content: bytes,
    ) -> list[ParsedSection]:
        if extension == ".pdf":
            reader = PdfReader(io.BytesIO(content))
            sections = []
            for index, page in enumerate(reader.pages, start=1):
                text = (page.extract_text() or "").strip()
                if text:
                    sections.append(ParsedSection(text=text, page=index))
            return sections

        try:
            text = content.decode("utf-8-sig").strip()
        except UnicodeDecodeError as exc:
            raise ValueError("文本文件必须使用 UTF-8 编码") from exc
        return [ParsedSection(text=text, page=None)] if text else []

    def _split_sections(
        self,
        sections: Iterable[ParsedSection],
    ) -> list[tuple[str, int | None]]:
        result = []
        for section in sections:
            for chunk in self.splitter.split_text(section.text):
                text = chunk.strip()
                if text:
                    result.append((text, section.page))
        return result

    @staticmethod
    def _optional_int(value: object) -> int | None:
        return int(value) if value is not None else None
