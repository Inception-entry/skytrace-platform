#!/usr/bin/env python3
"""Fail if production Keycloak realm would import local/dev users."""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KEYCLOAK_DIR = ROOT / "deploy" / "keycloak"
PRODUCTION_REALM = KEYCLOAK_DIR / "skytrace-realm.json"
LOCAL_REALM = KEYCLOAK_DIR / "skytrace-realm.local.json"
BASE_COMPOSE = ROOT / "deploy" / "docker-compose.yml"
PRODUCTION_COMPOSE = ROOT / "deploy" / "docker-compose.production.yml"

DEV_USERNAMES = {
    "skytrace-admin",
    "skytrace-operator",
    "skytrace-viewer",
}
SERVICE_ACCOUNT = "service-account-skytrace-service"


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(1)


def load_realm(path: Path) -> dict:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        fail(f"无法读取 {path}: {exc}")


def usernames(realm: dict) -> set[str]:
    return {str(user.get("username", "")) for user in realm.get("users", [])}


def emails(realm: dict) -> list[str]:
    return [
        str(user.get("email", ""))
        for user in realm.get("users", [])
        if user.get("email")
    ]


def web_client(realm: dict, path: Path) -> dict:
    for client in realm.get("clients", []):
        if client.get("clientId") == "skytrace-web":
            return client
    fail(f"{path} 缺少 skytrace-web client")


def assert_web_origin_placeholders(client: dict, path: Path) -> None:
    redirects = [str(item) for item in client.get("redirectUris") or []]
    origins = [str(item) for item in client.get("webOrigins") or []]
    blob = " ".join(redirects + origins)
    if "${SKYTRACE_WEB_ORIGIN}" not in blob:
        fail(f"{path} skytrace-web 必须用 SKYTRACE_WEB_ORIGIN 占位，不能写死 localhost")
    if "${SKYTRACE_WEB_ORIGIN_LOOPBACK}" not in blob:
        fail(f"{path} skytrace-web 必须用 SKYTRACE_WEB_ORIGIN_LOOPBACK 占位")
    if any("*." in item for item in redirects + origins):
        fail(f"{path} 不得使用 https://*.example.com 这类主机通配")
    if any("localhost" in item and "${" not in item for item in redirects + origins):
        fail(f"{path} 不得硬编码 localhost redirect/webOrigin")


def dump_without_users(realm: dict) -> str:
    payload = dict(realm)
    payload.pop("users", None)
    return json.dumps(payload, sort_keys=True, ensure_ascii=True)


def main() -> None:
    production = load_realm(PRODUCTION_REALM)
    local = load_realm(LOCAL_REALM)

    production_users = usernames(production)
    local_users = usernames(local)

    if production_users != {SERVICE_ACCOUNT}:
        fail(
            "生产 realm 只能保留 service account，"
            f"实际用户：{sorted(production_users)}"
        )
    if DEV_USERNAMES & production_users:
        fail("生产 realm 仍包含开发用户名")
    if any(email.endswith("@local.test") for email in emails(production)):
        fail("生产 realm 仍包含 @local.test 邮箱")
    if "SKYTRACE_DEV_USER_PASSWORD" in PRODUCTION_REALM.read_text(
        encoding="utf-8"
    ):
        fail("生产 realm 仍引用 SKYTRACE_DEV_USER_PASSWORD")

    if not DEV_USERNAMES.issubset(local_users):
        fail(f"本地 realm 缺少开发用户：{sorted(DEV_USERNAMES - local_users)}")
    if SERVICE_ACCOUNT not in local_users:
        fail("本地 realm 缺少 service account")
    if dump_without_users(production) != dump_without_users(local):
        fail("本地/生产 realm 除 users 外不一致，避免只改其中一个")

    assert_web_origin_placeholders(web_client(production, PRODUCTION_REALM), PRODUCTION_REALM)
    assert_web_origin_placeholders(web_client(local, LOCAL_REALM), LOCAL_REALM)

    base_compose = BASE_COMPOSE.read_text(encoding="utf-8")
    production_compose = PRODUCTION_COMPOSE.read_text(encoding="utf-8")
    if "skytrace-realm.local.json" not in base_compose:
        fail("docker-compose.yml 必须挂载 skytrace-realm.local.json")
    if "skytrace-realm.local.json" in production_compose:
        fail("production overlay 不得挂载 skytrace-realm.local.json")
    if "./keycloak/skytrace-realm.json:/opt/keycloak/data/import/skytrace-realm.json" not in production_compose:
        fail("production overlay 必须把导入文件换成 skytrace-realm.json")

    guard = subprocess.run(
        [str(ROOT / "scripts" / "keycloak" / "sync-test-users.sh")],
        env={
            **os.environ,
            "KEYCLOAK_ADMIN_USERNAME": "ci-admin",
            "KEYCLOAK_ADMIN_PASSWORD": "ci-password",
            "KEYCLOAK_TEST_USER_PASSWORD": "ci-user",
        },
        check=False,
        capture_output=True,
        text=True,
    )
    output = guard.stdout + guard.stderr
    if guard.returncode == 0:
        fail("未设置 KEYCLOAK_ALLOW_TEST_USERS 时，sync-test-users.sh 必须拒绝")
    if "KEYCLOAK_ALLOW_TEST_USERS" not in output:
        fail("拒绝信息应提示设置 KEYCLOAK_ALLOW_TEST_USERS=true")

    print("Keycloak realm 拆分断言通过")


if __name__ == "__main__":
    main()
