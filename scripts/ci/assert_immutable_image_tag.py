#!/usr/bin/env python3
"""Fail if staging/production deploy still accepts mutable IMAGE_TAG."""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY_PRODUCTION = ROOT / "scripts" / "deploy-production.sh"
DEPLOY_STAGING = ROOT / "scripts" / "deploy-staging.sh"
DEPLOY_PRODUCTION_YML = ROOT / ".github" / "workflows" / "deploy-production.yml"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def require_snippets(path: Path, snippets: list[str]) -> str:
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        if snippet not in text:
            fail(f"{path} 缺少：{snippet}")
    return text


def run_script(script: Path, image_tag: str | None) -> subprocess.CompletedProcess[str]:
    env = {
        **os.environ,
        "SKYTRACE_DOMAIN": "prod.example.com",
        "APP_DIR": "/tmp/skytrace-immutable-tag-should-not-be-used",
    }
    if image_tag is None:
        env.pop("IMAGE_TAG", None)
    else:
        env["IMAGE_TAG"] = image_tag
    return subprocess.run(
        ["bash", str(script)],
        cwd=str(ROOT),
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )


def assert_rejects(script: Path, image_tag: str | None, label: str) -> None:
    result = run_script(script, image_tag)
    if result.returncode != 2:
        fail(
            f"{script.name} 对 {label} 应 exit 2，实际 {result.returncode}\n"
            f"{result.stdout}{result.stderr}"
        )
    if "immutable main-<git-sha>" not in result.stderr and "IMAGE_TAG is required" not in result.stderr:
        fail(f"{script.name} 对 {label} 应说明 IMAGE_TAG 规则\n{result.stderr}")


def main() -> None:
    for script in (DEPLOY_PRODUCTION, DEPLOY_STAGING):
        require_snippets(
            script,
            [
                r"^main-[0-9a-f]{7,40}$",
                "IMAGE_TAG must be immutable main-<git-sha>",
            ],
        )
        assert_rejects(script, "latest", "latest")
        assert_rejects(script, "", "空字符串")
        assert_rejects(script, None, "未设置")
        assert_rejects(script, "v1.2.2", "SemVer tag")
        assert_rejects(script, "main-ABC1234", "大写 hex")
        assert_rejects(script, "main-abc123", "少于 7 位")
        assert_rejects(script, "main-abc1234-dirty", "后缀")

    workflow = require_snippets(
        DEPLOY_PRODUCTION_YML,
        [
            r"^main-[0-9a-f]{7,40}$",
            "Do not use latest",
        ],
    )
    if "default: latest" in workflow:
        fail("deploy-production.yml 不得再默认 latest")
    if "or latest" in workflow:
        fail("deploy-production.yml 描述不得再允许 latest")

    print("Immutable IMAGE_TAG 断言通过")


if __name__ == "__main__":
    main()
