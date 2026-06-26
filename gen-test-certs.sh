#!/usr/bin/env bash
#
# gen-test-certs.sh
# ---------------------------------------------------------------------------
# Generate a self-contained S/MIME test PKI for local development:
#   - one Test CA
#   - "our" signing/encryption cert + key      -> our-signing.p12   (alias: our-alias)
#   - a simulated "bank" cert + key            -> bank.p12          (alias: bank-alias)
#   - the bank PUBLIC cert                     -> bank-public.cer
#   - the CA cert                              -> testca.crt
#
# These certs carry the correct S/MIME attributes:
#   keyUsage         = digitalSignature, keyEncipherment
#   extendedKeyUsage = emailProtection
#   subjectAltName   = email:<address>
#
# This lets you exercise sign + encrypt + decrypt + verify entirely offline,
# with NO dependency on the bank. Swap in the bank's real test certs later
# without changing application code.
#
# Requires: openssl (1.1.1+ / 3.x). Usage: ./gen-test-certs.sh [output_dir]
# ---------------------------------------------------------------------------

set -euo pipefail

OUT_DIR="${1:-./smime-test-certs}"
DAYS_CA=3650
DAYS_LEAF=825
KEY_BITS=2048

OUR_CN="Our Ops"
OUR_EMAIL="ops@yourco.example"
OUR_P12_PASS="changeit"     # demo password — change for anything real

BANK_CN="Bank Host2Host"
BANK_EMAIL="host2host@bank.example"
BANK_P12_PASS="changeit"

mkdir -p "$OUT_DIR"
cd "$OUT_DIR"

echo ">> Output directory: $(pwd)"

# ---------------------------------------------------------------------------
# 1) Test CA
# ---------------------------------------------------------------------------
echo ">> [1/4] Creating Test CA ..."
openssl req -x509 -newkey "rsa:${KEY_BITS}" -nodes -days "$DAYS_CA" \
  -keyout testca.key -out testca.crt \
  -subj "/CN=Test S-MIME CA/O=Local Dev"

# Helper: issue an S/MIME leaf cert signed by the test CA.
# args: <name> <CN> <email>
# Uses an -extfile (not -addext) so it works on both OpenSSL 1.1.1 and 3.x;
# `openssl x509 -req -addext` is only available in newer 3.x builds.
issue_smime_cert () {
  local name="$1" cn="$2" email="$3"

  openssl req -newkey "rsa:${KEY_BITS}" -nodes \
    -keyout "${name}.key" -out "${name}.csr" \
    -subj "/CN=${cn}/emailAddress=${email}"

  cat > "${name}.ext" <<EOF
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = emailProtection
subjectAltName = email:${email}
EOF

  openssl x509 -req -in "${name}.csr" \
    -CA testca.crt -CAkey testca.key -CAcreateserial \
    -days "$DAYS_LEAF" -out "${name}.crt" \
    -extfile "${name}.ext"

  rm -f "${name}.csr" "${name}.ext"
}

# ---------------------------------------------------------------------------
# 2) "Our" cert + PKCS#12 (used by SMIMEMailSender to SIGN/decrypt)
# ---------------------------------------------------------------------------
echo ">> [2/4] Creating OUR S/MIME cert ..."
issue_smime_cert "me" "$OUR_CN" "$OUR_EMAIL"
openssl pkcs12 -export -in me.crt -inkey me.key -certfile testca.crt \
  -name our-alias -password "pass:${OUR_P12_PASS}" -out our-signing.p12

# ---------------------------------------------------------------------------
# 3) Simulated "bank" cert + PKCS#12 + public cert (used to ENCRYPT to bank
#    and, on the bank side, to decrypt/verify in your round-trip test)
# ---------------------------------------------------------------------------
echo ">> [3/4] Creating simulated BANK S/MIME cert ..."
issue_smime_cert "bank" "$BANK_CN" "$BANK_EMAIL"
openssl pkcs12 -export -in bank.crt -inkey bank.key -certfile testca.crt \
  -name bank-alias -password "pass:${BANK_P12_PASS}" -out bank.p12
# Public cert only — this is what your app needs to ENCRYPT mail to the bank.
cp bank.crt bank-public.cer

# ---------------------------------------------------------------------------
# 4) Summary + sanity check
# ---------------------------------------------------------------------------
echo ">> [4/4] Verifying leaf certs chain to the Test CA ..."
openssl verify -CAfile testca.crt me.crt bank.crt

cat <<EOF

============================================================
 Done. Files in: $(pwd)
------------------------------------------------------------
 testca.crt        Test CA cert (trust anchor for both sides)
 our-signing.p12   OUR key+cert      (alias: our-alias,  pass: ${OUR_P12_PASS})
 bank.p12          BANK key+cert     (alias: bank-alias, pass: ${BANK_P12_PASS})
 bank-public.cer   BANK public cert  (use this to ENCRYPT to the bank)
------------------------------------------------------------
 Map to SMIMEMailSender.java:
   loadSigner("our-signing.p12", "${OUR_P12_PASS}".toCharArray(), "our-alias")
   loadRecipientCert("bank-public.cer")
   // To test the receiving side, decrypt with bank.p12 / bank-alias.
------------------------------------------------------------
 NOTE: These are SELF-SIGNED test certs. The real bank will require
 certs issued by an agreed CA. Replace these files when the bank
 provides UAT certificates — no application code change needed.
============================================================
EOF
