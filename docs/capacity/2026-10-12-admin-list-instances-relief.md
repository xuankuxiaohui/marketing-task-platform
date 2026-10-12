# Capacity: admin-list instances 减压 — COUNT TTL 3s (2026-10-12)

**Status:** admin-list formal 5m **PASS** (勾空; 非 RC). **先测后合** — asking architect **批合**; do **not** auto-merge product.  
**Baseline before:** run-full-after-#175 admin-instances P95≈**795.78 ms** (≤800; near ceiling).  
**After:** admin-instances P95≈**118.07 ms**.  
**Locks:** Tomcat120; Redisson≤32/1s/1; portal **1536M not raised**; **禁降 Δ 门禁**.

## Change
- Root cause: unfiltered `SELECT COUNT(*) FROM task_instance` (~529k rows) avg≈**493 ms** under load (perf_schema); page `ORDER BY id DESC LIMIT 20` is cheap (PK backward scan).
- Relief: process-local **Caffeine TTL 3s** + `get(key, loader)` **singleflight** on admin `countAdmin` filter key in `TaskInstanceAppService.page`.
- List rows stay live (no page-row cache). Count may lag ≤3s (admin UI acceptable).

## EXPLAIN / timing (mkt MySQL, settled)
| Query | Plan / timing | Note |
|---|---|---|
| `COUNT(*)` unfiltered | index `idx_expire`, ~481k rows; idle ~96 ms; load avg≈493 ms | bottleneck |
| `ORDER BY id DESC LIMIT 20` | PRIMARY backward, 20 rows; ~0.8 ms | cheap |

## Formal admin-list 5m (**PASS**)

Same throwaway Compose `mkt`; reused `perf/state.json`; **no** scale reseed. Caps unchanged.

| Metric | Before (run-full-after-#175) | After relief | Gate |
|---|---|---|---|
| admin-instances P95 | **795.78 ms** | **118.07 ms** | ≤800 |
| admin-definitions P95 | 98.6 ms | 92.59 ms | ≤800 |
| admin-grants P95 | 296.34 ms | 208.37 ms | ≤800 |
| checks | 100% | 100% | — |
| http_fail | 0% | 0% | ==0 |
| Result | PASS (near ceiling) | **PASS** | — |

Warm 30s instances P95≈106.74 ms. Wall (CST): warm 12:27:19–12:27:51; formal 12:27:55–12:32:57.

Unit: `TaskInstanceAppServiceTest` (+`pageReusesAdminCountWithinTtl`) 18/18 PASS.

Logs: `/workspace/r6-capacity-logs/admin-list-relief/`. Tiny summary: `perf/reports/2026-10-12/admin-list-relief.json`.

## Non-claims
1. **Not** capacity checklist tick (勾仍空; 非 RC).
2. **Not** DEC-006 threshold value change / Δ gate lower / 1536M raise.
3. **Not** full run-full retest (targeted admin-list only; architect may order run-full after 批合).
4. **Not** auto-merge — await architect 批合.
