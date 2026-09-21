#!/usr/bin/env bash
# Retry Maven only when Maven Central/DNS/transfer flakes.
# Real compile or test failures exit immediately.

set -uo pipefail

max="${MVN_RETRY_MAX:-5}"
log="${RUNNER_TEMP:-/tmp}/mvn-retry.log"
attempt=1

is_network_flake() {
  grep -Eqi \
    'Temporary failure in name resolution|Could not transfer artifact|UnknownHostException|Unknown host|Connection reset|Connection timed out|Connection refused|502 Bad Gateway|503 Service Unavailable|504 Gateway Timeout|status code: 502|status code: 503|status code: 504' \
    "$1"
}

while true; do
  set +e
  mvn --batch-mode --no-transfer-progress "$@" 2>&1 | tee "$log"
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
  echo "Maven Central/network flake (attempt ${attempt}/${max}), retrying..." >&2
  attempt=$((attempt + 1))
  sleep $((attempt * 12))
done
