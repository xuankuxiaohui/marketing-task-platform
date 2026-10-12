#!/usr/bin/env bash
# P1 k6 full gate — NO scale reseed; fail-stop; for throwaway mkt continue/re-run.
# Staging compose, 5-minute soak. MUST NOT be invoked from routine PR CI.
# Heredoc note: never put `|| { … }` between <<'PY' and the heredoc body
# (bash → "syntax error: unexpected end of file"). Use `if ! …; then …; fi`.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/home/box/.local/jdk/jdk-26.0.2+10}"
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:${PATH:-}"

ENV_FILE="$ROOT/deploy/.env"
if [ ! -f "$ENV_FILE" ]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
fi

DATE_DIR="${REPORT_DATE:-$(date -u +%Y-%m-%d)}"
REPORT_DIR="$ROOT/perf/reports/${DATE_DIR}"
mkdir -p "$REPORT_DIR"
chmod a+rwx "$REPORT_DIR" "$ROOT/perf/reports" 2>/dev/null || true
DURATION="${DURATION:-5m}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"
NETWORK="${COMPOSE_NETWORK:-mkt_default}"
LOGDIR="${LOGDIR:-/workspace/r6-capacity-logs/run-full-noseed}"
mkdir -p "$LOGDIR"
RESULT="${RESULT:-$LOGDIR/run-full-result.txt}"
: > "$LOGDIR/run-full.log"

if [[ -n "${GITHUB_ACTIONS:-}" && "${ALLOW_K6_SOAK:-0}" != "1" ]]; then
  echo "refusing k6 soak in GitHub Actions (design §7.8 / R31.2). Not routine PR CI. Set ALLOW_K6_SOAK=1 only for a manual dispatch." >&2
  exit 2
fi

if [ "${SKIP_COMPOSE:-0}" != "1" ]; then
  bash "$ROOT/ci/e2e-compose.sh" --up-only
fi

# NO seed.py --scale (不重seed). Reuse existing perf/state.json.

echo "START $(date '+%Y-%m-%dT%H:%M:%S%z') tip=$(git rev-parse HEAD 2>/dev/null || true)" | tee -a "$LOGDIR/run-full.log"

run_k6() {
  local script="$1" name="$2"; shift 2
  docker ps -q --filter ancestor=grafana/k6:0.54.0 | xargs -r docker stop >/dev/null 2>&1 || true
  sleep 1
  echo "=== RUN $name $(date '+%Y-%m-%dT%H:%M:%S%z') ===" | tee -a "$LOGDIR/run-full.log"
  docker rm -f "mkt-k6-$name" >/dev/null 2>&1 || true
  docker run --name "mkt-k6-$name" --network "$NETWORK" \
    -v "$ROOT/perf:/scripts" -u 0 \
    -e BASE_URL=http://nginx -e INTERNAL_URL=http://portal-app-1:8081 \
    -e STATE_FILE=/scripts/state.json -e DURATION="$DURATION" \
    -e REPORT_DIR=/scripts/reports/${DATE_DIR} -w /scripts \
    "$@" \
    "$K6_IMAGE" run \
      --summary-export "/scripts/reports/${DATE_DIR}/${name}.summary.json" \
      "/scripts/${script}" \
    > "$LOGDIR/k6-${name}.log" 2>&1
  local docker_ec=$?
  docker rm -f "mkt-k6-$name" >/dev/null 2>&1 || true
  local ec=$docker_ec
  if command -v rg >/dev/null 2>&1; then
    if rg -q 'thresholds on metrics' "$LOGDIR/k6-${name}.log"; then ec=99; fi
    if rg -q "was aborted|signal: killed" "$LOGDIR/k6-${name}.log"; then ec=105; fi
  fi
  echo "=== DONE $name ec=$ec docker_ec=$docker_ec ===" | tee -a "$LOGDIR/run-full.log"
  tail -40 "$LOGDIR/k6-${name}.log" >> "$LOGDIR/run-full.log" || true
  return $ec
}

fail_stop() {
  local name="$1"
  echo "FAIL-STOP at $name $(date '+%Y-%m-%dT%H:%M:%S%z')" | tee "$RESULT"
  if command -v rg >/dev/null 2>&1; then
    rg -n 'thresholds|http_req_failed|dropped|p\(95\)|checks|✗|✓ \{' "$LOGDIR/k6-${name}.log" 2>/dev/null | tail -40 | tee -a "$RESULT" || true
  fi
  exit 0
}

run_k6 list.js list || fail_stop list
run_k6 advance.js advance || fail_stop advance

# Measurement hygiene: backdate today claims (does NOT raise dailyLimit). Needed after
# stripe expand uses shared fresh pool — prior scenarios burn daily quota.
python3 "$ROOT/perf/seed/clear_today_claim_counts.py" >>"$LOGDIR/run-full.log" 2>&1 || true
python3 "$ROOT/perf/seed/seed.py" --risk off >>"$LOGDIR/run-full.log" 2>&1 || true
run_k6 complete.js complete || { python3 "$ROOT/perf/seed/seed.py" --risk on >>"$LOGDIR/run-full.log" 2>&1 || true; fail_stop complete; }
python3 "$ROOT/perf/seed/seed.py" --risk on >>"$LOGDIR/run-full.log" 2>&1 || true

