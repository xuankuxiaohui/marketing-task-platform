#!/usr/bin/env bash
# DEC-006 paired risk-on/off with per-leg claim hygiene + settle + portal cold-restart
# between legs (measurement only). Does NOT lower Δ / raise dailyLimit / relax gates.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
LOGDIR="${LOGDIR:-/workspace/r6-capacity-logs/delta-cold-restart-after-193}"
DATE_DIR="${REPORT_DATE:-$(date -u +%Y-%m-%d)}"
REPORT_DIR="$ROOT/perf/reports/${DATE_DIR}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"
NETWORK="${COMPOSE_NETWORK:-mkt_default}"
DURATION="${DURATION:-5m}"
mkdir -p "$LOGDIR" "$REPORT_DIR"
chmod a+rwx "$REPORT_DIR" 2>/dev/null || true
RESULT="${RESULT:-$LOGDIR/paired-result.txt}"
: > "$LOGDIR/paired.log"
echo "START $(date '+%Y-%m-%dT%H:%M:%S%z') tip=$(git rev-parse --short HEAD)" | tee -a "$LOGDIR/paired.log"

settle() {
  local tag="$1"
  local ok=0
  date | tee "$LOGDIR/settle-start-$tag.txt"
  : > "$LOGDIR/settle-poll-$tag.txt"
  for i in $(seq 1 30); do
    line=$(docker stats --no-stream --format '{{.Name}}={{.CPUPerc}}' mkt-mysql-1 mkt-portal-app-1-1 2>/dev/null)
    mysql_cpu=$(echo "$line" | rg 'mysql' | sed -E 's/.*=([0-9.]+)%.*/\1/')
    p1_cpu=$(echo "$line" | rg 'portal-app-1' | sed -E 's/.*=([0-9.]+)%.*/\1/')
    echo "settle $tag $i mysql=${mysql_cpu}% p1=${p1_cpu}%" | tee -a "$LOGDIR/settle-poll-$tag.txt"
    python3 -c "open('/tmp/sokdelta','w').write('1' if float('${mysql_cpu}' or 999)<8 and float('${p1_cpu}' or 999)<15 else '0')"
    if [ "$(cat /tmp/sokdelta)" = 1 ]; then ok=$((ok+1)); else ok=0; fi
    if [ "$ok" -ge 3 ] && [ "$i" -ge 6 ]; then
      echo "SETTLE_READY $tag sample=$i" | tee "$LOGDIR/settle-ready-$tag.txt"
      date | tee "$LOGDIR/settle-end-$tag.txt"
      return 0
    fi
    sleep 20
  done
  echo "SETTLE_TIMEOUT $tag proceeding" | tee "$LOGDIR/settle-ready-$tag.txt"
  date | tee "$LOGDIR/settle-end-$tag.txt"
}

run_k6() {
  local script="$1" name="$2"; shift 2
  docker ps -q --filter ancestor=$K6_IMAGE | xargs -r docker stop >/dev/null 2>&1 || true
  sleep 1
  docker rm -f "mkt-k6-$name" >/dev/null 2>&1 || true
  echo "=== RUN $name $(date '+%Y-%m-%dT%H:%M:%S%z') ===" | tee -a "$LOGDIR/paired.log"
  docker run --name "mkt-k6-$name" --network "$NETWORK" \
    -v "$ROOT/perf:/scripts" -u 0 \
    -e BASE_URL=http://nginx -e INTERNAL_URL=http://portal-app-1:8081 \
    -e STATE_FILE=/scripts/state.json -e DURATION="$DURATION" \
    -e REPORT_DIR=/scripts/reports/${DATE_DIR} -w /scripts \
    "$@" \
    "$K6_IMAGE" run \
      --summary-export "/scripts/reports/${DATE_DIR}/${name}-cold-restart-after-193.summary.json" \
      "/scripts/${script}" \
    > "$LOGDIR/k6-${name}.log" 2>&1
  local docker_ec=$?
  docker rm -f "mkt-k6-$name" >/dev/null 2>&1 || true
  local ec=$docker_ec
  if rg -q 'thresholds on metrics' "$LOGDIR/k6-${name}.log"; then ec=99; fi
  if rg -q 'was aborted|signal: killed' "$LOGDIR/k6-${name}.log"; then ec=105; fi
  echo "=== DONE $name ec=$ec ===" | tee -a "$LOGDIR/paired.log"
  return $ec
}

