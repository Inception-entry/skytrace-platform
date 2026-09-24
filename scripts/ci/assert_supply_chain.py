#!/usr/bin/env python3
"""RC3: third-party images are digest-pinned, app images run as non-root, SBOM and blue/green exist."""

from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
COMPOSE_FILES = (
    "deploy/docker-compose.yml",
    "deploy/docker-compose.staging.yml",
    "deploy/docker-compose.monitoring.yml",
    "deploy/docker-compose.mqtt.yml",
)
APP_DOCKERFILES = {
    "backend-java/Dockerfile": "USER 10001:10001",
    "gateway-java/Dockerfile": "USER 10001:10001",
    "backend-node/Dockerfile": "USER 10001:10001",
    "admin-service/Dockerfile": "USER 10001:10001",
    "backend-ai/Dockerfile": "USER 10001:10001",
    "frontend/Dockerfile": "USER nginx",
    "admin-frontend/Dockerfile": "USER nginx",
}
IMAGE_RE = re.compile(r"^\s*image:\s*(\S+)\s*$", re.M)
DIGEST_RE = re.compile(r"@sha256:[0-9a-f]{64}$")


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(2)


def main() -> None:
    for rel in COMPOSE_FILES:
        text = (ROOT / rel).read_text(encoding="utf-8")
        for image in IMAGE_RE.findall(text):
            if image.startswith("${REGISTRY}"):
                continue
            if ":latest" in image:
                fail(f"{rel} 仍有 :latest：{image}")
            if not DIGEST_RE.search(image):
                fail(f"{rel} 的第三方镜像没有 digest：{image}")
    for rel, marker in APP_DOCKERFILES.items():
        if marker not in (ROOT / rel).read_text(encoding="utf-8"):
            fail(f"{rel} 缺少 {marker}")
    if "listen 8080;" not in (ROOT / "frontend/nginx.conf").read_text(encoding="utf-8"):
        fail("frontend nginx 仍监听 80")
    upstream = (ROOT / "deploy/caddy/upstream.caddy").read_text(encoding="utf-8")
    if "reverse_proxy frontend:8080" not in upstream:
        fail("默认 Caddy upstream 不是 frontend:8080")
    publish = (ROOT / ".github/workflows/publish.yml").read_text(encoding="utf-8")
    if "scripts/release_sbom.py generate" not in publish:
        fail("Publish 没有生成 SBOM")
    result = subprocess.run(
        [sys.executable, str(ROOT / "scripts/bluegreen.py"), "next", "--color", "blue"],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0 or result.stdout.strip() != "green":
        fail(result.stderr or "bluegreen next 失败")
    print("supply chain 断言通过")


if __name__ == "__main__":
    main()
