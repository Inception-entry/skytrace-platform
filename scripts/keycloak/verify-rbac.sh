#!/usr/bin/env bash

set -euo pipefail

KEYCLOAK_CONTAINER="${KEYCLOAK_CONTAINER:-skytrace-keycloak}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-uav}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-uav-web}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-}"
KEYCLOAK_TEST_USER_PASSWORD="${KEYCLOAK_TEST_USER_PASSWORD:-}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8082}"
NODE_URL="${NODE_URL:-http://localhost:3000}"
JAVA_URL="${JAVA_URL:-http://localhost:8081}"

if [[ -z "$KEYCLOAK_ADMIN_USERNAME" \
  || -z "$KEYCLOAK_ADMIN_PASSWORD" \
  || -z "$KEYCLOAK_TEST_USER_PASSWORD" ]]; then
  echo "缺少 Keycloak 管理账号或测试用户密码"
  exit 1
fi

KCADM="/opt/keycloak/bin/kcadm.sh"
KCADM_CONFIG="/tmp/kcadm-uav-rbac-verify.config"
RESPONSE_FILE="/tmp/uav-rbac-verify-response.$$"
CLIENT_UUID=""
PREVIOUS_DIRECT_GRANT=""

cleanup() {
  if [[ -n "$CLIENT_UUID" && -n "$PREVIOUS_DIRECT_GRANT" ]]; then
    docker exec "$KEYCLOAK_CONTAINER" "$KCADM" \
      update "clients/$CLIENT_UUID" \
      -r "$KEYCLOAK_REALM" \
      -s "directAccessGrantsEnabled=$PREVIOUS_DIRECT_GRANT" \
      --config "$KCADM_CONFIG" >/dev/null 2>&1 || true
  fi
  docker exec "$KEYCLOAK_CONTAINER" \
    sh -c "rm -f '$KCADM_CONFIG'" >/dev/null 2>&1 || true
  rm -f "$RESPONSE_FILE"
}
trap cleanup EXIT

docker exec "$KEYCLOAK_CONTAINER" "$KCADM" config credentials \
  --config "$KCADM_CONFIG" \
  --server http://127.0.0.1:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN_USERNAME" \
  --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1

client_record="$(
  docker exec "$KEYCLOAK_CONTAINER" "$KCADM" get clients \
    -r "$KEYCLOAK_REALM" \
    -q "clientId=$KEYCLOAK_CLIENT_ID" \
    --fields id,directAccessGrantsEnabled \
    --format csv \
    --noquotes \
    --config "$KCADM_CONFIG"
)"
IFS=',' read -r CLIENT_UUID PREVIOUS_DIRECT_GRANT <<<"$client_record"

if [[ -z "$CLIENT_UUID" || -z "$PREVIOUS_DIRECT_GRANT" ]]; then
  echo "找不到 Keycloak 客户端：$KEYCLOAK_CLIENT_ID"
  exit 1
fi

docker exec "$KEYCLOAK_CONTAINER" "$KCADM" \
  update "clients/$CLIENT_UUID" \
  -r "$KEYCLOAK_REALM" \
  -s directAccessGrantsEnabled=true \
  --config "$KCADM_CONFIG" >/dev/null

access_token() {
  local username="$1"
  local response

  response="$(
    curl --fail --silent --show-error \
      -X POST \
      "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=password" \
      --data-urlencode "client_id=$KEYCLOAK_CLIENT_ID" \
      --data-urlencode "username=$username" \
      --data-urlencode "password=$KEYCLOAK_TEST_USER_PASSWORD"
  )"
  python3 -c '
import json
import sys

token = json.load(sys.stdin).get("access_token")
if not token:
    raise SystemExit("Keycloak 响应中没有 access_token")
print(token)
' <<<"$response"
}

assert_status() {
  local label="$1"
  local expected="$2"
  local method="$3"
  local url="$4"
  local token="$5"
  local actual

  actual="$(
    curl --silent --show-error \
      -o "$RESPONSE_FILE" \
      -w "%{http_code}" \
      -X "$method" \
      -H "Authorization: Bearer $token" \
      -H "Content-Type: application/json" \
      --data '{}' \
      "$url"
  )"

  if [[ "$actual" != "$expected" ]]; then
    printf '验收失败：%s，期望 HTTP %s，实际 HTTP %s\n' \
      "$label" \
      "$expected" \
      "$actual"
    exit 1
  fi
  printf '验收通过：%-34s HTTP %s\n' "$label" "$actual"
}

admin_token="$(access_token uav-admin)"
operator_token="$(access_token uav-operator)"
viewer_token="$(access_token uav-viewer)"

assert_status "ADMIN Gateway 管理中心" 200 GET \
  "$GATEWAY_URL/api/admin/overview" "$admin_token"
assert_status "ADMIN Node 管理中心" 200 GET \
  "$NODE_URL/api/admin/overview" "$admin_token"
assert_status "ADMIN Java 管理中心" 200 GET \
  "$JAVA_URL/api/admin/overview" "$admin_token"

assert_status "OPERATOR Gateway 任务读取" 200 GET \
  "$GATEWAY_URL/api/inspection-tasks" "$operator_token"
assert_status "OPERATOR Gateway 管理中心拒绝" 403 GET \
  "$GATEWAY_URL/api/admin/overview" "$operator_token"
assert_status "OPERATOR Node 管理中心拒绝" 403 GET \
  "$NODE_URL/api/admin/overview" "$operator_token"
assert_status "OPERATOR Java 管理中心拒绝" 403 GET \
  "$JAVA_URL/api/admin/overview" "$operator_token"

assert_status "VIEWER Gateway 任务读取" 200 GET \
  "$GATEWAY_URL/api/inspection-tasks" "$viewer_token"
assert_status "VIEWER Node 任务读取" 200 GET \
  "$NODE_URL/api/inspection-tasks" "$viewer_token"
assert_status "VIEWER Java 任务读取" 200 GET \
  "$JAVA_URL/api/inspection-tasks" "$viewer_token"
assert_status "VIEWER Gateway 写操作拒绝" 403 POST \
  "$GATEWAY_URL/api/inspection-tasks" "$viewer_token"
assert_status "VIEWER Node 写操作拒绝" 403 POST \
  "$NODE_URL/api/inspection-tasks" "$viewer_token"
assert_status "VIEWER Java 写操作拒绝" 403 POST \
  "$JAVA_URL/api/inspection-tasks" "$viewer_token"

printf '三角色端到端权限验收全部通过。\n'
