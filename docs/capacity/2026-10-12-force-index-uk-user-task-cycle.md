# Capacity evidence: FORCE INDEX (uk_user_task_cycle) on dual-IN (2026-10-12)

**Result: list formal 5m FAIL.** Capacity勾空; 非 RC. run-full not started.

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

Files: `explain-force-uk-u1.txt`, `explain-force-uk-u2.txt` under logs.

## Warm → formal list 5m
Warm: brief settle + curl 30 users × nginx/portal1/portal2 (90 OK). Then `list.js` warm→formal.

| gate | value | pass |
|---|---|---|
| P95 list | **3.31 s** | ≤300 ✗ |
| http_fail | **0** | ✓ |
| checks | **100%** | ✓ |
| dropped | **226.9/s** (76389) | &lt;0.05 ✗ |
| Restarts/OOM | 0 / false | |

(Aborted mid-run formal1 discarded — SIGTERM; authoritative = formal2 full 5m.)

vs prior dual-IN **without** FORCE (pre-merge PASS P95 174ms dropped0; warm-list FAIL P95 1.58s): FORCE INDEX **did not improve** list gates under this load; EXPLAIN estimate ~2084 index ranges per dual-IN may explain higher latency.

## Caps / forbidden levers
Tomcat120, MaxRAM75, 1536M; no cycle_key-only, no pair128, no threshold relax, no 1536M raise, skip-renew deferred. mtr_* isolation **not** started (次刀).

## Artifacts
`/workspace/r6-capacity-logs/force-index/`
