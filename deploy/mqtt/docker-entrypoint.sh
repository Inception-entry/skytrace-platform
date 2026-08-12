#!/bin/sh
# Mosquitto 启动前根据环境变量生成 password_file。
# 若挂载了 /mosquitto/certs/server.crt，则改用 mosquitto-tls.conf（1883+8883）。
set -eu

PASSWD_FILE=/tmp/mosquitto.passwd
BACKEND_USER="${MQTT_BACKEND_USERNAME:-backend}"
BACKEND_PASS="${MQTT_BACKEND_PASSWORD:-skytrace-mqtt-backend}"
SIM_USER="${MQTT_SIM_USERNAME:-device-sim}"
SIM_PASS="${MQTT_SIM_PASSWORD:-skytrace-mqtt-sim}"

mosquitto_passwd -b -c "$PASSWD_FILE" "$BACKEND_USER" "$BACKEND_PASS"
mosquitto_passwd -b "$PASSWD_FILE" "$SIM_USER" "$SIM_PASS"
chmod 0600 "$PASSWD_FILE"

CONF=/mosquitto/config/mosquitto.conf
if [ -f /mosquitto/certs/server.crt ] && [ -f /mosquitto/config/mosquitto-tls.conf ]; then
  CONF=/mosquitto/config/mosquitto-tls.conf
  echo "MQTT TLS material detected; using $CONF (1883+8883)"
fi

echo "MQTT auth ready: users=${BACKEND_USER},${SIM_USER} (anonymous disabled)"
exec /usr/sbin/mosquitto -c "$CONF"
