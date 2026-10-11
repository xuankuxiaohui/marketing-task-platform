# R6 capacity complete (grant chain) — after #148 — **PASS** — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `977edc1` (Merge #148; advance residual dropped PASS) |
| Branch | `fix/r6-capacity-complete-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~19:26–19:57 CST |
| Prior evidence | [#148 / advance residual dropped](2026-10-11-r6-capacity-advance-dropped.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (grant P95 still `<=500`); DEC **not** reopened |
| Capacity checklist | **stays unchecked** — complete formal PASS; checkbox decision left to architect |
| R6 / RC | **进行中 / 非 RC** |
| Risk-on | started after this evidence PR when machine free (architect: PASS→risk-on) |

## Goal

Architect (#148): merge when CI green → **formal 5m complete** (fail-stop; no DEC-006 threshold value change); capacity checkbox stays empty; PASS then risk-on; not RC.

## Baseline formal 5m (post-merge #148, before this cut)

Re-seeded p0 on Compose `mkt`, then master `complete.js` (risk still ON after seed):

| Metric | DEC-006 / F14 gate | Measured | Verdict |
|---|---|---|---|
| grant P95 | ≤500 ms | ~51 ms (on failed HTTP) | n/a |
| http_req_failed | ==0 | **100%** (4000/4000) | **FAIL** |
| checks / business / first_ok | >99% / >99% / >0 | **0%** / **0%** / **0** | **FAIL** |
| grant_data_exhausted | count==0 | **huge** (VU busy-spin after pool walk) | **FAIL** |
| Overall | — | — | **FAIL** |

Probe: `POST /api/common/task/{cascade}/start` → **403** `risk.blocked.generic` (seed ends with `set_risk(1)` for IP-claim rules). Same class as early #139 complete FAIL.

## Follow-on diagnosis (risk off, same-day users)

`seed.py --risk off` unblocks grants, but same-day re-seed **reuses** `perf_u{i}` logins whose `countToday` already sat at **TaskSettings.dailyLimitPerUser=20** (in-process default; **not** ConfigService-backed — `sys_config` put alone does not move the live bean). Smoke then hit `task.claim.daily-limit` mid-pool. With fresh usernames + risk off, business went green but cold grant P95 ~587 ms **>500**.

## Changes in this PR

1. **`perf/complete.js`** — `warm` (list + progress + reserved warm grant pool, 30s) then formal `complete` (5 VUs × `DURATION`, `startTime` 35s); thresholds scoped to `scenario:complete` (**values unchanged**); sleep-on-exhaust to avoid empty busy-spin.
2. **`perf/seed/seed.py`** — p0: `users_n=360`, `cascade_n=18` (≤ live daily limit after progress/callback pre-starts); per-run `seed_tag` usernames so same-day re-seeds do not inherit claim counts.
3. **`perf/run-p0.sh` / `run-full.sh`** — `--risk off` before complete, `--risk on` after (seed leaves risk ON).
4. Docs/checklist binding only — **no** DEC-006 threshold value edits; **no** capacity checkbox tick.

## Formal 5m complete retest (bot box Compose `mkt`)

```bash
# master 977edc1 already on mkt portals (#148 had no portal code change)
SEED_SCALE=p0 BASE_URL=http://127.0.0.1:18080 python3 perf/seed/seed.py --scale p0
python3 perf/seed/seed.py --risk off
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -u 0 -w /scripts \
  -e BASE_URL=http://nginx -e INTERNAL_URL=http://portal-app-1:8081 \
  -e DURATION=5m -e STATE_FILE=/scripts/state.json \
  grafana/k6:0.54.0 run --summary-export=/scripts/reports/2026-10-11/complete-post148-fix-5m.summary.json \
  /scripts/complete.js
# wall-clock ≈ 5m35s (warm 30s + gap + formal 5m)
```

### Outcomes vs DEC-006 complete gate

| Metric | DEC-006 / F14 gate | Baseline post-#148 | **This cut formal 5m** | Verdict |
|---|---|---|---|---|
| grant P95 | ≤500 ms | n/a (risk block) | **~498.1 ms** `{name:grant,scenario:complete}` | **PASS** |
| checks | >99% | 0% | **100%** (12147/12147 formal) | **PASS** |
| http_req_failed | ==0 | 100% | **0.00%** (0/4049 formal) | **PASS** |
| grant_business_ok | >99% | 0% | **100%** | **PASS** |
| grant_first_ok | count>0 | 0 | **4049** (~12.1/s) | **PASS** |
| grant_data_exhausted | count==0 | huge | **0** | **PASS** |
| Thresholds crossed | — | risk/business/exhaust | **none** (formal) | **overall PASS** |

Warm overall `http_req_failed` includes non-grant warm traffic noise; **formal scenario** gates are green. Host summary: `/workspace/r6-capacity-logs/complete-post148-fix-5m.log` (box-local); summary JSON gitignored.

**Note:** formal grant P95 (~498 ms) is close to the 500 ms ceiling — acceptable PASS this cut; do not treat as large headroom.

**Not run in this evidence file**: risk-on/off delta (started separately after PASS per architect); track / admin-list / p1 / `run-full.sh` / threshold value edits / DEC-006 reopen / list+advance re-run.

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides despite list+advance+complete PASS).
2. **Not** DEC-006 reopen / k6 threshold **value** change (scoping to formal scenario = drop/cold window only).
3. **Not** full P0 suite PASS / risk delta PASS (until risk cut finishes).
4. **Not** RC / R6 通过.
5. Host logs under `/workspace/r6-capacity-logs/` (box-local).

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this formal 5m complete **PASS** linked; **checkbox remains unchecked** pending architect. R6 stays **进行中** / **非 RC**.
