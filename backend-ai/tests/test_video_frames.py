from __future__ import annotations

import subprocess
from pathlib import Path
from unittest.mock import patch

import pytest

from app.vision.video_frames import (
    MAX_FRAMES_CAP,
    FrameExtractionError,
    extract_video_frames,
)


def test_extract_video_frames_requires_ffmpeg() -> None:
    with patch("app.vision.video_frames.shutil.which", return_value=None):
        with pytest.raises(FrameExtractionError, match="ffmpeg"):
            extract_video_frames(b"fake-video", max_frames=2)


def test_extract_video_frames_rejects_excessive_max_frames() -> None:
    with pytest.raises(FrameExtractionError, match="maxFrames"):
        extract_video_frames(b"fake-video", max_frames=MAX_FRAMES_CAP + 1)


def test_extract_video_frames_reads_generated_jpegs(tmp_path: Path) -> None:
    fake_ffmpeg = tmp_path / "ffmpeg"
    fake_ffmpeg.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
    fake_ffmpeg.chmod(0o755)

    def fake_run(command, **kwargs):  # noqa: ANN001
        assert "-nostdin" in command
        assert "file,pipe,crypto" in command
        assert kwargs["stdin"] is subprocess.DEVNULL
        assert kwargs["timeout"] == 12
        pattern = Path(command[-1])
        pattern.parent.joinpath("frame_0001.jpg").write_bytes(b"jpeg-1")
        pattern.parent.joinpath("frame_0002.jpg").write_bytes(b"jpeg-2")

        class Result:
            returncode = 0
            stderr = b""

        return Result()

    with (
        patch("app.vision.video_frames.shutil.which", return_value=str(fake_ffmpeg)),
        patch("app.vision.video_frames.subprocess.run", side_effect=fake_run),
    ):
        frames = extract_video_frames(
            b"video-bytes",
            max_frames=2,
            timeout_seconds=12,
        )

    assert frames == [b"jpeg-1", b"jpeg-2"]


def test_extract_video_frames_times_out_without_leaking_stderr(tmp_path: Path) -> None:
    fake_ffmpeg = tmp_path / "ffmpeg"
    fake_ffmpeg.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
    fake_ffmpeg.chmod(0o755)

    def fake_run(command, **kwargs):  # noqa: ANN001
        raise subprocess.TimeoutExpired(cmd=command, timeout=kwargs["timeout"])

    with (
        patch("app.vision.video_frames.shutil.which", return_value=str(fake_ffmpeg)),
        patch("app.vision.video_frames.subprocess.run", side_effect=fake_run),
        pytest.raises(FrameExtractionError, match="视频处理超时") as caught,
    ):
        extract_video_frames(b"video-bytes", max_frames=2, timeout_seconds=1)

    assert "TimeoutExpired" not in str(caught.value)
    assert "stderr" not in str(caught.value).lower()


def test_extract_video_frames_hides_ffmpeg_stderr(tmp_path: Path) -> None:
    fake_ffmpeg = tmp_path / "ffmpeg"
    fake_ffmpeg.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
    fake_ffmpeg.chmod(0o755)

    def fake_run(command, **kwargs):  # noqa: ANN001
        class Result:
            returncode = 1
            stderr = b"secret-path /tmp/input.bin"

        return Result()

    with (
        patch("app.vision.video_frames.shutil.which", return_value=str(fake_ffmpeg)),
        patch("app.vision.video_frames.subprocess.run", side_effect=fake_run),
        pytest.raises(FrameExtractionError, match="视频抽帧失败") as caught,
    ):
        extract_video_frames(b"video-bytes", max_frames=2)

    assert "secret-path" not in str(caught.value)
