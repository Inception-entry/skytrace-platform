#!/usr/bin/env bash
# 公网入口蓝绿：拉起 frontend-<color>，健康后再改 Caddy upstream。
# 数据库和正在滚动的 API 不动。默认部署脚本不会调用本文件。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
# shellcheck disable=SC1091
[[ -f deploy/.env ]] && source deploy/.env

: "${IMAGE_TAG:?IMAGE_TAG is required}"
: "${REGISTRY:?REGISTRY is required}"
: "${SKYTRACE_DOMAIN:?SKYTRACE_DOMAIN is required}"

if [[ ! "$IMAGE_TAG" =~ ^main-[0-9a-f]{7,40}$ ]]; then
  echo "IMAGE_TAG must be immutable main-<git-sha>" >&2
  exit 2
fi

active="live"
if [[ -f .active-color ]]; then
  active="$(tr -d '[:space:]' < .active-color)"
fi
if [[ "$active" == "live" ]]; then
  next="blue"
else
  next="$(python3 scripts/bluegreen.py next --color "$active")"
fi

overlay="deploy/caddy/upstream.${next}.caddy"
stack="deploy/caddy/frontend-${next}.compose.yml"
python3 scripts/bluegreen.py stack --color "$next" --registry "$REGISTRY" --tag "$IMAGE_TAG" > "$stack"
python3 scripts/bluegreen.py upstream --color "$next" > "$overlay"

docker compose --env-file deploy/.env -f "$stack" up -d --no-build
port="${FRONTEND_COLOR_PORT:-8898}"
ok=0
for _ in $(seq 1 36); do
  if curl -sf "http://127.0.0.1:${port}/gateway-health" >/dev/null; then
    ok=1
    break
  fi
  sleep 5
done
if [[ "$ok" != 1 ]]; then
  echo "frontend-${next} 未通过健康检查，不切换 Caddy" >&2
  exit 1
fi

cp "$overlay" deploy/caddy/upstream.caddy
docker exec skytrace-caddy caddy reload --config /etc/caddy/Caddyfile
printf '%s\n' "$next" > .active-color
echo "Caddy 已切到 frontend-${next}"
