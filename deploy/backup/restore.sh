#!/usr/bin/env bash
# Restore a full dump, then optionally apply binlog until STOP_DATETIME (R31.3 PITR).
# Usage: STOP_DATETIME='2026-08-20 12:00:00' ./restore.sh deploy/backups/<stamp>
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
# shellcheck disable=SC1090
set -a && source "$ENV_FILE" && set +a

SRC="${1:?usage: restore.sh <backup-dir>}"
STOP_DATETIME="${STOP_DATETIME:-}"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml")

"${COMPOSE[@]}" exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "$SRC/full.sql"

mapfile -t BINS < <(ls "$SRC"/mysql-bin.* 2>/dev/null | sort || true)
if [ "${#BINS[@]}" -gt 0 ]; then
  ARGS=(mysqlbinlog)
  if [ -n "$STOP_DATETIME" ]; then
    ARGS+=(--stop-datetime="$STOP_DATETIME")
  fi
  "${ARGS[@]}" "${BINS[@]}" \
    | "${COMPOSE[@]}" exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
fi

echo "restored $SRC ${STOP_DATETIME:+until $STOP_DATETIME}"
