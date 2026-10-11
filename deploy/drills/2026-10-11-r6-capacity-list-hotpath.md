# R6 capacity list hot-path — MySQL/GC follow-up after #142 — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `6ac4133` (Merge #142; published-index cache + Hikari) |
| Branch | `fix/r6-capacity-list-hotpath-2026-10-11` |
| Executor | Grok Bot on bot box (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~17:08– CST |
| Prior evidence | Post-#142 list retest FAIL (`list-retest-5m`: ~116 HTTP/s, P95~30s, http_fail~61%, portals heap~1.45–1.48/1.5GiB then idle, MySQL~100% CPU) |
| Isolation | Live mkt diagnose + unit tests; optional short smoke after rebuild |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged**; DEC **not** reopened |
| Capacity checklist | **stays unchecked** (gate still FAIL until re-measure PASSes) |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Architect: list still FAIL → profile Tomcat/GC/Outbox (+ Redis hit / MySQL). **Do not** start callback this slice.

## Diagnosis (measured on mkt after #142)

One authenticated `GET /api/common/task/list` with warm catalog cache (general_log):

1. `SELECT COUNT(*) FROM risk_hit_log WHERE user_id=?` — from `RiskCheckPort.userSummary` used only for blacklist gate
2. `risk_list_item` BLACK lookup
3. `risk_list_item` WHITE lookup — unused for list gate
4. `task_instance WHERE user_id=? AND (task_id, cycle_key) IN ((…~1044 tuples…))`

Catalog path: **no** `listPublished` / snapshot DB on warm hit ( #142 cache works ). Redis `task:published-index:all` TTL 30s; snapshots present (~1044×~683B).

Heap: `fromCatalog` re-parsed ~1044 embedded snapshot JSON (~0.96 MiB) on **every** list even when L1 returned the same `CachedPublishedCatalog` instance → allocation/GC pressure matching portals climbing to ~1.45–1.48 / 1.5 GiB then ~0% CPU while MySQL stayed saturated.

Outbox: pending=0 (not the burner this run).

## Fix (this PR)

1. **`RiskCheckPort.onUserList`** — list blacklist uses BLACK membership only; `RiskCheckPortImpl` skips hit-log COUNT + WHITE.
2. **Instance hydrate** — `listByUser` (~tens of rows/user on seed) + in-memory cycle match; drop ~1044-tuple IN.
3. **Parsed catalog reuse** — process-local `ParsedPublishedCatalog` keyed by L1 catalog **identity**.
4. **Cache knobs** — `TASK_PUBLISHED_INDEX` TTL 30s→**5m** (publish still evicts); `TASK_SNAPSHOT` L1 capacity 1000→**2000** (catalog 1044).

## Explicit non-claims

1. **Not** capacity checklist PASS / checkbox tick / RC.
2. **Not** DEC-006 reopen or threshold change.
3. **Not** advance-callback / complete / risk-on path work.
4. Formal 5m list re-measure may be deferred; smoke only if rebuilt locally.

## Next

1. Architect review / merge-when-green.
2. Rebuild portal on mkt + targeted list re-measure (fail-stop).
3. If list PASS → advance-callback saturation; else continue profile (Tomcat maxThreads / GC flags) without threshold edits.

## Local smoke (optional; not formal gate)

After rebuild portal on Compose `mkt` (no wipe):

| Run | HTTP/s | P95 list | http_fail | checks | dropped/s | portal heap | MySQL CPU | Note |
|---|---|---|---|---|---|---|---|---|
| Post-#142 smoke 30s | ~90.8 | ~8.51s | (prior) | (prior) | (prior) | climbing | high | baseline after catalog cache |
| **This PR smoke 30s** | **~492.9** | **~693ms** | **0%** | **100%** | **~7.0** | **~0.38–0.49 / 1.5 GiB** | **~4% after** | thresholds still crossed (P95≤300ms, dropped&lt;0.05/s) |

general_log after fix (1 warm list): only `risk_list_item` BLACK + `task_instance WHERE user_id=? ORDER BY …` — no hit COUNT, no WHITE, no 1044-tuple IN.

**Still 非 RC / capacity unchecked.** Formal 5m deferred; do not tick DEC-006.
