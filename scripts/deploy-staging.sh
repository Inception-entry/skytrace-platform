#!/usr/bin/env bash
# Deploy a versioned image set to the test environment.
# Called on the test server by the deploy-test.yml workflow via SSH.
#
# Required env vars (set by the CI workflow):
#   IMAGE_TAG  e.g. main-abc1234
#   REGISTRY   e.g. ghcr.io/owner/uav-java-node-architecture
#   UAV_DOMAIN e.g. test.example.com
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/uav}"
cd "$APP_DIR"

PREV_TAG="$(cat .current-image-tag 2>/dev/null || true)"

compose() {
  docker compose \
    --env-file deploy/.env \
    -f deploy/docker-compose.yml \
    -f deploy/docker-compose.staging.yml \
    "$@"
}

rollback() {
  if [[ -n "${PREV_TAG:-}" ]]; then
    echo "=== Health check failed — rolling back to ${PREV_TAG} ==="
    IMAGE_TAG="${PREV_TAG}" compose up -d --no-build --remove-orphans || true
  fi
}

trap rollback ERR

echo "=== Deploying ${IMAGE_TAG} ==="
compose pull
compose up -d --no-build --remove-orphans

# Health check via frontend Nginx /gateway-health (tests Nginx + gateway together)
echo "=== Waiting for stack to become healthy ==="
for i in $(seq 1 36); do
  if curl -sf "http://127.0.0.1:${FRONTEND_PORT:-8888}/gateway-health" >/dev/null 2>&1; then
    echo "Stack healthy."
    break
  fi
  if [[ "$i" -eq 36 ]]; then
    echo "Health check timed out after 180 s."
    exit 1
  fi
  sleep 5
done

echo "${IMAGE_TAG}" > .current-image-tag
trap - ERR
echo "=== Deployment complete: ${IMAGE_TAG} ==="
