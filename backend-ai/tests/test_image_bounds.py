from __future__ import annotations

import struct
import zlib
from io import BytesIO

import pytest
from PIL import Image

from app.vision.image_bounds import (
    InvalidImage,
    assert_image_within_budget,
    decode_rgb_image,
)


def _png_bytes(width: int, height: int) -> bytes:
    buffer = BytesIO()
    Image.new("RGB", (width, height), (12, 34, 56)).save(buffer, format="PNG")
    return buffer.getvalue()


def _png_claiming_size(width: int, height: int) -> bytes:
    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(b"\x00\x00\x00"))
        + chunk(b"IEND", b"")
    )


def test_accepts_small_png() -> None:
    width, height = assert_image_within_budget(_png_bytes(32, 24))
    assert (width, height) == (32, 24)
    image = decode_rgb_image(_png_bytes(32, 24))
    assert image.mode == "RGB"
    assert image.size == (32, 24)


def test_rejects_invalid_bytes() -> None:
    with pytest.raises(InvalidImage, match="无法解析"):
        assert_image_within_budget(b"not-an-image")


def test_rejects_empty_bytes() -> None:
    with pytest.raises(InvalidImage, match="为空"):
        assert_image_within_budget(b"")


def test_rejects_header_pixel_bomb_before_full_decode() -> None:
    payload = _png_claiming_size(20_000, 20_000)
    with pytest.raises(InvalidImage, match="像素超过限制"):
        assert_image_within_budget(payload, max_side=4096, max_pixels=8_388_608)


def test_rejects_side_length_over_cap() -> None:
    with pytest.raises(InvalidImage, match="像素超过限制"):
        assert_image_within_budget(_png_bytes(64, 8), max_side=32, max_pixels=10_000)
