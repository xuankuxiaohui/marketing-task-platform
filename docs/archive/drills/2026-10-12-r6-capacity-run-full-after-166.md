# R6 capacity — run-full after #166 (2026-10-12)

| Field | Value |
|---|---|
| Merge | #166 → `af34d526b1b7aa738f3a5265c49335c582423bbd` (tip `5273202`) |
| Isolation | `mkt`; **no** scale re-seed |
| Pre-merge list | dual-IN PASS P95 174ms dropped0 |
| run-full | **FAIL-STOP at list** P95 804ms dropped 0.579/s |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1; skip-renew deferred |
| Capacity | **勾空**; **非 RC** |

Overall http_reqs ≈465/s; thresholds: dropped + list P95. Evidence: `docs/archive/capacity/2026-10-12-run-full-after-166.md`.
