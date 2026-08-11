#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AI_DIR="${ROOT_DIR}/backend-ai"
DEPLOY_ENV="${ROOT_DIR}/deploy/.env"

if [[ -f "${DEPLOY_ENV}" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "${DEPLOY_ENV}"
  set +a
fi

: "${AI_SERVICE_HOST:=127.0.0.1}"
: "${AI_SERVICE_PORT:=8000}"
: "${AI_OLLAMA_PORT:=11434}"
: "${AI_OLLAMA_BASE_URL:=http://127.0.0.1:${AI_OLLAMA_PORT}}"
: "${AI_OLLAMA_MODEL:=my-drone-expert}"
: "${AI_OLLAMA_EMBEDDING_MODEL:=nomic-embed-text}"
: "${REDIS_HOST_PORT:=6380}"
: "${QDRANT_HOST_PORT:=6333}"
: "${RABBITMQ_HOST_PORT:=5672}"
: "${RABBITMQ_DEFAULT_USER:=admin}"
: "${RABBITMQ_DEFAULT_PASS:=admin123}"
: "${AI_REDIS_URL:=redis://127.0.0.1:${REDIS_HOST_PORT}/0}"
: "${AI_QDRANT_URL:=http://127.0.0.1:${QDRANT_HOST_PORT}}"
: "${AI_QDRANT_COLLECTION:=skytrace_knowledge}"
: "${AI_RABBITMQ_URL:=amqp://${RABBITMQ_DEFAULT_USER}:${RABBITMQ_DEFAULT_PASS}@127.0.0.1:${RABBITMQ_HOST_PORT}/}"
: "${AI_MESSAGING_ENABLED:=true}"
: "${AI_VISION_ENABLED:=true}"
: "${AI_VISION_BACKEND:=mock}"

if [[ ! -x "${AI_DIR}/.venv/bin/uvicorn" ]]; then
  echo "backend-ai virtualenv not found. Run 'cd backend-ai && uv sync' first." >&2
  exit 1
fi

echo "Starting backend-ai on http://${AI_SERVICE_HOST}:${AI_SERVICE_PORT}"
echo "Using Qdrant collection: ${AI_QDRANT_COLLECTION}"

cd "${AI_DIR}"
exec ./.venv/bin/uvicorn app.main:app --host "${AI_SERVICE_HOST}" --port "${AI_SERVICE_PORT}"
