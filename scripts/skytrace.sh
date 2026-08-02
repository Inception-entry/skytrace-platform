#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${UAV_ENV_FILE:-$ROOT_DIR/deploy/.env}"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.yml"

compose() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "$@"
}

require_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "缺少环境变量文件：$ENV_FILE"
    echo "请先执行：cp deploy/.env.example deploy/.env"
    exit 1
  fi
}

show_help() {
  cat <<'EOF'
用法：
  skytrace.sh start [服务...]      启动服务，不重新构建镜像
  skytrace.sh rebuild [服务...]    重新构建镜像并启动服务
  skytrace.sh stop [服务...]       停止服务；未指定服务时关闭整套环境
  skytrace.sh restart [服务...]    重启现有服务
  skytrace.sh status               查看服务状态
  skytrace.sh logs [服务...]       持续查看日志
  skytrace.sh auth-start           单独启动本地 Keycloak
  skytrace.sh auth-stop            停止本地 Keycloak
  skytrace.sh auth-logs            查看 Keycloak 日志
  skytrace.sh auth-users           同步 OPERATOR、VIEWER 测试账号
  skytrace.sh auth-verify          执行 ADMIN/OPERATOR/VIEWER 权限验收
  skytrace.sh auth-token           获取 uav-service 的 Bearer Token
  skytrace.sh help                 显示帮助

示例：
  skytrace.sh start
  skytrace.sh rebuild backend-ai
  skytrace.sh rebuild backend-java
  skytrace.sh rebuild gateway
  skytrace.sh restart temporal-ui
  skytrace.sh logs gateway
  skytrace.sh auth-start
  skytrace.sh auth-users
  skytrace.sh auth-verify
  skytrace.sh auth-token
  skytrace.sh logs backend-ai
  skytrace.sh logs backend-java
EOF
}

env_value() {
  local key="$1"
  awk -F= -v key="$key" '
    $1 == key {
      sub(/^[^=]*=/, "")
      print
      exit
    }
  ' "$ENV_FILE"
}

env_value_or_default() {
  local key="$1"
  local default_value="$2"
  local value

  value="$(env_value "$key")"
  printf '%s\n' "${value:-$default_value}"
}

require_service_client_secret() {
  local client_secret

  client_secret="$(env_value KEYCLOAK_UAV_SERVICE_CLIENT_SECRET)"
  if [[ -z "$client_secret" ]]; then
    echo "请先在 deploy/.env 设置 KEYCLOAK_UAV_SERVICE_CLIENT_SECRET"
    exit 1
  fi
}

require_local_keycloak_secrets() {
  local admin_password
  local dev_user_password

  admin_password="$(env_value KEYCLOAK_ADMIN_PASSWORD)"
  dev_user_password="$(env_value KEYCLOAK_DEV_USER_PASSWORD)"

  if [[ -z "$admin_password" \
    || "$admin_password" == "change-me-before-start" ]]; then
    echo "请先在 deploy/.env 设置非默认的 KEYCLOAK_ADMIN_PASSWORD"
    exit 1
  fi
  if [[ -z "$dev_user_password" ]]; then
    echo "请先在 deploy/.env 设置 KEYCLOAK_DEV_USER_PASSWORD"
    exit 1
  fi
  require_service_client_secret
}

require_env

ACTION="${1:-help}"
shift || true

case "$ACTION" in
  start)
    compose up -d "$@"
    ;;
  rebuild)
    compose up -d --build "$@"
    ;;
  stop)
    if (($# > 0)); then
      compose stop "$@"
    else
      compose down
    fi
    ;;
  restart)
    compose restart "$@"
    ;;
  status | ps)
    compose ps "$@"
    ;;
  logs)
    compose logs -f "$@"
    ;;
  auth-start)
    require_local_keycloak_secrets
    compose --profile auth up -d keycloak
    ;;
  auth-stop)
    compose --profile auth stop keycloak
    ;;
  auth-logs)
    compose --profile auth logs -f keycloak
    ;;
  auth-users)
    require_local_keycloak_secrets
    KEYCLOAK_CONTAINER=skytrace-keycloak \
    KEYCLOAK_REALM=uav \
    KEYCLOAK_ADMIN_USERNAME="$(env_value KEYCLOAK_ADMIN_USERNAME)" \
    KEYCLOAK_ADMIN_PASSWORD="$(env_value KEYCLOAK_ADMIN_PASSWORD)" \
    KEYCLOAK_TEST_USER_PASSWORD="$(env_value KEYCLOAK_DEV_USER_PASSWORD)" \
      "$ROOT_DIR/scripts/keycloak/sync-test-users.sh"
    ;;
  auth-verify)
    require_local_keycloak_secrets
    KEYCLOAK_CONTAINER=skytrace-keycloak \
    KEYCLOAK_REALM=uav \
    KEYCLOAK_CLIENT_ID=uav-web \
    KEYCLOAK_URL="$(env_value_or_default \
      KEYCLOAK_PUBLIC_URL http://localhost:8180)" \
    KEYCLOAK_ADMIN_USERNAME="$(env_value KEYCLOAK_ADMIN_USERNAME)" \
    KEYCLOAK_ADMIN_PASSWORD="$(env_value KEYCLOAK_ADMIN_PASSWORD)" \
    KEYCLOAK_TEST_USER_PASSWORD="$(env_value KEYCLOAK_DEV_USER_PASSWORD)" \
    GATEWAY_URL="http://localhost:$(env_value_or_default \
      GATEWAY_PORT 8082)" \
    NODE_URL="http://localhost:$(env_value_or_default \
      NODE_PORT 3000)" \
    JAVA_URL="http://localhost:$(env_value_or_default \
      JAVA_PORT 8081)" \
      "$ROOT_DIR/scripts/keycloak/verify-rbac.sh"
    ;;
  auth-token)
    require_service_client_secret
    keycloak_url="$(env_value KEYCLOAK_PUBLIC_URL)"
    keycloak_url="${keycloak_url:-http://localhost:8180}"
    client_secret="$(env_value KEYCLOAK_UAV_SERVICE_CLIENT_SECRET)"
    response="$(curl --fail --silent --show-error \
      -X POST \
      "$keycloak_url/realms/uav/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=client_credentials" \
      --data-urlencode "client_id=uav-service" \
      --data-urlencode "client_secret=$client_secret")"
    token="$(python3 -c '
import json
import sys

payload = json.load(sys.stdin)
token = payload.get("access_token")
if not token:
    raise SystemExit("Keycloak 响应中没有 access_token")
print(token)
' <<<"$response")"
    printf 'Bearer %s\n' "$token"
    ;;
  help | -h | --help)
    show_help
    ;;
  *)
    echo "未知操作：$ACTION"
    echo
    show_help
    exit 2
    ;;
esac
