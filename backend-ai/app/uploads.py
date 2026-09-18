from __future__ import annotations

from typing import Protocol


class UploadEmpty(ValueError):
    """Raised when the upload body is empty."""


class UploadTooLarge(ValueError):
    """Raised when the upload exceeds the allowed byte budget."""


class AsyncByteReader(Protocol):
    async def read(self, size: int = -1) -> bytes: ...

    async def close(self) -> None: ...


async def read_upload_capped(upload: AsyncByteReader, max_bytes: int) -> bytes:
    try:
        content = await upload.read(max_bytes + 1)
    finally:
        await upload.close()
    if not content:
        raise UploadEmpty()
    if len(content) > max_bytes:
        raise UploadTooLarge()
    return content
