#!/usr/bin/env bash
# Measurement hygiene: cold-restart portal replicas between risk-on/off legs
# so JIT/heap heat does not asymmetrically skew DEC-006 Δ. Does NOT change gates.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE=(docker compose --env-file "$ROOT/deploy/.env" -f "$ROOT/deploy/docker-compose.yml" -p mkt)
echo "cold-restart portals $(date '+%Y-%m-%dT%H:%M:%S%z')"
"${COMPOSE[@]}" restart portal-app-1 portal-app-2
# wait healthy (compose healthcheck)
for i in $(seq 1 60); do
  s1=$(docker inspect --format '{{.State.Health.Status}}' mkt-portal-app-1-1 2>/dev/null || echo missing)
  s2=$(docker inspect --format '{{.State.Health.Status}}' mkt-portal-app-2-1 2>/dev/null || echo missing)
  echo "health $i p1=$s1 p2=$s2"
  if [ "$s1" = healthy ] && [ "$s2" = healthy ]; then
    echo COLD_RESTART_READY
    exit 0
  fi
  sleep 5
done
echo COLD_RESTART_TIMEOUT >&2
exit 1
