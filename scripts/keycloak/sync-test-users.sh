#!/usr/bin/env bash

set -euo pipefail

KEYCLOAK_CONTAINER="${KEYCLOAK_CONTAINER:-skytrace-keycloak}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-uav}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-}"
KEYCLOAK_TEST_USER_PASSWORD="${KEYCLOAK_TEST_USER_PASSWORD:-}"

if [[ -z "$KEYCLOAK_ADMIN_USERNAME" \
  || -z "$KEYCLOAK_ADMIN_PASSWORD" \
  || -z "$KEYCLOAK_TEST_USER_PASSWORD" ]]; then
  echo "缺少 Keycloak 管理账号或测试用户密码"
  exit 1
fi

KCADM="/opt/keycloak/bin/kcadm.sh"
KCADM_CONFIG="/tmp/kcadm-uav-test-users.config"

cleanup() {
  docker exec "$KEYCLOAK_CONTAINER" \
    sh -c "rm -f '$KCADM_CONFIG'" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker exec "$KEYCLOAK_CONTAINER" "$KCADM" config credentials \
  --config "$KCADM_CONFIG" \
  --server http://127.0.0.1:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN_USERNAME" \
  --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1

kcadm() {
  docker exec "$KEYCLOAK_CONTAINER" "$KCADM" \
    "$@" \
    --config "$KCADM_CONFIG"
}

sync_user() {
  local username="$1"
  local role="$2"
  local last_name="$3"
  local user_id

  user_id="$(
    kcadm get users \
      -r "$KEYCLOAK_REALM" \
      -q exact=true \
      -q "username=$username" \
      --fields id \
      --format csv \
      --noquotes
  )"

  if [[ -z "$user_id" ]]; then
    kcadm create users \
      -r "$KEYCLOAK_REALM" \
      -s "username=$username" \
      -s enabled=true \
      -s "email=$username@local.test" \
      -s emailVerified=true \
      -s firstName=UAV \
      -s "lastName=$last_name" >/dev/null
    user_id="$(
      kcadm get users \
        -r "$KEYCLOAK_REALM" \
        -q exact=true \
        -q "username=$username" \
        --fields id \
        --format csv \
        --noquotes
    )"
  fi

  kcadm update "users/$user_id" \
    -r "$KEYCLOAK_REALM" \
    -s enabled=true \
    -s "email=$username@local.test" \
    -s emailVerified=true \
    -s firstName=UAV \
    -s "lastName=$last_name" >/dev/null

  kcadm set-password \
    -r "$KEYCLOAK_REALM" \
    --username "$username" \
    --new-password "$KEYCLOAK_TEST_USER_PASSWORD" >/dev/null

  for business_role in ADMIN OPERATOR VIEWER; do
    kcadm remove-roles \
      -r "$KEYCLOAK_REALM" \
      --uusername "$username" \
      --rolename "$business_role" >/dev/null 2>&1 || true
  done

  kcadm add-roles \
    -r "$KEYCLOAK_REALM" \
    --uusername "$username" \
    --rolename "$role" >/dev/null

  printf '已同步测试账号：%s -> %s\n' "$username" "$role"
}

sync_user "uav-admin" "ADMIN" "Administrator"
sync_user "uav-operator" "OPERATOR" "Operator"
sync_user "uav-viewer" "VIEWER" "Viewer"
