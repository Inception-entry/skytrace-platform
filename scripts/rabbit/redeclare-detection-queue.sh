#!/usr/bin/env bash
# Inspect skytrace.detection.alarms for DLX arguments.
# Without SKYTRACE_REDECLARE_DETECTION_QUEUE=true this only reports.
# With the flag, a mismatched queue is deleted so Java can recreate it.
# Deletion discards messages still in that queue.
set -euo pipefail

RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER:-}"
RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS:-}"
RABBITMQ_MANAGEMENT_PORT="${RABBITMQ_MANAGEMENT_PORT:-15672}"
QUEUE="skytrace.detection.alarms"
APPLY="${SKYTRACE_REDECLARE_DETECTION_QUEUE:-}"
if [[ -z "$RABBITMQ_DEFAULT_USER" || -z "$RABBITMQ_DEFAULT_PASS" ]]; then
  echo "缺少 RABBITMQ_DEFAULT_USER / RABBITMQ_DEFAULT_PASS" >&2
  exit 1
fi

url="http://127.0.0.1:${RABBITMQ_MANAGEMENT_PORT}/api/queues/%2F/${QUEUE}"
body="$(mktemp)"
trap 'rm -f "$body"' EXIT
status="$(curl -s -o "$body" -w '%{http_code}' -u "${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}" "$url" || true)"
if [[ "$status" == "404" ]]; then
  echo "队列不存在，Java 启动时会按死信参数创建：${QUEUE}"
  exit 0
fi
if [[ "$status" != "200" ]]; then
  echo "无法读取队列（HTTP ${status}）" >&2
  exit 1
fi

if python3 - "$body" <<'PY'
import json
import sys
from pathlib import Path
payload = json.loads(Path(sys.argv[1]).read_text())
args = payload.get("arguments") or {}
ok = (
    args.get("x-dead-letter-exchange") == "skytrace.detection.dlx"
    and args.get("x-dead-letter-routing-key") == "alarm.dlq"
)
raise SystemExit(0 if ok else 2)
PY
then
  echo "detection 队列已带死信参数"
  exit 0
fi

if [[ "$APPLY" != "true" ]]; then
  echo "队列 ${QUEUE} 没有死信参数。确认可以丢掉其中的消息后，设置 SKYTRACE_REDECLARE_DETECTION_QUEUE=true 再跑本脚本，然后重启 backend-java。" >&2
  exit 2
fi

curl -sf -u "${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}" -X DELETE "$url" >/dev/null
echo "已删除 ${QUEUE}。重启 backend-java 后会按死信参数重建。"
