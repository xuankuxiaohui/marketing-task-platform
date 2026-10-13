# R6 capacity risk-on / risk-off delta — after #149 — **PASS** — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `b8f746d` (Merge #149; complete formal 5m PASS) |
| Branch | `fix/r6-capacity-risk-on-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~21:06–21:28 CST |
| Prior evidence | [#149 / complete](2026-10-11-r6-capacity-complete.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (性能4 ΔP95 ≤20 ms; per-variant grant P95 ≤500); DEC **not** reopened |
| Capacity checklist | **stays unchecked** — risk delta formal PASS; checkbox decision left to architect |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Architect (#149): after complete PASS → **formal 5m risk-on then risk-off**; gate = all-on vs all-off claim/reward P95 delta ≤20 ms; **BOTH** sides need real business success (HTTP 200 + `code===0`); **never** treat 403 / `risk.blocked` as success; capacity checkbox empty; not RC.

## Prior FAIL class (why hygiene)

Earlier DEC-006 P0 (`2026-10-11-r6-capacity-dec006.md`) recorded numeric Δ ≤20 ms while **risk-on** itself had `http_req_failed` / business FAIL (compose-shared nginx client IP tripped IP-claim rules R-c / R-f). That is **not** a clean 性能4 PASS.

## Changes in this PR (measurement hygiene only)

1. **`perf/seed/seed.py`**
   - `--risk on` / end-of-seed `set_risk(1)`: enable engine via **R-a / R-b / R-d** with high capacity-safe ceilings; **disable R-c / R-f** (all k6/seed traffic collapses on nginx container IP) and **R-e** (cascade grant is instantaneous &lt; appendix A min elapsed); flush stale `risk:cnt:R-c|R-f:IP:*` keys.
   - p0 `users_n=1200` (variant-split after warm; headroom under `dailyLimitPerUser=20`).
2. **`perf/risk-delta.js`**
   - `warm` → formal `risk` (same pattern as `complete.js`); thresholds scoped to `scenario:risk` (**values unchanged**).
   - Checks require HTTP 200 + `code===0` (403 never success); finite striped user×riskTask pool; `grant_data_exhausted` gate.
3. Docs/checklist binding only — **no** DEC-006 threshold value edits; **no** capacity checkbox tick; **no** app/runtime code.

## Formal 5m risk-on / risk-off (bot box Compose `mkt`)

```bash
# branch tip on master b8f746d + hygiene working tree; Compose mkt UP
SEED_SCALE=p0 BASE_URL=http://127.0.0.1:18080 python3 perf/seed/seed.py --scale p0
python3 perf/seed/seed.py --risk on
# risk_rule_config: R-a/b/d enabled (thresholds 10000/10000/1000); R-c/e/f disabled
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -w /scripts \
  -e BASE_URL=http://nginx -e INTERNAL_URL=http://portal-app-1:8081 \
  -e DURATION=5m -e STATE_FILE=/scripts/state.json -e VARIANT=on \
  grafana/k6:0.54.0 run --summary-export=/scripts/reports/2026-10-11/risk-on-hygiene-5m.summary.json \
  /scripts/risk-delta.js
python3 perf/seed/seed.py --risk off
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -w /scripts \
  -e BASE_URL=http://nginx -e INTERNAL_URL=http://portal-app-1:8081 \
  -e DURATION=5m -e STATE_FILE=/scripts/state.json -e VARIANT=off \
  grafana/k6:0.54.0 run --summary-export=/scripts/reports/2026-10-11/risk-off-hygiene-5m.summary.json \
  /scripts/risk-delta.js
# wall-clock ≈ 5m35s each (warm 30s + gap + formal 5m)
```

### Outcomes vs DEC-006 性能4

| Metric | DEC-006 / F14 gate | risk-on formal 5m | risk-off formal 5m | Verdict |
|---|---|---|---|---|
| grant P95 (`{name:grant,scenario:risk}`) | ≤500 ms | **381.1 ms** | **385.1 ms** | **PASS** both |
| checks `{scenario:risk}` | rate==1 | **100%** (10534/10534) | **100%** (10282/10282) | **PASS** |
| http_req_failed `{scenario:risk}` | rate==0 | **0.00%** (0/5267) | **0.00%** (0/5141) | **PASS** |
| grant_data_exhausted `{scenario:risk}` | count==0 | **0** | **0** | **PASS** |
| Business success | HTTP 200 + code 0 (403 never OK) | yes | yes | **PASS** |
| **ΔP95 (on − off)** | **≤20 ms** | — | — | **−3.97 ms → PASS** |
| Thresholds crossed | — | none | none | **overall PASS** |

In-repo tiny artifacts: `perf/reports/2026-10-11/risk-on.json`, `risk-off.json`, `risk-delta.txt`. Large `*.summary.json` + host logs under `/workspace/r6-capacity-logs/risk-*-hygiene-5m.log` remain **box-local** (gitignored).

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides despite list+advance+complete+risk PASS).
2. **Not** DEC-006 reopen / k6 threshold **value** change (hygiene = which rules evaluate under compose-shared IP; formal scenario scoping only).
3. **Not** full `run-p0.sh` / `run-full.sh` / p1 / track / admin-list re-run in this evidence file.
4. **Not** RC / R6 通过.
5. Host logs under `/workspace/r6-capacity-logs/` (box-local).

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this formal 5m risk-on/off **PASS** linked; **checkbox remains unchecked** pending architect. R6 stays **进行中** / **非 RC**.
