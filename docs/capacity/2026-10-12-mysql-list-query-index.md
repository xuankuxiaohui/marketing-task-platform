# Capacity evidence: MySQL list query/index hygiene (2026-10-12)

**Result: list formal 5m PASS** (Capacity勾仍空; 非 RC). Architect #165 架构通过已合入后下一刀。

## #165 merge
- Tip requested: `7f38f10a9fb55ebb0cc790c239bd17c8754b0d37`
- Master merge SHA: `5243ceb90d31870d461a8a180059505f6adb9191`
- CI: web / e2e / openapi-backend / server / deploy-smoke all SUCCESS before merge

## Problem (post-#165 residual)
Formal list after bounded `task_id IN` + `cycle_key IN` still **FAIL** dropped≈0.0955/s.
MySQL digest under list@~500 QPS: instance SELECT avg **~53ms** (plan/parse cost of ~1k `task_id IN`).
MySQL CPU ~98%. Heap headroom (~1.33/1.5 GiB) — not a 1536M raise case.

## Cut (this PR)
1. **Flyway V8** `idx_user_cycle (user_id, cycle_key)` — uk_user_task_cycle cannot seek `cycle_key` without `task_id`.
2. Portal list current-instance path: `selectByUserCycleKeys` / `listByUserCycleKeys` (few cycle keys, typically `NONE`) + Java pair filter for published `(task_id, cycle_key)`.
3. Caps unchanged: Redisson≤32/1s/1, Tomcat**120**, **1536M**. skip-renew **not** landed. No Tomcat 200–250.

## Formal list 5m (DEC-006)

| gate | value | pass |
|---|---|---|
| list QPS (CAR 500) | 150001 / 5m ≈500/s | ✓ |
| P95 list | **33.85 ms** | ✓ (≤300) |
| http_fail | **0** | ✓ |
| checks | **100%** | ✓ |
| dropped rate | **0** (0 drops) | ✓ (<0.05) |
| OOM / restarts | none | ✓ |
| peak mem (docker stats) | ~**874 MiB** / 1.5 GiB | headroom |

Digest after formal: instance SELECT avg **~0.4 ms** (was ~53 ms); uses `idx_user_cycle`.

## Caps confirmed
- `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- memcg **1536M**; Redisson product constants **32 / 1000ms / 1**

## run-full residual
Started after list PASS (no re-seed; fail-stop). See PR body / follow-up note for suite outcome.

## Artifacts
`/workspace/r6-capacity-logs/mysql-dropped/` (formal log, digest, stats, unit/package logs).
`perf/reports/2026-10-12/list-mysql-dropped.summary.json`
