# R6 capacity — MySQL list query/index hygiene (2026-10-12)

| Field | Value |
|---|---|
| Prior | #165 merge `5243ceb` — dump bound path; architect: MySQL residual; **no** 1536M raise |
| Cut | V8 `idx_user_cycle` + `listByUserCycleKeys` |
| Caps | Tomcat120 / MaxRAM75 / 1536M / Redisson≤32/1s/1 |
| skip-renew | not landed |
| Capacity | **勾空**; **非 RC**; gates unchanged |

## Formal list 5m (authoritative formal3)

| gate | value | pass |
|---|---|---|
| P95 | 1.05 s | ✗ |
| http_fail | 0 | ✓ |
| checks | 100% | ✓ |
| dropped | 0.654/s (219) | ✗ |
| peak mem | ~772 MiB / 1.5 GiB | ✓ |
| Restarts/OOM | 0/false | ✓ |

**FAIL.** Mid-run abort metrics must not be used as PASS. run-full not continued.
