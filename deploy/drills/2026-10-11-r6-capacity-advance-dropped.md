# R6 capacity advance-callback — residual dropped cut after #147 — **PASS** — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `152734c` (Merge #147; list residual dropped PASS) |
| Branch | `fix/r6-capacity-advance-dropped-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~18:53–19:09 CST |
| Prior evidence | [#147 / list residual dropped](2026-10-11-r6-capacity-list-dropped-residual.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (gate value still `rate<0.05`); DEC **not** reopened |
| Capacity checklist | **stays unchecked** — advance formal PASS; checkbox decision left to architect |
| R6 / RC | **进行中 / 非 RC** |
| Complete / risk / p1 | **not started** (fail-stop after this advance cut) |

## Goal

Architect (#147): merge when CI green → **advance-callback** (fail-stop; no threshold / DEC-006 value change); capacity checkbox stays empty; not RC. List formal already PASS on tip `69f364c` — **did not** re-run list after merge.

## Baseline formal 5m (post-merge #147, before this cut)

Re-seeded p0 on Compose `mkt`, then `advance.js` as on master (`preAllocatedVUs=60`, no warm):

| Metric | DEC-006 gate | Measured | Verdict |
|---|---|---|---|
| HTTP/s | 300 QPS | ~299.86 | near target |
| progress P95 | ≤200 ms | ~58.0 ms | **PASS** |
| callback P95 | ≤200 ms | ~191.4 ms | **PASS** |
| checks | >99% | 100% | **PASS** |
| http_req_failed | ==0 | 0% | **PASS** |
| advance_business_ok | >99% | 100% | **PASS** |
| advance_data_exhausted | count==0 | 0 | **PASS** |
| dropped_iterations | rate&lt;0.05 | **~0.133/s (40)** | **FAIL** |
| Overall | — | — | **FAIL** (dropped only) |

Observation: `vus_max` grew 60→99 around brief latency spikes (progress max ~490 ms); drops are arrival-rate scheduling, not mass 5xx. Same residual class as list post-#144.

## Changes in this PR

1. **`perf/advance.js`** — `warm` (progress-only, 100 QPS × 30s) then formal `advance` (300 QPS × `DURATION`, `startTime` 35s); formal `preAllocatedVUs` **120**; thresholds scoped to `scenario:advance` (**values unchanged**). Warm does **not** consume the callback pool so formal keeps real-advance metrics.
2. Docs/checklist binding only — **no** server/Tomcat/nginx/threshold value edits this cut.

## Formal 5m advance retest (bot box Compose `mkt`)

```bash
# master 152734c already on mkt portals from #147 list cut; re-seed p0 for fresh callbacks
SEED_SCALE=p0 BASE_URL=http://127.0.0.1:18080 python3 perf/seed/seed.py --scale p0
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -u 0 -w /scripts \
  -e BASE_URL=http://nginx -e INTERNAL_URL=http://portal-app-1:8081 \
  -e DURATION=5m -e STATE_FILE=/scripts/state.json \
  grafana/k6:0.54.0 run --summary-export=/scripts/reports/2026-10-11/advance-dropped-5m.summary.json \
  /scripts/advance.js
# wall-clock ≈ 5m35s (warm 30s + gap + formal 5m)
```

### Outcomes vs DEC-006 advance gate (and vs post-#147 baseline)

| Metric | DEC-006 gate | Post-#147 baseline | **This cut formal 5m** | Verdict |
|---|---|---|---|---|
| Formal HTTP/s | 300 QPS | ~299.86 | **90001 / 5m ≈ 300.0** | **PASS** |
| progress P95 | ≤200 ms | ~58.0 ms | **~60.6 ms** | **PASS** |
| callback P95 | ≤200 ms | ~191.4 ms | **~41.7 ms** | **PASS** |
| dropped_iterations | rate &lt; 0.05 /s | ~0.133/s (40) | **0 /s (0)** `{scenario:advance}` | **PASS** |
| checks | >99% | 100% | **100%** (270003/270003 formal) | **PASS** |
| http_req_failed | ==0 | 0.00% | **0.00%** (0/90001 formal) | **PASS** |
| advance_business_ok | >99% | 100% | **100%** | **PASS** |
| callback_real_advance | (structural) | 178 | **100** (seed pool) | ok |
| progress_advance_ok | count>0 | 89783 | **89901** | **PASS** |
| Thresholds crossed | — | dropped only | **none** | **overall PASS** |

`vus_max` stayed at prealloc **160** (warm 40 + advance 120) — no mid-run VU stampede. Host summary: `/workspace/r6-capacity-logs/advance-dropped-5m.log` (box-local); summary JSON gitignored.

**Not run**: complete / risk / track / admin-list / p1 / `run-full.sh` / threshold value edits / DEC-006 reopen / list re-run.

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides despite list+advance PASS).
2. **Not** DEC-006 reopen / k6 threshold **value** change (scoping to formal scenario = drop window only).
3. **Not** complete / risk-on / full P0 suite PASS.
4. **Not** RC / R6 通过.
5. Host logs under `/workspace/r6-capacity-logs/` (box-local).

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this formal 5m advance **PASS** linked; **checkbox remains unchecked** pending architect. R6 stays **进行中** / **非 RC**. **Do not** start complete/risk in this slice.
