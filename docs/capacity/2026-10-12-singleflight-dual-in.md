# Capacity: dual-IN + V9 + Caffeine TTL3s singleflight (2026-10-12)

**Status:** product cut in progress (勾空; 非 RC). 先测后合 — ask architect before merge.  
**Caps:** DEC-006 list 500 QPS, P95≤300ms, http_fail==0, checks>0.99, dropped&lt;0.05.  
**Locks:** Tomcat120, Redisson≤32/1s/1, 1536M **not raised**; no oneshot-tuple-only; no cycle_key-only; no pair128; no FORCE.

## Change
- Keep **dual-IN** `listByUserTaskIdsAndCycleKeys` (master path).
- **V9** drop `idx_user_cycle`.
- Caffeine **TTL 3s** + **`get(key, loader)` singleflight** on `currentInstances`.

## Evidence
Logs: `/workspace/r6-capacity-logs/singleflight-dual-in/`.
