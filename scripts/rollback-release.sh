#!/usr/bin/env bash
# Roll the last successful app release back to the previous image tag.
#
# The seven application images move together. MySQL does not. This script
# reads the release record written by deploy-production.sh / deploy-staging.sh
# and chooses one path:
#   images  — previous code can run on the current Flyway version
#   restore — schema moved past imageRollbackSafeThrough; restore the backup
#             named in the record before changing images
#
# Usage (on the deploy host, same env as the deploy scripts):
#   scripts/rollback-release.sh --plan production
#   IMAGE_TAG is taken from the record. REGISTRY and SKYTRACE_DOMAIN are required
#   to actually roll images:
#   scripts/rollback-release.sh production
#   scripts/rollback-release.sh staging
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_DIR="${APP_DIR:-/opt/skytrace}"
GATE="${ROOT_DIR}/scripts/schema_rollback_gate.py"
PLAN_ONLY=0

if [[ "${1:-}" == "--plan" ]]; then
  PLAN_ONLY=1
  shift
fi

STACK="${1:-production}"
if [[ "$STACK" != "production" && "$STACK" != "staging" ]]; then
  echo "用法：scripts/rollback-release.sh [--plan] production|staging" >&2
  exit 2
fi

RECORD="${APP_DIR}/.current-release-record"
if [[ ! -f "$RECORD" ]]; then
  cat >&2 <<'EOF'
没有发布回退单（.current-release-record）。
应用镜像按七个服务一起退；MySQL 不会跟着退。
在发布机上成功执行 deploy-production.sh 或 deploy-staging.sh 之后，
回退单会记下上一镜像 tag、旧代码还能跑的 Flyway 版本，以及可选的发布前备份。
破坏性迁移（删列、改类型）要把 deploy/schema-compat.json 的
imageRollbackSafeThrough 停在旧代码仍能读写的版本。
EOF
  exit 2
fi

read_field() {
  python3 -c '
import json, sys
doc = json.load(open(sys.argv[1], encoding="utf-8"))
value = doc.get(sys.argv[2], "")
if value is None:
    value = ""
if isinstance(value, list):
    print(" ".join(str(item) for item in value))
else:
    print(value)
' "$RECORD" "$1"
}

CURRENT_TAG="$(read_field tag)"
PREVIOUS_TAG="$(read_field previousTag)"
SAFE_THROUGH="$(read_field imageRollbackSafeThrough)"
BACKUP_OBJECT="$(read_field backupObject)"
RECORDED_STACK="$(read_field stack)"
COMPOSE_FILES="$(read_field composeFiles)"
OUTSIDE_RELEASE="$(read_field outsideRelease)"

if [[ -z "$PREVIOUS_TAG" ]]; then
  echo "回退单 ${CURRENT_TAG} 没有上一镜像 tag，不能回退。" >&2
  exit 2
fi
if [[ -n "$RECORDED_STACK" && "$RECORDED_STACK" != "$STACK" ]]; then
  echo "回退单记录的是 ${RECORDED_STACK}，不能用 ${STACK} 的发布脚本退。" >&2
  exit 2
fi

db_version=""
if [[ -n "${SCHEMA_DB_VERSION:-}" ]]; then
  db_version="${SCHEMA_DB_VERSION}"
elif docker inspect skytrace-mysql >/dev/null 2>&1; then
  env_file="${APP_DIR}/deploy/.env"
  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
  db_name="${MYSQL_DATABASE:-skytrace_inspection}"
  db_version="$(docker exec skytrace-mysql \
    mysql -N -uroot -p"${MYSQL_ROOT_PASSWORD:-}" \
    -e "SELECT IFNULL(MAX(version+0), 0) FROM \`${db_name}\`.flyway_schema_history WHERE success=1" \
    2>/dev/null || true)"
  db_version="${db_version//$'\r'/}"
fi

decide_args=(--safe-through "$SAFE_THROUGH")
if [[ "$db_version" =~ ^[0-9]+$ && "$db_version" != "0" ]]; then
  decide_args+=(--db-version "$db_version")
fi

set +e
outcome="$(python3 "$GATE" decide "${decide_args[@]}")"
gate_status=$?
set -e

echo "当前镜像：${CURRENT_TAG}"
echo "回退目标：${PREVIOUS_TAG}"
echo "旧代码可接受的最高 Flyway 版本：${SAFE_THROUGH}"
if [[ -n "$db_version" ]]; then
  echo "当前库版本：${db_version}"
else
  echo "当前库版本：未知"
fi
if [[ -n "$BACKUP_OBJECT" ]]; then
  echo "发布前备份：${BACKUP_OBJECT}"
fi
if [[ -n "$COMPOSE_FILES" ]]; then
  echo "这次发布的 Compose：${COMPOSE_FILES}"
fi
if [[ -n "$OUTSIDE_RELEASE" ]]; then
  echo "不在这次回退里：${OUTSIDE_RELEASE}"
fi

if [[ "$gate_status" -eq 10 || "$outcome" == "restore" ]]; then
  echo "库版本已经超过旧代码能跑的范围。先恢复上面的 MySQL 备份，再退镜像。" >&2
  if [[ -z "$BACKUP_OBJECT" ]]; then
    echo "这张回退单没有绑定备份。用发布前的 mysqldump 恢复，或确认迁移仍兼容后设置 SCHEMA_DB_VERSION。" >&2
  fi
  exit 10
fi

if [[ "$gate_status" -eq 11 || "$outcome" == "unknown" ]]; then
  echo "读不到 Flyway 版本，不能判断只退镜像是否安全。" >&2
  echo "在发布机上导出版本后重跑：SCHEMA_DB_VERSION=<版本> scripts/rollback-release.sh ${STACK}" >&2
  exit 11
fi

if [[ "$outcome" != "images" ]]; then
  echo "无法识别的回退判断：${outcome}" >&2
  exit 2
fi

echo "可以只退七个应用镜像，不必恢复数据库。"
if [[ "$PLAN_ONLY" -eq 1 ]]; then
  exit 0
fi

export IMAGE_TAG="${PREVIOUS_TAG}"
if [[ "$STACK" == "production" ]]; then
  exec bash "${ROOT_DIR}/scripts/deploy-production.sh"
fi
exec bash "${ROOT_DIR}/scripts/deploy-staging.sh"
