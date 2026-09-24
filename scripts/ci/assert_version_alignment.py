#!/usr/bin/env python3
"""Fail unless all SkyTrace subprojects declare the same platform version."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EXPECTED = "1.3.0"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def package_json_version(path: Path) -> str:
    return str(json.loads(path.read_text(encoding="utf-8"))["version"])


def pom_version(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    match = re.search(
        r"<artifactId>skytrace-[^<]+</artifactId>\s*<version>([^<]+)</version>",
        text,
    )
    if not match:
        fail(f"{path} 找不到项目 version")
    return match.group(1)


def toml_version(path: Path) -> str:
    match = re.search(
        r'(?m)^version = "([^"]+)"',
        path.read_text(encoding="utf-8"),
    )
    if not match:
        fail(f"{path} 找不到 version")
    return match.group(1)


def main() -> None:
    checks = {
        ROOT / "frontend" / "package.json": package_json_version,
        ROOT / "backend-node" / "package.json": package_json_version,
        ROOT / "admin-frontend" / "package.json": package_json_version,
        ROOT / "admin-service" / "package.json": package_json_version,
        ROOT / "e2e" / "package.json": package_json_version,
        ROOT / "backend-java" / "pom.xml": pom_version,
        ROOT / "gateway-java" / "pom.xml": pom_version,
        ROOT / "backend-ai" / "pyproject.toml": toml_version,
    }
    mismatches = []
    for path, reader in checks.items():
        actual = reader(path)
        if actual != EXPECTED:
            mismatches.append(f"{path.relative_to(ROOT)}: {actual}")
    if mismatches:
        fail(
            "子项目版本未对齐 "
            + EXPECTED
            + ":\n"
            + "\n".join(mismatches)
        )
    print(f"平台版本对齐 {EXPECTED}")


if __name__ == "__main__":
    main()
