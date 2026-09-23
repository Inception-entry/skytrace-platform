#!/usr/bin/env bash
# Disable leftover skytrace-admin/operator/viewer accounts in an existing realm.
# Does not delete users. Refuses to run unless KEYCLOAK_DISABLE_DEV_USERS=true.
set -euo pipefail

if [[ "${KEYCLOAK_DISABLE_DEV_USERS:-}" != "true" ]]; then
  echo "未禁用开发账号。生产核对时设置 KEYCLOAK_DISABLE_DEV_USERS=true" >&2
  exit 1
fi

KEYCLOAK_CONTAINER="${KEYCLOAK_CONTAINER:-skytrace-keycloak}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-skytrace}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-}"
if [[ -z "$KEYCLOAK_ADMIN_USERNAME" || -z "$KEYCLOAK_ADMIN_PASSWORD" ]]; then
  echo "缺少 KEYCLOAK_ADMIN_USERNAME / KEYCLOAK_ADMIN_PASSWORD" >&2
  exit 1
fi

KCADM="/opt/keycloak/bin/kcadm.sh"
KCADM_CONFIG="/tmp/kcadm-skytrace-disable-dev.config"

cleanup() {
  docker exec "$KEYCLOAK_CONTAINER" sh -c "rm -f '$KCADM_CONFIG'" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker exec "$KEYCLOAK_CONTAINER" "$KCADM" config credentials \
  --config "$KCADM_CONFIG" \
  --server http://127.0.0.1:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN_USERNAME" \
  --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

kcadm() {
  docker exec "$KEYCLOAK_CONTAINER" "$KCADM" "$@" --config "$KCADM_CONFIG"
}

disable_user() {
  local username="$1"
  local user_id
  user_id="$(
    kcadm get users \
      -r "$KEYCLOAK_REALM" \
      -q exact=true \
      -q "username=${username}" \
      --fields id \
      --format csv \
      --noquotes || true
  )"
  if [[ -z "$user_id" ]]; then
    echo "不存在，跳过：${username}"
    return 0
  fi
  kcadm update "users/${user_id}" -r "$KEYCLOAK_REALM" -s enabled=false >/dev/null
  echo "已禁用：${username}"
}

disable_user "skytrace-admin"
disable_user "skytrace-operator"
disable_user "skytrace-viewer"
