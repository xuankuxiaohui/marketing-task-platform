# R6 capacity — risk stripe expand after #189 (2026-10-12)

| Field | Value |
|---|---|
| Master baseline | #189 → `96c2de4` |
| Expand | users 2000→**2800**, riskTasks=**20**, capacity≈**52600** (`expand_risk_stripe.py`; **no** full reseed; dailyLimit=20 unchanged) |
| risk-off | **PASS** grant P95≈484.70ms; checks 100%; http_fail 0%; exhausted **0** |
| risk-on | **PASS** grant P95≈382.64ms; checks 100%; http_fail 0%; exhausted **0** |
| Δ | **PASS** on−off ≈ **−102.06 ms** ≤20 |
| Capacity | **勾空**; **非 RC**; ask architect merge-when-green + next (run-full-noseed in flight) |

Evidence: [docs/archive/capacity/2026-10-12-risk-stripe-expand-after-189.md](../capacity/2026-10-12-risk-stripe-expand-after-189.md).
