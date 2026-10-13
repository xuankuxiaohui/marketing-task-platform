# R6 capacity — run-full after #178 / #179 (2026-10-12)

| Field | Value |
|---|---|
| Merge under test | #179 → `83a5af0` (tip `37d40a7`); product #178 admin COUNT TTL3s on `mkt` |
| Isolation | `mkt`; **no** scale re-seed; `perf/run-full-noseed.sh` |
| run-full | **FAIL-STOP at list** — dropped_iterations{scenario:list} ≈0.319/s (107); P95≈121.41ms (gate ok); checks 100%; http_fail 0 |
| Later stages | advance…risk-delta **not run** |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1; **禁降 Δ / 禁抬 1536M** |
| Capacity | **勾空**; **非 RC** |
| Next | **Ask architect next knife** |

Evidence: [docs/archive/capacity/2026-10-12-run-full-after-178.md](../capacity/2026-10-12-run-full-after-178.md).  
Logs: `/workspace/r6-capacity-logs/run-full-after-178/`.
