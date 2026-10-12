# R6 capacity — FORCE INDEX uk_user_task_cycle on dual-IN (2026-10-12)

| Field | Value |
|---|---|
| Prior | #168 merge `2778ea9`; warm-list FAIL (P95≈1.58s dropped≈33/s; digest dual-IN ~244k rows via idx_user_cycle) |
| Cut | `FORCE INDEX (uk_user_task_cycle)` on `selectByUserTaskIdsAndCycleKeys` only |
| Caps | Tomcat120 / MaxRAM75 / 1536M / Redisson≤32/1s/1; V8 kept |
| Forbidden | cycle_key-only / pair128 / threshold / 1536M raise / skip-renew |
| Capacity | **勾空**; **非 RC** |

## Acceptance plan
EXPLAIN must show `uk_user_task_cycle` (~1–2k rows) → same mkt no reseed warm→formal list 5m → PASS then run-full fail-stop.
