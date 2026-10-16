#!/usr/bin/env python3
"""Decide whether an image rollback is enough, or the MySQL backup must be restored.

imageRollbackSafeThrough is the highest Flyway version the previous application
image can still run. Additive migrations (new tables or columns with defaults)
may raise it together with flywayVersion. A destructive migration must leave
imageRollbackSafeThrough at the last version the old code understands.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

TAG_RE = __import__("re").compile(r"^main-[0-9a-f]{7,40}$")

STACK_FILES = {
    "production": [
        "deploy/docker-compose.yml",
        "deploy/docker-compose.vision.yml",
        "deploy/docker-compose.staging.yml",
        "deploy/docker-compose.production.yml",
    ],
    "staging": [
        "deploy/docker-compose.yml",
        "deploy/docker-compose.vision.yml",
        "deploy/docker-compose.staging.yml",
    ],
}

OUTSIDE_RELEASE = [
    "deploy/docker-compose.mqtt.yml",
    "deploy/docker-compose.mqtt-tls.yml",
    "deploy/docker-compose.monitoring.yml",
]


def fail(message: str, code: int = 2) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(code)


def load_compat(path: Path) -> dict[str, int]:
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"无法读取 schema 兼容声明 {path}: {exc}")
    if not isinstance(doc, dict):
        fail(f"{path} 不是 JSON object")
    flyway = doc.get("flywayVersion")
    safe = doc.get("imageRollbackSafeThrough")
    if not isinstance(flyway, int) or flyway < 1:
        fail(f"{path} 的 flywayVersion 必须是正整数")
    if not isinstance(safe, int) or safe < 1 or safe > flyway:
        fail(f"{path} 的 imageRollbackSafeThrough 必须是 1..flywayVersion")
    return {"flywayVersion": flyway, "imageRollbackSafeThrough": safe}


def decide(db_version: int | None, safe_through: int) -> str:
    if db_version is None:
        return "unknown"
    if db_version > safe_through:
        return "restore"
    return "images"


def write_record(args: argparse.Namespace) -> None:
    compat = load_compat(Path(args.compat))
    tag = args.tag.strip()
    if not TAG_RE.fullmatch(tag):
        fail(f"tag 必须是 main-<git-sha>：{tag}")
    previous = (args.previous_tag or "").strip()
    if previous and not TAG_RE.fullmatch(previous):
        fail(f"previous tag 必须是 main-<git-sha>：{previous}")
    record: dict[str, Any] = {
        "tag": tag,
        "previousTag": previous,
        "flywayVersion": compat["flywayVersion"],
        "imageRollbackSafeThrough": compat["imageRollbackSafeThrough"],
        "backupObject": (args.backup or "").strip(),
        "stack": args.stack,
        "composeFiles": STACK_FILES[args.stack],
        "outsideRelease": OUTSIDE_RELEASE,
    }
    output = Path(args.output)
    output.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote release record {output}")


def run_decide(args: argparse.Namespace) -> None:
    db_version = args.db_version
    outcome = decide(db_version, args.safe_through)
    print(outcome)
    if outcome == "restore":
        raise SystemExit(10)
    if outcome == "unknown":
        raise SystemExit(11)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    record = sub.add_parser("record")
    record.add_argument("--compat", required=True)
    record.add_argument("--tag", required=True)
    record.add_argument("--previous-tag", default="")
    record.add_argument("--backup", default="")
    record.add_argument("--stack", choices=("production", "staging"), required=True)
    record.add_argument("--output", required=True)
    record.set_defaults(func=write_record)

    decision = sub.add_parser("decide")
    decision.add_argument("--safe-through", type=int, required=True)
    decision.add_argument("--db-version", type=int)
    decision.set_defaults(func=run_decide)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
