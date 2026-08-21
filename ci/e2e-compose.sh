#!/usr/bin/env bash
# Task 43: bring up staging compose and run Playwright journey-core / journey-admin.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/home/box/.local/jdk/jdk-26.0.2+10}"
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:${PATH:-}"

ENV_FILE="$ROOT/deploy/.env"
if [ ! -f "$ENV_FILE" ]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
fi
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [ "${REDIS_DATABASE}" != "2" ]; then
  echo "REDIS_DATABASE must be 2" >&2
  exit 1
fi

ADMIN_JAR="$ROOT/server/admin-app/target/admin-app-0.1.0-SNAPSHOT.jar"
PORTAL_JAR="$ROOT/server/portal-app/target/portal-app-0.1.0-SNAPSHOT-exec.jar"
if [ ! -f "$ADMIN_JAR" ] || [ ! -f "$PORTAL_JAR" ]; then
  (cd "$ROOT/server" && mvn -q -DskipTests -DskipITs package)
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml")

dump_stack() {
  echo "compose failed; dumping admin-app / portal-app / mysql / redis" >&2
  "${COMPOSE[@]}" ps -a || true
  "${COMPOSE[@]}" logs --no-color --tail=400 admin-app || true
  "${COMPOSE[@]}" logs --no-color --tail=200 portal-app-1 portal-app-2 || true
  "${COMPOSE[@]}" logs --no-color --tail=80 mysql redis || true
}
trap dump_stack ERR

"${COMPOSE[@]}" up -d --build

wait_stack() {
  local tries=60
  local i
  for i in $(seq 1 "$tries"); do
    if curl -fsS "http://127.0.0.1:${NGINX_HTTP_PORT:-18080}/healthz" >/dev/null 2>&1 \
      && "${COMPOSE[@]}" exec -T admin-app curl -fsS http://127.0.0.1:8080/actuator/health/readiness | grep -q UP \
      && "${COMPOSE[@]}" exec -T portal-app-1 curl -fsS http://127.0.0.1:8081/actuator/health/readiness | grep -q UP \
      && "${COMPOSE[@]}" exec -T portal-app-2 curl -fsS http://127.0.0.1:8081/actuator/health/readiness | grep -q UP; then
      return 0
    fi
    sleep 5
  done
  echo "stack not healthy" >&2
  "${COMPOSE[@]}" ps
  return 1
}

wait_stack

if [ "${1:-}" = "--up-only" ]; then
  echo "compose up ok"
  exit 0
fi

export E2E_API_BASE="http://127.0.0.1:${NGINX_HTTP_PORT:-18080}"
export PLAYWRIGHT_BASE_URL="${PLAYWRIGHT_BASE_URL:-http://127.0.0.1:5174}"
export PLAYWRIGHT_ADMIN_BASE_URL="${PLAYWRIGHT_ADMIN_BASE_URL:-http://127.0.0.1:5173}"
export PORTAL_PROXY_TARGET="$E2E_API_BASE"
export ADMIN_PROXY_TARGET="$E2E_API_BASE"

(cd "$ROOT/web" && pnpm install --frozen-lockfile)
(cd "$ROOT/web" && pnpm exec playwright install --with-deps chromium)
(cd "$ROOT/web" && pnpm test:e2e)

if [ -n "${CI:-}" ]; then
  "${COMPOSE[@]}" down
fi

echo "e2e ok"
