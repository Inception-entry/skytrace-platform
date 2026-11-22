#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${SKYTRACE_ENV_FILE:-$ROOT_DIR/deploy/.env}"
COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.yml"
MQTT_COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.mqtt.yml"
MQTT_TLS_COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.mqtt-tls.yml"
MONITORING_COMPOSE_FILE="$ROOT_DIR/deploy/docker-compose.monitoring.yml"

compose() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    "$@"
}

# Mosquitto + device-sim 在 overlay 中；同时重建 backend-java 以启用 MQTT 订阅
compose_mqtt() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    -f "$MQTT_COMPOSE_FILE" \
    "$@"
}

compose_mqtt_tls() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    -f "$MQTT_COMPOSE_FILE" \
    -f "$MQTT_TLS_COMPOSE_FILE" \
    "$@"
}

compose_monitoring() {
  docker compose \
    --env-file "$ENV_FILE" \
    -f "$COMPOSE_FILE" \
    -f "$MONITORING_COMPOSE_FILE" \
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
  skytrace.sh stop [服务...]       停止指定服务并保留容器。省略服务时移除整套容器和项目网络，保留数据卷
  skytrace.sh restart [服务...]    重启现有服务
  skytrace.sh status               查看服务状态
  skytrace.sh logs [服务...]       持续查看日志
  skytrace.sh auth-start           单独启动本地 Keycloak
  skytrace.sh auth-stop            停止本地 Keycloak
  skytrace.sh auth-logs            查看 Keycloak 日志
  skytrace.sh auth-users           同步 OPERATOR、VIEWER 测试账号
  skytrace.sh auth-verify          执行 ADMIN/OPERATOR/VIEWER 权限验收
  skytrace.sh auth-token           获取 skytrace-service 的 Bearer Token
  skytrace.sh mqtt-start           启动 Mosquitto + device-sim，并启用 Java MQTT
  skytrace.sh mqtt-tls-start       同上，并生成本地证书启用 mqtts
  skytrace.sh mqtt-stop            停止 Mosquitto 和 device-sim，关闭运行中的 Java 后端的 MQTT 订阅
  skytrace.sh mqtt-logs            查看 MQTT / device-sim 日志
  skytrace.sh backup               备份 MySQL 业务库到 MinIO
  skytrace.sh rollback-plan        查看发布机上的回退单（只判断，不改运行中的服务）
  skytrace.sh monitoring-start     启动 Prometheus / Grafana / Loki
  skytrace.sh monitoring-stop      停止监控栈
  skytrace.sh cleanup              停栈并移除项目容器、网络和孤立容器，保留数据卷
  skytrace.sh help                 显示帮助

macOS 默认使用 Docker 上下文 colima 和 Compose 项目名 deploy。
可用 DOCKER_CONTEXT（或 DOCKER_HOST）和 COMPOSE_PROJECT_NAME 覆盖。

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
  skytrace.sh mqtt-start
  skytrace.sh mqtt-tls-start
  skytrace.sh mqtt-logs
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

  client_secret="$(env_value KEYCLOAK_SERVICE_CLIENT_SECRET)"
  if [[ -z "$client_secret" ]]; then
    echo "请先在 deploy/.env 设置 KEYCLOAK_SERVICE_CLIENT_SECRET"
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

ACTION="${1:-help}"
shift || true

case "$ACTION" in
  help | -h | --help)
    show_help
    exit 0
    ;;
esac

require_env

if [[ "$(uname -s)" == "Darwin" ]]; then
  if [[ -z "${DOCKER_CONTEXT:-}" && -z "${DOCKER_HOST:-}" ]]; then
    export DOCKER_CONTEXT=colima
  fi
  export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-deploy}"
fi

