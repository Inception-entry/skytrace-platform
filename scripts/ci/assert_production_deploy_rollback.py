#!/usr/bin/env python3
"""Fail if production deploy still rolls back only the broken service."""

from __future__ import annotations

import json
import os
import stat
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEPLOY_PRODUCTION = ROOT / "scripts" / "deploy-production.sh"
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

FAKE_DOCKER = """#!/usr/bin/env bash
set -u
echo "IMAGE_TAG=${IMAGE_TAG:-} args=$*" >> "${FAKE_DOCKER_LOG:?}"
if [[ " $* " == *" pull "* ]]; then
  exit 0
fi
if [[ " $* " == *" up "* ]]; then
  svc="${@: -1}"
  if [[ -n "${FAIL_SERVICE:-}" && "$svc" == "${FAIL_SERVICE}" && "${IMAGE_TAG:-}" == "${FAIL_IMAGE_TAG:-}" ]]; then
    exit 1
  fi
fi
exit 0
"""

FAKE_CURL = """#!/usr/bin/env bash
set -u
url=""
for arg in "$@"; do
  if [[ "$arg" == http://* || "$arg" == https://* ]]; then
    url="$arg"
  fi
done
once="${FAIL_HEALTH_ONCE_FILE:-}"
needle="${FAIL_HEALTH_SUBSTRING:-}"
if [[ -n "$once" && -f "$once" && -n "$needle" && "$url" == *"$needle"* ]]; then
  rm -f "$once"
  exit 1
fi
exit 0
"""


def sample_manifest(tag: str) -> str:
    return json.dumps(
        {"tag": tag, "images": {name: DIGEST for name in SERVICES}},
        indent=2,
    )


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def require_snippets(path: Path, snippets: list[str]) -> str:
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        if snippet not in text:
            fail(f"{path} 缺少：{snippet}")
    return text


def parse_up_order(log_text: str) -> list[tuple[str, str]]:
    events: list[tuple[str, str]] = []
    for line in log_text.splitlines():
        if " args=" not in line or " up " not in line:
            continue
        tag = line.split("IMAGE_TAG=", 1)[1].split(" args=", 1)[0]
        args = line.split(" args=", 1)[1]
        service = args.split()[-1]
        events.append((tag, service))
    return events


def run_deploy(
    tmp: Path,
    *,
    image_tag: str,
    prev_tag: str | None,
    fail_service: str | None = None,
    fail_health_substring: str | None = None,
) -> subprocess.CompletedProcess[str]:
    app_dir = tmp / "app"
    bin_dir = tmp / "bin"
    log_path = tmp / "docker.log"
    app_dir.mkdir()
    bin_dir.mkdir()
    (app_dir / "deploy").mkdir()
    (app_dir / "deploy" / ".env").write_text("", encoding="utf-8")
    if prev_tag is not None:
        (app_dir / ".current-image-tag").write_text(prev_tag + "\n", encoding="utf-8")
        (app_dir / ".current-release-manifest").write_text(
            sample_manifest(prev_tag),
            encoding="utf-8",
        )
    new_manifest = tmp / "release-manifest.json"
    new_manifest.write_text(sample_manifest(image_tag), encoding="utf-8")
    log_path.write_text("", encoding="utf-8")

    docker = bin_dir / "docker"
    curl = bin_dir / "curl"
    docker.write_text(FAKE_DOCKER, encoding="utf-8")
    curl.write_text(FAKE_CURL, encoding="utf-8")
    docker.chmod(docker.stat().st_mode | stat.S_IEXEC)
    curl.chmod(curl.stat().st_mode | stat.S_IEXEC)

    fail_once = tmp / "fail-health-once"
    env = {
        **os.environ,
        "PATH": f"{bin_dir}{os.pathsep}{os.environ.get('PATH', '')}",
        "APP_DIR": str(app_dir),
        "IMAGE_TAG": image_tag,
        "REGISTRY": "ghcr.io/test/skytrace",
        "RELEASE_MANIFEST": str(new_manifest),
        "SKYTRACE_DOMAIN": "prod.example.com",
        "FAKE_DOCKER_LOG": str(log_path),
        "HEALTH_ATTEMPTS": "1",
        "HEALTH_SLEEP_SECONDS": "0",
    }
    if fail_service:
        env["FAIL_SERVICE"] = fail_service
        env["FAIL_IMAGE_TAG"] = image_tag
    if fail_health_substring:
        fail_once.write_text("1", encoding="utf-8")
        env["FAIL_HEALTH_ONCE_FILE"] = str(fail_once)
        env["FAIL_HEALTH_SUBSTRING"] = fail_health_substring

    result = subprocess.run(
        ["bash", str(DEPLOY_PRODUCTION)],
        cwd=str(app_dir),
        env=env,
        capture_output=True,
        text=True,
        check=False,
    )
    result.log_text = log_path.read_text(encoding="utf-8")  # type: ignore[attr-defined]
    result.tag_file = app_dir / ".current-image-tag"  # type: ignore[attr-defined]
    return result


