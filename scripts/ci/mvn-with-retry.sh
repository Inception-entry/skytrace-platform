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

# setup-java restores ~/.m2. A hit recorded under another repository id, or a
# cached Central miss, makes Maven report the artifact "present, but unavailable"
# and then refuse to try again until -U.
is_stale_repository_cache() {
  grep -Eq \
    'present, but unavailable|failure was cached in the local repository|resolution is not reattempted until the update interval' \
    "$1"
}

clear_cached_misses() {
  local repo="${HOME}/.m2/repository"
  if [ -d "$repo" ]; then
    find "$repo" -name '*.lastUpdated' -delete
  fi
}

force_update=0

while true; do
  mvn_args=(--batch-mode --no-transfer-progress)
  if [ "$force_update" -eq 1 ]; then
    mvn_args+=(-U)
  fi
  mvn_args+=("$@")
  set +e
  mvn "${mvn_args[@]}" 2>&1 | tee "$log"
  status="${PIPESTATUS[0]}"
  set -e
  if [ "$status" -eq 0 ]; then
    exit 0
  fi
  if [ "$attempt" -ge "$max" ]; then
    exit "$status"
  fi
  if is_stale_repository_cache "$log"; then
    echo "Maven local-repository cache is stale (attempt ${attempt}/${max}), clearing cached misses and retrying with -U..." >&2
    clear_cached_misses
    force_update=1
  elif is_network_flake "$log"; then
    echo "Maven Central/network flake (attempt ${attempt}/${max}), retrying..." >&2
  else
    exit "$status"
  fi
  attempt=$((attempt + 1))
  sleep $((attempt * 12))
done
