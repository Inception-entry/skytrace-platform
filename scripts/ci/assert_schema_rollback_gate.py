#!/usr/bin/env python3
"""Schema rollback gate: additive schema may roll images; a newer schema may not."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GATE = ROOT / "scripts" / "schema_rollback_gate.py"
COMPAT = ROOT / "deploy" / "schema-compat.json"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def run_gate(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(GATE), *args],
        capture_output=True,
        text=True,
        check=False,
    )


def main() -> None:
    compat = json.loads(COMPAT.read_text(encoding="utf-8"))
    if compat.get("flywayVersion") != 25 or compat.get("imageRollbackSafeThrough") != 25:
        fail(f"V25 仍是加列，旧镜像应能留在这个库上：{compat}")

    same = run_gate("decide", "--safe-through", "25", "--db-version", "25")
    if same.returncode != 0 or same.stdout.strip() != "images":
        fail(f"同版本应只退镜像：{same.returncode} {same.stdout} {same.stderr}")

    ahead = run_gate("decide", "--safe-through", "25", "--db-version", "26")
    if ahead.returncode != 10 or ahead.stdout.strip() != "restore":
        fail(f"超出版本线必须先恢复库：{ahead.returncode} {ahead.stdout} {ahead.stderr}")

    unknown = run_gate("decide", "--safe-through", "25")
    if unknown.returncode != 11 or unknown.stdout.strip() != "unknown":
        fail(f"未知库版本不得假装安全：{unknown.returncode} {unknown.stdout}")

    with tempfile.TemporaryDirectory() as raw:
        output = Path(raw) / "record.json"
        wrote = run_gate(
            "record",
            "--compat",
            str(COMPAT),
            "--tag",
            "main-abc1234",
            "--previous-tag",
            "main-def5678",
            "--stack",
            "production",
            "--backup",
            "skytrace_inspection/20260101T000000Z.sql.gz",
            "--output",
            str(output),
        )
        if wrote.returncode != 0:
            fail(wrote.stderr)
        record = json.loads(output.read_text(encoding="utf-8"))
        if record["previousTag"] != "main-def5678" or record["backupObject"] == "":
            fail(f"回退单没有留下上一 tag 和备份：{record}")
        if "deploy/docker-compose.production.yml" not in record["composeFiles"]:
            fail(f"生产回退单没有记下 Compose 文件：{record}")
        if "deploy/docker-compose.mqtt.yml" not in record["outsideRelease"]:
            fail(f"回退单没有标明 MQTT 不在这次发布里：{record}")

    print("Schema rollback gate 断言通过")


if __name__ == "__main__":
    main()
