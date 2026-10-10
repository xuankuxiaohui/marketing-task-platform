#!/usr/bin/env bash
# Bring up isolated TLS+static nginx, curl-verify portal/admin Host paths, tear down.
# Writes a machine log under this directory; human evidence MD is filled by the operator/PR.
set -euo pipefail

DRILL_DIR="$(cd "$(dirname "$0")" && pwd)"
NGINX_DIR="$(cd "$DRILL_DIR/.." && pwd)"
COMPOSE_FILE="$DRILL_DIR/docker-compose.tls-static.yml"
HTTP_PORT="${NGINX_HTTP_PORT:-18080}"
HTTPS_PORT="${NGINX_HTTPS_PORT:-18443}"
LOG="${DRILL_DIR}/verify-tls-static.last.log"
export NGINX_HTTP_PORT="$HTTP_PORT"
export NGINX_HTTPS_PORT="$HTTPS_PORT"

exec > >(tee "$LOG") 2>&1

echo "=== F16 TLS/static verify $(date -Iseconds) ==="
echo "HTTP_PORT=$HTTP_PORT HTTPS_PORT=$HTTPS_PORT"

"$NGINX_DIR/generate-self-signed-certs.sh"

echo "--- nginx -t (TLS templates + certs) ---"
docker run --rm \
  -v "$NGINX_DIR/nginx.conf:/etc/nginx/nginx.conf:ro" \
  -v "$NGINX_DIR/redirect-https.inc.tls:/etc/nginx/redirect-https.inc:ro" \
  -v "$NGINX_DIR/tls-servers.inc.example:/etc/nginx/tls-servers.inc:ro" \
  -v "$NGINX_DIR/tls.conf.example:/etc/nginx/tls.conf:ro" \
  -v "$NGINX_DIR/certs:/etc/nginx/certs:ro" \
  -v "$NGINX_DIR/html-placeholders/client:/usr/share/nginx/html/client:ro" \
  -v "$NGINX_DIR/html-placeholders/admin:/usr/share/nginx/html/admin:ro" \
  nginx:1.27-alpine nginx -t

cleanup() {
  docker compose -f "$COMPOSE_FILE" down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "--- compose up ---"
docker compose -f "$COMPOSE_FILE" up -d --pull missing
docker compose -f "$COMPOSE_FILE" ps

echo "--- wait healthy ---"
for i in $(seq 1 30); do
  st="$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Service}} {{.Health}}' 2>/dev/null | awk '$1=="nginx"{print $2}')"
  echo "attempt $i health=$st"
  [[ "$st" == "healthy" ]] && break
  sleep 2
done

fail=0
check() {
  local name="$1"; shift
  echo "--- CHECK: $name ---"
  if "$@"; then
    echo "PASS: $name"
  else
    echo "FAIL: $name"
    fail=1
  fi
}

# OpenSSL handshake / protocol
check "openssl_s_client_tls12plus" bash -c "
  echo | openssl s_client -connect 127.0.0.1:${HTTPS_PORT} -servername portal.local -tls1_2 2>/dev/null \
    | grep -E 'Protocol  : TLSv1\.[23]|Verify return code'
"

# Portal HTTPS static
check "portal_https_root" bash -c "
  body=\$(curl -sk --fail -H 'Host: portal.local' \"https://127.0.0.1:${HTTPS_PORT}/\")
  echo \"\$body\" | grep -q 'client placeholder'
"

check "portal_https_healthz" bash -c "
  curl -sk --fail -H 'Host: portal.local' \"https://127.0.0.1:${HTTPS_PORT}/healthz\" | grep -qx 'ok'
"

# Admin HTTPS static under /console/
check "admin_https_console" bash -c "
  body=\$(curl -sk --fail -H 'Host: admin.local' \"https://127.0.0.1:${HTTPS_PORT}/console/\")
  echo \"\$body\" | grep -q 'admin placeholder'
"

check "admin_https_root_redirect" bash -c "
  code=\$(curl -sk -o /dev/null -w '%{http_code}' -H 'Host: admin.local' \"https://127.0.0.1:${HTTPS_PORT}/\")
  loc=\$(curl -sk -o /dev/null -w '%{redirect_url}' -H 'Host: admin.local' \"https://127.0.0.1:${HTTPS_PORT}/\")
  echo \"code=\$code loc=\$loc\"
  [[ \"\$code\" == \"302\" ]] && echo \"\$loc\" | grep -q '/console/'
"

check "admin_https_healthz" bash -c "
  curl -sk --fail -H 'Host: admin.local' \"https://127.0.0.1:${HTTPS_PORT}/healthz\" | grep -qx 'ok'
"

# HTTP → HTTPS redirect
check "http_to_https_portal" bash -c "
  code=\$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: portal.local' \"http://127.0.0.1:${HTTP_PORT}/\")
  loc=\$(curl -s -o /dev/null -w '%{redirect_url}' -H 'Host: portal.local' \"http://127.0.0.1:${HTTP_PORT}/\")
  echo \"code=\$code loc=\$loc\"
  [[ \"\$code\" == \"301\" ]] && echo \"\$loc\" | grep -q '^https://portal.local'
"

check "http_to_https_admin" bash -c "
  code=\$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: admin.local' \"http://127.0.0.1:${HTTP_PORT}/console/\")
  loc=\$(curl -s -o /dev/null -w '%{redirect_url}' -H 'Host: admin.local' \"http://127.0.0.1:${HTTP_PORT}/console/\")
  echo \"code=\$code loc=\$loc\"
  [[ \"\$code\" == \"301\" ]] && echo \"\$loc\" | grep -q '^https://admin.local'
"

# Deny paths still 404 on HTTPS
check "deny_actuator_https" bash -c "
  code=\$(curl -sk -o /dev/null -w '%{http_code}' -H 'Host: portal.local' \"https://127.0.0.1:${HTTPS_PORT}/actuator\")
  echo \"code=\$code\"
  [[ \"\$code\" == \"404\" ]]
"

# HSTS header present
check "hsts_header" bash -c "
  hdr=\$(curl -sk -D - -o /dev/null -H 'Host: portal.local' \"https://127.0.0.1:${HTTPS_PORT}/\" | tr -d '\\r')
  echo \"\$hdr\" | grep -qi 'strict-transport-security:.*max-age='
"

# Cert SAN peek
check "cert_san_hosts" bash -c "
  echo | openssl s_client -connect 127.0.0.1:${HTTPS_PORT} -servername portal.local 2>/dev/null \
    | openssl x509 -noout -ext subjectAltName 2>/dev/null \
    | tee /dev/stderr | grep -q 'portal.local' \
  && echo | openssl s_client -connect 127.0.0.1:${HTTPS_PORT} -servername admin.local 2>/dev/null \
    | openssl x509 -noout -text 2>/dev/null | grep -q 'admin.local'
"

if [[ "$fail" -ne 0 ]]; then
  echo "=== FAILED — see $LOG ==="
  docker compose -f "$COMPOSE_FILE" logs --tail=80 nginx || true
  exit 1
fi

echo "=== ALL CHECKS PASSED ==="
echo "log=$LOG"
