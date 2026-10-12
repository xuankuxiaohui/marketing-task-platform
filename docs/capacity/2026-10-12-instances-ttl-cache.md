# Capacity: currentInstances Caffeine TTL 3s + oneshot tuple IN + V9 (2026-10-12)

**Status:** product cut in progress (勾空; 非 RC). 先测后合 — ask architect before merge.  
**Caps:** DEC-006 list 500 QPS, P95≤300ms, http_fail==0, checks>0.99, dropped&lt;0.05.  
**Locks:** Tomcat120, Redisson≤32/1s/1, 1536M **not raised**; no cycle_key-only; no pair128; no FORCE; no ≤64 serial.

## Change
- Process-local **Caffeine** cache on portal `currentInstances` — `expireAfterWrite(3s)`, max 8192 users.
- Keep **one-shot** `(task_id, cycle_key) IN` via `listByUserTaskCycles` + **V9** (idx_user_cycle dropped).
- #170/#171 prior knives 产品不合 (evidence-only).

## Evidence
Logs: `/workspace/r6-capacity-logs/instances-ttl-cache/`.
