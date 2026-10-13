# R6 capacity — list-under-p1 内存/热路径补强 — list 5m **FAIL** (dropped only; OOM cleared) — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `f96102d` (Merge #158; rebuild-list FAIL + portal 1.5GiB memcg OOM addendum) |
| Branch | `feat/list-under-p1-mem-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~03:01–03:27 CST |
| Prior | [#158](https://github.com/xuankuxiaohui/marketing-task-platform/pull/158) / architect: **list-under-p1 内存/热路径补强**（卡在 DEC-006 **1536M** 内，**不抬限额、不降门禁**）；修完再 list 5m；容量勾仍空；非 RC |
| Isolation | Compose project `mkt` (throwaway; **reused** p1 MySQL volume from #158 rebuild; portal apps rebuilt only) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged**; DEC **not** reopened |
| Memory limit | **1536M unchanged** (memcg `memory.max=1610612736`) |
| Capacity checklist | **both boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Goal

1. Merge #158 when CI green (tip `9a3b78a` OOM addendum).
2. Pressure L1 (SNAPSHOT / ParsedPublishedCatalog) + heap/GC **without** raising 1536M.
3. Redeploy portals on throwaway `mkt`; reuse p1 seed; formal list 5m (may include cache-prime).
4. Fail-stop on FAIL; evidence PR; **capacity勾仍空**; **非 RC**.

## Step A — merge #158

| Item | Result |
|---|---|
| Head | `9a3b78a9bca8d7c91976e981965cceda4e596f35` |
| CI | web / openapi-backend / e2e / deploy-smoke / server — all **SUCCESS** |
| Merge | merge commit (repo habit) |
| Master tip | **`f96102d4029b39254016aaaf938cdc641aa51767`** (Merge pull request #158) |

## Memory / hot-path reinforcement (within 1536M)

| Change | Why |
|---|---|
| `JAVA_TOOL_OPTIONS`: **`-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`** (compose + Dockerfile) | Cap native stacks; G1 under container RAM; **do not** raise 1536M |
| Portal Tomcat **max threads 300→120**, min-spare **50→25** | 300× stacks + 75% heap → memcg OOM under p1; 120 fits with Xss512k |
| `CachedPublishedCatalog.Entry` **metadata-only** `(id,status,version)` | Stop embedding ~1MB snapshot JSON in published-index L1/L2 |
| Catalog load **does not** `put` TASK_SNAPSHOT; `fromCatalog` bulk-reads DB once into `ParsedPublishedCatalog` | Avoid dual residency of ~1k JSON strings + parsed objects in L1 |
| `list`: hoist `CrowdMembership` / `EvalContext` **once per request** | Was N× (~1000) allocs |
| `list`: `PendingCard` then `toCard` **only for page window** | Avoid ~1000 card/rewardPreview allocs before pageSize=20 |

**Not done:** raising compose `memory: 1536M`; changing DEC-006 k6 gates; fake tokens; DEC-002/006 reopen; wipe/reseed (p1 volume kept).

## Environment

| Item | Detail |
|---|---|
| Compose | `mkt` UP; portal×2 rebuilt; MySQL/Redis volumes retained (p1) |
| Verified env | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`; `SERVER_TOMCAT_THREADS_MAX=120`; cgroup max **1610612736** |
| Data | tokenUsers=**2000**; users=1,002,000 / instances=500,150 / events=5,002,150 |
| Smoke | list HTTP 200 / code 0 (total≈966) |
| k6 | `DURATION=5m` `list.js` warm→formal; report `perf/reports/2026-10-12/list-mem.summary.json` |

## Per-scene table

| Scene | Result | Notes |
|---|---|---|
| mem code + portal rebuild | **done** | 1536M unchanged; p1 DB kept |
| **list** | **FAIL** | formal 5m; **only dropped** gate fails; **fail-stop** |
| advance…risk | **not run** | fail-stop |

### list formal 5m (FAIL) — OOM cleared; 3/4 gates PASS

| Gate (DEC-006 / list.js) | Observed | Verdict |
|---|---|---|
| arrival ~500/s | formal completed ≈**149898**/300s ≈**499.7**/s (overall http_reqs ≈460.7/s incl. warm) | near target |
| P95 ≤300 ms | **≈88.89 ms** | **PASS** |
| http_fail rate == 0 | **0%** (0 / 149898 formal) | **PASS** |
| checks rate >0.99 | **100%** (299796/299796) | **PASS** |
| dropped_iterations rate <0.05 | **≈0.304/s** (103 drops) | **FAIL** |

k6 exit **99**. Threshold crossed: `dropped_iterations{scenario:list}` only.

Compare to #158 rebuild-list FAIL: P95≈1580 / http_fail≈1.64% / checks≈0.984 / dropped≈83/s + portal memcg OOM (Restarts 6/5).

### Memory / OOM observation (formal window)

| Item | Value |
|---|---|
| portal-app-1 Restarts / OOMKilled | **0** / **false** (28/28 monitor samples) |
| portal-app-2 Restarts / OOMKilled | **0** / **false** |
| Peak RSS during list | portal-1 ≈**1.462 GiB** / 1.5 GiB; portal-2 ≈**1.455 GiB** / 1.5 GiB |
| Post-list | both ≈1.456 GiB; MySQL CPU spike samples ~98% then calm |
| dmesg memcg OOM in window | **none** (prior OOMs are pre-fix PIDs) |

## Overall verdict

**FAIL** on residual **dropped_iterations ≈0.304/s** (gate `<0.05`). Latency/error/check gates **PASS**. Portal **1.5 GiB memcg OOM eliminated** under this list formal (no restarts). **Capacity勾仍空**; **非 RC**; **no** threshold edits.

## Ask

@架构师 please review: list-under-p1 mem cut inside 1536M → OOM cleared; list formal near-PASS (dropped only). Decide next cut — do **not** lower gates on this alone. Capacity勾仍空; 非 RC.
