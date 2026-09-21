import asyncio
import time
from importlib.metadata import version
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import AsyncMock

import pytest
from pypdf import PdfWriter

from app.config import Settings
from app.knowledge_base import KnowledgeBase


class FakeEmbeddings:
    async def aembed_documents(self, texts: list[str]) -> list[list[float]]:
        return [[1.0, float(index), 0.5] for index, _ in enumerate(texts)]

    async def aembed_query(self, text: str) -> list[float]:
        return [1.0, float("返航" in text), 0.5]


def test_imports_utf8_document_as_qdrant_points() -> None:
    client = SimpleNamespace(
        collection_exists=AsyncMock(return_value=False),
        create_collection=AsyncMock(),
        delete=AsyncMock(),
        upsert=AsyncMock(),
    )
    knowledge_base = KnowledgeBase(
        Settings(knowledge_chunk_size=20, knowledge_chunk_overlap=4),
        client=client,
        embeddings=FakeEmbeddings(),
    )

    document = asyncio.run(
        knowledge_base.import_document(
            "flight-manual.md",
            "text/markdown",
            "低电量告警后应确认返航点。\n\n返航前检查航线和剩余电量。".encode(),
        )
    )

    assert document.filename == "flight-manual.md"
    assert document.chunk_count >= 1
    client.create_collection.assert_awaited_once()
    client.delete.assert_awaited_once()
    client.upsert.assert_awaited_once()
    points = client.upsert.await_args.kwargs["points"]
    assert len(points) == document.chunk_count
    assert points[0].payload["document_id"] == document.document_id
    assert points[0].payload["filename"] == "flight-manual.md"


def test_search_maps_qdrant_payload_to_traceable_result() -> None:
    client = SimpleNamespace(
        collection_exists=AsyncMock(return_value=True),
        query_points=AsyncMock(
            return_value=SimpleNamespace(
                points=[
                    SimpleNamespace(
                        score=0.91,
                        payload={
                            "document_id": "doc-001",
                            "filename": "fault-guide.pdf",
                            "text": "图传中断后先保持航向并检查链路。",
                            "page": 8,
                            "chunk_index": 3,
                        },
                    )
                ]
            )
        ),
    )
    knowledge_base = KnowledgeBase(
        Settings(),
        client=client,
        embeddings=FakeEmbeddings(),
    )

    results = asyncio.run(knowledge_base.search("图传中断如何返航？", top_k=3))

    assert len(results) == 1
    assert results[0].document_id == "doc-001"
    assert results[0].filename == "fault-guide.pdf"
    assert results[0].page == 8
    assert results[0].score == 0.91
    assert client.query_points.await_args.kwargs["limit"] == 3


def test_pypdf_lock_is_at_least_advisory_fix() -> None:
    major, minor, patch = (int(part) for part in version("pypdf").split(".")[:3])
    assert (major, minor, patch) >= (6, 15, 0)


def _blank_pdf_bytes(pages: int = 1) -> bytes:
    buffer = BytesIO()
    writer = PdfWriter()
    for _ in range(pages):
        writer.add_blank_page(width=72, height=72)
    writer.write(buffer)
    return buffer.getvalue()


def test_blank_pdf_has_no_extractable_text() -> None:
    knowledge_base = KnowledgeBase(
        Settings(),
        client=SimpleNamespace(),
        embeddings=FakeEmbeddings(),
    )

    with pytest.raises(ValueError, match="没有可提取的文字"):
        asyncio.run(
            knowledge_base.import_document(
                "empty.pdf",
                "application/pdf",
                _blank_pdf_bytes(),
            )
        )


def test_corrupt_pdf_is_rejected() -> None:
    knowledge_base = KnowledgeBase(
        Settings(),
        client=SimpleNamespace(),
        embeddings=FakeEmbeddings(),
    )

    with pytest.raises(ValueError, match="损坏或无法解析"):
        asyncio.run(
            knowledge_base.import_document(
                "broken.pdf",
                "application/pdf",
                b"%PDF-1.4 this is not a valid pdf",
            )
        )


