#!/usr/bin/env bash
# Daily full dump + binlog copy (R31.3 / 14-deployment §9). Run on the MySQL host or via compose exec.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
# shellcheck disable=SC1090
set -a && source "$ENV_FILE" && set +a

OUT_DIR="${BACKUP_DIR:-$ROOT/deploy/backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="$OUT_DIR/$STAMP"
mkdir -p "$DEST"

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml")

"${COMPOSE[@]}" exec -T mysql \
  mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --routines --events --triggers \
    --master-data=2 --flush-logs \
    "${MYSQL_DATABASE}" > "$DEST/full.sql"

"${COMPOSE[@]}" exec -T mysql sh -c 'ls /var/lib/mysql/mysql-bin.* 2>/dev/null || true' \
  | while read -r bin; do
      [ -z "$bin" ] && continue
      base="$(basename "$bin")"
      "${COMPOSE[@]}" exec -T mysql sh -c "cat '$bin'" > "$DEST/$base" || true
    done

sha256sum "$DEST"/* > "$DEST/SHA256SUMS"
echo "$DEST"
