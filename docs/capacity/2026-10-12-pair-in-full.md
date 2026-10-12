# Capacity: tuple IN 单次打满 + V9 (2026-10-12)

**Status:** product cut in progress (勾空; 非 RC). 先测后合 — ask architect before merge.  
**Caps:** DEC-006 list 500 QPS, P95≤300ms, http_fail==0, checks>0.99, dropped&lt;0.05.  
**Locks:** Tomcat120, Redisson≤32/1s/1, 1536M **not raised**; no cycle_key-only; no pair128 / dual-IN×128; no FORCE; no ≤64 serial chunking.

## Change
- `listByUserTaskCycles` one-shot `(task_id, cycle_key) IN (...)` — **no** PAIR_IN_BATCH=64 serial.
- **V9** drop `idx_user_cycle` (EXPLAIN-justified).
- #170 pair≤64 FAIL evidence-only (product stripped).

## Evidence
Logs: `/workspace/r6-capacity-logs/pair-in-full/`.
