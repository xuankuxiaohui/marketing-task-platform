# R6 capacity — settle → isolated advance after #186 — **FAIL** — 2026-10-12

| Field | Value |
|---|---|
| Master | `091d367` (Merge #186) |
| Env | bot box Compose `mkt` (throwaway; no reseed) |
| Result | isolated advance formal 5m **FAIL** (dropped≈0.113/s; callback P95≈307.8ms; progress P95≈101.2ms PASS) |
| Next | progress 产品刀 — hotspot evidence on `/internal/task/progress` (FOR UPDATE / attrs+crowds+snapshot / evt+outbox+mtr co-load) |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1; **未**抬阈值; **非 RC**; **勾空** |

Full write-up: [docs/archive/capacity/2026-10-12-advance-settle-after-186.md](../capacity/2026-10-12-advance-settle-after-186.md).
