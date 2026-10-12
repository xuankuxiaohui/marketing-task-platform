# R6 capacity — skip-renew + instances TTL 5s + run-full after #186 product (2026-10-12)

| Field | Value |
|---|---|
| Product tip | `21fc981` (#186 skip-renew + TTL 5s) |
| Isolated list | **PASS** P95≈94.3 ms; dropped **0**; checks 100%; http_fail 0% |
| run-full-noseed | **FAIL-STOP at advance** — progress P95≈**218.11 ms** (>200); dropped≈**0.0627/s** (21); checks 100%; http_fail 0%; list stage PASS P95≈88.95 dropped0 |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1 / 禁降Δ / 禁抬阈值 / 禁抬日限 / 禁revert #178 / 禁Tomcat200–250 / cycle_key-only / pair128 / FORCE / Redisson bump |
| Capacity | **勾空**; **非 RC**; **ask architect next knife** (advance residual) |

Evidence: [docs/capacity/2026-10-12-skip-renew-list-ttl5-run-full.md](../../docs/capacity/2026-10-12-skip-renew-list-ttl5-run-full.md).
