# Capacity evidence: FORCE INDEX (uk_user_task_cycle) on dual-IN (2026-10-12)

**Result: list formal 5m FAIL.** Capacity勾空; 非 RC. run-full not started.

**Product disposition:** FORCE INDEX **stripped** before merge — evidence-only; do not land FORCE on master.

## #168 merge
- Tip: `550c42b891c1726ab9d11939c57ec72104e6e230`
- Master: `2778ea97356b5e94423262c7daa10eea6eb279f5`

## Product cut
`TaskInstanceMapper.xml#selectByUserTaskIdsAndCycleKeys`:
```sql
FROM task_instance FORCE INDEX (uk_user_task_cycle)
WHERE user_id = ? AND task_id IN (...) AND cycle_key IN (...)
```
Still dual-IN + Java pair filter. **Not** cycle_key-only / pair128. Caps unchanged; 1536M not raised.

## EXPLAIN proof (before load)
| user | no FORCE | FORCE INDEX (uk_user_task_cycle) |
|---|---|---|
| user_id=1 | key=`idx_user_cycle` rows≈2 | key=**`uk_user_task_cycle`** key_len=178 rows≈2084 |
| user_id=2 | key=`idx_user_status` rows≈3 | key=**`uk_user_task_cycle`** key_len=178 rows≈2084 |

Digest under load: dual-IN **avg_rows≈2** (FORCE effective vs prior ~244k) but **avg_ms≈217**.

## Warm → formal list 5m
Warm: settle + curl warm. Then `list.js` warm→formal.

| gate | value | pass |
|---|---|---|
| P95 list | **3.31 s** | ≤300 ✗ |
| http_fail | **0** | ✓ |
| checks | **100%** | ✓ |
| dropped | **226.9/s** (76389) | &lt;0.05 ✗ |
| Restarts/OOM | 0 / false | |

(Aborted mid-run formal1 discarded — SIGTERM; authoritative = **formal2** full 5m EXIT99.)

### Secondary isolate during formal2
`admin-app` **stopped** before formal2 (kill in-flight `mtr_*` INSERT; MetricsScheduler `@Scheduled` 60s). Formal2 still **FAIL** with MySQL CPU ~100% on dual-IN itself. Admin restarted after.

vs prior dual-IN **without** FORCE (PASS P95 174ms dropped0; warm-list FAIL P95 1.58s): FORCE fixed plan/rows but **did not recover list gates** under this load.

## Caps / forbidden levers
Tomcat120, MaxRAM75, 1536M; no cycle_key-only, no pair128, no threshold relax, no 1536M raise, skip-renew deferred.

## Artifacts
`/workspace/r6-capacity-logs/force-index/` — explain-*, list-force-index2.summary.json, digest-after-formal2.txt, admin-isolate.txt
