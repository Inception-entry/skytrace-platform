#!/bin/bash
# Create Keycloak database and grant the application user access.
# Runs once during MySQL container first-boot initialization.
set -e

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS keycloak
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON keycloak.* TO '${MYSQL_USER}'@'%';
FLUSH PRIVILEGES;
SQL
