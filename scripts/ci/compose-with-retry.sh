#!/usr/bin/env bash
# Retry docker compose when Docker Hub / registry TLS and TCP flakes.
# Image-not-found, denied, and compose file errors exit immediately.

set -uo pipefail

max="${COMPOSE_RETRY_MAX:-5}"
log="${RUNNER_TEMP:-/tmp}/compose-retry.log"
attempt=1

is_network_flake() {
  grep -Eqi \
    'connection reset|connection timed out|Temporary failure in name resolution|no such host|Unknown host|TLS handshake|i/o timeout|unexpected EOF|: EOF|toomanyrequests|Too Many Requests|Client\.Timeout|dial tcp|GOAWAY|broken pipe|context deadline exceeded|503 Service Unavailable|502 Bad Gateway|504 Gateway Timeout|net/http:' \
    "$1" \
  && ! grep -Eqi 'pull access denied|manifest unknown|manifest not found|No such service|invalid compose' "$1"
}

while true; do
  set +e
  docker compose "$@" 2>&1 | tee "$log"
  status="${PIPESTATUS[0]}"
  set -e
  if [ "$status" -eq 0 ]; then
    exit 0
  fi
  if [ "$attempt" -ge "$max" ]; then
    exit "$status"
  fi
  if ! is_network_flake "$log"; then
    exit "$status"
  fi
  echo "Docker Hub/registry flake (attempt ${attempt}/${max}), retrying..." >&2
  attempt=$((attempt + 1))
  sleep $((attempt * 15))
done
