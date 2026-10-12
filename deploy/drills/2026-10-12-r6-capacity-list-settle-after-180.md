# R6 capacity — list settle + run-full after #180 (2026-10-12)

| Field | Value |
|---|---|
| Merge under test | #180 → `5e665f0` (tip `536f717`) |
| Isolation | `mkt`; **no** scale re-seed; settle → isolated list → `perf/run-full-noseed.sh` |
| Isolated list | **PASS** — P95≈139.22ms; dropped **0**; http_fail 0; checks 100% |
| run-full | list **PASS** (P95≈96.2ms dropped0) → advance **PASS** → **FAIL-STOP at complete** (checks≈94.91%; http_fail≈5.08%; grant P95≈443ms) |
| Later stages | track…risk-delta **not run** |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1; **禁降 Δ / 禁抬 1536M**; **禁 revert #178** |
| Capacity | **勾空**; **非 RC** |
| Next | **Ask architect next knife** (complete grant residual) |

Evidence: [docs/capacity/2026-10-12-list-settle-run-full-after-180.md](../../docs/capacity/2026-10-12-list-settle-run-full-after-180.md).  
Logs: `/workspace/r6-capacity-logs/list-settle-after-180/`, `/workspace/r6-capacity-logs/run-full-after-180/`.
