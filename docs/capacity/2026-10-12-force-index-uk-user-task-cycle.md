# Capacity evidence: FORCE INDEX uk_user_task_cycle on dual-IN (2026-10-12)

**Result: pending acceptance** (Capacity勾空; 非 RC).

## Architect (~08:00 CST, #168 tip 550c42b 批后下一刀)
- dual-IN 误用 `idx_user_cycle`（warm-list FAIL digest avg≈71ms / ~244k rows）。
- **FORCE INDEX (`uk_user_task_cycle`)** on `selectByUserTaskIdsAndCycleKeys` only.
- Forbid: cycle_key-only / pair128 / threshold raise / 1536M raise / skip-renew. Leave V8.
- Acceptance: EXPLAIN → uk_user_task_cycle + rows ~thousand; same mkt no reseed → warm → formal list 5m; PASS→run-full else evidence.

## #168 merge
- Master tip: `2778ea97356b5e94423262c7daa10eea6eb279f5`

## Code
- `TaskInstanceMapper.xml` `selectByUserTaskIdsAndCycleKeys`: `FROM task_instance FORCE INDEX (uk_user_task_cycle)`.
- `selectByUserCycleKeys` untouched. Caps locked (Tomcat120 / 1536M / Redisson≤32/1s/1).

## EXPLAIN (pre-list, same mkt)
| shape | key | rows |
|---|---|---|
| no FORCE (baseline) | idx_user_cycle | 2 (filtered; digest-era examined ~244k) |
| FORCE uk_user_task_cycle | **uk_user_task_cycle** | **~2084** |

## Formal list 5m / run-full
*(filled after acceptance)*

## Artifacts
`/workspace/r6-capacity-logs/force-index/`
