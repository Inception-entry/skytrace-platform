#!/usr/bin/env bash
# 生成本地 Mosquitto password_file（演示凭据，勿用于生产）。
# 用法：在仓库根目录执行 ./deploy/mqtt/generate-passwd.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
PASSWD_FILE="${ROOT}/passwd"

BACKEND_USER="${MQTT_BACKEND_USERNAME:-backend}"
BACKEND_PASS="${MQTT_BACKEND_PASSWORD:-skytrace-mqtt-backend}"
SIM_USER="${MQTT_SIM_USERNAME:-device-sim}"
SIM_PASS="${MQTT_SIM_PASSWORD:-skytrace-mqtt-sim}"

if command -v mosquitto_passwd >/dev/null 2>&1; then
  mosquitto_passwd -b -c "$PASSWD_FILE" "$BACKEND_USER" "$BACKEND_PASS"
  mosquitto_passwd -b "$PASSWD_FILE" "$SIM_USER" "$SIM_PASS"
else
  docker run --rm \
    -v "${ROOT}:/out" \
    eclipse-mosquitto:2 \
    sh -c "mosquitto_passwd -b -c /out/passwd '${BACKEND_USER}' '${BACKEND_PASS}' && mosquitto_passwd -b /out/passwd '${SIM_USER}' '${SIM_PASS}'"
fi

chmod 0600 "$PASSWD_FILE"
echo "已写入 ${PASSWD_FILE}"
echo "  ${BACKEND_USER} / (见 MQTT_BACKEND_PASSWORD，默认 skytrace-mqtt-backend)"
echo "  ${SIM_USER} / (见 MQTT_SIM_PASSWORD，默认 skytrace-mqtt-sim)"
