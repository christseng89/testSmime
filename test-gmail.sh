#!/usr/bin/env bash
#
# test-gmail.sh
# ---------------------------------------------------------------------------
# REAL send of an S/MIME signed mail via Gmail SMTP (smtp.gmail.com:587).
# Runs GreenMailSmimeTest in MAIL_ENV=gmail mode. Windows / Git Bash.
#
# Prerequisites:
#   - Gmail account has 2-Step Verification ON and an App Password created.
#   - Signing keystore is auto-generated if missing (no ordering required).
#   - Credentials live in a .env file in this folder (do NOT commit it):
#
#       GMAIL_USER=samfire5200@gmail.com
#       GMAIL_APP_PASSWORD=oxar vder xknk wxyz   # spaces are OK, auto-stripped
#
# Usage:  bash test-gmail.sh
# ---------------------------------------------------------------------------

set -euo pipefail
cd "$(dirname "$0")"

ENV_FILE=".env"
P12="smime-test-certs/our-signing.p12"
CP=".;lib/*"   # Windows java classpath separator is ';'

# --- Load .env (line-by-line, so values may contain spaces) -----------------
if [ -f "$ENV_FILE" ]; then
  while IFS= read -r line || [ -n "$line" ]; do
    line="${line%$'\r'}"                       # strip Windows CR
    case "$line" in ''|'#'*) continue ;; esac  # skip blank / comment lines
    [ "${line#*=}" = "$line" ] && continue     # skip lines without '='
    key="${line%%=*}"
    val="${line#*=}"
    key="$(printf '%s' "$key" | tr -d '[:space:]')"   # trim key
    val="${val%\"}"; val="${val#\"}"                  # strip surrounding " "
    val="${val%\'}"; val="${val#\'}"                  # strip surrounding ' '
    export "$key=$val"
  done < "$ENV_FILE"
  echo "==> Loaded credentials from $ENV_FILE"
else
  echo "ERROR: $ENV_FILE not found in $(pwd)." >&2
  echo "Create it with GMAIL_USER= and GMAIL_APP_PASSWORD= lines." >&2
  exit 1
fi

# --- Validate prerequisites -------------------------------------------------
: "${GMAIL_USER:?Missing GMAIL_USER in .env}"
: "${GMAIL_APP_PASSWORD:?Missing GMAIL_APP_PASSWORD in .env}"

# Auto-generate the signing keystore if missing (so script order doesn't matter).
KEYSTORE_PASS="${KEYSTORE_PASS:-changeit}"
SIGNER_EMAIL="${FROM_EMAIL:-samfire5200@gmail.com}"
mkdir -p smime-test-certs
if [ ! -f "$P12" ]; then
  echo "==> $P12 not found — generating self-signed S/MIME keystore via keytool ..."
  keytool -genkeypair \
    -alias our-alias -keyalg RSA -keysize 2048 \
    -dname "CN=Our Ops" \
    -ext "san=email:${SIGNER_EMAIL}" \
    -ext "ku=digitalSignature,keyEncipherment" \
    -ext "eku=emailProtection" \
    -validity 825 \
    -storetype PKCS12 -keystore "$P12" \
    -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS"
  echo "    Created $P12"
fi

# Strip any spaces a pasted app password may contain.
GMAIL_APP_PASSWORD="${GMAIL_APP_PASSWORD// /}"

if [ ! -d "lib" ] || [ -z "$(ls -A lib 2>/dev/null)" ]; then
  echo "==> Resolving dependencies into ./lib ..."
  mvn -q dependency:copy-dependencies "-DoutputDirectory=lib"
fi

echo "==> Compiling GreenMailSmimeTest.java ..."
javac -cp "lib/*" GreenMailSmimeTest.java

echo "==> Sending real signed mail via Gmail ..."
MAIL_ENV=gmail \
KEYSTORE_PASS="${KEYSTORE_PASS:-changeit}" \
FROM_EMAIL="${FROM_EMAIL:-samfire5200@gmail.com}" \
TO_EMAIL="${TO_EMAIL:-samfire5201@gmail.com,njpm@chinasystems.com}" \
GMAIL_USER="$GMAIL_USER" \
GMAIL_APP_PASSWORD="$GMAIL_APP_PASSWORD" \
java -cp "$CP" GreenMailSmimeTest

echo "==> Sent. Check samfire5201@gmail.com (look for a 'smime.p7s' attachment)."
