"""Run a picklable function in a child process that can be killed on timeout."""

from __future__ import annotations

import asyncio
import multiprocessing as mp
import os
import signal
import time
from collections.abc import Callable
from typing import Any, TypeVar

from app.cgroup_memory import (
    MemoryLimitError,
    attach_pid,
    cgroup_root,
    release_cgroup,
)

T = TypeVar("T")

_CTX = mp.get_context("spawn")


def hang_for_tests(seconds: float) -> None:
    time.sleep(seconds)


def waste_memory(nbytes: int) -> str:
    blob = bytearray(nbytes)
    for offset in range(0, nbytes, 4096):
        blob[offset] = 1
    return f"ok:{len(blob)}"


def kill_self_for_tests() -> None:
    os.kill(os.getpid(), signal.SIGKILL)


def _call_and_send(
    conn: Any,
    func: Callable[..., Any],
    args: tuple[Any, ...],
    memory_bytes: int,
    cgroup_dir: str,
) -> None:
    if memory_bytes > 0:
        attach_pid(os.getpid(), memory_bytes, root=cgroup_dir)
    try:
        conn.send(("ok", func(*args)))
    except Exception as exc:
        try:
            conn.send(("err", exc))
        except Exception:
            conn.send(("err", RuntimeError(type(exc).__name__)))
    finally:
        conn.close()


def _kill(process: mp.Process) -> None:
    if not process.is_alive():
        process.join(timeout=0.1)
        return
    process.terminate()
    process.join(timeout=0.5)
    if process.is_alive():
        process.kill()
        process.join(timeout=0.5)


def _was_oom_killed(process: mp.Process) -> bool:
    code = process.exitcode
    if code is None:
        return False
    return code in (-signal.SIGKILL, -9, 137)


async def run_in_killable_process(
    func: Callable[..., T],
    *args: Any,
    timeout: float,
    memory_bytes: int = 0,
) -> T:
    parent, child = _CTX.Pipe(duplex=False)
    limit = memory_bytes if memory_bytes > 0 else 0
    cgroup_dir = str(cgroup_root())
    process = _CTX.Process(
        target=_call_and_send,
        args=(child, func, args, limit, cgroup_dir),
        daemon=True,
        name="skytrace-knowledge-parse",
    )
    process.start()
    child.close()
    job = None
    if limit > 0 and process.pid:
        job = attach_pid(process.pid, limit, root=cgroup_dir)
    try:
        ready = await asyncio.to_thread(parent.poll, timeout)
        if not ready:
            _kill(process)
            if limit > 0 and _was_oom_killed(process):
                raise MemoryLimitError("文档解析超出内存上限")
            raise TimeoutError()
        try:
            status, payload = parent.recv()
        except EOFError as exc:
            if process.exitcode is None:
                process.join(timeout=0.5)
            if limit > 0 and _was_oom_killed(process):
                raise MemoryLimitError("文档解析超出内存上限") from exc
            raise RuntimeError("解析进程异常退出") from exc
        if status == "err":
            raise payload
        return payload
    finally:
        parent.close()
        if process.is_alive():
            _kill(process)
        if process.exitcode is None:
            process.join(timeout=1)
        release_cgroup(job)
