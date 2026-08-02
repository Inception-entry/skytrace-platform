from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path


class FrameExtractionError(RuntimeError):
    """Raised when video frames cannot be extracted."""


def extract_video_frames(
    video_bytes: bytes,
    *,
    frame_interval_sec: float = 2.0,
    max_frames: int = 10,
) -> list[bytes]:
    """Extract JPEG frames from a video using ffmpeg when available."""
    if not video_bytes:
        raise FrameExtractionError("视频内容为空")
    if max_frames < 1:
        raise FrameExtractionError("maxFrames 必须大于 0")
    if frame_interval_sec <= 0:
        raise FrameExtractionError("frameIntervalSec 必须大于 0")

    ffmpeg = shutil.which("ffmpeg")
    if ffmpeg is None:
        raise FrameExtractionError(
            "未找到 ffmpeg，请在 AI 镜像中安装 ffmpeg 后再上传视频"
        )

    with tempfile.TemporaryDirectory(prefix="skytrace-frames-") as temp_dir:
        workdir = Path(temp_dir)
        video_path = workdir / "input.bin"
        video_path.write_bytes(video_bytes)
        pattern = workdir / "frame_%04d.jpg"

        command = [
            ffmpeg,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(video_path),
            "-vf",
            f"fps=1/{frame_interval_sec}",
            "-frames:v",
            str(max_frames),
            str(pattern),
        ]
        completed = subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
        )
        if completed.returncode != 0:
            detail = (completed.stderr or completed.stdout or "").strip()
            raise FrameExtractionError(
                f"ffmpeg 抽帧失败: {detail or 'unknown error'}"
            )

        frames = sorted(workdir.glob("frame_*.jpg"))
        if not frames:
            raise FrameExtractionError("未能从视频中抽取到有效帧")
        return [path.read_bytes() for path in frames[:max_frames]]
