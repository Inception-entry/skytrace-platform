#!/usr/bin/env bash
# MySQL backup: dump → gzip → upload to MinIO → prune old backups.
#
# Usage (run from the repo root or /opt/uav):
#   bash scripts/mysql-backup.sh
#
# Required env vars (or set in deploy/.env):
#   MYSQL_ROOT_PASSWORD, MYSQL_DATABASE, MYSQL_HOST (default: 127.0.0.1),
#   MYSQL_PORT (default: 3307),
#   MINIO_ENDPOINT, MINIO_ROOT_USER, MINIO_ROOT_PASSWORD,
#   BACKUP_BUCKET (default: uav-backups), BACKUP_RETAIN_DAYS (default: 7)
set -euo pipefail

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3307}"
BACKUP_BUCKET="${BACKUP_BUCKET:-uav-backups}"
BACKUP_RETAIN_DAYS="${BACKUP_RETAIN_DAYS:-7}"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="/tmp/skytrace-mysql-${TIMESTAMP}.sql.gz"
OBJECT_PATH="${MYSQL_DATABASE}/${TIMESTAMP}.sql.gz"

# Load .env if present and vars are not already set
ENV_FILE="${ENV_FILE:-$(dirname "$0")/../deploy/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -o allexport
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +o allexport
fi

echo "=== UAV MySQL backup: ${TIMESTAMP} ==="

# 1. Dump
mysqldump \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT}" \
  --user=root \
  --password="${MYSQL_ROOT_PASSWORD}" \
  --single-transaction \
  --routines \
  --triggers \
  "${MYSQL_DATABASE}" \
  | gzip > "${BACKUP_FILE}"

echo "Dump complete: $(du -sh "${BACKUP_FILE}" | cut -f1)"

# 2. Upload via MinIO Client (mc)
MC_ALIAS="skytrace-minio"
mc alias set "${MC_ALIAS}" \
  "${MINIO_ENDPOINT}" \
  "${MINIO_ROOT_USER}" \
  "${MINIO_ROOT_PASSWORD}" \
  --quiet

mc mb --ignore-existing "${MC_ALIAS}/${BACKUP_BUCKET}"

mc cp "${BACKUP_FILE}" "${MC_ALIAS}/${BACKUP_BUCKET}/${OBJECT_PATH}"
echo "Uploaded to MinIO: ${BACKUP_BUCKET}/${OBJECT_PATH}"

# 3. Remove local temp file
rm -f "${BACKUP_FILE}"

# 4. Prune backups older than BACKUP_RETAIN_DAYS
echo "Pruning backups older than ${BACKUP_RETAIN_DAYS} days..."
mc find "${MC_ALIAS}/${BACKUP_BUCKET}/${MYSQL_DATABASE}/" \
  --older-than "${BACKUP_RETAIN_DAYS}d24h" \
  --exec "mc rm {}" || true

echo "=== Backup complete ==="
