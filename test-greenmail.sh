#!/usr/bin/env bash
#
# test-greenmail.sh
# ---------------------------------------------------------------------------
# One-shot OFFLINE S/MIME test on Windows (Git Bash / MINGW64).
# Runs GreenMailSmimeTest in GreenMail mode: signs, sends to an in-memory
# SMTP server, receives, and verifies the signature. No network, no account.
#
# Steps it automates:
#   1. Ensure the signing keystore exists (generates a self-signed S/MIME
#      PKCS#12 via keytool if missing).
#   2. Download project dependencies into ./lib (skips if already present).
#   3. Compile GreenMailSmimeTest.java.
#   4. Run it with MAIL_ENV=greenmail.
#
# Usage:  bash test-greenmail.sh
# ---------------------------------------------------------------------------

set -euo pipefail
cd "$(dirname "$0")"

CERT_DIR="smime-test-certs"
P12="$CERT_DIR/our-signing.p12"
ALIAS="our-alias"
STOREPASS="changeit"
SIGNER_EMAIL="samfire5200@gmail.com"   # must match FROM_EMAIL for a clean signature

# Windows java uses ';' as the classpath separator (even under Git Bash).
CP=".;lib/*"

echo "==> [1/4] Checking signing keystore ..."
mkdir -p "$CERT_DIR"
if [ -f "$P12" ]; then
  echo "    Found $P12 (reusing)."
else
  echo "    Generating self-signed S/MIME keystore via keytool ..."
  keytool -genkeypair \
    -alias "$ALIAS" -keyalg RSA -keysize 2048 \
    -dname "CN=Our Ops" \
    -ext "san=email:${SIGNER_EMAIL}" \
    -ext "ku=digitalSignature,keyEncipherment" \
    -ext "eku=emailProtection" \
    -validity 825 \
    -storetype PKCS12 -keystore "$P12" \
    -storepass "$STOREPASS" -keypass "$STOREPASS"
  echo "    Created $P12"
fi

echo "==> [2/4] Resolving dependencies into ./lib ..."
if [ -d "lib" ] && [ -n "$(ls -A lib 2>/dev/null)" ]; then
  echo "    ./lib already populated (skipping). Delete it to force refresh."
else
  mvn -q dependency:copy-dependencies "-DoutputDirectory=lib"
fi

echo "==> [3/4] Compiling GreenMailSmimeTest.java ..."
javac -cp "lib/*" GreenMailSmimeTest.java

echo "==> [4/4] Running offline GreenMail test ..."
MAIL_ENV=greenmail \
KEYSTORE_PASS="$STOREPASS" \
FROM_EMAIL="$SIGNER_EMAIL" \
TO_EMAIL="samfire5201@gmail.com" \
java -cp "$CP" GreenMailSmimeTest

echo "==> Done. Expect: 'SIGNATURE VERIFIED — offline round-trip succeeded.'"
