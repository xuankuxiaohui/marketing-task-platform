# R6 capacity — hold-lock after #187 — isolated advance **PASS** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `628e35d` (Merge #187) |
| Product tip | `b181217` (PR #188) |
| Env | bot box Compose `mkt` (throwaway; no reseed; `MKT_METRICS_AGGREGATE_ENABLED=false`) |
| Result | isolated advance **PASS** (progress≈41.6; callback≈48.5; dropped0); run-full list/advance/complete **PASS** (grant≈363) |
| Next | run-full through complete **PASS** (续); CI绿就合勾空 |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1; **未**抬频率; **非 RC**; **勾空** |

Full write-up: [docs/archive/capacity/2026-10-12-advance-hold-lock-after-187.md](../capacity/2026-10-12-advance-hold-lock-after-187.md).
