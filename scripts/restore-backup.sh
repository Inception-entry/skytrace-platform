#!/usr/bin/env bash
# Restore a MySQL backup from MinIO to the running MySQL container.
#
# Usage:
#   # List available backups:
#   bash scripts/restore-backup.sh --list
#
#   # Restore a specific backup:
#   RESTORE_FILE=uav_inspection/20240101T120000Z.sql.gz \
#   bash scripts/restore-backup.sh
set -euo pipefail

ENV_FILE="${ENV_FILE:-$(dirname "$0")/../deploy/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -o allexport
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +o allexport
fi

BACKUP_BUCKET="${BACKUP_BUCKET:-uav-backups}"
MC_ALIAS="uav-minio"
TMP_FILE="/tmp/uav-restore-$$.sql.gz"

mc alias set "${MC_ALIAS}" \
  "${MINIO_ENDPOINT}" \
  "${MINIO_ROOT_USER}" \
  "${MINIO_ROOT_PASSWORD}" \
  --quiet

if [[ "${1:-}" == "--list" ]]; then
  echo "Available backups in ${BACKUP_BUCKET}/${MYSQL_DATABASE}/:"
  mc ls "${MC_ALIAS}/${BACKUP_BUCKET}/${MYSQL_DATABASE}/"
  exit 0
fi

if [[ -z "${RESTORE_FILE:-}" ]]; then
  echo "Usage: RESTORE_FILE=<bucket-path/file.sql.gz> bash $0"
  echo "       bash $0 --list"
  exit 1
fi

echo "=== Downloading ${RESTORE_FILE} ==="
mc cp "${MC_ALIAS}/${BACKUP_BUCKET}/${RESTORE_FILE}" "${TMP_FILE}"

echo "=== Restoring to ${MYSQL_DATABASE} on ${MYSQL_HOST:-127.0.0.1}:${MYSQL_PORT:-3307} ==="
echo "WARNING: This will overwrite the current database. Ctrl-C within 5 seconds to abort."
sleep 5

zcat "${TMP_FILE}" | mysql \
  --host="${MYSQL_HOST:-127.0.0.1}" \
  --port="${MYSQL_PORT:-3307}" \
  --user=root \
  --password="${MYSQL_ROOT_PASSWORD}" \
  "${MYSQL_DATABASE}"

rm -f "${TMP_FILE}"
echo "=== Restore complete ==="
