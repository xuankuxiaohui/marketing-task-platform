# R6 capacity — settle → isolated list after #184 (2026-10-12)

| Field | Value |
|---|---|
| Merge / tip | #184 → `a6db07d` (Merge cool-complete + run-full-after-183 evidence) |
| Settle | **SETTLE_TIMEOUT proceeding** (24×20s; MySQL CPU oscillated; never 3 consecutive mysql&lt;8% & p1&lt;15%) |
| Isolated list formal 5m | **FAIL** dropped≈**133 / 0.397/s**; list P95≈**156.40ms**≤300; checks **100%**; http_fail **0%** |
| run-full-noseed | **not run** (FAIL branch) |
| Caps | Tomcat120 / 1536M / 禁降Δ / 禁抬阈值 / 禁抬日限 / 禁revert #178 |
| Capacity | **勾空**; **非 RC**; **ask architect** dropped residual product knife scope |

Evidence: [docs/archive/capacity/2026-10-12-list-settle-dropped-after-184.md](../capacity/2026-10-12-list-settle-dropped-after-184.md).
