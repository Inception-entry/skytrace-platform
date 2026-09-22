#!/usr/bin/env bash
# Rolling production deploy: restart services one at a time,
# health-check each before proceeding. On failure, reverse-roll every
# service already updated in this release back to .current-image-tag.
#
# Required env vars (set by deploy-production.yml via SSH):
#   IMAGE_TAG, REGISTRY, SKYTRACE_DOMAIN
set -euo pipefail

SERVICES=(backend-ai backend-java backend-node gateway frontend admin-service admin-frontend)
updated_services=()
PREV_TAG=""
PREV_OVERLAY=""
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST_HELPER="${REPO_ROOT}/scripts/release_manifest.py"

require_domain() {
  if [[ -z "${SKYTRACE_DOMAIN:-}" ]]; then
    echo "SKYTRACE_DOMAIN is required (hostname like prod.example.com)" >&2
    exit 1
  fi
  if [[ "$SKYTRACE_DOMAIN" == *"://"* || "$SKYTRACE_DOMAIN" == *"/"* || "$SKYTRACE_DOMAIN" == *"*"* ]]; then
    echo "SKYTRACE_DOMAIN must be a hostname, not a URL or wildcard" >&2
    exit 1
  fi
  if [[ "$SKYTRACE_DOMAIN" == "localhost" || "$SKYTRACE_DOMAIN" == "127.0.0.1" ]]; then
    echo "SKYTRACE_DOMAIN cannot be localhost" >&2
    exit 1
  fi
}

require_image_tag() {
  if [[ -z "${IMAGE_TAG:-}" ]]; then
    echo "IMAGE_TAG is required (main-<git-sha>)" >&2
    exit 2
  fi
  if [[ ! "$IMAGE_TAG" =~ ^main-[0-9a-f]{7,40}$ ]]; then
    echo "IMAGE_TAG must be immutable main-<git-sha>" >&2
    exit 2
  fi
}

prepare_overlay() {
  local tag="$1"
  local overlay="$2"
  local manifest_out="$3"
  local source_manifest="${4:-}"
  if [[ -z "${REGISTRY:-}" ]]; then
    echo "REGISTRY is required to pin image digests" >&2
    exit 1
  fi
  if [[ -z "$source_manifest" ]]; then
    source_manifest="${RELEASE_MANIFEST:-}"
  fi
  if [[ -n "$source_manifest" ]]; then
    python3 "$MANIFEST_HELPER" validate --manifest "$source_manifest" --expect-tag "$tag"
    cp "$source_manifest" "$manifest_out"
  else
    python3 "$MANIFEST_HELPER" inspect --registry "$REGISTRY" --tag "$tag" --output "$manifest_out"
  fi
  python3 "$MANIFEST_HELPER" overlay \
    --registry "$REGISTRY" \
    --manifest "$manifest_out" \
    --output "$overlay" \
    --expect-tag "$tag"
}

compose() {
  # vision overlay forces AI_VISION_BACKEND=yolo26 on published images
  # (INSTALL_VISION=1 is baked at Publish time). Digest overlay pins
  # the seven app images to sha256 so a retagged IMAGE_TAG cannot drift.
  local files=(
    -f deploy/docker-compose.yml
    -f deploy/docker-compose.vision.yml
    -f deploy/docker-compose.staging.yml
    -f deploy/docker-compose.production.yml
  )
  if [[ -n "${DIGEST_OVERLAY:-}" && -f "${DIGEST_OVERLAY}" ]]; then
    files+=(-f "${DIGEST_OVERLAY}")
  fi
  docker compose \
    --env-file deploy/.env \
    "${files[@]}" \
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
  local attempts="${HEALTH_ATTEMPTS:-36}"
  local sleep_seconds="${HEALTH_SLEEP_SECONDS:-5}"
  [[ -z "$url" ]] && return 0
  local i
  for i in $(seq 1 "$attempts"); do
    if curl -sf "$url" >/dev/null 2>&1; then
      echo "  ✓ $service healthy"
      return 0
    fi
    if [[ "$i" -eq "$attempts" ]]; then
      break
    fi
    sleep "$sleep_seconds"
  done
  echo "  ✗ $service failed health check after $((attempts * sleep_seconds)) s"
  return 1
}

rollback_service() {
  local service="$1"
  echo "  Rolling back $service to ${PREV_TAG}..."
  if ! DIGEST_OVERLAY="${PREV_OVERLAY:-}" IMAGE_TAG="${PREV_TAG}" compose up -d --no-deps --no-build "$service"; then
    echo "  rollback compose failed for $service" >&2
    return 1
  fi
  if ! wait_healthy "$service"; then
    echo "  rollback health check failed for $service" >&2
    return 1
  fi
}

rollback_release() {
  local failed="$1"
  echo "=== Deploy failed at ${failed}; rolling back this release ==="
  if [[ -z "${PREV_TAG:-}" ]]; then
    echo "No previous IMAGE_TAG in .current-image-tag; cannot unwind partial deploy" >&2
    return 1
  fi
  local targets=("$failed")
  local i
  for ((i=${#updated_services[@]}-1; i>=0; i--)); do
    targets+=("${updated_services[$i]}")
  done
  echo "  Rollback order: ${targets[*]} -> ${PREV_TAG}"
  local failures=0
  local svc
  for svc in "${targets[@]}"; do
    if ! rollback_service "$svc"; then
      failures=$((failures + 1))
    fi
  done
  if [[ "$failures" -gt 0 ]]; then
    echo "Rollback incomplete: ${failures} service(s) failed" >&2
  fi
  return 1
}

main() {
  require_domain
  require_image_tag
  local app_dir="${APP_DIR:-/opt/skytrace}"
  cd "$app_dir"
  PREV_TAG="$(cat .current-image-tag 2>/dev/null || true)"
  PREV_OVERLAY=""
  updated_services=()

  if [[ -n "${PREV_TAG}" && -f .current-release-manifest ]]; then
    prepare_overlay \
      "${PREV_TAG}" \
      "${app_dir}/.previous-release-images.yml" \
      "${app_dir}/.previous-release-manifest" \
      "${app_dir}/.current-release-manifest"
    PREV_OVERLAY="${app_dir}/.previous-release-images.yml"
  fi

  prepare_overlay \
    "${IMAGE_TAG}" \
    "${app_dir}/.release-images.yml" \
    "${app_dir}/.release-manifest.json"
  DIGEST_OVERLAY="${app_dir}/.release-images.yml"

  echo "=== Production deploy: ${IMAGE_TAG} (digest pinned) ==="

  # Pull all new images first (fail-fast before touching any running container)
  compose pull

  local svc
  for svc in "${SERVICES[@]}"; do
    echo "--- Restarting $svc ---"
    if ! compose up -d --no-deps --no-build "$svc"; then
      echo "  docker compose up failed for $svc"
      rollback_release "$svc" || exit 1
    fi
    if ! wait_healthy "$svc"; then
      rollback_release "$svc" || exit 1
    fi
    updated_services+=("$svc")
  done

  echo "${IMAGE_TAG}" > .current-image-tag
  cp "${app_dir}/.release-manifest.json" .current-release-manifest
  echo "=== Production deploy complete: ${IMAGE_TAG} ==="
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  main "$@"
fi
