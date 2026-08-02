#!/usr/bin/env bash
# One-time bootstrap for the test server.
# Run as a user with sudo access after cloning or on a fresh machine.
#
# Usage:
#   REPO_URL=https://github.com/OWNER/skytrace-platform.git \
#   bash scripts/staging-init.sh
set -euo pipefail

APP_DIR="/opt/uav"
REPO_URL="${REPO_URL:-https://github.com/OWNER/skytrace-platform.git}"

# --- Docker ---
if ! command -v docker &>/dev/null; then
  echo "Installing Docker..."
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker "$USER"
  echo "Docker installed. Log out and back in so the docker group takes effect."
fi

# --- Repo ---
if [[ ! -d "$APP_DIR/.git" ]]; then
  echo "Cloning repo to $APP_DIR..."
  sudo git clone "$REPO_URL" "$APP_DIR"
  sudo chown -R "$USER:$USER" "$APP_DIR"
else
  echo "$APP_DIR already exists; skipping clone."
fi

# --- Env file ---
ENV_FILE="$APP_DIR/deploy/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  cp "$APP_DIR/deploy/.env.example" "$ENV_FILE"
  echo ""
  echo "Created $ENV_FILE from example. IMPORTANT: edit it now:"
  echo "  - Set real passwords for MySQL, RabbitMQ, MinIO, Keycloak."
  echo "  - Set KEYCLOAK_PUBLIC_URL=https://\$YOUR_DOMAIN"
  echo "  - Set GATEWAY_ALLOWED_ORIGIN=https://\$YOUR_DOMAIN"
  echo "  - Set GATEWAY_JWT_ISSUER_URI=https://\$YOUR_DOMAIN/realms/uav"
  echo "  - Set GATEWAY_JWT_JWK_SET_URI=https://\$YOUR_DOMAIN/realms/uav/protocol/openid-connect/certs"
fi

# --- Permissions ---
chmod +x "$APP_DIR/scripts/deploy-staging.sh"

# --- Firewall reminder ---
echo ""
echo "=== Open firewall ports 80 and 443 (TCP) if not already done ==="
echo ""
echo "=== GitHub Secrets to configure ==="
echo "  STAGING_DOMAIN    your test domain, e.g. test.example.com"
echo "  STAGING_KEYCLOAK_URL  https://\$STAGING_DOMAIN"
echo "  TEST_SSH_HOST     this server's IP or hostname"
echo "  TEST_SSH_USER     SSH username (e.g. $(whoami))"
echo "  TEST_SSH_KEY      contents of the SSH private key for this user"
echo ""
echo "=== GitHub Environment to create ==="
echo "  Name: test  (Settings → Environments → New environment)"
echo "  Add STAGING_DOMAIN, TEST_SSH_HOST, TEST_SSH_USER and TEST_SSH_KEY"
echo "  as environment secrets. The workflow uses GITHUB_TOKEN to pull GHCR images."
echo "  Publish now includes admin-service and admin-frontend images."
echo ""
echo "Bootstrap complete. Push to main to trigger CI → Publish → Deploy."
