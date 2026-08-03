#!/usr/bin/env bash
# Rolling production deploy: restart services one at a time,
# health-check each before proceeding; rollback entire service on failure.
#
# Required env vars (set by deploy-production.yml via SSH):
#   IMAGE_TAG, REGISTRY, UAV_DOMAIN
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/uav}"
cd "$APP_DIR"

PREV_TAG="$(cat .current-image-tag 2>/dev/null || true)"

compose() {
  # vision overlay forces AI_VISION_BACKEND=yolo26 on published images
  # (INSTALL_VISION=1 is baked at Publish time).
  docker compose \
    --env-file deploy/.env \
    -f deploy/docker-compose.yml \
    -f deploy/docker-compose.vision.yml \
    -f deploy/docker-compose.staging.yml \
    -f deploy/docker-compose.production.yml \
    "$@"
}

# Health endpoints indexed by service name
declare -A HEALTH_URL=(
  [backend-ai]="http://127.0.0.1:8000/health"
  [backend-java]="http://127.0.0.1:${JAVA_PORT:-8081}/api/actuator/health"
  [backend-node]="http://127.0.0.1:${NODE_PORT:-3000}/api/health"
  [gateway]="http://127.0.0.1:${GATEWAY_PORT:-8082}/actuator/health"
  [frontend]="http://127.0.0.1:${FRONTEND_PORT:-8888}/gateway-health"
  [admin-service]="http://127.0.0.1:${ADMIN_SERVICE_PORT:-3100}/admin-api/health"
  [admin-frontend]="http://127.0.0.1:${ADMIN_FRONTEND_PORT:-8889}/"
)

wait_healthy() {
  local service="$1"
  local url="${HEALTH_URL[$service]:-}"
  [[ -z "$url" ]] && return 0
  for i in $(seq 1 36); do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "  ✓ $service healthy"
      return 0
    fi
    sleep 5
  done
  echo "  ✗ $service failed health check after 180 s"
  return 1
}

rollback_service() {
  local service="$1"
  if [[ -n "${PREV_TAG:-}" ]]; then
    echo "  Rolling back $service to ${PREV_TAG}..."
    IMAGE_TAG="${PREV_TAG}" compose up -d --no-deps --no-build "$service" || true
  fi
}

echo "=== Production deploy: ${IMAGE_TAG} ==="

# Pull all new images first (fail-fast before touching any running container)
compose pull

# Rolling restart in dependency order
SERVICES=(backend-ai backend-java backend-node gateway frontend admin-service admin-frontend)

for svc in "${SERVICES[@]}"; do
  echo "--- Restarting $svc ---"
  if ! compose up -d --no-deps --no-build "$svc"; then
    echo "  docker compose up failed for $svc"
    rollback_service "$svc"
    exit 1
  fi
  if ! wait_healthy "$svc"; then
    rollback_service "$svc"
    exit 1
  fi
done

echo "${IMAGE_TAG}" > .current-image-tag
echo "=== Production deploy complete: ${IMAGE_TAG} ==="
