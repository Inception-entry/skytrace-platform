#!/usr/bin/env bash

set -euo pipefail

# 这份脚本通过真实 Gateway、Node、Java、Temporal 和 MinIO 验证归档链路。
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-skytrace}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-skytrace-service}"
KEYCLOAK_CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-${KEYCLOAK_SERVICE_CLIENT_SECRET:-}}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8082}"
TASK_CODE="${ARCHIVE_TASK_CODE:-ARCHIVE-$(date +%s)-$RANDOM}"

# 默认数据量适合开发机；上线前可以逐步提高文件数和单文件大小。
ARCHIVE_FILE_COUNT="${ARCHIVE_FILE_COUNT:-8}"
ARCHIVE_FILE_SIZE_MB="${ARCHIVE_FILE_SIZE_MB:-2}"
ARCHIVE_POLL_ATTEMPTS="${ARCHIVE_POLL_ATTEMPTS:-120}"
ARCHIVE_POLL_INTERVAL_SECONDS="${ARCHIVE_POLL_INTERVAL_SECONDS:-2}"

# 设置为大于 0 的秒数时，脚本会让 MinIO 短暂离线并验证 Temporal 重试恢复。
ARCHIVE_MINIO_OUTAGE_SECONDS="${ARCHIVE_MINIO_OUTAGE_SECONDS:-0}"
TEMPORAL_NAMESPACE="${TEMPORAL_NAMESPACE:-default}"
TEMPORAL_CONTAINER="${TEMPORAL_CONTAINER:-skytrace-temporal}"
MINIO_HEALTH_URL="${MINIO_HEALTH_URL:-http://localhost:9011/minio/health/live}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
COMPOSE_DIR="${COMPOSE_DIR:-$REPOSITORY_ROOT/deploy}"
COMPOSE_FILE="${COMPOSE_FILE:-$COMPOSE_DIR/docker-compose.yml}"

TEMP_DIRECTORY="$(mktemp -d)"
MINIO_STOPPED=false

compose_command() {
  local -a command=(
    docker compose
    --project-directory "$COMPOSE_DIR"
    -f "$COMPOSE_FILE"
  )
  if [[ -f "$COMPOSE_DIR/.env" ]]; then
    command+=(--env-file "$COMPOSE_DIR/.env")
  fi
  "${command[@]}" "$@"
}

restore_minio() {
  if [[ "$MINIO_STOPPED" == "true" ]]; then
    printf '恢复 MinIO，避免故障注入影响后续开发。\n'
    compose_command start minio >/dev/null
    MINIO_STOPPED=false
  fi
}

cleanup() {
  local exit_code=$?
  trap - EXIT
  restore_minio
  rm -rf "$TEMP_DIRECTORY"
  exit "$exit_code"
}
trap cleanup EXIT

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf '缺少运行命令：%s\n' "$1"
    exit 1
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    printf '%s 必须是正整数，当前值：%s\n' "$name" "$value"
    exit 1
  fi
}

require_non_negative_integer() {
  local name="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    printf '%s 必须是非负整数，当前值：%s\n' "$name" "$value"
    exit 1
  fi
}

require_command curl
require_command python3
require_positive_integer ARCHIVE_FILE_COUNT "$ARCHIVE_FILE_COUNT"
require_positive_integer ARCHIVE_FILE_SIZE_MB "$ARCHIVE_FILE_SIZE_MB"
require_positive_integer ARCHIVE_POLL_ATTEMPTS "$ARCHIVE_POLL_ATTEMPTS"
require_positive_integer ARCHIVE_POLL_INTERVAL_SECONDS "$ARCHIVE_POLL_INTERVAL_SECONDS"
require_non_negative_integer ARCHIVE_MINIO_OUTAGE_SECONDS "$ARCHIVE_MINIO_OUTAGE_SECONDS"

# 19 MB 是业务单文件上限；脚本限制到 18 MB，避免 multipart 边界超过 20 MB 请求上限。
if ((ARCHIVE_FILE_SIZE_MB > 18)); then
  printf 'ARCHIVE_FILE_SIZE_MB 最大为 18，当前值：%s\n' "$ARCHIVE_FILE_SIZE_MB"
  exit 1
