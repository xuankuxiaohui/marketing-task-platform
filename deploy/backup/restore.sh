#!/usr/bin/env bash
# Restore a full dump, then apply binlog from the dump's recorded start point
# until optional STOP_DATETIME (R31.3 PITR).
#
# Usage:
#   RESTORE_I_ACCEPT_DATA_LOSS=YES STOP_DATETIME='2026-08-20 12:00:00' \
#     ./restore.sh deploy/backups/<stamp>
#
# Safety: refuses to run unless RESTORE_I_ACCEPT_DATA_LOSS=YES (isolated target only).
# Does not invent a start point: coordinates come from mysqldump --master-data=2
# (CHANGE MASTER / CHANGE REPLICATION SOURCE comment) or backup BINLOG_START file.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SRC="${1:?usage: restore.sh <backup-dir>}"
STOP_DATETIME="${STOP_DATETIME:-}"

if [ "${RESTORE_I_ACCEPT_DATA_LOSS:-}" != "YES" ]; then
  echo "refusing restore: set RESTORE_I_ACCEPT_DATA_LOSS=YES only after confirming an isolated target DB" >&2
  echo "(see deploy/backup/RESTORE-DRILL.md). Refusing prevents overwrite of shared/dev data." >&2
  exit 2
fi

if [ ! -f "$SRC/full.sql" ]; then
  echo "missing $SRC/full.sql" >&2
  exit 1
fi

ENV_FILE="${ENV_FILE:-$ROOT/deploy/.env}"
# shellcheck disable=SC1090
set -a && source "$ENV_FILE" && set +a

if [ -f "$SRC/SHA256SUMS" ]; then
  echo "verifying SHA256SUMS..."
  (cd "$SRC" && sha256sum -c SHA256SUMS)
else
  echo "warning: no SHA256SUMS in $SRC; integrity not verified" >&2
fi

# Parse binlog start coordinates written by mysqldump --master-data=2.
# Prefer sidecar BINLOG_START (file\tpos) written by backup.sh; fall back to dump comment.
parse_binlog_start() {
  local file="" pos=""
  if [ -f "$SRC/BINLOG_START" ]; then
    # format: <file>\t<pos>
    IFS=$'\t' read -r file pos < "$SRC/BINLOG_START" || true
  fi
  if [ -z "${file:-}" ] || [ -z "${pos:-}" ]; then
    # MySQL 5.7/8.0 classic:
    #   -- CHANGE MASTER TO MASTER_LOG_FILE='mysql-bin.000003', MASTER_LOG_POS=157;
    # MySQL 8.0.26+:
    #   -- CHANGE REPLICATION SOURCE TO SOURCE_LOG_FILE='...', SOURCE_LOG_POS=...;
    local line
    line="$(grep -E '^-- CHANGE (MASTER TO|REPLICATION SOURCE TO)' "$SRC/full.sql" | head -n1 || true)"
    if [ -n "$line" ]; then
      file="$(printf '%s\n' "$line" | sed -n -E "s/.*(MASTER_LOG_FILE|SOURCE_LOG_FILE)='([^']+)'.*/\2/p")"
      pos="$(printf '%s\n' "$line" | sed -n -E "s/.*(MASTER_LOG_POS|SOURCE_LOG_POS)=([0-9]+).*/\2/p")"
    fi
  fi
  if [ -z "${file:-}" ] || [ -z "${pos:-}" ]; then
    echo "cannot determine binlog start from BINLOG_START or full.sql CHANGE MASTER/SOURCE comment" >&2
    echo "refusing to replay from the first archived binlog (would duplicate dump contents)" >&2
    exit 1
  fi
  printf '%s\t%s\n' "$file" "$pos"
}

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml")

mapfile -t ALL_BINS < <(ls "$SRC"/mysql-bin.* "$SRC"/binlog.* 2>/dev/null | sort || true)
HAVE_BINS=0
if [ "${#ALL_BINS[@]}" -gt 0 ] && [ -n "${ALL_BINS[0]:-}" ]; then
  HAVE_BINS=1
fi

# Coordinates required only when replaying binlogs (dump-only may omit them).
START_FILE=""
START_POS=""
if [ "$HAVE_BINS" -eq 1 ]; then
  START_LINE="$(parse_binlog_start)"
  START_FILE="$(printf '%s\n' "$START_LINE" | cut -f1)"
  START_POS="$(printf '%s\n' "$START_LINE" | cut -f2)"
  echo "binlog start: file=$START_FILE pos=$START_POS"
fi

echo "importing full dump..."
"${COMPOSE[@]}" exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" < "$SRC/full.sql"

if [ "$HAVE_BINS" -ne 1 ]; then
  echo "restored $SRC (dump only; no archived binlog files)"
  exit 0
fi

# Keep only binlog files at/after START_FILE (lexical order matches mysql-bin.NNNNNN).
BINS=()
SEEN_START=0
for b in "${ALL_BINS[@]}"; do
  base="$(basename "$b")"
  if [ "$base" = "$START_FILE" ]; then
    SEEN_START=1
    BINS+=("$b")
  elif [ "$SEEN_START" -eq 1 ]; then
    BINS+=("$b")
  fi
done

if [ "$SEEN_START" -ne 1 ]; then
  echo "archived binlogs do not include start file $START_FILE; incomplete archive chain" >&2
  exit 1
fi

echo "replaying ${#BINS[@]} binlog file(s) from $START_FILE:$START_POS${STOP_DATETIME:+ until $STOP_DATETIME}..."

# First file starts at recorded position; later files from the beginning.
FIRST=1
for b in "${BINS[@]}"; do
  ARGS=(mysqlbinlog)
  if [ "$FIRST" -eq 1 ]; then
    ARGS+=(--start-position="$START_POS")
    FIRST=0
  fi
  if [ -n "$STOP_DATETIME" ]; then
    ARGS+=(--stop-datetime="$STOP_DATETIME")
  fi
  "${ARGS[@]}" "$b" \
    | "${COMPOSE[@]}" exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}"
done

echo "restored $SRC from $START_FILE:$START_POS${STOP_DATETIME:+ until $STOP_DATETIME}"
