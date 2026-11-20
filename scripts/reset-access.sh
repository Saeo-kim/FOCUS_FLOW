#!/usr/bin/env bash
# Developer recovery for a forgotten password.
#
# Issues a one-time access code for ONE account, the same thing the admin "Reset access"
# button does. The account's old password stops working. The code is printed once here,
# is valid for 7 days, and is stored only as a SHA-256 hash.
#
# Use it on the sign-in page: "I have an invitation code", enter the code, then choose a new password.
#
# Usage, in the Codespace terminal (or on the machine that runs FOCUS_FLOW):
#   bash scripts/reset-access.sh <username>
#
# Database settings come from ~/.focusflow-db.env (written by .devcontainer/setup.sh),
# or from the DB_USERNAME / DB_PASSWORD / DB_NAME environment variables.
set -eu

USER_NAME="${1:-}"
LEN=${#USER_NAME}
case "$USER_NAME" in
  ''|*[!A-Za-z0-9._-]*)
    echo "usage: bash scripts/reset-access.sh <username>" >&2
    exit 1
    ;;
esac
if [ "$LEN" -lt 3 ] || [ "$LEN" -gt 40 ]; then
  echo "usage: bash scripts/reset-access.sh <username>  (username must be 3-40 characters)" >&2
  exit 1
fi

if [ -z "${DB_PASSWORD:-}" ] && [ -f "$HOME/.focusflow-db.env" ]; then
  . "$HOME/.focusflow-db.env"
fi
if [ -z "${DB_PASSWORD:-}" ]; then
  echo "DB_PASSWORD is not set. Run this in the Codespace, where ~/.focusflow-db.env exists." >&2
  exit 1
fi
DB_NAME="${DB_NAME:-focus_flow}"
DB_USER="${DB_USERNAME:-root}"
export MYSQL_PWD="$DB_PASSWORD"

COUNT=$(mysql -u"$DB_USER" -N "$DB_NAME" -e "SELECT COUNT(*) FROM app_users WHERE LOWER(username) = LOWER('$USER_NAME')")
if [ "$COUNT" != "1" ]; then
  echo "No single account named '$USER_NAME' was found. Nothing was changed." >&2
  exit 1
fi

# Same alphabet and format as the app (12 characters in groups of four), and the same
# hashing: remove the dashes, uppercase, then SHA-256 the result.
OUT=$(python3 -c "import secrets, hashlib; a='ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; code='-'.join(''.join(secrets.choice(a) for _ in range(4)) for _ in range(3)); h=hashlib.sha256(code.replace('-','').upper().encode()).hexdigest(); print(code, h, 'locked' + chr(36) + secrets.token_hex(16))")
set -- $OUT
CODE="$1"
HASH="$2"
LOCKED="$3"

mysql -u"$DB_USER" "$DB_NAME" -e "UPDATE app_users SET invite_code_hash='$HASH', invite_expires_at=DATE_ADD(NOW(), INTERVAL 7 DAY), password_hash='$LOCKED', password_set=0 WHERE LOWER(username) = LOWER('$USER_NAME')"

echo "New access code for '$USER_NAME' (valid 7 days, shown once):"
echo "  $CODE"
echo "On the sign-in page, choose 'I have an invitation code', enter this code, then choose a new password."
