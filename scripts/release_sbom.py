#!/usr/bin/env python3
"""Write one CycloneDX SBOM command per application image in a release manifest."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

SERVICES = (
    "backend-ai",
    "backend-java",
    "backend-node",
    "gateway",
    "frontend",
    "admin-service",
    "admin-frontend",
)
SYFT_IMAGE = "anchore/syft:v1.32.0"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(2)


def plan(manifest: Path, registry: str, output_dir: Path) -> list[tuple[str, str, Path]]:
    doc = json.loads(manifest.read_text(encoding="utf-8"))
    images = doc.get("images")
    if not isinstance(images, dict):
        fail("manifest.images 缺失")
    registry = registry.rstrip("/")
    rows: list[tuple[str, str, Path]] = []
    for name in SERVICES:
        digest = images.get(name)
        if not isinstance(digest, str) or not digest.startswith("sha256:"):
            fail(f"manifest.images.{name} 不是 digest")
        rows.append((name, f"{registry}/{name}@{digest}", output_dir / f"{name}.cdx.json"))
    return rows


def cmd_plan(args: argparse.Namespace) -> None:
    for name, ref, path in plan(Path(args.manifest), args.registry, Path(args.output_dir)):
        print(f"{name} {ref} {path}")


def cmd_generate(args: argparse.Namespace) -> None:
    out = Path(args.output_dir)
    out.mkdir(parents=True, exist_ok=True)
    for _name, ref, path in plan(Path(args.manifest), args.registry, out):
        result = subprocess.run(
            [
                "docker", "run", "--rm",
                SYFT_IMAGE,
                ref,
                "-o", "cyclonedx-json",
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            fail(result.stderr or result.stdout or f"syft 失败：{ref}")
        path.write_text(result.stdout, encoding="utf-8")
        print(path)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    for name in ("plan", "generate"):
        item = sub.add_parser(name)
        item.add_argument("--manifest", required=True)
        item.add_argument("--registry", required=True)
        item.add_argument("--output-dir", required=True)
    args = parser.parse_args()
    if args.command == "plan":
        cmd_plan(args)
    else:
        cmd_generate(args)


if __name__ == "__main__":
    main()
