# Capacity: currentInstances Caffeine TTL 3s + oneshot tuple IN + V9 (2026-10-12)

**Status:** list formal 5m **FAIL** (勾空; 非 RC). 先测后合 — **ask architect 批合**; do not auto-merge.  
**PR:** #172.  
**Caps:** DEC-006 list 500 QPS, P95≤300ms, http_fail==0, checks>0.99, dropped&lt;0.05.  
**Locks:** Tomcat120, Redisson≤32/1s/1, 1536M **not raised**.

## Change
- Caffeine `expireAfterWrite(3s)` on `currentInstances` (max 8192 users).
- Keep one-shot `(task_id, cycle_key) IN` + V9 (`idx_user_cycle` dropped).

## EXPLAIN
SQL unchanged vs oneshot+V9: `uk_user_task_cycle` rows=N; `idx_user_cycle` absent.

## List formal 5m (FAIL)

| Metric | Value | Gate |
|---|---|---|
| Result | **FAIL** | — |
| checks (list) | 100% (205232/205232) | >0.99 |
| http_fail (list) | 0% | ==0 |
| http_req_duration p95 (list) | **~2.03s** | ≤300ms |
| dropped_iterations (list) | **~141.0/s** (47385) | &lt;0.05 |
| realized http_reqs | ~323/s (target 500) | — |
| Wall | 09:21:55–09:27:35 CST | — |

vs #171 oneshot-alone FAIL (P95≈2.08s, dropped≈192/s, ~73 QPS): TTL cache improved throughput/drops but **still misses P95 gate**.

During list: MySQL ≈100–103% CPU; portals ≈1.43GiB / 1.5GiB memcg (1536M **not** raised).

Unit: `TaskPortalAppServiceTest` 16/16 pass locally (JaCoCo module check N/A for single-class run).

run-full **not** started. Logs: `/workspace/r6-capacity-logs/instances-ttl-cache/`.
