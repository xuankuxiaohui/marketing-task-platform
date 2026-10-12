# R6 capacity — hold-lock after #187 — isolated advance **PASS** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `628e35d` (Merge #187) |
| Product tip | `b181217` (PR #188) |
| Env | bot box Compose `mkt` (throwaway; no reseed; `MKT_METRICS_AGGREGATE_ENABLED=false`) |
| Result | isolated advance formal 5m **PASS** (progress P95≈41.6ms; callback P95≈48.5ms; dropped **0**) |
| Next | run-full-noseed (fail-stop); CI绿就合勾空 |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1; **未**抬频率; **非 RC**; **勾空** |

Full write-up: [docs/capacity/2026-10-12-advance-hold-lock-after-187.md](../../docs/capacity/2026-10-12-advance-hold-lock-after-187.md).
