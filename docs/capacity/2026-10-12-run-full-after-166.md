# Capacity evidence: run-full after #166 (2026-10-12)

**Result: run-full FAIL-STOP at list.** Capacity勾空; 非 RC.

## #166 merge
- Approved tip: `52732029d526c069c14c191fbf1fc819bc758aa0`
- Master merge SHA: `af34d526b1b7aa738f3a5265c49335c582423bbd`
- Landed: dual-IN list primary + V8 `idx_user_cycle`; pair128 reverted; cycle_key-only not shipped.
- Caps: Redisson≤32/1s/1, Tomcat120, **1536M not raised**; skip-renew deferred.

## Pre-merge dual-IN formal (tip 5273202) — brief
Isolated formal2 on dual-IN restore (no concurrent k6):

| gate | value | pass |
|---|---|---|
| P95 list | **173.95 ms** | ✓ |
| http_fail | 0 | ✓ |
| checks | 100% | ✓ |
| dropped | **0** | ✓ |

Pair-batch(128) formal was FAIL (P95~5.5s) and **not** merged.

## run-full after merge (no re-seed, fail-stop)

Isolation: compose project `mkt`; reused existing `perf/state.json` (p0/p1 tokens); **no** `seed.py --scale`.

| scene | result | notes |
|---|---|---|
| **list** | **FAIL** | P95 **804.4 ms** ✗; dropped **0.579/s** (194) ✗; http_fail **0** ✓; checks **100%** ✓ |
| advance…capacity_check | **not run** | fail-stop at list |

vs pre-merge dual-IN PASS (P95 174 / dropped 0): post-merge suite list degraded (MySQL still ~1GiB memcg; possible residual noise / cold portals after prior suite). Honest FAIL — Capacity勾仍空.

## Caps confirmed on mkt
- `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- memcg 1536M; dual-IN path live (`listByUserTaskIdsAndCycleKeys`)

## Artifacts
`/workspace/r6-capacity-logs/run-full-after-166/` (k6-list.log, run-full-result.txt, master-tip.txt)
Prior dual-IN PASS log: `/workspace/r6-capacity-logs/dual-in-restore/list-formal2-5m.log`