fi

if ((ARCHIVE_MINIO_OUTAGE_SECONDS > 0)); then
  require_command docker
fi

json_value() {
  local path="$1"
  python3 -c '
import json
import sys

value = json.load(sys.stdin)
for part in sys.argv[1].split("."):
    value = value[part]
if value is not None:
    print(value)
' "$path"
}

if [[ -z "$KEYCLOAK_CLIENT_SECRET"
      && -n "$KEYCLOAK_ADMIN_USERNAME"
      && -n "$KEYCLOAK_ADMIN_PASSWORD" ]]; then
  # 本地旧环境可能没有把服务密钥写回 .env；此时用管理员凭据读取现有密钥。
  admin_token_response="$(
    curl --fail --silent --show-error \
      -X POST \
      "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=password" \
      --data-urlencode "client_id=admin-cli" \
      --data-urlencode "username=$KEYCLOAK_ADMIN_USERNAME" \
      --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD"
  )"
  admin_access_token="$(json_value access_token <<<"$admin_token_response")"
  client_response="$(
    curl --fail --silent --show-error \
      -H "Authorization: Bearer $admin_access_token" \
      "$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM/clients?clientId=$KEYCLOAK_CLIENT_ID"
  )"
  client_uuid="$(
    python3 -c '
import json
import sys

clients = json.load(sys.stdin)
if clients:
    print(clients[0]["id"])
' <<<"$client_response"
  )"
  if [[ -n "$client_uuid" ]]; then
    secret_response="$(
      curl --fail --silent --show-error \
        -H "Authorization: Bearer $admin_access_token" \
        "$KEYCLOAK_URL/admin/realms/$KEYCLOAK_REALM/clients/$client_uuid/client-secret"
    )"
    KEYCLOAK_CLIENT_SECRET="$(json_value value <<<"$secret_response")"
  fi
fi

if [[ -z "$KEYCLOAK_CLIENT_SECRET" ]]; then
  echo "缺少服务账号密钥；请设置 KEYCLOAK_CLIENT_SECRET"
  exit 1
fi

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
    "\"taskName\":\"Evidence archive load verification\"," \
    "\"deviceCode\":\"UAV-001\"," \
    "\"planStartTime\":\"2030-01-01T08:00:00\"," \
    "\"planEndTime\":\"2030-01-01T09:00:00\"}"
)"
api_request POST "/api/inspection-tasks" "$create_payload" >/dev/null
printf '已创建压测巡检任务：%s\n' "$TASK_CODE"

evidence_file="$TEMP_DIRECTORY/archive-load.png"
python3 - "$evidence_file" "$ARCHIVE_FILE_SIZE_MB" <<'PY'
import base64
import os
import pathlib
import sys

target = pathlib.Path(sys.argv[1])
size_bytes = int(sys.argv[2]) * 1024 * 1024
# 先写一个可解码的 1x1 PNG，再追加不可压缩负载，避免 ZIP 把大文件压成几 KB。
png = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
)
with target.open("wb") as output:
    output.write(png)
    remaining = size_bytes - len(png)
    while remaining > 0:
        chunk_size = min(1024 * 1024, remaining)
        output.write(os.urandom(chunk_size))
        remaining -= chunk_size
PY

upload_started_at="$(date +%s)"
for ((index = 1; index <= ARCHIVE_FILE_COUNT; index++)); do
  upload_response="$(
    curl --fail --silent --show-error \
      -X POST \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      -F "file=@${evidence_file};filename=archive-load-${index}.png;type=image/png" \
      -F "taskCode=${TASK_CODE}" \
      -F "deviceCode=UAV-001" \
      "$GATEWAY_URL/api/evidence"
  )"
  evidence_code="$(json_value data.evidenceCode <<<"$upload_response")"
  if [[ -z "$evidence_code" ]]; then
    printf '第 %s 个证据上传失败：缺少 evidenceCode\n' "$index"
    exit 1
  fi
  printf '\r上传证据：%s/%s' "$index" "$ARCHIVE_FILE_COUNT"
