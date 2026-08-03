#!/usr/bin/env bash

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-uav}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-uav-service}"
KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8082}"
TASK_CODE="${CI_TASK_CODE:-CI-$(date +%s)-$RANDOM}"

if [[ -z "$KEYCLOAK_CLIENT_SECRET" ]]; then
  echo "缺少 KEYCLOAK_CLIENT_SECRET"
  exit 1
fi

json_value() {
  local path="$1"
  python3 -c '
import json
import sys

value = json.load(sys.stdin)
for part in sys.argv[1].split("."):
    value = value[part]
print(value)
' "$path"
}

token_response="$(
  curl --fail --silent --show-error \
    -X POST \
    "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=client_credentials" \
    --data-urlencode "client_id=$KEYCLOAK_CLIENT_ID" \
    --data-urlencode "client_secret=$KEYCLOAK_CLIENT_SECRET"
)"
ACCESS_TOKEN="$(json_value access_token <<<"$token_response")"

api_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local -a arguments=(
    --fail
    --silent
    --show-error
    -X "$method"
    -H "Authorization: Bearer $ACCESS_TOKEN"
    -H "Content-Type: application/json"
  )

  if [[ -n "$body" ]]; then
    arguments+=(--data "$body")
  fi
  curl "${arguments[@]}" "$GATEWAY_URL$path"
}

poll_task_status() {
  local expected="$1"
  local attempts="${2:-30}"
  local response
  local actual=""

  for ((attempt = 1; attempt <= attempts; attempt++)); do
    response="$(api_request GET "/api/inspection-tasks/$TASK_CODE")"
    actual="$(json_value data.status <<<"$response")"
    if [[ "$actual" == "$expected" ]]; then
      printf '状态验收通过：%-12s attempt=%s\n' "$expected" "$attempt"
      return 0
    fi
    sleep 1
  done

  printf '状态验收失败：期望 %s，实际 %s\n' "$expected" "$actual"
  return 1
}

create_payload="$(
  printf '%s' \
    "{\"taskCode\":\"$TASK_CODE\"," \
    "\"taskName\":\"CI integration inspection\"," \
    "\"deviceCode\":\"UAV-001\"," \
    "\"planStartTime\":\"2030-01-01T08:00:00\"," \
    "\"planEndTime\":\"2030-01-01T09:00:00\"}"
)"
create_response="$(
  api_request POST "/api/inspection-tasks" "$create_payload"
)"
created_code="$(json_value data.taskCode <<<"$create_response")"
if [[ "$created_code" != "$TASK_CODE" ]]; then
  printf '任务创建验收失败：期望 %s，实际 %s\n' \
    "$TASK_CODE" \
    "$created_code"
  exit 1
fi
printf '任务创建验收通过：%s\n' "$TASK_CODE"

start_response="$(
  api_request POST "/api/inspection-tasks/$TASK_CODE/start" '{}'
)"
workflow_id="$(json_value data.workflowId <<<"$start_response")"
if [[ "$workflow_id" != "inspection-$TASK_CODE" ]]; then
  printf 'Temporal 工作流启动失败：%s\n' "$workflow_id"
  exit 1
fi
printf 'Temporal 工作流启动成功：%s\n' "$workflow_id"

poll_task_status RUNNING
api_request POST "/api/inspection-tasks/$TASK_CODE/complete" '{}' >/dev/null
poll_task_status COMPLETED

printf '%s\n' \
  "全链路验收通过：Gateway -> Node BFF -> Java -> Temporal -> MySQL"
