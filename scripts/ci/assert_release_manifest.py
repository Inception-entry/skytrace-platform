#!/usr/bin/env python3
"""Fail if release digest manifest helper or deploy wiring is missing."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HELPER = ROOT / "scripts" / "release_manifest.py"
DEPLOY_PRODUCTION = ROOT / "scripts" / "deploy-production.sh"
DEPLOY_STAGING = ROOT / "scripts" / "deploy-staging.sh"
PUBLISH_YML = ROOT / ".github" / "workflows" / "publish.yml"

SERVICES = (
    "backend-ai",
    "backend-java",
    "backend-node",
    "gateway",
    "frontend",
    "admin-service",
    "admin-frontend",
)
DIGEST = "sha256:" + ("a" * 64)
SAMPLE = {
    "tag": "main-abc1234",
    "images": {name: DIGEST for name in SERVICES},
}


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def require_snippets(path: Path, snippets: list[str]) -> str:
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        if snippet not in text:
            fail(f"{path} 缺少：{snippet}")
    return text


def run_helper(args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(HELPER), *args],
        cwd=str(ROOT),
        capture_output=True,
        text=True,
        check=False,
    )


def main() -> None:
    require_snippets(
        DEPLOY_PRODUCTION,
        [
            "scripts/release_manifest.py",
            "DIGEST_OVERLAY",
            ".current-release-manifest",
        ],
    )
    require_snippets(
        DEPLOY_STAGING,
        [
            "scripts/release_manifest.py",
            "DIGEST_OVERLAY",
            ".current-release-manifest",
        ],
    )
    require_snippets(
        PUBLISH_YML,
        [
            "digest-manifest",
            "scripts/release_manifest.py inspect",
        ],
    )

    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        good = tmp / "good.json"
        overlay = tmp / "overlay.yml"
        good.write_text(json.dumps(SAMPLE), encoding="utf-8")
        result = run_helper(
            [
                "validate",
                "--manifest",
                str(good),
                "--expect-tag",
                "main-abc1234",
            ]
        )
        if result.returncode != 0:
            fail("合法 manifest 应通过\n" + result.stdout + result.stderr)

        bad_tag = dict(SAMPLE)
        bad_tag["tag"] = "latest"
        bad = tmp / "bad.json"
        bad.write_text(json.dumps(bad_tag), encoding="utf-8")
        result = run_helper(["validate", "--manifest", str(bad)])
        if result.returncode != 2:
            fail("latest tag 应 exit 2")

        result = run_helper(
            [
                "overlay",
                "--registry",
                "ghcr.io/acme/skytrace",
                "--manifest",
                str(good),
                "--output",
                str(overlay),
            ]
        )
        if result.returncode != 0:
            fail("overlay 应成功\n" + result.stdout + result.stderr)
        text = overlay.read_text(encoding="utf-8")
        if "image: ghcr.io/acme/skytrace/gateway@" + DIGEST not in text:
            fail("overlay 未按 digest 钉 gateway\n" + text)
        if ":latest" in text or ":main-abc1234" in text:
            fail("overlay 不得再用 tag\n" + text)

    print("Release digest manifest 断言通过")


if __name__ == "__main__":
    main()
