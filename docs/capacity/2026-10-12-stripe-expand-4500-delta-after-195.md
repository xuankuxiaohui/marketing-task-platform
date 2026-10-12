# Capacity evidence: stripe expand →5000 after #195 — cold Δ FAIL at risk-on http_fail (2026-10-12/13)

**Result:** Measurement expand users **2800→5000** (cap≈96600; **no** reseed / **no** dailyLimit raise). Cold-symmetric paired Δ **FAIL-STOP at risk-on** on `http_req_failed` / checks — **`grant_data_exhausted=0`** (stripe sufficient). Δ / risk-off / run-full **not run**. Capacity **勾空**; **非 RC**. **Δ收口 → project replan** (no further capacity knives by default).

## #195 merge
- tip `1a2c5df` → merge **`e5c3f4f`** (listAll Caffeine TTL3s; 勾空)

## Measurement
| Does | Does not |
|---|---|
| `expand_risk_stripe.py --target-users 5000` (≥4500) | Full-DB reseed / raise dailyLimit |
| clear_today + settle + portal cold-restart pairing | Lower Δ / relax http_fail·checks·exhausted |
| formal≈4830 × 20 → k6_capacity≈96600 | Redisson / 1536M / revert #178 |

## Isolated cold-symmetric risk-on (tip `e5c3f4f`)

### Clean paired run (authoritative FAIL)
| metric | value | gate |
|---|---|---|
| grant P95 | ≈**67.96** ms | ≤500 ✓ |
| exhausted | **0** | count==0 ✓ |
| http_fail | ≈**0.273%** (35/12815) | rate==0 ✗ |
| checks | ≈**99.73%** (70 fails) | rate==1 ✗ |

### Collision note
An earlier risk-on leg (same tip, before log overwrite) had **PASS** (P95≈481.35; exhausted=0; checks 100%; http_fail 0) then interrupted during settle-off; salvaged under `collision-salvage/`. Clean re-run is the FAIL above.

## Disposition
- Stripe expand **cleared** prior exhausted=378 residual.
- Residual is **http_fail/checks** under risk-on — not pool size. Per team 2026-10-12 ~23:51: **Δ收口后规划主线**; **do not** invent more capacity knives / CntWindow / rewrite.
- **勾空; 非 RC.** Ask 架构师/产品 for whole-project replan (see project-replan draft).

## Artifacts
`/workspace/r6-capacity-logs/stripe-expand-4500-after-195/` (`paired-result.txt`, `k6-risk-on.log`, `collision-salvage/`, `expand-apply.log`)
