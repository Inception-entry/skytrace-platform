from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

import pytest

from app.vision.video_frames import FrameExtractionError, extract_video_frames


def test_extract_video_frames_requires_ffmpeg(tmp_path: Path) -> None:
    with patch("app.vision.video_frames.shutil.which", return_value=None):
        with pytest.raises(FrameExtractionError, match="ffmpeg"):
            extract_video_frames(b"fake-video", max_frames=2)


def test_extract_video_frames_reads_generated_jpegs(tmp_path: Path) -> None:
    fake_ffmpeg = tmp_path / "ffmpeg"
    fake_ffmpeg.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
    fake_ffmpeg.chmod(0o755)

    def fake_run(command, check, capture_output, text):  # noqa: ANN001
        # last arg is output pattern like /tmp/.../frame_%04d.jpg
        pattern = Path(command[-1])
        pattern.parent.joinpath("frame_0001.jpg").write_bytes(b"jpeg-1")
        pattern.parent.joinpath("frame_0002.jpg").write_bytes(b"jpeg-2")

        class Result:
            returncode = 0
            stderr = ""
            stdout = ""

        return Result()

    with (
        patch("app.vision.video_frames.shutil.which", return_value=str(fake_ffmpeg)),
        patch("app.vision.video_frames.subprocess.run", side_effect=fake_run),
    ):
        frames = extract_video_frames(b"video-bytes", max_frames=2)

    assert frames == [b"jpeg-1", b"jpeg-2"]