# --- risk-on leg ---
python3 "$ROOT/perf/seed/clear_today_claim_counts.py" 2>&1 | tee "$LOGDIR/hygiene-before-on.log"
python3 "$ROOT/perf/seed/seed.py" --risk on 2>&1 | tee "$LOGDIR/risk-on-seed.log" || true
settle on
run_k6 risk-delta.js risk-on -e VARIANT=on -e RISK_POOL=shared
ON_EC=$?
echo RISK_ON_EXIT=$ON_EC | tee "$LOGDIR/risk-on-exit.txt"
if [ "$ON_EC" != 0 ]; then
  echo "FAIL-STOP at risk-on" | tee "$RESULT"
  exit 0
fi

# --- cold restart portals between legs (required; not order-only) ---
bash "$ROOT/perf/seed/cold_restart_portals.sh" 2>&1 | tee "$LOGDIR/cold-restart.log"
CR_EC=${PIPESTATUS[0]}
echo COLD_RESTART_EXIT=$CR_EC | tee "$LOGDIR/cold-restart-exit.txt"
if [ "$CR_EC" != 0 ]; then
  echo "FAIL-STOP at cold-restart" | tee "$RESULT"
  exit 0
fi

# --- risk-off leg ---
python3 "$ROOT/perf/seed/clear_today_claim_counts.py" 2>&1 | tee "$LOGDIR/hygiene-before-off.log"
python3 "$ROOT/perf/seed/seed.py" --risk off 2>&1 | tee "$LOGDIR/risk-off-seed.log" || true
settle off
run_k6 risk-delta.js risk-off -e VARIANT=off -e RISK_POOL=shared
OFF_EC=$?
echo RISK_OFF_EXIT=$OFF_EC | tee "$LOGDIR/risk-off-exit.txt"
python3 "$ROOT/perf/seed/seed.py" --risk on >>"$LOGDIR/paired.log" 2>&1 || true
if [ "$OFF_EC" != 0 ]; then
  echo "FAIL-STOP at risk-off" | tee "$RESULT"
  exit 0
fi

# --- Δ (禁降Δ: still ≤20) ---
if ! python3 - "$REPORT_DIR" "$LOGDIR" <<'PY' | tee -a "$LOGDIR/paired.log"
import json, sys
from pathlib import Path
report_dir, logdir = Path(sys.argv[1]), Path(sys.argv[2])
on_p = report_dir / "risk-on-cold-restart-after-193.summary.json"
off_p = report_dir / "risk-off-cold-restart-after-193.summary.json"
compact_on, compact_off = report_dir / "risk-on.json", report_dir / "risk-off.json"

def p95(summary_path, compact_path):
    if compact_path.exists():
        j = json.loads(compact_path.read_text())
        if j.get("p95") is not None:
            return float(j["p95"])
    s = json.loads(summary_path.read_text())
    m = s.get("metrics", {})
    for k, v in m.items():
        if "http_req_duration" in k and "grant" in k and "risk" in k:
            vals = v.get("values", v) if isinstance(v, dict) else {}
            if isinstance(vals, dict) and vals.get("p(95)") is not None:
                return float(vals["p(95)"])
    hd = m.get("http_req_duration", {})
    vals = hd.get("values", hd) if isinstance(hd, dict) else {}
    return float(vals.get("p(95)", 0))

on_p95 = p95(on_p, compact_on)
off_p95 = p95(off_p, compact_off)
delta = on_p95 - off_p95
line = f"risk p95 on={on_p95} off={off_p95} delta={delta}"
print(line)
(logdir / "risk-delta.txt").write_text(f"on={on_p95} off={off_p95} delta={delta}\n")
if delta > 20:
    print("FAIL-DELTA")
    raise SystemExit(1)
print("PASS-DELTA")
PY
then
  echo "FAIL at risk-delta-gate $(date '+%Y-%m-%dT%H:%M:%S%z')" | tee "$RESULT"
  cat "$LOGDIR/risk-delta.txt" 2>/dev/null | tee -a "$RESULT" || true
  exit 0
fi

echo "PASS paired risk-on/off/delta $(date '+%Y-%m-%dT%H:%M:%S%z')" | tee "$RESULT"
exit 0
