"""Run a picklable function in a child process that can be killed on timeout."""

from __future__ import annotations

import asyncio
import multiprocessing as mp
import time
from collections.abc import Callable
from typing import Any, TypeVar

T = TypeVar("T")

_CTX = mp.get_context("spawn")


def hang_for_tests(seconds: float) -> None:
    time.sleep(seconds)


def _call_and_send(
    conn: Any,
    func: Callable[..., Any],
    args: tuple[Any, ...],
) -> None:
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


async def run_in_killable_process(
    func: Callable[..., T],
    *args: Any,
    timeout: float,
) -> T:
    parent, child = _CTX.Pipe(duplex=False)
    process = _CTX.Process(
        target=_call_and_send,
        args=(child, func, args),
        daemon=True,
        name="skytrace-knowledge-parse",
    )
    process.start()
    child.close()
    try:
        ready = await asyncio.to_thread(parent.poll, timeout)
        if not ready:
            _kill(process)
            raise TimeoutError()
        try:
            status, payload = parent.recv()
        except EOFError as exc:
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
