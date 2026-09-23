#!/usr/bin/env bash
# Deploy a versioned image set to the test environment.
# Called on the test server by the deploy-test.yml workflow via SSH.
#
# Required env vars (set by the CI workflow):
#   IMAGE_TAG  e.g. main-abc1234
#   REGISTRY   e.g. ghcr.io/owner/skytrace-platform
#   SKYTRACE_DOMAIN e.g. test.example.com
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST_HELPER="${REPO_ROOT}/scripts/release_manifest.py"
PREV_OVERLAY=""
DIGEST_OVERLAY=""

if [[ -z "${SKYTRACE_DOMAIN:-}" ]]; then
  echo "SKYTRACE_DOMAIN is required (hostname like test.example.com)" >&2
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
if [[ -z "${IMAGE_TAG:-}" ]]; then
  echo "IMAGE_TAG is required (main-<git-sha>)" >&2
  exit 2
fi
if [[ ! "$IMAGE_TAG" =~ ^main-[0-9a-f]{7,40}$ ]]; then
  echo "IMAGE_TAG must be immutable main-<git-sha>" >&2
  exit 2
fi

APP_DIR="${APP_DIR:-/opt/skytrace}"
cd "$APP_DIR"

PREV_TAG="$(cat .current-image-tag 2>/dev/null || true)"

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
  # (INSTALL_VISION=1 is baked at Publish time).
  local files=(
    -f deploy/docker-compose.yml
    -f deploy/docker-compose.vision.yml
    -f deploy/docker-compose.staging.yml
  )
  if [[ -n "${DIGEST_OVERLAY:-}" && -f "${DIGEST_OVERLAY}" ]]; then
    files+=(-f "${DIGEST_OVERLAY}")
  fi
  docker compose \
    --env-file deploy/.env \
    "${files[@]}" \
    "$@"
}

rollback() {
  if [[ -n "${PREV_TAG:-}" ]]; then
    echo "=== Health check failed — rolling back to ${PREV_TAG} ==="
    DIGEST_OVERLAY="${PREV_OVERLAY:-}" IMAGE_TAG="${PREV_TAG}" compose up -d --no-build --remove-orphans || true
  fi
}

trap rollback ERR

if [[ -n "${PREV_TAG}" && -f .current-release-manifest ]]; then
  prepare_overlay \
    "${PREV_TAG}" \
    "${APP_DIR}/.previous-release-images.yml" \
    "${APP_DIR}/.previous-release-manifest" \
    "${APP_DIR}/.current-release-manifest"
  PREV_OVERLAY="${APP_DIR}/.previous-release-images.yml"
fi

prepare_overlay \
  "${IMAGE_TAG}" \
  "${APP_DIR}/.release-images.yml" \
  "${APP_DIR}/.release-manifest.json"
DIGEST_OVERLAY="${APP_DIR}/.release-images.yml"

echo "=== Deploying ${IMAGE_TAG} (digest pinned) ==="
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
cp "${APP_DIR}/.release-manifest.json" .current-release-manifest
trap - ERR

if [[ -f deploy/.env ]]; then
  set +e
  set -a
  # shellcheck disable=SC1091
  source deploy/.env
  set +a
  scripts/keycloak/reconcile-web-client.sh
  if [[ "$?" -ne 0 ]]; then
    echo "Keycloak redirect 未对齐。栈就绪后重跑 scripts/keycloak/reconcile-web-client.sh"
  fi
  set -e
fi

echo "=== Deployment complete: ${IMAGE_TAG} ==="
