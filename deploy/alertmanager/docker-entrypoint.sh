#!/bin/sh
set -eu

TEMPLATE="/etc/alertmanager/alertmanager.yml.template"
OUTPUT="/tmp/alertmanager.yml"
WEBHOOK_URL="${ALERTMANAGER_WEBHOOK_URL:-http://127.0.0.1:9/webhook-placeholder}"

sed "s|__ALERTMANAGER_WEBHOOK_URL__|${WEBHOOK_URL}|g" "$TEMPLATE" > "$OUTPUT"
exec /bin/alertmanager \
  --config.file="$OUTPUT" \
  --storage.path=/alertmanager
