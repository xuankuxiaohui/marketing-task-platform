# R6 capacity — settle list PASS; run-full FAIL at risk-on after #191 (2026-10-12)

| Field | Value |
|---|---|
| Merge | #191 → `aebe150` (tip `61ada0b`) |
| Isolated list | **PASS** P95≈58.75; dropped≈0.024/s; settle READY |
| run-full | **FAIL-STOP at risk-on** — grant P95≈337 ✓; exhausted 0; http_fail 1/28755; checks 2 fails; risk-off/Δ not run |
| Prior PASS in same run | list/advance/complete/track/admin-list/ad |
| Capacity | **勾空**; **非 RC**; ask architect next knife |

Evidence: [docs/archive/capacity/2026-10-12-run-full-risk-on-fail-after-191.md](../capacity/2026-10-12-run-full-risk-on-fail-after-191.md).
