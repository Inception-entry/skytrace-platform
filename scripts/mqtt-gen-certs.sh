#!/usr/bin/env bash
# Generate self-signed CA + server cert for local MQTT over TLS (mqtts).
# Output: deploy/mqtt/certs/{ca.crt,ca.key,server.crt,server.key}
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CERT_DIR="${CERT_DIR:-$ROOT/deploy/mqtt/certs}"
DAYS="${MQTT_TLS_DAYS:-825}"
CN="${MQTT_TLS_CN:-mqtt.skytrace.local}"

mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

if [[ -f server.crt && -f server.key && -f ca.crt ]]; then
  echo "certs already exist in $CERT_DIR (delete to regenerate)"
  exit 0
fi

openssl req -x509 -newkey rsa:2048 -nodes \
  -keyout ca.key -out ca.crt -days "$DAYS" \
  -subj "/CN=SkyTrace MQTT Dev CA"

openssl req -newkey rsa:2048 -nodes \
  -keyout server.key -out server.csr \
  -subj "/CN=${CN}"

cat > server.ext <<EOF
basicConstraints=CA:FALSE
keyUsage = digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = ${CN}
DNS.2 = mqtt
DNS.3 = localhost
IP.1 = 127.0.0.1
EOF

openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out server.crt -days "$DAYS" -extfile server.ext

rm -f server.csr server.ext ca.srl
chmod 600 ca.key server.key
chmod 644 ca.crt server.crt

echo "Generated MQTT TLS material in $CERT_DIR"
echo "Use with: docker compose ... -f deploy/docker-compose.mqtt.yml -f deploy/docker-compose.mqtt-tls.yml"
