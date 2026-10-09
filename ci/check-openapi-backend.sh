#!/usr/bin/env bash
# F12: live backend OpenAPI JSON must match committed web/packages/shared/openapi/*.json,
# then JSON→TS via check-openapi-types.sh.
#
# Compose defaults SPRING_PROFILES_ACTIVE=prod, which closes springdoc (#99).
# This gate forces a non-prod profile name with no application-*.yml override so
# application.yml keeps api-docs enabled. Never expect prod Nginx /v3/api-docs 200.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

SPEC_DIR="$ROOT/web/packages/shared/openapi"
# Never name this GROUPS: bash treats GROUPS as a special readonly array of GIDs;
# assignments are ignored, so the loop would look for "$LIVE_DIR/1001.json" etc.
SPEC_GROUPS=(admin portal internal)
if [ "${#SPEC_GROUPS[@]}" -ne 3 ] || [ "${SPEC_GROUPS[*]}" != "admin portal internal" ]; then
  echo "SPEC_GROUPS must be exactly: admin portal internal (got: ${SPEC_GROUPS[*]-})" >&2
  echo "Do not rename SPEC_GROUPS back to GROUPS — bash GROUPS is special." >&2
  exit 1
fi

json_equal() {
  python3 - "$1" "$2" <<'PY'
import json, sys
a = json.load(open(sys.argv[1], encoding="utf-8"))
b = json.load(open(sys.argv[2], encoding="utf-8"))
if a != b:
    sys.stderr.write(f"OpenAPI JSON drift: {sys.argv[1]} != {sys.argv[2]}\n")
    sys.exit(1)
PY
}

self_test() {
  # Regression: SPEC_GROUPS must stay a real name list (bash GROUPS is GIDs).
  if [ "${#SPEC_GROUPS[@]}" -ne 3 ] || [ "${SPEC_GROUPS[*]}" != "admin portal internal" ]; then
    echo "self-test SPEC_GROUPS broken: ${SPEC_GROUPS[*]-}" >&2
    exit 1
  fi
  if [ "${SPEC_GROUPS[*]}" = "${GROUPS[*]-}" ]; then
    echo "self-test: SPEC_GROUPS must not equal bash GROUPS (${GROUPS[*]-})" >&2
    exit 1
  fi
  local dir
  dir="$(mktemp -d)"
  trap 'rm -rf "$dir"' RETURN
  printf '%s\n' '{"openapi":"3.1.0","paths":{"/a":{}}}' >"$dir/a.json"
  cp "$dir/a.json" "$dir/b.json"
  json_equal "$dir/a.json" "$dir/b.json"
  printf '%s\n' '{"openapi":"3.1.0","paths":{"/a":{},"/drift":{}}}' >"$dir/b.json"
  if json_equal "$dir/a.json" "$dir/b.json" 2>/dev/null; then
    echo "self-test expected drift to fail" >&2
    exit 1
  fi
  echo "openapi-backend self-test ok"
}

if [ "${1:-}" = "--self-test" ]; then
  self_test
  exit 0
fi

export JAVA_HOME="${JAVA_HOME:-/home/box/.local/jdk/jdk-26.0.2+10}"
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:${PATH:-}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker required for backend→JSON OpenAPI gate" >&2
  exit 1
fi

ENV_FILE="$ROOT/deploy/.env"
if [ ! -f "$ENV_FILE" ]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
fi

# Non-prod profile so springdoc stays on (empty would fall back to compose :-prod).
TMP_ENV="$(mktemp)"
trap 'rm -f "$TMP_ENV"' EXIT
cp "$ENV_FILE" "$TMP_ENV"
if grep -q '^SPRING_PROFILES_ACTIVE=' "$TMP_ENV"; then
  sed -i 's/^SPRING_PROFILES_ACTIVE=.*/SPRING_PROFILES_ACTIVE=openapi/' "$TMP_ENV"
else
  printf '\nSPRING_PROFILES_ACTIVE=openapi\n' >>"$TMP_ENV"
fi

set -a
# shellcheck disable=SC1090
source "$TMP_ENV"
set +a

if [ "${REDIS_DATABASE}" != "2" ]; then
  echo "REDIS_DATABASE must be 2" >&2
  exit 1