done
printf '\n上传完成：%s 个文件，单文件 %s MB，用时 %s 秒\n' \
  "$ARCHIVE_FILE_COUNT" \
  "$ARCHIVE_FILE_SIZE_MB" \
  "$(( $(date +%s) - upload_started_at ))"

if ((ARCHIVE_MINIO_OUTAGE_SECONDS > 0)); then
  printf '停止 MinIO %s 秒，开始 Temporal 失败恢复验证。\n' \
    "$ARCHIVE_MINIO_OUTAGE_SECONDS"
  compose_command stop minio >/dev/null
  MINIO_STOPPED=true
fi

archive_response="$(
  api_request POST "/api/evidence/archive-jobs" \
    "{\"scopeType\":\"TASK\",\"scopeValue\":\"$TASK_CODE\"}"
)"
JOB_CODE="$(json_value data.jobCode <<<"$archive_response")"
if [[ -z "$JOB_CODE" ]]; then
  echo "归档任务创建失败：缺少 jobCode"
  exit 1
fi
printf '已创建归档任务：%s\n' "$JOB_CODE"

if [[ "$MINIO_STOPPED" == "true" ]]; then
  sleep "$ARCHIVE_MINIO_OUTAGE_SECONDS"
  restore_minio
  for ((attempt = 1; attempt <= 30; attempt++)); do
    if curl --fail --silent "$MINIO_HEALTH_URL" >/dev/null 2>&1; then
      printf 'MinIO 已恢复，等待 Temporal 重试。\n'
      break
    fi
    if ((attempt == 30)); then
      echo "MinIO 恢复超时"
      exit 1
    fi
    sleep 1
  done
fi

archive_started_at="$(date +%s)"
job_response=""
for ((attempt = 1; attempt <= ARCHIVE_POLL_ATTEMPTS; attempt++)); do
  job_response="$(api_request GET "/api/evidence/archive-jobs/$JOB_CODE")"
  status="$(json_value data.status <<<"$job_response")"
  case "$status" in
    COMPLETED)
      printf '归档完成：attempt=%s，用时 %s 秒\n' \
        "$attempt" \
        "$(( $(date +%s) - archive_started_at ))"
      break
      ;;
    FAILED)
      error_message="$(json_value data.errorMessage <<<"$job_response")"
      printf '归档失败：%s\n' "$error_message"
      exit 1
      ;;
    PENDING|RUNNING)
      sleep "$ARCHIVE_POLL_INTERVAL_SECONDS"
      ;;
    *)
      printf '归档任务返回未知状态：%s\n' "$status"
      exit 1
      ;;
  esac
done

status="$(json_value data.status <<<"$job_response")"
if [[ "$status" != "COMPLETED" ]]; then
  printf '归档超时：最后状态为 %s\n' "$status"
  exit 1
fi

total_files="$(json_value data.totalFiles <<<"$job_response")"
package_hash="$(json_value data.packageContentHash <<<"$job_response")"
if [[ "$total_files" != "$ARCHIVE_FILE_COUNT" ]]; then
  printf '归档文件数不一致：期望 %s，实际 %s\n' \
    "$ARCHIVE_FILE_COUNT" \
    "$total_files"
  exit 1
fi
if [[ ! "$package_hash" =~ ^sha256:[0-9a-f]{64}$ ]]; then
  printf '归档包哈希格式错误：%s\n' "$package_hash"
  exit 1
fi

package_url_response="$(
  api_request POST "/api/evidence/archive-jobs/$JOB_CODE/download-url" '{}'
)"
manifest_url_response="$(
  api_request POST "/api/evidence/archive-jobs/$JOB_CODE/manifest-url" '{}'
)"
package_url="$(json_value data.url <<<"$package_url_response")"
manifest_url="$(json_value data.url <<<"$manifest_url_response")"