def test_pdf_page_limit_is_enforced() -> None:
    knowledge_base = KnowledgeBase(
        Settings(knowledge_max_pages=2),
        client=SimpleNamespace(),
        embeddings=FakeEmbeddings(),
    )

    with pytest.raises(ValueError, match="页数超过限制"):
        asyncio.run(
            knowledge_base.import_document(
                "too-many-pages.pdf",
                "application/pdf",
                _blank_pdf_bytes(pages=3),
            )
        )


def test_chunk_limit_is_enforced() -> None:
    knowledge_base = KnowledgeBase(
        Settings(
            knowledge_chunk_size=10,
            knowledge_chunk_overlap=0,
            knowledge_max_chunks=1,
        ),
        client=SimpleNamespace(),
        embeddings=FakeEmbeddings(),
    )

    with pytest.raises(ValueError, match="切片数量超过限制"):
        asyncio.run(
            knowledge_base.import_document(
                "long.md",
                "text/markdown",
                ("低电量告警后应立即返航检查。\n" * 8).encode(),
            )
        )


def test_extract_char_limit_is_enforced() -> None:
    knowledge_base = KnowledgeBase(
        Settings(knowledge_max_extract_chars=20),
        client=SimpleNamespace(),
        embeddings=FakeEmbeddings(),
    )

    with pytest.raises(ValueError, match="提取文字超过限制"):
        asyncio.run(
            knowledge_base.import_document(
                "long.md",
                "text/markdown",
                "低电量告警后应确认返航点并检查剩余电量，不要继续飞行。".encode(),
            )
        )


def test_parse_timeout_is_enforced() -> None:
    knowledge_base = KnowledgeBase(
        Settings(knowledge_parse_timeout_seconds=0.1),
        client=SimpleNamespace(),
        embeddings=FakeEmbeddings(),
    )

    def slow(_extension: str, _content: bytes) -> list:
        time.sleep(0.4)
        return []

    knowledge_base._parse_document = slow  # type: ignore[method-assign]

    with pytest.raises(ValueError, match="解析超时"):
        asyncio.run(
            knowledge_base.import_document(
                "slow.md",
                "text/markdown",
                "低电量告警后应确认返航点。".encode(),
            )
        )


def test_killable_process_terminates_hung_worker() -> None:
    import multiprocessing

    from app.process_timeout import hang_for_tests, run_in_killable_process

    started = time.monotonic()
    with pytest.raises(TimeoutError):
        asyncio.run(run_in_killable_process(hang_for_tests, 8.0, timeout=0.3))
    elapsed = time.monotonic() - started
    assert elapsed < 2.5
    time.sleep(0.2)
    assert multiprocessing.active_children() == []


def test_killable_process_maps_sigkill_to_memory_limit() -> None:
    import multiprocessing

    from app.cgroup_memory import MemoryLimitError
    from app.process_timeout import kill_self_for_tests, run_in_killable_process

    with pytest.raises(MemoryLimitError, match="内存上限"):
        asyncio.run(
            run_in_killable_process(
                kill_self_for_tests,
                timeout=5,
                memory_bytes=64 * 1024 * 1024,
            )
        )
    time.sleep(0.2)
    assert multiprocessing.active_children() == []


def test_pdf_memory_limit_is_mapped_to_value_error(monkeypatch: pytest.MonkeyPatch) -> None:
    from app import knowledge_base as knowledge_base_module
    from app.cgroup_memory import MemoryLimitError

    async def boom(*_args: object, **_kwargs: object) -> list:
        raise MemoryLimitError("文档解析超出内存上限")

    monkeypatch.setattr(knowledge_base_module, "run_in_killable_process", boom)
    knowledge_base = KnowledgeBase(
        Settings(),
        client=SimpleNamespace(),
        embeddings=FakeEmbeddings(),
    )
    with pytest.raises(ValueError, match="超出内存上限"):
        asyncio.run(
            knowledge_base.import_document(
                "bomb.pdf",
                "application/pdf",
                b"%PDF-1.4 x",
            )
        )

