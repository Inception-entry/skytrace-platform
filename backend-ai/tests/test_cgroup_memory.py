import os
from pathlib import Path

from app.cgroup_memory import attach_pid, cgroup_root, release_cgroup


def test_attach_pid_writes_memory_max(tmp_path: Path) -> None:
    (tmp_path / "cgroup.controllers").write_text("memory\n", encoding="ascii")
    pid = os.getpid()
    job = attach_pid(pid, 64 * 1024 * 1024, root=tmp_path)
    assert job is not None
    assert job.name == f"skytrace-pdf-{pid}"
    assert (job / "memory.max").read_text(encoding="ascii").strip() == str(
        64 * 1024 * 1024
    )
    assert (job / "memory.swap.max").read_text(encoding="ascii").strip() == "0"
    assert str(pid) in (job / "cgroup.procs").read_text(encoding="ascii")
    release_cgroup(job)
    assert not job.exists()


def test_attach_pid_skips_without_memory_controller(tmp_path: Path) -> None:
    (tmp_path / "cgroup.controllers").write_text("cpu pids\n", encoding="ascii")
    assert attach_pid(os.getpid(), 64 * 1024 * 1024, root=tmp_path) is None


def test_attach_pid_skips_zero_limit(tmp_path: Path) -> None:
    (tmp_path / "cgroup.controllers").write_text("memory\n", encoding="ascii")
    assert attach_pid(os.getpid(), 0, root=tmp_path) is None


def test_cgroup_root_reads_env(tmp_path: Path, monkeypatch) -> None:
    monkeypatch.setenv("SKYTRACE_CGROUP_ROOT", str(tmp_path))
    assert cgroup_root() == tmp_path
