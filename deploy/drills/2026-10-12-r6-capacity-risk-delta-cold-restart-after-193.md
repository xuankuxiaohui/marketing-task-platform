# R6 capacity — Δ paired cold-restart after #193: on/off PASS; Δ FAIL (2026-10-12)

| Field | Value |
|---|---|
| Merge base | #193 → `9a3d594` |
| Protocol | per-leg clear_today+settle; **cold-restart portals between legs**; isolated Δ |
| Harness tip | `5433a9f` (`perf/run-risk-delta-paired.sh` + `cold_restart_portals.sh`) |
| risk-on | **PASS** grant P95≈295.73; exhausted 0; http_fail 0; checks 100% |
| cold-restart | **PASS** exit 0 |
| risk-off | **PASS** grant P95≈82.23; exhausted 0; http_fail 0; checks 100% |
| Δ | **FAIL** ≈**213.50** ms (gate ≤20); structural (wider than prior ≈103) |
| run-full-noseed | not started |
| Capacity | **勾空**; **非 RC**; residual for product knife — 禁发明产品刀 / 禁降Δ |

Evidence: [docs/capacity/2026-10-12-risk-delta-cold-restart-after-193.md](../../docs/capacity/2026-10-12-risk-delta-cold-restart-after-193.md).
