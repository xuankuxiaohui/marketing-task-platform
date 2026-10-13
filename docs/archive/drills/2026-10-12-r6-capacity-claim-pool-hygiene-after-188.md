# R6 capacity — claim-pool hygiene after #188 (2026-10-12)

| Field | Value |
|---|---|
| Merge | #188 → `21f9101` (合前 tip `fe41c60`) |
| Hygiene | `perf/seed/clear_today_claim_counts.py` — backdate today `created_at` for state users; **not** raise daily limit; **no** reseed |
| Effect | instances_today 9587→0; users_at_limit 0; HYGIENE_EXIT=0 |
| Isolated risk-off | **FAIL** grant P95≈149.27ms ✓; http_fail 0% ✓; checks≈95.29% ✗; `grant_data_exhausted` **1787** ✗ (finite pool, not daily-limit) |
| Δ | **not run** (FAIL-STOP at risk-off) |
| Capacity | **勾空**; **非 RC**; ask architect next knife |

Evidence: [docs/archive/capacity/2026-10-12-claim-pool-hygiene-after-188.md](../capacity/2026-10-12-claim-pool-hygiene-after-188.md).
