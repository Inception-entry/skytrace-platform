#!/usr/bin/env bash

set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-uav}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-uav-service}"
KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8082}"
TASK_CODE="${CI_ALARM_TASK_CODE:-ALARM-$(date +%s)-$RANDOM}"

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
  )

  if [[ -n "$body" ]]; then
    arguments+=(-H "Content-Type: application/json" --data "$body")
  fi
  curl "${arguments[@]}" "$GATEWAY_URL$path"
}

create_payload="$(
  printf '%s' \
    "{\"taskCode\":\"$TASK_CODE\"," \
    "\"taskName\":\"CI alarm evidence inspection\"," \
    "\"deviceCode\":\"UAV-CI-ALARM\"," \
    "\"planStartTime\":\"2030-01-01T08:00:00\"," \
    "\"planEndTime\":\"2030-01-01T09:00:00\"}"
)"
api_request POST "/api/inspection-tasks" "$create_payload" >/dev/null
api_request POST "/api/inspection-tasks/$TASK_CODE/start" '{}' >/dev/null
printf '巡检任务已启动：%s\n' "$TASK_CODE"

evidence_file="$(mktemp --suffix=.png)"
printf '\x89PNG\r\n\x1a\n' > "$evidence_file"
evidence_response="$(
  curl --fail --silent --show-error \
    -X POST \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -F "file=@${evidence_file};type=image/png" \
    -F "taskCode=${TASK_CODE}" \
    "$GATEWAY_URL/api/evidence"
)"
rm -f "$evidence_file"
object_key="$(json_value data.objectKey <<<"$evidence_response")"
if [[ -z "$object_key" ]]; then
  echo "证据上传失败：缺少 objectKey"
  exit 1
fi
printf '证据上传验收通过：%s\n' "$object_key"

detection_payload="$(
  printf '%s' \
    "{\"deviceCode\":\"UAV-CI-ALARM\"," \
    "\"taskCode\":\"$TASK_CODE\"," \
    "\"eventType\":\"WEAPON_DETECTED\"," \
    "\"weaponType\":\"KNIFE\"," \
    "\"confidence\":0.97," \
    "\"latitude\":31.2304," \
    "\"longitude\":121.4737," \
    "\"imageObjectKey\":\"$object_key\"," \
    "\"eventTime\":\"2030-01-01T08:15:00\"}"
)"
api_request POST "/api/alarms/detections" "$detection_payload" >/dev/null
printf '识别告警已投递到 RabbitMQ\n'

found=""
for ((attempt = 1; attempt <= 30; attempt++)); do
  latest="$(api_request GET "/api/alarms/latest")"
  found="$(
    python3 -c '
import json
import sys

payload = json.load(sys.stdin)
task = sys.argv[1]
object_key = sys.argv[2]
for item in payload.get("data", []):
    if item.get("taskCode") == task and item.get("imageUrl") == object_key:
        print(item.get("eventCode", ""))
        break
' "$TASK_CODE" "$object_key" <<<"$latest"
  )"
  if [[ -n "$found" ]]; then
    printf '告警落库验收通过：%s attempt=%s\n' "$found" "$attempt"
    break
  fi
  sleep 1
done
if [[ -z "$found" ]]; then
  echo "告警落库验收失败：未找到对应 task/imageObjectKey"
  exit 1
fi

signaled=""
for ((attempt = 1; attempt <= 20; attempt++)); do
  status_response="$(
    api_request GET "/api/inspection-tasks/$TASK_CODE/workflow-status"
  )"
  signaled="$(json_value data.lastAlarmEventCode <<<"$status_response")"
  if [[ "$signaled" == "$found" ]]; then
    printf 'Temporal Signal 验收通过：%s\n' "$signaled"
    break
  fi
  sleep 1
done
if [[ "$signaled" != "$found" ]]; then
  printf 'Temporal Signal 验收失败：期望 %s，实际 %s\n' \
    "$found" \
    "$signaled"
  exit 1
fi

api_request POST "/api/inspection-tasks/$TASK_CODE/complete" '{}' >/dev/null

printf '%s\n' \
  "告警/证据链路验收通过：Gateway -> Node -> Java/MinIO/RabbitMQ -> Temporal"
