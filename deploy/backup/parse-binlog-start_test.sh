#!/usr/bin/env bash
# Static fixture test for binlog-start parsing used by restore.sh / backup.sh.
# Does NOT connect to MySQL or run restore. Safe on any workstation.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
FIX="$DIR/.fixture-parse-binlog-start"
rm -rf "$FIX"
mkdir -p "$FIX"

# --- helpers mirrored from restore.sh (keep in sync) ---
parse_from_dump() {
  local sql="$1"
  local line file pos
  line="$(grep -E '^-- CHANGE (MASTER TO|REPLICATION SOURCE TO)' "$sql" | head -n1 || true)"
  [ -n "$line" ] || return 1
  file="$(printf '%s\n' "$line" | sed -n -E "s/.*(MASTER_LOG_FILE|SOURCE_LOG_FILE)='([^']+)'.*/\2/p")"
  pos="$(printf '%s\n' "$line" | sed -n -E "s/.*(MASTER_LOG_POS|SOURCE_LOG_POS)=([0-9]+).*/\2/p")"
  [ -n "$file" ] && [ -n "$pos" ] || return 1
  printf '%s\t%s\n' "$file" "$pos"
}

filter_bins_from() {
  local start_file="$1"
  shift
  local seen=0 b base
  for b in "$@"; do
    base="$(basename "$b")"
    if [ "$base" = "$start_file" ]; then
      seen=1
      printf '%s\n' "$b"
    elif [ "$seen" -eq 1 ]; then
      printf '%s\n' "$b"
    fi
  done
  [ "$seen" -eq 1 ]
}

fail=0
assert_eq() {
  local label="$1" got="$2" want="$3"
  if [ "$got" = "$want" ]; then
    echo "OK  $label"
  else
    echo "FAIL $label: got=[$got] want=[$want]" >&2
    fail=1
  fi
}

# 1) Classic CHANGE MASTER
cat > "$FIX/classic.sql" <<'SQL'
-- MySQL dump
-- CHANGE MASTER TO MASTER_LOG_FILE='mysql-bin.000003', MASTER_LOG_POS=157;
SQL
got="$(parse_from_dump "$FIX/classic.sql")"
assert_eq "classic CHANGE MASTER" "$got" $'mysql-bin.000003\t157'

# 2) MySQL 8.0.26+ CHANGE REPLICATION SOURCE
cat > "$FIX/source.sql" <<'SQL'
-- CHANGE REPLICATION SOURCE TO SOURCE_LOG_FILE='binlog.000012', SOURCE_LOG_POS=456;
SQL
got="$(parse_from_dump "$FIX/source.sql")"
assert_eq "CHANGE REPLICATION SOURCE" "$got" $'binlog.000012\t456'

# 3) BINLOG_START sidecar preferred shape
printf '%s\t%s\n' 'mysql-bin.000005' '1024' > "$FIX/BINLOG_START"
got="$(IFS=$'\t' read -r f p < "$FIX/BINLOG_START"; printf '%s\t%s\n' "$f" "$p")"
assert_eq "BINLOG_START sidecar" "$got" $'mysql-bin.000005\t1024'

# 4) Filter archive chain from start file (skip earlier files)
touch "$FIX/mysql-bin.000002" "$FIX/mysql-bin.000003" "$FIX/mysql-bin.000004"
mapfile -t all < <(ls "$FIX"/mysql-bin.* | sort)
mapfile -t filtered < <(filter_bins_from 'mysql-bin.000003' "${all[@]}")
got="$(printf '%s,' "${filtered[@]##*/}")"
assert_eq "filter from start file" "$got" 'mysql-bin.000003,mysql-bin.000004,'

# 5) Missing CHANGE comment must fail parse
cat > "$FIX/empty.sql" <<'SQL'
-- no coordinates here
SQL
if parse_from_dump "$FIX/empty.sql" >/dev/null 2>&1; then
  echo "FAIL empty dump should not parse" >&2
  fail=1
else
  echo "OK  empty dump refuses parse"
fi

# 6) restore.sh dry guards (no docker): missing confirm + missing start
if RESTORE_I_ACCEPT_DATA_LOSS= "$DIR/restore.sh" "$FIX" >/dev/null 2>&1; then
  echo "FAIL restore without confirm should exit non-zero" >&2
  fail=1
else
  echo "OK  restore refuses without RESTORE_I_ACCEPT_DATA_LOSS"
fi

rm -rf "$FIX"

if [ "$fail" -ne 0 ]; then
  echo "parse-binlog-start_test: FAILED" >&2
  exit 1
fi
echo "parse-binlog-start_test: PASSED"
