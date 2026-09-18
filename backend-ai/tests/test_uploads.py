from __future__ import annotations

import anyio

from app.uploads import UploadEmpty, UploadTooLarge, read_upload_capped


class FakeUpload:
    def __init__(self, data: bytes) -> None:
        self._data = data
        self.closed = False

    async def read(self, size: int = -1) -> bytes:
        if size < 0:
            return self._data
        return self._data[:size]

    async def close(self) -> None:
        self.closed = True


def test_read_upload_capped_returns_body_within_limit() -> None:
    upload = FakeUpload(b"abc")

    async def _run() -> bytes:
        return await read_upload_capped(upload, 8)

    assert anyio.run(_run) == b"abc"
    assert upload.closed is True


def test_read_upload_capped_rejects_empty() -> None:
    upload = FakeUpload(b"")

    async def _run() -> None:
        await read_upload_capped(upload, 8)

    try:
        anyio.run(_run)
        raise AssertionError("expected UploadEmpty")
    except UploadEmpty:
        assert upload.closed is True


def test_read_upload_capped_does_not_read_past_limit() -> None:
    payload = b"x" * 20
    upload = FakeUpload(payload)

    async def _run() -> None:
        await read_upload_capped(upload, 8)

    try:
        anyio.run(_run)
        raise AssertionError("expected UploadTooLarge")
    except UploadTooLarge:
        pass
