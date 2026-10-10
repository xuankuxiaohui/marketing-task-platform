#!/usr/bin/env bash
# Generate self-signed TLS material for local / drill HTTPS (F16).
# Writes deploy/nginx/certs/{fullchain,privkey}.pem (gitignored).
# Not for production — replace with a real CA-issued chain before go-live.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
CERT_DIR="${CERT_DIR:-$ROOT/certs}"
DAYS="${CERT_DAYS:-825}"
CN="${CERT_CN:-portal.local}"

mkdir -p "$CERT_DIR"
KEY="$CERT_DIR/privkey.pem"
CRT="$CERT_DIR/fullchain.pem"
CFG="$(mktemp)"
trap 'rm -f "$CFG"' EXIT

cat >"$CFG" <<CFG
[req]
default_bits = 2048
prompt = no
default_md = sha256
distinguished_name = dn
x509_extensions = ext

[dn]
CN = ${CN}
O = marketing-task-platform-f16-drill
OU = local-self-signed

[ext]
subjectAltName = @san
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth

[san]
DNS.1 = portal.local
DNS.2 = admin.local
DNS.3 = localhost
IP.1 = 127.0.0.1
CFG

openssl req -x509 -nodes -newkey rsa:2048 \
  -keyout "$KEY" \
  -out "$CRT" \
  -days "$DAYS" \
  -config "$CFG"

chmod 600 "$KEY"
chmod 644 "$CRT"

echo "Wrote $CRT"
echo "Wrote $KEY"
openssl x509 -in "$CRT" -noout -subject -dates -ext subjectAltName 2>/dev/null \
  || openssl x509 -in "$CRT" -noout -subject -dates -text | head -40
