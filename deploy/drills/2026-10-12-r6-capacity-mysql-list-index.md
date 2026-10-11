# R6 capacity — MySQL list query/index hygiene (2026-10-12)

| Field | Value |
|---|---|
| Prior | [#165](https://github.com/xuankuxiaohui/marketing-task-platform/pull/165) merge `5243ceb` — heap-dump bound path; list formal FAIL dropped only; architect: next = MySQL list query/index; **no** 1536M raise; caps lock Redisson≤32/1s/1 Tomcat120 1536M; skip-renew deferred; 勾空; 非 RC |
| Isolation | Compose project **`mkt`** (not wipe; existing p1 seed) |
| Cut | `V8__task_instance_user_cycle_idx` + `listByUserCycleKeys` (cycle_key IN via `idx_user_cycle`, Java pair filter) |
| Caps | Tomcat **120**, MaxRAM75, memcg **1536M**, Redisson **32/1s/1** |
| skip-renew | **not** landed |
| Capacity checkbox | **empty**; **not** RC; gates unchanged |

## Formal list 5m

| gate | value | pass |
|---|---|---|
| QPS | ~500/s (150001 / 5m CAR) | ✓ |
| P95 | 33.85 ms | ✓ |
| http_fail | 0 | ✓ |
| checks | 100% | ✓ |
| dropped | 0 / 0/s | ✓ |
| peak mem | ~874 MiB / 1.5 GiB | ✓ |
| Restarts/OOM | 0 / false | ✓ |

Digest: instance SELECT avg ~**0.4 ms** (was ~53 ms under dual IN).

## Artifacts
- `docs/capacity/2026-10-12-mysql-list-query-index.md`
- `/workspace/r6-capacity-logs/mysql-dropped/`
- `perf/reports/2026-10-12/list-mysql-dropped.summary.json`
