# Capacity evidence: complete residual after #181 (2026-10-12)

**Result: isolated complete (risk-off) FAIL** — dominant fail code **`task.claim.daily-limit` (HTTP 400)**. Capacity勾空; 非 RC. Do **not** tick. **Ask architect next knife.**

## Baseline
- #181 tip `c78e0be` evidence approved → merge SHA **`68317e1`**
- Prior: #180→`5e665f0`; list settle PASS; run-full-after-180 **FAIL-STOP at complete** (checks≈94.91%, http_fail≈5.08%, grant P95≈443ms)
- Isolation: compose `mkt`; **no** scale reseed; reused `perf/state.json`
- Caps locked: Tomcat120; **1536M not raised**; Δ ≤20 **not lowered**; **#178 not reverted**

## Fail-code investigation (run-full complete window)

| source | finding |
|---|---|
| k6 complete | checks 94.91%; http_fail 5.08% (783); grant P95 443ms ✓; exhausted 0 |
| portal-1 + portal-2 WARN | **`task.claim.daily-limit` × ~786** (391+395) ≈ k6 fail count |
| ErrorCode | `TaskErrorCodes.CLAIM_DAILY_LIMIT("task.claim.daily-limit", **400**, …)` |
| Handler | `ResponseEntity.status(errorCode.httpStatus())` → HTTP 400 → k6 `http_req_failed` |
| Secondary | outbox `evt_event_log` lock-wait timeouts (scheduling; not 1:1 with grant fails) |
| Ruled out | pool exhausted; grant P95; dominant 403/429/5xx; risk.blocked (risk-off applied) |

Thesis: same-day throwaway reuse — cascade users near/at `TaskSettings dailyLimitPerUser` (historically 20) → HTTP 400 on `/start`.

## Isolated complete risk-off (post-#181 settle)

| field | value |
|---|---|
| Settle | quiet×3 mysql CPU≈0.82 @ 14:36:02 CST |
| Risk | `--risk off` (R-a..f = 0) before k6 |
| Wall | 14:36:08 → 14:41:51 CST |
| **Result** | **FAIL** (thresholds: checks / grant_business_ok / http_req_failed) |

| metric | value | gate |
|---|---|---|
| grant P95 | **407.52 ms** | ≤500 ✓ |
| checks | **93.30%** | >0.99 ✗ |
| http_fail | **6.70%** (1044/15582) | ==0 ✗ |
| grant_business_ok | 93.30% | >0.99 ✗ |
| grant_first_ok | 14538 | >0 ✓ |
| grant_data_exhausted | 0 | ==0 ✓ |

Post-isolated portal reject tallies (cumulative container logs): daily-limit dominant (539+507); `task.step.complete` ×3+3 only.

## Disposition
- **勾空; 非 RC.** Do not tick either capacity box.
- **No run-full** this slice (isolated still FAIL).
- Evidence-only. **禁降 Δ / 禁抬 1536M / 禁revert #178.**
- **Ask architect: next knife** for complete `task.claim.daily-limit` residual on same-mkt no-reseed path (seed day-cap / pool hygiene / product — await direction).

## Artifacts
`/workspace/r6-capacity-logs/complete-residual-after-181/`  
(`FAIL-CODE-FINDINGS.txt`, `k6-complete-isolated.log`, `complete-formal-result.txt`, `business-reject-codes-isolated.txt`, settle/risk logs)  
Prior run-full complete: `/workspace/r6-capacity-logs/run-full-after-180/`
