from __future__ import annotations

import warnings
from io import BytesIO

from PIL import Image

DEFAULT_MAX_SIDE = 4096
DEFAULT_MAX_PIXELS = 8_388_608


class InvalidImage(ValueError):
    """Raised when an image cannot be decoded within the pixel budget."""


def assert_image_within_budget(
    image_bytes: bytes,
    *,
    max_side: int = DEFAULT_MAX_SIDE,
    max_pixels: int = DEFAULT_MAX_PIXELS,
) -> tuple[int, int]:
    if not image_bytes:
        raise InvalidImage("图片内容为空")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(image_bytes)) as probe:
                width, height = probe.size
                if width <= 0 or height <= 0:
                    raise InvalidImage("图片尺寸无效")
                if width > max_side or height > max_side or width * height > max_pixels:
                    raise InvalidImage("图片像素超过限制")
                probe.verify()
    except InvalidImage:
        raise
    except (Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
        raise InvalidImage("图片像素超过限制") from exc
    except Exception as exc:
        raise InvalidImage("图片无法解析") from exc
    return width, height


def decode_rgb_image(
    image_bytes: bytes,
    *,
    max_side: int = DEFAULT_MAX_SIDE,
    max_pixels: int = DEFAULT_MAX_PIXELS,
) -> Image.Image:
    assert_image_within_budget(
        image_bytes,
        max_side=max_side,
        max_pixels=max_pixels,
    )
    with Image.open(BytesIO(image_bytes)) as source:
        return source.convert("RGB")
