#!/usr/bin/env bash
# Runs every time the Codespace starts: brings MySQL and FOCUS FLOW back up.
set -euo pipefail
sudo service mysql start >/dev/null
# shellcheck disable=SC1090
. "$HOME/.focusflow-db.env"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$(ls "$ROOT"/Focus_Flow/build/libs/project-flow-*.jar | head -1)"
mkdir -p "$HOME/focusflow-uploads"
pkill -f 'project-flow-.*\.jar' 2>/dev/null || true
cd "$ROOT/Focus_Flow"
# The forwarded URL is HTTPS, so the session cookie is marked Secure.
PORT=8080 SERVER_ADDRESS=0.0.0.0 SESSION_COOKIE_SECURE=true UPLOAD_DIR="$HOME/focusflow-uploads" \
DB_NAME=focus_flow DB_USERNAME="$DB_USERNAME" DB_PASSWORD="$DB_PASSWORD" \
nohup java -jar "$JAR" > "$HOME/focusflow.log" 2>&1 &
echo "FOCUS FLOW starting on port 8080; log: $HOME/focusflow.log"
