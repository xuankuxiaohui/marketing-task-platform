#!/usr/bin/env bash
# Daily full dump + binlog copy (R31.3 / 14-deployment §9). Run on the MySQL host or via compose exec.
# Writes BINLOG_START (file\tpos) parsed from --master-data=2 so restore.sh does not guess.
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

# Sidecar coordinates for restore (also left in full.sql as CHANGE MASTER/SOURCE comment).
START_LINE="$(grep -E '^-- CHANGE (MASTER TO|REPLICATION SOURCE TO)' "$DEST/full.sql" | head -n1 || true)"
if [ -n "$START_LINE" ]; then
  START_FILE="$(printf '%s\n' "$START_LINE" | sed -n -E "s/.*(MASTER_LOG_FILE|SOURCE_LOG_FILE)='([^']+)'.*/\2/p")"
  START_POS="$(printf '%s\n' "$START_LINE" | sed -n -E "s/.*(MASTER_LOG_POS|SOURCE_LOG_POS)=([0-9]+).*/\2/p")"
  if [ -n "${START_FILE:-}" ] && [ -n "${START_POS:-}" ]; then
    printf '%s\t%s\n' "$START_FILE" "$START_POS" > "$DEST/BINLOG_START"
    echo "BINLOG_START $START_FILE $START_POS"
  else
    echo "warning: could not parse binlog coordinates from dump comment" >&2
  fi
else
  echo "warning: dump has no CHANGE MASTER/SOURCE comment; restore will refuse binlog replay" >&2
fi

# Copy visible binlog files. Fail the backup if a listed file cannot be read
# (incomplete archive is worse than a loud failure). Continuous shipping still
# needs a separate archival job; this is a point-in-time companion copy only.
while read -r bin; do
  [ -z "$bin" ] && continue
  base="$(basename "$bin")"
  # skip index sidecar; not a binlog stream
  case "$base" in *.index) continue ;; esac
  # </dev/null: docker compose exec otherwise steals while-read stdin and only first binlog is copied
  "${COMPOSE[@]}" exec -T mysql sh -c "cat '$bin'" < /dev/null > "$DEST/$base"
done < <("${COMPOSE[@]}" exec -T mysql sh -c 'ls /var/lib/mysql/mysql-bin.* /var/lib/mysql/binlog.* 2>/dev/null || true')

sha256sum "$DEST"/* > "$DEST/SHA256SUMS"
echo "$DEST"
