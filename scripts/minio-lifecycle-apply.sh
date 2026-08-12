#!/usr/bin/env bash
# Apply recommended MinIO lifecycle / versioning for SkyTrace buckets.
#
# - skytrace-backups: expire objects older than BACKUP_RETAIN_DAYS
# - skytrace-evidence: enable versioning (no auto-delete ILM)
#
# Usage:
#   ./scripts/minio-lifecycle-apply.sh
#
# Requires: mc (MinIO Client), deploy/.env
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -o allexport
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +o allexport
fi

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://127.0.0.1:${MINIO_API_PORT:-9011}}"
BACKUP_BUCKET="${BACKUP_BUCKET:-skytrace-backups}"
EVIDENCE_BUCKET="${MINIO_EVIDENCE_BUCKET:-skytrace-evidence}"
BACKUP_RETAIN_DAYS="${BACKUP_RETAIN_DAYS:-7}"
MC_ALIAS="skytrace-minio"
TMP_RULE="$(mktemp)"

cleanup() { rm -f "$TMP_RULE"; }
trap cleanup EXIT

if ! command -v mc >/dev/null 2>&1; then
  echo "需要本机安装 MinIO Client (mc)：https://min.io/docs/minio/linux/reference/minio-mc.html"
  exit 1
fi

mc alias set "${MC_ALIAS}" \
  "${MINIO_ENDPOINT}" \
  "${MINIO_ROOT_USER:?Set MINIO_ROOT_USER}" \
  "${MINIO_ROOT_PASSWORD:?Set MINIO_ROOT_PASSWORD}" \
  --quiet

mc mb --ignore-existing "${MC_ALIAS}/${BACKUP_BUCKET}"
mc mb --ignore-existing "${MC_ALIAS}/${EVIDENCE_BUCKET}"

# Backup bucket: expire after retain days (ILM)
cat > "${TMP_RULE}" <<EOF
{
  "Rules": [
    {
      "ID": "skytrace-backup-expire",
      "Status": "Enabled",
      "Filter": { "Prefix": "" },
      "Expiration": { "Days": ${BACKUP_RETAIN_DAYS} }
    }
  ]
}
EOF

echo "=== Apply ILM on ${BACKUP_BUCKET} (expire ${BACKUP_RETAIN_DAYS}d) ==="
mc ilm import "${MC_ALIAS}/${BACKUP_BUCKET}" < "${TMP_RULE}"

echo "=== Enable versioning on ${EVIDENCE_BUCKET} ==="
mc version enable "${MC_ALIAS}/${EVIDENCE_BUCKET}" || true

echo "=== Bucket usage ==="
mc du "${MC_ALIAS}/${BACKUP_BUCKET}" || true
mc du "${MC_ALIAS}/${EVIDENCE_BUCKET}" || true

echo "=== minio-lifecycle-apply complete ==="
echo "注意：证据桶不做按天自动删除；物理清理走 EvidenceCleanup / retention-policy。"
