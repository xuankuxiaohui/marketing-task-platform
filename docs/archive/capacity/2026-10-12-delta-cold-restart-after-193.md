# Capacity evidence: risk Δ with portal cold-restart after #193 (2026-10-12)

**Result:** Measurement knife (per-leg `clear_today` + settle + **portal cold-restart between legs**) → both risk-on/off formal **PASS**; **Δ FAIL** (≈**213.5** ms ≫ 20). Capacity **勾空**; **非 RC**. Ask architect next — **do not invent product knife**; **do not lower Δ**; cold-restart was applied (not order-only).

## #193 merge
- tip `3866cdf` → merge **`9a3d594`** (勾空). Prior Δ≈103 vs #190 Δ≈−102 diagnosed as leg heat asymmetry.

## Measurement change
| Does | Does not |
|---|---|
| `clear_today_claim_counts` before each leg | Raise dailyLimit / 1536M / frequency |
| True settle attempt before each leg (on: TIMEOUT proceed; off: READY sample=23) | Relax http_fail / checks / exhausted / Δ gates |
| `perf/seed/cold_restart_portals.sh` between on→off (both portals healthy) | Full reseed / revert #178 |
| `perf/run-risk-delta-paired.sh` + `run-full-noseed` risk section updated | Skip restart and only flip order |

## Isolated paired formal (same mkt, tip during run `7eca2dd` → rebased `5433a9f`)

| leg | grant P95 | exhausted | checks | http_fail | gate |
|---|---|---|---|---|---|
| risk-on | ≈**295.73** ms | **0** | 100% | 0% | PASS |
| risk-off (after cold-restart) | ≈**82.23** ms | **0** | 100% | 0% | PASS |
| **Δ (on−off)** | **≈213.50** ms | — | — | — | ≤20 **FAIL** |

Cold-restart log: portals restarted 22:39:10 CST → healthy by poll 4. Off settle SETTLE_READY sample=23.

## Disposition
- **勾空; 非 RC.**
- Cold-restart **did not** shrink Δ (103 → 213); off became much faster after cold JVM — asymmetry remains / worsened in this direction.
- **Ask architect next knife.** Do not invent product scope; do not lower Δ; do not raise limits; do not relax gates.

## Artifacts
`/workspace/r6-capacity-logs/delta-cold-restart-after-193/` (`paired-result.txt`, `k6-risk-on.log`, `k6-risk-off.log`, `cold-restart.log`, `risk-delta.txt`, settle polls).
