#!/usr/bin/env bash
# P1 k6 full gate (design §7.8 NFR 性能 1–8 + §2.6 capacity).
# Staging compose, 5-minute soak. MUST NOT be invoked from routine PR CI.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/home/box/.local/jdk/jdk-26.0.2+10}"
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:${PATH:-}"

ENV_FILE="$ROOT/deploy/.env"
if [ ! -f "$ENV_FILE" ]; then
  cp "$ROOT/deploy/.env.example" "$ENV_FILE"
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -f "$ROOT/deploy/docker-compose.yml")
DATE_DIR="${REPORT_DATE:-$(date -u +%Y-%m-%d)}"
REPORT_DIR="$ROOT/perf/reports/${DATE_DIR}"
mkdir -p "$REPORT_DIR"
DURATION="${DURATION:-5m}"
SEED_SCALE="${SEED_SCALE:-p1}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"
NETWORK="${COMPOSE_NETWORK:-mkt_default}"

if [[ -n "${GITHUB_ACTIONS:-}" && "${ALLOW_K6_SOAK:-0}" != "1" ]]; then
  echo "refusing k6 soak in GitHub Actions (design §7.8 / R31.2). Not routine PR CI. Set ALLOW_K6_SOAK=1 only for a manual dispatch." >&2
  exit 2
fi

if [ "${SKIP_COMPOSE:-0}" != "1" ]; then
  bash "$ROOT/ci/e2e-compose.sh" --up-only
fi

python3 "$ROOT/perf/seed/seed.py" --scale "$SEED_SCALE"

run_k6() {
  local script="$1"
  local name="$2"
  shift 2
  docker run --rm \
    --network "$NETWORK" \
    -v "$ROOT/perf:/scripts" \
    -e BASE_URL=http://nginx \
    -e INTERNAL_URL=http://portal-app-1:8081 \
    -e STATE_FILE=/scripts/state.json \
    -e DURATION="$DURATION" \
    -e REPORT_DIR=/scripts/reports/${DATE_DIR} \
    -w /scripts \
    "$@" \
    "$K6_IMAGE" run \
      --summary-export "/scripts/reports/${DATE_DIR}/${name}.summary.json" \
      "/scripts/${script}"
}

run_k6 list.js list
run_k6 advance.js advance
run_k6 complete.js complete
run_k6 track.js track
run_k6 admin-list.js admin-list
run_k6 ad.js ad

run_k6 risk-delta.js risk-on -e VARIANT=on
python3 "$ROOT/perf/seed/seed.py" --risk off
run_k6 risk-delta.js risk-off -e VARIANT=off
python3 "$ROOT/perf/seed/seed.py" --risk on

python3 - "$REPORT_DIR" <<'PY'
import json, sys
from pathlib import Path
report_dir = Path(sys.argv[1])
on = json.loads((report_dir / "risk-on.json").read_text())
off = json.loads((report_dir / "risk-off.json").read_text())
delta = float(on["p95"]) - float(off["p95"])
print(f"risk p95 on={on['p95']} off={off['p95']} delta={delta}")
if delta > 20:
    raise SystemExit(f"NFR 性能 4 broken: P95 delta {delta} > 20 ms")
PY

python3 "$ROOT/perf/seed/capacity_check.py" --report-dir "$REPORT_DIR"
python3 "$ROOT/perf/seed/explain_hot.py" --report-dir "$REPORT_DIR"

echo "k6 P1 full ok reports=$REPORT_DIR"
echo "5m soak is a release signing item; not routine PR CI"
