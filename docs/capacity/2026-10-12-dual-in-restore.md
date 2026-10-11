# Capacity evidence: dual-IN restore + keep V8 (2026-10-12)

**Status:** in progress at commit time — formal results appended after list 5m.

## Architect (2026-10-12 ~06:56 CST)
- #166: **revert** cycle_key-only list query (regressed vs #165 near-PASS); **keep V8** `idx_user_cycle`.
- Restore dual IN (`selectByUserTaskIdsAndCycleKeys`); then near-PASS confirm; then pair 分批/噪声 (**forbid** cycle_key-only).
- No 1536M raise; 勾空; 非 RC.

## Code
- List primary: `listByUserTaskIdsAndCycleKeys` (task_id IN + cycle_key IN + Java pair filter).
- `listByUserCycleKeys` retained on store/mapper but **not** used by list.
- Flyway V8 kept; FlywayFullIT expects 10 migrations + asserts `idx_user_cycle`.

## Caps
Redisson≤32/1s/1, Tomcat120, 1536M; skip-renew not landed.
