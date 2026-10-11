# R6 capacity — list-under-p1 内存补强 — list 5m **FAIL** (OOM cleared) — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `f96102d` (Merge #158; rebuild-list FAIL + portal 1.5GiB memcg OOM addendum) |
| Branch | `feat/list-under-p1-mem-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~03:11–03:19 CST |
| Prior | [#158](https://github.com/xuankuxiaohui/marketing-task-platform/pull/158) / architect: **list-under-p1 内存补强**（卡在 DEC-006 **1536M** 内，**不抬限额、不降门禁**）；修完再 list 5m；容量勾仍空；非 RC |
| Isolation | Compose project `mkt` (throwaway; **reused** p1 MySQL volume from #158 rebuild; portal images rebuilt) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged**; DEC **not** reopened |
| Memory limit | **1536M unchanged** (memcg `memory.max=1610612736`) |
| Capacity checklist | **both boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Goal

1. Merge #158 when CI green (tip `9a3b78a` OOM addendum).
2. Fix/reduce portal memory pressure for list under p1 **without** raising 1536M.
3. Redeploy portals on throwaway `mkt`; reuse p1 seed; formal list 5m.
4. Fail-stop; evidence PR; **capacity勾仍空**; **非 RC**.

## Step A — merge #158

| Item | Result |
|---|---|
| Head | `9a3b78a9bca8d7c91976e981965cceda4e596f35` |
| CI | web / openapi-backend / e2e / deploy-smoke / server — all **SUCCESS** |
| Master tip | **`f96102d4029b39254016aaaf938cdc641aa51767`** (Merge pull request #158) |

## Memory reinforcement (within 1536M)

| Change | Why |
|---|---|
| `JAVA_TOOL_OPTIONS`: add **`-Xss512k`** (compose + Dockerfile) | Cap native thread stacks; 300× default stacks + 75% heap exceeded memcg under p1 |
| Portal Tomcat **max threads 300→120**, min-spare **50→25** (compose env + `application.yml` defaults) | Enough for ~250 QPS/instance when latency healthy; 300 spun stacks → OOM |
| `CachedPublishedCatalog.Entry` **metadata-only** `(id,status,version)` — drop embedded `snapshotJson` | Avoid duplicating ~1MB snapshot bodies inside published-index L1/L2 alongside `TASK_SNAPSHOT` |
| `fromCatalog` resolves snapshot via `TASK_SNAPSHOT` | Same parse reuse path; single catalog source of truth for bodies |
| `TaskPortalAppService.list`: hoist `CrowdMembership` / `CrowdResolver` / `EvalContext` **once per request** | Was N× (~1000) allocs per list |
| `list`: `PendingCard` then `toCard` **only for page window** | Avoid ~1000 `TaskCardView` + `rewardPreview` allocs per request before pageSize=20 |

**Not done:** raising compose `memory: 1536M`; changing DEC-006 k6 gates; fake tokens; DEC-002 reopen.

## Environment

| Item | Detail |
|---|---|
| Compose | `mkt` UP; portal×2 rebuilt `--no-cache`; MySQL/Redis volumes retained (p1) |
| Verified env | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -Xss512k`; `SERVER_TOMCAT_THREADS_MAX=120`; cgroup max **1610612736** |
| Data | tokenUsers=**2000**; users=1,002,000 / instances=500,150 / events=5,002,150 |
| Smoke | list HTTP 200 / code 0 |
| k6 | `DURATION=5m` `list.js` warm→formal |

## Per-scene table

| Scene | Result | Notes |
|---|---|---|
| mem code + portal rebuild | **done** | 1536M unchanged |
| **list** | **FAIL** | formal 5m; see metrics; **fail-stop** |
| advance…risk | **not run** | fail-stop |

### list formal 5m (FAIL) — OOM cleared

| Gate (DEC-006 / list.js) | Observed | Verdict |
|---|---|---|
| arrival ~500/s | ≈**297.6**/s overall http_reqs | under |
| P95 ≤300 ms | **≈1780.7 ms** | **FAIL** |
| http_fail rate == 0 | **≈29.27%** (30061 / 102692 formal) | **FAIL** |
| dropped_iterations rate <0.05 | **≈129.6/s** (47308) | **FAIL** |
| checks rate >0.99 | ≈**0.707** | **FAIL** |

k6 exit **99**. Thresholds crossed: checks / dropped / P95 / http_fail (all four formal gates).

### Memory / OOM observation (this run)

| Item | Value |
|---|---|
| portal-app-1 Restarts / OOMKilled | **0** / **false** |
| portal-app-2 Restarts / OOMKilled | **0** / **false** |
| Peak mem during list | portal-1 ≈**540 MiB**; portal-2 ≈**799 MiB** / **1.5 GiB** |
| vs #158 rebuild formal | portals hit **1.5 GiB** memcg → repeated java OOM kills (restarts 6/5) |

**Memcg OOM under list-at-p1 on 1536M is mitigated** by this cut. List DEC-006 gates remain **FAIL** (latency/errors/drops) — next architect cut as needed; **do not** lower gates on this evidence.

## Artifacts

| Artifact | Location |
|---|---|
| In-repo aggregate | [perf/reports/2026-10-12/list-under-p1-mem.json](../../perf/reports/2026-10-12/list-under-p1-mem.json) |
| Host logs | `/workspace/r6-capacity-logs/list-under-p1-mem/` |

## Explicit non-claims

1. **Not** capacity checklist tick.
2. **Not** RC.
3. **Not** DEC-006 / DEC-002 reopen; **no** k6 threshold edits.
4. **Not** raising 1536M memory limit.
5. Subsequent `run-full` scenes **not** run (fail-stop).

## Checklist pointer

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: evidence linked; **checkbox remains unchecked**.
