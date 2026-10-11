# R6 capacity list-path reinforcement — root cause + code fix — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `ff1864b` (Merge #141; browser SPA) |
| Branch | `fix/r6-capacity-list-path-2026-10-11` |
| Executor | Grok Bot on bot box (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~16:35– CST |
| Prior evidence | [#139 capacity P0 FAIL](2026-10-11-r6-capacity-dec006.md) |
| Isolation | Analysis + unit tests on box; **no** new seed / k6 re-measure this slice |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged**; DEC **not** reopened |
| Capacity checklist | **stays unchecked** (gate still FAIL until re-measure PASSes) |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Architect direction after #141: **先 list 饱和（资源/池/查询路径）**，再 advance callback / complete 数据耗尽 / risk-on.

This slice: root-cause from #139 FAIL + concrete list-path fix (cache + Hikari pool) + unit regression. **Do not** claim list/DEC-006 PASS.

## Root cause (from #139 facts)

| Symptom (list 5m) | Evidence |
|---|---|
| ~54.5 HTTP/s vs 500 QPS gate | [capacity DEC-006](2026-10-11-r6-capacity-dec006.md) |
| P95 ~16.8s vs ≤300ms | same |
| dropped_iterations ~413/s | same |
| portals ~1.42–1.44 / 1.5 GiB | resources-during |
| MySQL ~100% of 1 CPU | resources-during |
| Hikari | no `maximum-pool-size` → Boot default **10** |
| Published rows on box Compose | **1044** (`task_definition` PUBLISHED) |

### Query-path finding

`TaskPortalAppService.list` (pre-fix) on every authenticated request:

1. `definitions.listPublished()` → full `SELECT … WHERE status='PUBLISHED'` (~1000 rows)
2. `snapshots.listByTaskAndVersions` → all live snapshot JSON blobs
3. In-memory visibility + sort + page

Namespaces `TASK_PUBLISHED_INDEX` / `TASK_SNAPSHOT` already existed (evict/put on publish) but **portal list never read them** — every list hit MySQL cold for the full catalog. That matches MySQL CPU saturation + portal heap pressure (re-parse ~1000 JSON snapshots per request).

Seed gray is mostly `NONE` / `RATIO` (no crowd DB N+1 on list for p0) → primary lever is **catalog cache + pool**, not crowd batching (deferred).

## Fix (this PR)

1. **Published catalog cache**: `TaskPortalAppService` loads `CachedPublishedCatalog` via `PlatformCache.get(TASK_PUBLISHED_INDEX, "all", …)` (TTL 30s); entries embed snapshot JSON. Publish path already `evictAfterCommit(TASK_PUBLISHED_INDEX, "all")`.
2. **Snapshot string cache**: `snapshotOf` / catalog load also fill `TASK_SNAPSHOT` (`taskId:version`), matching publish `putAfterCommit`.
3. **Hikari**: portal `maximum-pool-size=40` (min idle 10); admin `20` / `5`. portal×2×40 + admin×20 = **100** &lt; MySQL `max_connections` 151. **Not** a DEC-006 QPS/P95 threshold edit.
4. **Unit tests**: cache miss loads store once; cache hit skips `listPublished`.

## Explicit non-claims

1. **Not** capacity checklist PASS / checkbox tick.
2. **Not** re-ran k6 list / P0 suite this slice (standing order: prefer analyze + code/tests; re-measure only when essential after fix — deferred to architect-authorized follow-up).
3. **Not** advance-callback / complete / risk-on fixes (next slices per architect order).
4. **Not** p1 / `run-full.sh` / DEC-006 reopen / RC.

## Next concrete steps (after merge / architect)

1. Rebuild portal images on bot box Compose `mkt` (leave UP; no `down -v`).
2. Targeted list re-measure (`DURATION` short smoke then 5m) only if authorized; document commands + fail-stop on Docker Hub rate limit.
3. If list still FAIL → profile remaining (Tomcat threads, GC, Outbox drain). Else proceed to **advance callback** saturation.

**Update (2026-10-11 CST)**: rebuild + targeted list retest **DONE FAIL** — see [list-retest](2026-10-11-r6-capacity-list-retest.md). Throughput ~54→~116/s but P95/errors worse; **fail-stop**; advance **not** started.
