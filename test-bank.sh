#!/usr/bin/env bash
#
# test-bank.sh
# ---------------------------------------------------------------------------
# Send an S/MIME signed (or signed+encrypted) mail to the BANK SMTP gateway,
# using BankSmimeTest. Windows / Git Bash.
#
# Design: the bank setup is fully isolated in its OWN folder (BANK_DIR, default
# ./bank-certs) so it never touches the GreenMail/Gmail test certs in
# ./smime-test-certs. The script:
#   1. Loads .env.bank.
#   2. Checks the bank's public cert is READY (present) when encrypting.
#   3. Generates your signing keystore (our-signing.p12) INSIDE BANK_DIR if it
#      doesn't exist yet — separate from the other tests.
#   4. Compiles + runs BankSmimeTest (MAIL via env).
#
# Usage:  bash test-bank.sh
# ---------------------------------------------------------------------------

set -euo pipefail
cd "$(dirname "$0")"

ENV_FILE=".env.bank"
CP=".;lib/*"   # Windows java classpath separator is ';'

# --- 1) Load .env.bank ------------------------------------------------------
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found. Copy .env.bank.example to .env.bank and fill it in." >&2
  exit 1
fi
while IFS= read -r line || [ -n "$line" ]; do
  line="${line%$'\r'}"
  case "$line" in ''|'#'*) continue ;; esac
  [ "${line#*=}" = "$line" ] && continue
  key="${line%%=*}"; val="${line#*=}"
  key="$(printf '%s' "$key" | tr -d '[:space:]')"
  val="${val%\"}"; val="${val#\"}"; val="${val%\'}"; val="${val#\'}"
  export "$key=$val"
done < "$ENV_FILE"
echo "==> Loaded bank settings from $ENV_FILE"

# --- defaults (isolated bank folder) ---------------------------------------
BANK_DIR="${BANK_DIR:-bank-certs}"
KEYSTORE_PATH="${KEYSTORE_PATH:-$BANK_DIR/our-signing.p12}"
KEYSTORE_PASS="${KEYSTORE_PASS:-changeit}"
KEYSTORE_ALIAS="${KEYSTORE_ALIAS:-our-alias}"
BANK_CERT="${BANK_CERT:-$BANK_DIR/bank-public.cer}"
BANK_ENCRYPT="${BANK_ENCRYPT:-true}"
mkdir -p "$BANK_DIR"
export KEYSTORE_PATH KEYSTORE_PASS KEYSTORE_ALIAS BANK_CERT BANK_ENCRYPT

# --- validate addresses -----------------------------------------------------
: "${BANK_SMTP_HOST:?Missing BANK_SMTP_HOST in .env.bank}"
: "${FROM_EMAIL:?Missing FROM_EMAIL in .env.bank}"
: "${TO_EMAIL:?Missing TO_EMAIL in .env.bank}"

# --- 2) Check the bank cert is READY (only needed when encrypting) ----------
if [ "$BANK_ENCRYPT" = "true" ]; then
  if [ ! -f "$BANK_CERT" ]; then
    echo "ERROR: bank certificate not ready: $BANK_CERT" >&2
    echo "       Put the bank-provided public cert (.cer/.crt/.pem) there, then re-run." >&2
    echo "       (Or set BANK_ENCRYPT=false in .env.bank to test signed-only first.)" >&2
    exit 1
  fi
  echo "==> Bank cert READY: $BANK_CERT"
else
  echo "==> BANK_ENCRYPT=false -> sign-only test (bank cert not required)."
fi

# --- 3) Generate the signing keystore inside BANK_DIR if missing ------------
if [ -f "$KEYSTORE_PATH" ]; then
  echo "==> Signing keystore present: $KEYSTORE_PATH (reusing)."
else
  echo "==> Generating signing keystore at $KEYSTORE_PATH ..."
  keytool -genkeypair \
    -alias "$KEYSTORE_ALIAS" -keyalg RSA -keysize 2048 \
    -dname "CN=Our Ops" \
    -ext "san=email:${FROM_EMAIL}" \
    -ext "ku=digitalSignature,keyEncipherment" \
    -ext "eku=emailProtection" \
    -validity 825 \
    -storetype PKCS12 -keystore "$KEYSTORE_PATH" \
    -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS"
  echo "    NOTE: this is SELF-SIGNED — fine for connectivity tests. For real use,"
  echo "    replace it with a cert issued by the CA the bank trusts (same alias/path)."
fi

# --- 4) Build + run ---------------------------------------------------------
if [ ! -d "lib" ] || [ -z "$(ls -A lib 2>/dev/null)" ]; then
  echo "==> Resolving dependencies into ./lib ..."
  mvn -q dependency:copy-dependencies "-DoutputDirectory=lib"
fi

echo "==> Compiling BankSmimeTest.java ..."
javac -cp "lib/*" BankSmimeTest.java

echo "==> Sending to bank ($BANK_SMTP_HOST) ..."
java -cp "$CP" BankSmimeTest

echo "==> Done."