fi
if [ "${SPRING_PROFILES_ACTIVE}" = "prod" ]; then
  echo "SPRING_PROFILES_ACTIVE must not be prod for OpenAPI export" >&2
  exit 1
fi

ADMIN_JAR="$ROOT/server/admin-app/target/admin-app-0.1.0-SNAPSHOT.jar"
PORTAL_JAR="$ROOT/server/portal-app/target/portal-app-0.1.0-SNAPSHOT-exec.jar"
if [ ! -f "$ADMIN_JAR" ] || [ ! -f "$PORTAL_JAR" ]; then
  (cd "$ROOT/server" && mvn -q -DskipTests -DskipITs package)
fi

export COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-mkt-openapi}"
COMPOSE=(docker compose --env-file "$TMP_ENV" -f "$ROOT/deploy/docker-compose.yml")

dump_stack() {
  echo "openapi-backend compose failed; dumping admin-app / portal-app / mysql / redis" >&2
  "${COMPOSE[@]}" ps -a || true
  "${COMPOSE[@]}" logs --no-color --tail=200 admin-app || true
  "${COMPOSE[@]}" logs --no-color --tail=120 portal-app-1 || true
  "${COMPOSE[@]}" logs --no-color --tail=40 mysql redis || true
}

cleanup() {
  "${COMPOSE[@]}" down --remove-orphans >/dev/null 2>&1 || true
  rm -f "$TMP_ENV"
}
trap 'dump_stack; cleanup' ERR
trap cleanup EXIT

"${COMPOSE[@]}" up -d --build mysql redis admin-app portal-app-1

wait_stack() {
  local tries=60 i
  for i in $(seq 1 "$tries"); do
    if "${COMPOSE[@]}" exec -T admin-app curl -fsS http://127.0.0.1:8080/actuator/health/readiness 2>/dev/null | grep -q UP \
      && "${COMPOSE[@]}" exec -T portal-app-1 curl -fsS http://127.0.0.1:8081/actuator/health/readiness 2>/dev/null | grep -q UP; then
      return 0
    fi
    sleep 5
  done
  echo "openapi-backend stack not healthy" >&2
  "${COMPOSE[@]}" ps
  return 1
}

wait_stack

# Prove springdoc is on under the non-prod profile (prod would 404 / empty).
if ! "${COMPOSE[@]}" exec -T admin-app curl -fsS http://127.0.0.1:8080/admin/v3/api-docs/admin | grep -q '"openapi"'; then
  echo "admin OpenAPI export missing; refused to use prod/closed springdoc" >&2
  exit 1
fi

LIVE_DIR="$(mktemp -d)"
trap 'rm -rf "$LIVE_DIR"; cleanup' EXIT

"${COMPOSE[@]}" exec -T admin-app curl -fsS http://127.0.0.1:8080/admin/v3/api-docs/admin >"$LIVE_DIR/admin.json"
"${COMPOSE[@]}" exec -T portal-app-1 curl -fsS http://127.0.0.1:8081/api/v3/api-docs/portal >"$LIVE_DIR/portal.json"
"${COMPOSE[@]}" exec -T portal-app-1 curl -fsS http://127.0.0.1:8081/api/v3/api-docs/internal >"$LIVE_DIR/internal.json"

# Match gen-api:fetch: validate JSON + trailing newline when rewriting would be needed.
for name in "${SPEC_GROUPS[@]}"; do
  python3 -c "import json,sys; json.load(open(sys.argv[1],encoding='utf-8'))" "$LIVE_DIR/$name.json"
  # Ensure trailing newline like gen-api.mjs writeFileSync(`${body}\n`)
  if [ -s "$LIVE_DIR/$name.json" ] && [ "$(tail -c1 "$LIVE_DIR/$name.json" | wc -l)" -eq 0 ]; then
    printf '\n' >>"$LIVE_DIR/$name.json"
  fi
  json_equal "$LIVE_DIR/$name.json" "$SPEC_DIR/$name.json"
done

echo "backend→JSON OpenAPI match ok (admin/portal/internal)"

# JSON→TS gate (existing).
bash "$ROOT/ci/check-openapi-types.sh"

echo "openapi-backend chain ok"
