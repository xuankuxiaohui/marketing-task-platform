# Capacity evidence: claim-pool measurement hygiene after #182 (2026-10-12)

**Result: isolated complete (risk-off) FAIL — grant P95 only** after daily-claim hygiene. Capacity勾空; 非 RC. **Ask architect next knife** (P95 residual; daily-limit cleared).

## #182 merge
- Tip: `496f9cf` (CI all green) → merge SHA **`31c391e`**
- Prior residual: `task.claim.daily-limit` HTTP 400

## Hygiene change (measurement only)
**Path:** `perf/seed/clear_today_claim_counts.py`

| Does | Does not |
|---|---|
| Backdate `task_instance.created_at` by 1 day for `perf/state.json` userIds within Asia/Shanghai today window | Raise `TaskSettings.dailyLimitPerUser` (stays 20) |
| Make `countToday` return 0 → new `/start` not blocked by daily-limit | Treat HTTP 400 as k6 success |
| No wipe / no scale reseed | Revert #178 / change DEC-006 gates / raise 1536M |

Applied on live `mkt`: `instances_today` **532964 → 0**; `users_at_limit_ge20` **371 → 0**.

## Isolated complete after hygiene (risk-off)

| metric | value | gate |
|---|---|---|
| grant P95 | **510.51 ms** | ≤500 ✗ |
| checks | **100%** | >0.99 ✓ |
| http_fail | **0%** | ==0 ✓ |
| grant_business_ok | **100%** | >0.99 ✓ |
| grant_first_ok / exhausted | 16046 / 0 | ✓ |
| `task.claim.daily-limit` | **cleared** (not dominant) | — |

Sole crossed threshold: `http_req_duration{name:grant,scenario:complete}`. **No run-full** this slice.

## Disposition
- **勾空; 非 RC.**
- **Ask architect next knife** for grant P95≈510 vs 500 (cooldown? complete warm? product — await).
- 禁降Δ / 禁抬1536M / 禁revert #178 / 禁抬业务日限 / 禁400当成功.

## Artifacts
`/workspace/r6-capacity-logs/claim-pool-hygiene-after-182/`
