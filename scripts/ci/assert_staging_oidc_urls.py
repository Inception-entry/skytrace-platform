#!/usr/bin/env python3
"""Fail if staging/production OIDC URLs are not derived from SKYTRACE_DOMAIN."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STAGING_COMPOSE = ROOT / "deploy" / "docker-compose.staging.yml"
PRODUCTION_COMPOSE = ROOT / "deploy" / "docker-compose.production.yml"
BASE_COMPOSE = ROOT / "deploy" / "docker-compose.yml"
DEPLOY_STAGING = ROOT / "scripts" / "deploy-staging.sh"
DEPLOY_PRODUCTION = ROOT / "scripts" / "deploy-production.sh"
ENV_EXAMPLE = ROOT / "deploy" / ".env.example"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def require_snippets(path: Path, snippets: list[str]) -> str:
    text = path.read_text(encoding="utf-8")
    for snippet in snippets:
        if snippet not in text:
            fail(f"{path} 缺少：{snippet}")
    return text


def main() -> None:
    staging = require_snippets(
        STAGING_COMPOSE,
        [
            "KEYCLOAK_PUBLIC_URL: https://${SKYTRACE_DOMAIN}",
            "GATEWAY_ALLOWED_ORIGIN: https://${SKYTRACE_DOMAIN}",
            "GATEWAY_JWT_ISSUER_URI: https://${SKYTRACE_DOMAIN}/realms/skytrace",
            "AUTH_JWT_ISSUER_URI: https://${SKYTRACE_DOMAIN}/realms/skytrace",
            "WS_ALLOWED_ORIGIN: https://${SKYTRACE_DOMAIN}",
            "SKYTRACE_WEB_ORIGIN: https://${SKYTRACE_DOMAIN}",
            "SKYTRACE_WEB_ORIGIN_LOOPBACK: https://${SKYTRACE_DOMAIN}",
        ],
    )
    production = require_snippets(
        PRODUCTION_COMPOSE,
        [
            "SKYTRACE_WEB_ORIGIN: https://${SKYTRACE_DOMAIN}",
            "SKYTRACE_WEB_ORIGIN_LOOPBACK: https://${SKYTRACE_DOMAIN}",
        ],
    )
    base = require_snippets(
        BASE_COMPOSE,
        [
            "SKYTRACE_WEB_ORIGIN: ${SKYTRACE_WEB_ORIGIN:-http://localhost:8888}",
            "SKYTRACE_WEB_ORIGIN_LOOPBACK: ${SKYTRACE_WEB_ORIGIN_LOOPBACK:-http://127.0.0.1:8888}",
        ],
    )
    for text, path in (
        (staging, STAGING_COMPOSE),
        (production, PRODUCTION_COMPOSE),
        (base, BASE_COMPOSE),
    ):
        if "https://*." in text or "http://*." in text:
            fail(f"{path} 不得使用主机通配 redirect")

    for script in (DEPLOY_STAGING, DEPLOY_PRODUCTION):
        text = script.read_text(encoding="utf-8")
        if 'SKYTRACE_DOMAIN:-' in text and 'if [[ -z "${SKYTRACE_DOMAIN:-}" ]]' not in text:
            fail(f"{script} 必须在部署前拒绝空的 SKYTRACE_DOMAIN")
        if 'if [[ -z "${SKYTRACE_DOMAIN:-}" ]]' not in text:
            fail(f"{script} 必须检查 SKYTRACE_DOMAIN")
        if "localhost" not in text:
            fail(f"{script} 必须拒绝 localhost 域名")

    env_example = ENV_EXAMPLE.read_text(encoding="utf-8")
    if "KEYCLOAK_PUBLIC_URL=https://" in env_example:
        fail("本地 .env.example 不应把 KEYCLOAK_PUBLIC_URL 写成 https 公网地址")

    docker = shutil.which("docker")
    if docker:
        domain = "oidc.test.example"
        env = {
            **os.environ,
            "SKYTRACE_DOMAIN": domain,
            "IMAGE_TAG": "ci-oidc",
            "REGISTRY": "example.invalid/skytrace",
        }
        result = subprocess.run(
            [
                docker,
                "compose",
                "--env-file",
                str(ENV_EXAMPLE),
                "-f",
                str(ROOT / "deploy" / "docker-compose.yml"),
                "-f",
                str(ROOT / "deploy" / "docker-compose.vision.yml"),
                "-f",
                str(STAGING_COMPOSE),
                "config",
            ],
            check=False,
            capture_output=True,
            text=True,
            cwd=str(ROOT),
            env=env,
        )
        if result.returncode != 0:
            fail(
                "staging compose config 失败：\n"
                + (result.stderr or result.stdout)
            )
        rendered = result.stdout
        expected = [
            f"https://{domain}",
            f"https://{domain}/realms/skytrace",
        ]
        for snippet in expected:
            if snippet not in rendered:
                fail(f"staging compose config 未展开 {snippet}")
        for key in (
            "KEYCLOAK_PUBLIC_URL",
            "GATEWAY_ALLOWED_ORIGIN",
            "GATEWAY_JWT_ISSUER_URI",
            "AUTH_JWT_ISSUER_URI",
            "WS_ALLOWED_ORIGIN",
            "SKYTRACE_WEB_ORIGIN",
        ):
            if f"{key}: https://{domain}" not in rendered and f"{key}: \"https://{domain}" not in rendered:
                fail(f"staging compose config 未给 {key} 展开 https://{domain}")
        if "GATEWAY_JWT_JWK_SET_URI: https://" in rendered:
            fail("内部 JWKS 不应被改成公网 URL")

    print("Staging OIDC URL 断言通过")


if __name__ == "__main__":
    main()
