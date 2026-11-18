#!/usr/bin/env bash
# Runs once when the Codespace is created. Installs MySQL 8.0 inside the container,
# creates a random root password (stored only in the container, mode 600), and builds the jar.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq
sudo apt-get install -y -qq mysql-server >/dev/null
sudo service mysql start

SECRET="$HOME/.focusflow-db.env"
if [ ! -f "$SECRET" ]; then
  PW="$(openssl rand -hex 24)"
  umask 077
  printf 'DB_USERNAME=root\nDB_PASSWORD=%s\n' "$PW" > "$SECRET"
fi
# shellcheck disable=SC1090
. "$SECRET"
sudo mysql -e "ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '${DB_PASSWORD}'; CREATE DATABASE IF NOT EXISTS focus_flow;"

cd "$(cd "$(dirname "$0")/.." && pwd)/Focus_Flow"
bash ./gradlew bootJar --no-daemon -q
