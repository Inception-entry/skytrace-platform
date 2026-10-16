#!/usr/bin/env bash
# Render every legal Compose combination. A missing overlay or an unset
# variable that breaks interpolation fails here, before a deploy host.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ENV_FILE="${SKYTRACE_ENV_FILE:-$ROOT_DIR/deploy/.env.example}"
export SKYTRACE_DOMAIN="${SKYTRACE_DOMAIN:-ci.example.com}"
export REGISTRY="${REGISTRY:-ghcr.io/example/skytrace}"
export IMAGE_TAG="${IMAGE_TAG:-main-abcdef1}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "缺少环境文件：$ENV_FILE" >&2
  exit 1
fi

compose_config() {
  local name="$1"
  shift
  echo "compose config: ${name}"
  docker compose --env-file "$ENV_FILE" "$@" config --quiet
}

cd "$ROOT_DIR"

compose_config base \
  -f deploy/docker-compose.yml

compose_config mqtt \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mqtt.yml

compose_config mqtt-tls \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.mqtt.yml \
  -f deploy/docker-compose.mqtt-tls.yml

compose_config monitoring \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.monitoring.yml

compose_config staging \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.vision.yml \
  -f deploy/docker-compose.staging.yml

compose_config production \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.vision.yml \
  -f deploy/docker-compose.staging.yml \
  -f deploy/docker-compose.production.yml

echo "Compose overlay 配置通过"
