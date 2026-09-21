from __future__ import annotations

import io
from dataclasses import dataclass

from pypdf import PdfReader


@dataclass(frozen=True)
class ParsedSection:
    text: str
    page: int | None


def parse_document(
    extension: str,
    content: bytes,
    max_pages: int,
    max_extract_chars: int,
) -> list[ParsedSection]:
    if extension == ".pdf":
        reader = PdfReader(io.BytesIO(content))
        if len(reader.pages) > max_pages:
            raise ValueError("文档页数超过限制")
        sections: list[ParsedSection] = []
        extracted = 0
        for index, page in enumerate(reader.pages, start=1):
            text = (page.extract_text() or "").strip()
            if not text:
                continue
            extracted += len(text)
            if extracted > max_extract_chars:
                raise ValueError("文档提取文字超过限制")
            sections.append(ParsedSection(text=text, page=index))
        return sections

    try:
        text = content.decode("utf-8-sig").strip()
    except UnicodeDecodeError as exc:
        raise ValueError("文本文件必须使用 UTF-8 编码") from exc
    if len(text) > max_extract_chars:
        raise ValueError("文档提取文字超过限制")
    return [ParsedSection(text=text, page=None)] if text else []
