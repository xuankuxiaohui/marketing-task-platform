# R6 capacity — claim-pool hygiene after #182 (2026-10-12)

| Field | Value |
|---|---|
| Merge | #182 → `31c391e` (tip `496f9cf`) |
| Hygiene | `perf/seed/clear_today_claim_counts.py` — backdate today `created_at` for state users; **not** raise daily limit |
| Effect | instances_today 532964→0; daily-limit residual cleared |
| Isolated complete | **FAIL** grant P95≈510.51ms; checks 100%; http_fail 0% |
| Capacity | **勾空**; **非 RC**; ask next knife (P95) |

Evidence: [docs/archive/capacity/2026-10-12-claim-pool-hygiene-after-182.md](../capacity/2026-10-12-claim-pool-hygiene-after-182.md).