run_k6 track.js track || fail_stop track
run_k6 admin-list.js admin-list || fail_stop admin-list
run_k6 ad.js ad || fail_stop ad

# Per-leg claim hygiene before risk-on (shared pool).
python3 "$ROOT/perf/seed/clear_today_claim_counts.py" >>"$LOGDIR/run-full.log" 2>&1 || true
run_k6 risk-delta.js risk-on -e VARIANT=on -e RISK_POOL=shared || fail_stop risk-on
# Between legs: portal cold-restart (symmetric JIT/heap) — required measurement hygiene;
# do NOT skip restart and only flip order to game Δ.
bash "$ROOT/perf/seed/cold_restart_portals.sh" >>"$LOGDIR/run-full.log" 2>&1 || fail_stop cold-restart
python3 "$ROOT/perf/seed/clear_today_claim_counts.py" >>"$LOGDIR/run-full.log" 2>&1 || true
python3 "$ROOT/perf/seed/seed.py" --risk off >>"$LOGDIR/run-full.log" 2>&1 || true
# brief settle after cold restart
ok=0
for i in $(seq 1 18); do
  line=$(docker stats --no-stream --format '{{.Name}}={{.CPUPerc}}' mkt-mysql-1 mkt-portal-app-1-1 2>/dev/null)
  mysql_cpu=$(echo "$line" | rg 'mysql' | sed -E 's/.*=([0-9.]+)%.*/\1/')
  p1_cpu=$(echo "$line" | rg 'portal-app-1' | sed -E 's/.*=([0-9.]+)%.*/\1/')
  echo "settle-off $i mysql=${mysql_cpu}% p1=${p1_cpu}%" >>"$LOGDIR/run-full.log"
  python3 -c "open('/tmp/sokrf','w').write('1' if float('${mysql_cpu}' or 999)<8 and float('${p1_cpu}' or 999)<15 else '0')"
  if [ "$(cat /tmp/sokrf)" = 1 ]; then ok=$((ok+1)); else ok=0; fi
  if [ "$ok" -ge 3 ] && [ "$i" -ge 3 ]; then echo SETTLE_OFF_READY >>"$LOGDIR/run-full.log"; break; fi
  sleep 15
done
run_k6 risk-delta.js risk-off -e VARIANT=off -e RISK_POOL=shared || { python3 "$ROOT/perf/seed/seed.py" --risk on >>"$LOGDIR/run-full.log" 2>&1 || true; fail_stop risk-off; }
python3 "$ROOT/perf/seed/seed.py" --risk on >>"$LOGDIR/run-full.log" 2>&1 || true

# Fixed: if ! … heredoc …; then …; fi  (NOT  cmd <<PY || { … }  with body after)
if ! python3 - "$REPORT_DIR" "$LOGDIR" <<'PY' >>"$LOGDIR/run-full.log" 2>&1
import json, sys
from pathlib import Path
report_dir = Path(sys.argv[1])
logdir = Path(sys.argv[2])

def load(stem):
    for n in (f"{stem}.json", f"{stem}-after-173.json", f"{stem}-retest.json"):
        p = report_dir / n
        if p.exists():
            data = json.loads(p.read_text())
            if "p95" in data:
                return data
    for n in (f"{stem}.summary.json", f"{stem}-after-173.summary.json", f"{stem}-retest.summary.json"):
        p = report_dir / n
        if p.exists():
            s = json.loads(p.read_text())
            m = s.get("metrics", {}).get("http_req_duration", {})
            # summary-export shape: values nested or flat
            values = m.get("values", m) if isinstance(m, dict) else {}
            return {"p95": values.get("p(95)", 0)}
    raise FileNotFoundError(stem)

on = load("risk-on")
off = load("risk-off")
delta = float(on["p95"]) - float(off["p95"])
line = f"risk p95 on={on['p95']} off={off['p95']} delta={delta}"
print(line)
(logdir / "risk-delta.txt").write_text(
    f"on={on['p95']} off={off['p95']} delta={delta}\n")
if delta > 20:
    raise SystemExit(f"NFR 性能 4 broken: P95 delta {delta} > 20 ms")
PY
then
  echo "FAIL-STOP at risk-delta-gate $(date '+%Y-%m-%dT%H:%M:%S%z')" | tee "$RESULT"
  if [ -f "$LOGDIR/risk-delta.txt" ]; then
    cat "$LOGDIR/risk-delta.txt" | tee -a "$RESULT"
  fi
  exit 0
fi

echo "PASS-ALL $(date '+%Y-%m-%dT%H:%M:%S%z')" | tee "$RESULT"
