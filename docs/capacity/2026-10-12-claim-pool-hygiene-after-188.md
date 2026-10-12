# Capacity evidence: claim-pool hygiene after #188 (2026-10-12)

**Result: isolated risk-off FAIL — finite grant pool exhausted** after daily-claim hygiene. Capacity勾空; 非 RC. **Ask architect next knife** (do not invent product scope).

## #188 merge
- Tip (合前): `fe41c60` → merge SHA **`21f9101`** (Merge pull request #188 hold-lock)
- CI on merge tip: deploy-smoke / e2e / openapi-backend / **server** / web — ALL GREEN
- Prior residual (run-full after hold-lock): risk-off FAIL `grant_data_exhausted=122`; grant P95≈297 ms; Δ not run

## Hygiene change (measurement only)
**Path:** `perf/seed/clear_today_claim_counts.py`

| Does | Does not |
|---|---|
| Backdate `task_instance.created_at` by 1 day for `perf/state.json` userIds within Asia/Shanghai today window | Raise `TaskSettings.dailyLimitPerUser` (stays 20) |
| Make `countToday` return 0 → new `/start` not blocked by daily-limit | Treat HTTP 400 / exhausted as k6 success |
| No wipe / no scale reseed | Revert #178 / change DEC-006 gates / raise 1536M / Tomcat / Redisson |

Applied on live `mkt` (**no reseed**): `instances_today` **9587 → 0**; `users_at_limit_ge20` **0 → 0**; `HYGIENE_EXIT=0`. Settle: `SETTLE_READY`.

## Isolated risk-off after hygiene (VARIANT=off, 5m)

| metric | pre-hygiene run-full risk-off (#188) | **after hygiene** | gate |
|---|---|---|---|
| grant P95 `{name:grant,scenario:risk}` | ≈297.32 ms ✓ | ≈**149.27 ms** ✓ | ≤500 |
| http_fail `{scenario:risk}` | 0% ✓ | **0%** ✓ | ==0 |
| checks `{scenario:risk}` | ≈99.62% ✗ | ≈**95.29%** ✗ | rate==1 |
| `grant_data_exhausted{scenario:risk}` | **122** ✗ | **1787** ✗ | count==0 |
| risk grant http / accepted | — | ✓ when traffic existed | — |
| iterations | — | 20103 | — |
| Δ | not run | **not run** (FAIL-STOP) | ≤20 ms |

Sole crossed thresholds: `checks{scenario:risk}` and `grant_data_exhausted{scenario:risk}`. Latency gate green; **faster** grants after hygiene burned the finite off-pool sooner.

### Residual interpretation (for architect — do not invent knife)
- **Not** `task.claim.daily-limit` / HTTP 400 (hygiene cleared today claim counts).
- **Not** a P95 / Tomcat / Redisson / hold-lock / Δ-gate product residual.
- **Finite striped grant pool**: k6 `index >= riskCapacity` (~905 users × 20 riskTasks ≈18100 pool; residual iterations after burn → exhausted=1787).
- Hygiene made grants faster vs prior run-full risk-off (P95≈297, exhausted=122), so 5m burned the finite off-pool and residual iterations counted as exhausted.
- **Ask architect next knife** (measurement hygiene vs seed/pool sizing vs other — await). Do **not** invent product scope here.

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- **Ask architect next knife** after this evidence PR.
- Caps held: 禁降Δ / 禁抬1536M / 禁抬频率 / 禁抬日限 / 禁revert #178 / 禁Tomcat200–250 / Redisson加码 / 禁400当成功 / 禁把 exhausted 当成功.
- Merge this docs PR when CI green + architect nod; **do not** merge product knives from here.

## Artifacts
`/workspace/r6-capacity-logs/risk-off-hygiene-after-188/`
- `STATUS.md`, `hygiene.log`, `k6-risk-off.log`, `result.txt` (`FAIL-STOP at risk-off`), `pipeline.log`, `settle-*.txt`
- summary: `perf/reports/2026-10-12/risk-off-hygiene-after-188.summary.json` (box-local)
