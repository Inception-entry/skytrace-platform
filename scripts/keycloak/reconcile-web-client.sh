#!/usr/bin/env bash
# Point an already-imported skytrace-web client at https://$SKYTRACE_DOMAIN.
# --import-realm does not update an existing realm. Safe to re-run.
set -euo pipefail

if [[ -z "${SKYTRACE_DOMAIN:-}" ]]; then
  echo "需要 SKYTRACE_DOMAIN（主机名，不要带 https://）" >&2
  exit 1
fi
if [[ "$SKYTRACE_DOMAIN" == *"://"* || "$SKYTRACE_DOMAIN" == *"/"* || "$SKYTRACE_DOMAIN" == *"*"* ]]; then
  echo "SKYTRACE_DOMAIN 必须是主机名" >&2
  exit 1
fi

KEYCLOAK_CONTAINER="${KEYCLOAK_CONTAINER:-skytrace-keycloak}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-skytrace}"
KEYCLOAK_ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-}"
KEYCLOAK_ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-}"
if [[ -z "$KEYCLOAK_ADMIN_USERNAME" || -z "$KEYCLOAK_ADMIN_PASSWORD" ]]; then
  echo "缺少 KEYCLOAK_ADMIN_USERNAME / KEYCLOAK_ADMIN_PASSWORD" >&2
  exit 1
fi

ORIGIN="https://${SKYTRACE_DOMAIN}"
KCADM="/opt/keycloak/bin/kcadm.sh"
KCADM_CONFIG="/tmp/kcadm-skytrace-web-client.config"

cleanup() {
  docker exec "$KEYCLOAK_CONTAINER" sh -c "rm -f '$KCADM_CONFIG'" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker exec "$KEYCLOAK_CONTAINER" "$KCADM" config credentials \
  --config "$KCADM_CONFIG" \
  --server http://127.0.0.1:8080 \
  --realm master \
  --user "$KEYCLOAK_ADMIN_USERNAME" \
  --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null

kcadm() {
  docker exec "$KEYCLOAK_CONTAINER" "$KCADM" "$@" --config "$KCADM_CONFIG"
}

client_id="$(
  kcadm get clients \
    -r "$KEYCLOAK_REALM" \
    -q clientId=skytrace-web \
    --fields id \
    --format csv \
    --noquotes
)"
if [[ -z "$client_id" ]]; then
  echo "realm ${KEYCLOAK_REALM} 里没有 skytrace-web" >&2
  exit 1
fi

kcadm update "clients/${client_id}" \
  -r "$KEYCLOAK_REALM" \
  -s "redirectUris=[\"${ORIGIN}/*\"]" \
  -s "webOrigins=[\"${ORIGIN}\"]" \
  -s "attributes.\"post.logout.redirect.uris\"=\"${ORIGIN}/*\"" >/dev/null

echo "已把 skytrace-web redirect/webOrigin 对齐到 ${ORIGIN}"