case "$ACTION" in
  start)
    compose up -d --no-build "$@"
    ;;
  rebuild)
    compose up -d --build "$@"
    ;;
  stop)
    if (($# > 0)); then
      compose stop "$@"
    else
      compose down --remove-orphans
    fi
    ;;
  cleanup)
    compose down --remove-orphans
    echo "已停止并移除项目容器和网络，保留数据卷"
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
    KEYCLOAK_ALLOW_TEST_USERS=true \
    KEYCLOAK_CONTAINER=skytrace-keycloak \
    KEYCLOAK_REALM=skytrace \
    KEYCLOAK_ADMIN_USERNAME="$(env_value KEYCLOAK_ADMIN_USERNAME)" \
    KEYCLOAK_ADMIN_PASSWORD="$(env_value KEYCLOAK_ADMIN_PASSWORD)" \
    KEYCLOAK_TEST_USER_PASSWORD="$(env_value KEYCLOAK_DEV_USER_PASSWORD)" \
      "$ROOT_DIR/scripts/keycloak/sync-test-users.sh"
    ;;
  auth-verify)
    require_local_keycloak_secrets
    KEYCLOAK_CONTAINER=skytrace-keycloak \
    KEYCLOAK_REALM=skytrace \
    KEYCLOAK_CLIENT_ID=skytrace-web \
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
    client_secret="$(env_value KEYCLOAK_SERVICE_CLIENT_SECRET)"
    response="$(curl --fail --silent --show-error \
      -X POST \
      "$keycloak_url/realms/skytrace/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      --data-urlencode "grant_type=client_credentials" \
      --data-urlencode "client_id=skytrace-service" \
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
  mqtt-start)
    if [[ ! -f "$MQTT_COMPOSE_FILE" ]]; then
      echo "缺少 MQTT 叠加文件：$MQTT_COMPOSE_FILE"
      exit 1
    fi
    # overlay 会给 backend-java 注入 MQTT_ENABLED=true，需 recreate 才生效
    compose_mqtt up -d --build mqtt device-sim
    compose_mqtt up -d --force-recreate --no-deps backend-java
    echo "MQTT 已启动：skytrace-mqtt / skytrace-device-sim（backend-java 已启用 MQTT）"
    echo "认证：backend / device-sim 用户名密码见 deploy/.env（默认见 .env.example）"
    ;;
  mqtt-tls-start)
    if [[ ! -f "$MQTT_COMPOSE_FILE" || ! -f "$MQTT_TLS_COMPOSE_FILE" ]]; then
      echo "缺少 MQTT / MQTT-TLS 叠加文件"
      exit 1
    fi
    bash "$ROOT_DIR/scripts/mqtt-gen-certs.sh"
    compose_mqtt_tls up -d --build mqtt device-sim
    compose_mqtt_tls up -d --force-recreate --no-deps backend-java
    echo "MQTT TLS 已启动：1883 + 8883（mqtts）；Java/sim 使用 ssl://mqtt:8883"
    echo "本地自签默认 MQTT_TLS_INSECURE=true；生产请换受信 CA 并设为 false"
    ;;
  mqtt-stop)
    if [[ ! -f "$MQTT_COMPOSE_FILE" ]]; then
      echo "缺少 MQTT 叠加文件：$MQTT_COMPOSE_FILE"
      exit 1
    fi
    # TLS 叠加文件仍使用同一个项目和服务名。
    compose_mqtt stop device-sim mqtt
    running_java="$(compose ps --status running --quiet backend-java)"
    if [[ -n "$running_java" ]]; then
      # 以主 Compose 配置重建，去掉 MQTT 叠加配置。
      MQTT_ENABLED=false compose up -d --no-build --no-deps backend-java
    fi
    echo "MQTT 已停止：skytrace-mqtt / skytrace-device-sim"
    ;;
  mqtt-logs)
    if [[ ! -f "$MQTT_COMPOSE_FILE" ]]; then
      echo "缺少 MQTT 叠加文件：$MQTT_COMPOSE_FILE"
      exit 1
    fi
    compose_mqtt logs -f mqtt device-sim
    ;;
  backup)
    bash "$ROOT_DIR/scripts/mysql-backup.sh"
    ;;
  rollback-plan)
    APP_DIR="${APP_DIR:-$ROOT_DIR}" bash "$ROOT_DIR/scripts/rollback-release.sh" --plan "${1:-production}"
    ;;
  monitoring-start)
    if [[ ! -f "$MONITORING_COMPOSE_FILE" ]]; then
      echo "缺少监控叠加文件：$MONITORING_COMPOSE_FILE"
      exit 1
    fi
    compose_monitoring up -d
    echo "监控栈已启动。Grafana 默认 http://127.0.0.1:$(env_value_or_default GRAFANA_PORT 3030)"
    ;;
  monitoring-stop)
    if [[ ! -f "$MONITORING_COMPOSE_FILE" ]]; then
      echo "缺少监控叠加文件：$MONITORING_COMPOSE_FILE"
      exit 1
    fi
    compose_monitoring stop prometheus grafana loki promtail alertmanager cadvisor node-exporter
    echo "监控栈已停止"
    ;;
  *)
    echo "未知操作：$ACTION"
    echo
    show_help
    exit 2
    ;;
esac
