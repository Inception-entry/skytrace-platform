"""Confine a PID to a cgroup v2 with memory.max.

Docker 容器里 `/sys/fs/cgroup` 是该命名空间的根，可以建子 cgroup。
本地/CI 写不了就返回 None，解析照跑，只是没有内存上限。
"""

from __future__ import annotations

import os
from pathlib import Path

DEFAULT_CGROUP_ROOT = "/sys/fs/cgroup"
CGROUP_ROOT_ENV = "SKYTRACE_CGROUP_ROOT"


class MemoryLimitError(Exception):
    """解析子进程被内存上限杀掉。"""


def cgroup_root(root: str | Path | None = None) -> Path:
    if root is not None:
        return Path(root)
    configured = os.environ.get(CGROUP_ROOT_ENV, "").strip()
    return Path(configured or DEFAULT_CGROUP_ROOT)


def attach_pid(
    pid: int,
    memory_bytes: int,
    *,
    root: str | Path | None = None,
) -> Path | None:
    if memory_bytes <= 0 or pid <= 0:
        return None
    base = cgroup_root(root)
    controllers = base / "cgroup.controllers"
    if not controllers.is_file():
        return None
    try:
        if "memory" not in controllers.read_text(encoding="ascii"):
            return None
    except OSError:
        return None
    job = base / f"skytrace-pdf-{pid}"
    try:
        job.mkdir(exist_ok=True)
        (job / "memory.max").write_text(str(memory_bytes), encoding="ascii")
        try:
            (job / "memory.swap.max").write_text("0", encoding="ascii")
        except OSError:
            pass
        (job / "cgroup.procs").write_text(str(pid), encoding="ascii")
    except OSError:
        release_cgroup(job)
        return None
    return job


def release_cgroup(job: Path | None) -> None:
    if job is None:
        return
    try:
        if not job.is_dir():
            return
    except OSError:
        return
    parent_procs = job.parent / "cgroup.procs"
    job_procs = job / "cgroup.procs"
    if job_procs.is_file() and parent_procs.is_file():
        try:
            pids = job_procs.read_text(encoding="ascii").split()
        except OSError:
            pids = []
        for item in pids:
            try:
                parent_procs.write_text(item, encoding="ascii")
            except OSError:
                pass
    try:
        job.rmdir()
        return
    except OSError:
        pass
    try:
        for child in job.iterdir():
            try:
                if child.is_file():
                    child.unlink()
            except OSError:
                pass
        job.rmdir()
    except OSError:
        pass
