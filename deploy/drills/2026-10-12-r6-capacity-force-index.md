# R6 capacity — FORCE INDEX uk_user_task_cycle (2026-10-12)

| Field | Value |
|---|---|
| Prior merge | #168 → `2778ea9` |
| Cut | dual-IN + `FORCE INDEX (uk_user_task_cycle)` |
| EXPLAIN | key=`uk_user_task_cycle` (was idx_user_cycle / idx_user_status) |
| Formal list | **FAIL** P95 3.31s / dropped 227/s / http_fail0 / checks100% |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1 |
| Capacity | **勾空**; **非 RC** |
