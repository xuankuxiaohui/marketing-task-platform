# Capacity: dual-IN + V9 + Caffeine TTL3s singleflight (2026-10-12)

**Status:** list formal 5m **PASS** (勾空; 非 RC). 先测后合 — **asking architect 批合**; do not auto-merge.  
**PR:** #173.  
**Caps:** DEC-006 list 500 QPS, P95≤300ms, http_fail==0, checks>0.99, dropped&lt;0.05.  
**Locks:** Tomcat120, Redisson≤32/1s/1, 1536M **not raised**.

## Change
- Keep **dual-IN** `listByUserTaskIdsAndCycleKeys`.
- **V9** drop `idx_user_cycle`.
- Caffeine **TTL 3s** + **`get(key, loader)` singleflight**.

## EXPLAIN (post-V9)
| Query | key | rows | Note |
|---|---|---|---|
| dual-IN 64 tasks × 2 cycle keys, user=1 | `uk_user_task_cycle` | 128 | Using index — **not** giant scan |
| dual-IN all 1042 published × 2 keys, user=1 | `uk_user_task_cycle` | 2084 | Using index — **not** 500k scan |
| idx_user_cycle | absent | — | V9 applied |

## List formal 5m (**PASS**)

| Metric | Value | Gate |
|---|---|---|
| Result | **PASS** | — |
| checks (list) | 100% (300002/300002) | >0.99 |
| http_fail (list) | 0% | ==0 |
| http_req_duration p95 (list) | **~65.5ms** | ≤300ms |
| dropped_iterations (list) | **0** | &lt;0.05 |
| realized http_reqs | ~466/s (target 500) | — |
| Wall | 09:35:39–~09:41 CST | — |

run-full **not** started (await architect after merge). Logs: `/workspace/r6-capacity-logs/singleflight-dual-in/`.
