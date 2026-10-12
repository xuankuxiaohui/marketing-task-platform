# R6 capacity — risk rule listAll cache after #194 (2026-10-12)

| Field | Value |
|---|---|
| Merge | #194 → `b37965c` (tip `57dadc4`+`97c4795`) |
| Knife | `RiskRuleConfigStore.listAll` Caffeine TTL3s singleflight + update invalidate |
| Isolated risk-on | **FAIL** exhausted=378; P95≈75 (was≈296); http_fail 0; Δ/off/run-full not run |
| Capacity | **勾空**; **非 RC**; ask architect (stripe vs CntWindow) |

Evidence: [docs/capacity/2026-10-12-risk-rule-cache-after-194.md](../../docs/capacity/2026-10-12-risk-rule-cache-after-194.md).
