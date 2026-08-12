#!/usr/bin/env bash
# Wipe or inspect the SkyTrace Qdrant knowledge collection for rebuild.
#
# Usage:
#   ./scripts/qdrant-rebuild.sh status
#   ./scripts/qdrant-rebuild.sh wipe          # DELETE current collection
#   ./scripts/qdrant-rebuild.sh wipe --yes    # no prompt
#
# After wipe: restart backend-ai and re-upload documents via /knowledge.
# See docs/data-governance.md §4.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
if [[ -f "$ENV_FILE" ]]; then
  set -o allexport
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +o allexport
fi

QDRANT_URL="${QDRANT_URL:-http://127.0.0.1:${QDRANT_HOST_PORT:-6333}}"
COLLECTION="${AI_QDRANT_COLLECTION:-skytrace_knowledge}"
ACTION="${1:-status}"

case "$ACTION" in
  status)
    echo "Qdrant: ${QDRANT_URL}"
    echo "Collection: ${COLLECTION}"
    curl -fsS "${QDRANT_URL}/collections/${COLLECTION}" | head -c 2000 || {
      echo
      echo "collection 不存在或 Qdrant 不可达（可能已是空状态）"
      exit 0
    }
    echo
    ;;
  wipe)
    if [[ "${2:-}" != "--yes" ]]; then
      echo "将删除 Qdrant collection: ${COLLECTION} @ ${QDRANT_URL}"
      echo "知识库向量会丢失，需之后在 /knowledge 重新上传文档。"
      read -r -p "确认输入 YES 继续: " confirm
      if [[ "$confirm" != "YES" ]]; then
        echo "已取消"
        exit 1
      fi
    fi
    code="$(curl -s -o /tmp/qdrant-wipe-body.txt -w '%{http_code}' \
      -X DELETE "${QDRANT_URL}/collections/${COLLECTION}")"
    echo "HTTP ${code}"
    cat /tmp/qdrant-wipe-body.txt 2>/dev/null || true
    echo
    if [[ "$code" != "200" && "$code" != "404" ]]; then
      echo "删除失败"
      exit 1
    fi
    echo "已删除（或不存在）。下一步："
    echo "  1) ollama pull \${AI_OLLAMA_EMBEDDING_MODEL:-nomic-embed-text}"
    echo "  2) ./scripts/skytrace.sh restart backend-ai"
    echo "  3) 打开 /knowledge 用 ADMIN 重新上传文档并做一次语义检索"
    ;;
  *)
    echo "Usage: $0 {status|wipe} [--yes]"
    exit 1
    ;;
esac