package_file="$TEMP_DIRECTORY/$JOB_CODE.zip"
manifest_file="$TEMP_DIRECTORY/manifest.json"
curl --fail --location --silent --show-error "$package_url" -o "$package_file"
curl --fail --location --silent --show-error "$manifest_url" -o "$manifest_file"
printf '预签名下载链接可用：ZIP=%s bytes，manifest=%s bytes\n' \
  "$(stat -c %s "$package_file")" \
  "$(stat -c %s "$manifest_file")"

python3 - "$package_file" "$manifest_file" "$ARCHIVE_FILE_COUNT" "$package_hash" <<'PY'
import hashlib
import json
import pathlib
import sys
import zipfile

package_path = pathlib.Path(sys.argv[1])
manifest_path = pathlib.Path(sys.argv[2])
expected_count = int(sys.argv[3])
expected_package_hash = sys.argv[4].removeprefix("sha256:")

actual_package_hash = hashlib.sha256(package_path.read_bytes()).hexdigest()
if actual_package_hash != expected_package_hash:
    raise SystemExit("下载 ZIP 的 SHA-256 与归档任务记录不一致")

external_manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
files = external_manifest.get("files", [])
if external_manifest.get("totalFiles") != expected_count or len(files) != expected_count:
    raise SystemExit("manifest 中的证据数量与压测输入不一致")

with zipfile.ZipFile(package_path) as archive:
    embedded_manifest = json.loads(archive.read("manifest.json"))
    if embedded_manifest != external_manifest:
        raise SystemExit("ZIP 内 manifest 与独立下载的 manifest 不一致")

    checksum_lines = archive.read("checksums.sha256").decode("utf-8").splitlines()
    checksums = {}
    for line in checksum_lines:
        checksum, archive_path = line.split("  ", 1)
        checksums[archive_path] = checksum

    expected_paths = {item["archivePath"] for item in files}
    if set(checksums) != expected_paths:
        raise SystemExit("checksums.sha256 与 manifest 文件集合不一致")

    for item in files:
        archive_path = item["archivePath"]
        actual_hash = hashlib.sha256(archive.read(archive_path)).hexdigest()
        manifest_hash = item["contentHash"].removeprefix("sha256:")
        if actual_hash != manifest_hash or actual_hash != checksums[archive_path]:
            raise SystemExit(f"证据哈希核验失败：{archive_path}")

print("ZIP、manifest、checksums 与逐文件 SHA-256 核验通过")
PY

if ((ARCHIVE_MINIO_OUTAGE_SECONDS > 0)); then
  history="$(
    docker exec "$TEMPORAL_CONTAINER" temporal workflow show \
      --address temporal:7233 \
      --namespace "$TEMPORAL_NAMESPACE" \
      --workflow-id "evidence-archive-$JOB_CODE" \
      --output json
  )"
  retry_evidence="$(
    python3 -c '
import json
import sys

payload = json.load(sys.stdin)
failed_events = 0
max_attempt = 1

def inspect(value):
    global failed_events, max_attempt
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "eventType" and child == "EVENT_TYPE_ACTIVITY_TASK_FAILED":
                failed_events += 1
            if key == "activityTaskStartedEventAttributes" and isinstance(child, dict):
                max_attempt = max(max_attempt, int(child.get("attempt", 1)))
            inspect(child)
    elif isinstance(value, list):
        for child in value:
            inspect(child)

inspect(payload)
print(f"{failed_events}:{max_attempt}")
' <<<"$history"
  )"
  IFS=: read -r retry_failures max_attempt <<<"$retry_evidence"
  if ((retry_failures < 1 && max_attempt < 2)); then
    echo "故障注入后 Activity attempt 仍为 1，无法证明 Temporal 发生过重试"
    exit 1
  fi
  printf 'Temporal 失败恢复通过：失败事件=%s，最大 Activity attempt=%s。\n' \
    "$retry_failures" \
    "$max_attempt"
fi

printf '%s\n' \
  "证据归档真实链路验收通过：Gateway -> Node -> Java -> Temporal -> MinIO -> Nginx 下载"
