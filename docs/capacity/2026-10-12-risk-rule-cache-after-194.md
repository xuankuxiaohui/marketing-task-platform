# Capacity evidence: RiskRuleConfigStore.listAll Caffeine TTL3s after #194 (2026-10-12)

**Result:** Product knife deployed on throwaway `mkt` → cold-symmetric isolated **risk-on FAIL** on `grant_data_exhausted` (378) — **not** P95/http_fail. Grant P95 collapsed ≈295→**≈75** ms (cache effective). Δ / risk-off / run-full **not run**. Capacity **勾空**; **非 RC**. Ask architect next (stripe expand vs CntWindow trim).

## #194 merge
- tip `57dadc4` (includes `97c4795`) → merge **`b37965c`** (勾空)

## Product change
`MybatisRiskRuleConfigStore.listAll`: Caffeine `expireAfterWrite(3s)` + `get(key, loader)` singleflight; `update` invalidates. Unit test `MybatisRiskRuleConfigStoreCacheTest` PASS.

## Isolated cold-symmetric risk-on (tip `46ac78d` jar on mkt)

| metric | pre-knife (#193 cold Δ on) | **after cache** | gate |
|---|---|---|---|
| grant P95 | ≈295.73 ms | ≈**74.98** ms | ≤500 ✓ |
| http_fail | 0% | **0%** | ==0 ✓ |
| exhausted | 0 | **378** | count==0 ✗ |
| checks | 100% | ≈**99.60%** | rate==1 ✗ |

Fail: pool burn from higher throughput after rule-load cache. Caps held (禁降Δ / 禁抬日限·1536M / 禁放宽门禁 / 禁Redisson).

## Disposition
- **勾空; 非 RC.** Do not tick.
- **Ask architect:** expand stripe further (measurement) to finish Δ, or proceed CntWindow read-path trim. Do not invent further product scope here.