NEW_TAG = "main-abc1234"
OLD_TAG = "main-def5678"


def assert_failed_gateway_rolls_back_updated() -> None:
    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        result = run_deploy(
            tmp,
            image_tag=NEW_TAG,
            prev_tag=OLD_TAG,
            fail_service="gateway",
        )
        if result.returncode == 0:
            fail("gateway compose 失败时部署必须非 0\n" + result.stdout + result.stderr)
        events = parse_up_order(result.log_text)
        expected = [
            (NEW_TAG, "backend-ai"),
            (NEW_TAG, "backend-java"),
            (NEW_TAG, "backend-node"),
            (NEW_TAG, "gateway"),
            (OLD_TAG, "gateway"),
            (OLD_TAG, "backend-node"),
            (OLD_TAG, "backend-java"),
            (OLD_TAG, "backend-ai"),
        ]
        if events != expected:
            fail(f"回滚顺序不对：{events}\n期望：{expected}\nlog:\n{result.log_text}")
        tag = result.tag_file.read_text(encoding="utf-8").strip()
        if tag != OLD_TAG:
            fail(f"失败后不得改写 .current-image-tag，实际：{tag}")
        if "frontend" in {svc for _, svc in events}:
            fail("失败点之后的服务不应被更新")


def assert_health_failure_rolls_back_updated() -> None:
    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        result = run_deploy(
            tmp,
            image_tag=NEW_TAG,
            prev_tag=OLD_TAG,
            fail_health_substring=":8082/",
        )
        if result.returncode == 0:
            fail("gateway 健康检查失败时部署必须非 0\n" + result.stdout + result.stderr)
        events = parse_up_order(result.log_text)
        expected = [
            (NEW_TAG, "backend-ai"),
            (NEW_TAG, "backend-java"),
            (NEW_TAG, "backend-node"),
            (NEW_TAG, "gateway"),
            (OLD_TAG, "gateway"),
            (OLD_TAG, "backend-node"),
            (OLD_TAG, "backend-java"),
            (OLD_TAG, "backend-ai"),
        ]
        if events != expected:
            fail(f"健康检查失败回滚顺序不对：{events}\n期望：{expected}\nlog:\n{result.log_text}")


def assert_success_writes_tag() -> None:
    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        result = run_deploy(
            tmp,
            image_tag=NEW_TAG,
            prev_tag=OLD_TAG,
        )
        if result.returncode != 0:
            fail("全绿部署应成功\n" + result.stdout + result.stderr)
        tag = result.tag_file.read_text(encoding="utf-8").strip()
        if tag != NEW_TAG:
            fail(f"成功后应写入新 tag，实际：{tag}")
        manifest = result.tag_file.parent / ".current-release-manifest"
        if not manifest.exists():
            fail("成功后应写入 .current-release-manifest")
        body = json.loads(manifest.read_text(encoding="utf-8"))
        if body.get("tag") != NEW_TAG:
            fail(f"成功后 manifest.tag 应为新 tag，实际：{body}")
        events = parse_up_order(result.log_text)
        rolled = [svc for tag_name, svc in events if tag_name == OLD_TAG]
        if rolled:
            fail(f"成功路径不应回滚：{rolled}")


def assert_first_deploy_without_prev_does_not_invent_rollback() -> None:
    with tempfile.TemporaryDirectory() as raw:
        tmp = Path(raw)
        result = run_deploy(
            tmp,
            image_tag=NEW_TAG,
            prev_tag=None,
            fail_service="backend-java",
        )
        if result.returncode == 0:
            fail("无上一 tag 且中途失败时必须非 0")
        events = parse_up_order(result.log_text)
        if any(tag_name == OLD_TAG for tag_name, _ in events):
            fail(f"没有 .current-image-tag 时不应虚构回滚：{events}")
        if result.tag_file.exists():
            fail("首次部分失败不得创建 .current-image-tag")
        if "cannot unwind partial deploy" not in result.stderr:
            fail("无上一 tag 时应明确说明无法回滚\n" + result.stderr)


def main() -> None:
    text = require_snippets(
        DEPLOY_PRODUCTION,
        [
            "updated_services+=",
            "rollback_release",
            "Rolling back $service to ${PREV_TAG}",
            "release_manifest.py",
            "DIGEST_OVERLAY",
        ],
    )
    if "rollback_service \"$svc\"" in text and "updated_services" not in text:
        fail("生产脚本仍只回滚当前失败服务")
    assert_failed_gateway_rolls_back_updated()
    assert_health_failure_rolls_back_updated()
    assert_success_writes_tag()
    assert_first_deploy_without_prev_does_not_invent_rollback()
    print("Production whole-deploy rollback 断言通过")


if __name__ == "__main__":
    main()
