# Capacity evidence: dual-IN restore + keep V8 (2026-10-12)

**Result: list formal 5m PASS** (dual-IN restore). Capacity勾空; 非 RC. Pair-batch attempted → FAIL → reverted.

## Architect (2026-10-12 ~06:56 CST)
- Revert cycle_key-only; keep V8 `idx_user_cycle`; restore dual IN; confirm near-PASS; then pair 分批 (**forbid** cycle_key-only).
- No 1536M raise.

## #165 baseline
Merge `5243ceb`. Formal2 near-PASS: P95 277ms, http_fail0, checks100%, dropped 0.0955/s.

## Code landed
- List primary: `listByUserTaskIdsAndCycleKeys` (full dual IN + Java pair filter).
- V8 `idx_user_cycle` kept; FlywayFullIT → 10 migrations.
- `listByUserCycleKeys` unused by list (not deleted from store/mapper).
- Pair 分批 (task_id chunks of 128) formal FAIL → **not shipped**.

## Formal list 5m

| run | shape | P95 | http_fail | checks | dropped | verdict |
|---|---|---|---|---|---|---|
| formal2 (authoritative) | dual-IN restore | **173.95 ms** | 0 | 100% | **0** | **PASS** |
| formal3 | dual-IN + batch128 | 5.57 s | 0 | 100% | 362/s | FAIL → reverted |
| formal1 | dual-IN + concurrent k6 noise / SIGTERM | (invalid) | — | — | — | discarded |

vs #165 formal2: better on dropped (0 vs 0.0955/s) and P95 (174 vs 277ms).

## Caps
Tomcat120, MaxRAM75, memcg 1536M, Redisson≤32/1s/1. skip-renew not landed.

## Artifacts
`/workspace/r6-capacity-logs/dual-in-restore/` — list-formal2-5m.log (PASS), list-formal3-5m.log (batch FAIL).
