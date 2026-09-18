from __future__ import annotations

import logging
import shutil
import subprocess
import tempfile
from pathlib import Path

logger = logging.getLogger(__name__)

DEFAULT_FFMPEG_TIMEOUT_SECONDS = 30.0
MAX_FRAMES_CAP = 30
MAX_FRAME_INTERVAL_SEC = 60.0
STDERR_LOG_LIMIT = 4096


class FrameExtractionError(RuntimeError):
    """Raised when video frames cannot be extracted."""


def extract_video_frames(
    video_bytes: bytes,
    *,
    frame_interval_sec: float = 2.0,
    max_frames: int = 10,
    timeout_seconds: float = DEFAULT_FFMPEG_TIMEOUT_SECONDS,
) -> list[bytes]:
    """Extract JPEG frames from a video using ffmpeg when available."""
    if not video_bytes:
        raise FrameExtractionError("视频内容为空")
    if max_frames < 1 or max_frames > MAX_FRAMES_CAP:
        raise FrameExtractionError(f"maxFrames 必须在 1 到 {MAX_FRAMES_CAP} 之间")
    if frame_interval_sec <= 0 or frame_interval_sec > MAX_FRAME_INTERVAL_SEC:
        raise FrameExtractionError("frameIntervalSec 超出允许范围")
    if timeout_seconds <= 0:
        raise FrameExtractionError("视频处理超时配置无效")

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
            "-nostdin",
            "-hide_banner",
            "-loglevel",
            "error",
            "-protocol_whitelist",
            "file,pipe,crypto",
            "-y",
            "-i",
            str(video_path),
            "-vf",
            f"fps=1/{frame_interval_sec}",
            "-frames:v",
            str(max_frames),
            str(pattern),
        ]
        try:
            completed = subprocess.run(
                command,
                check=False,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired as exc:
            raise FrameExtractionError("视频处理超时") from exc

        if completed.returncode != 0:
            detail = (completed.stderr or b"")[:STDERR_LOG_LIMIT]
            logger.warning("ffmpeg 抽帧失败 returncode=%s stderr=%r", completed.returncode, detail)
            raise FrameExtractionError("视频抽帧失败")

        frames = sorted(workdir.glob("frame_*.jpg"))
        if not frames:
            raise FrameExtractionError("未能从视频中抽取到有效帧")
        return [path.read_bytes() for path in frames[:max_frames]]
