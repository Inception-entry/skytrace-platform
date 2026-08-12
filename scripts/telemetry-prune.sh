#!/usr/bin/env bash
# 清理过期任务遥测点（device_telemetry_point）。默认 dry-run。
#
# Usage:
#   ./scripts/telemetry-prune.sh              # 预览将删行数
#   ./scripts/telemetry-prune.sh --apply      # 真正删除
#
# Env:
#   TELEMETRY_RETAIN_DAYS (default 90)
#   MYSQL_* 同 mysql-backup.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -o allexport
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +o allexport
fi

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
TELEMETRY_RETAIN_DAYS="${TELEMETRY_RETAIN_DAYS:-90}"
APPLY=0
if [[ "${1:-}" == "--apply" ]]; then
  APPLY=1
fi

mysql_cmd=(
  mysql
  --host="${MYSQL_HOST}"
  --port="${MYSQL_PORT}"
  --user=root
  --password="${MYSQL_ROOT_PASSWORD:?Set MYSQL_ROOT_PASSWORD}"
  --batch --skip-column-names
  "${MYSQL_DATABASE:?Set MYSQL_DATABASE}"
)

COUNT_SQL="SELECT COUNT(*) FROM device_telemetry_point
  WHERE recorded_at < (UTC_TIMESTAMP() - INTERVAL ${TELEMETRY_RETAIN_DAYS} DAY);"

count="$("${mysql_cmd[@]}" -e "${COUNT_SQL}" | tr -d '[:space:]')"
echo "过期遥测点（>${TELEMETRY_RETAIN_DAYS} 天）: ${count}"

if [[ "$APPLY" -ne 1 ]]; then
  echo "dry-run。确认后执行: $0 --apply"
  exit 0
fi

if [[ "${count}" == "0" ]]; then
  echo "无需删除"
  exit 0
fi

DELETE_SQL="DELETE FROM device_telemetry_point
  WHERE recorded_at < (UTC_TIMESTAMP() - INTERVAL ${TELEMETRY_RETAIN_DAYS} DAY);"
"${mysql_cmd[@]}" -e "${DELETE_SQL}"
echo "已删除 ${count} 行"
