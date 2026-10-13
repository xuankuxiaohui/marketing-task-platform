# R6 capacity — dual-IN restore + V8 keep (2026-10-12)

| Field | Value |
|---|---|
| Prior | #165 `5243ceb` near-PASS; #166 cycle_key-only FAIL → revert query, keep V8 |
| Landed | dual-IN list primary + V8 idx; pair-batch not shipped |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1 |
| Capacity | 勾空; 非 RC |

## Formal list 5m (dual-IN)

| gate | value | pass |
|---|---|---|
| P95 | 173.95 ms | ✓ |
| http_fail | 0 | ✓ |
| checks | 100% | ✓ |
| dropped | 0 | ✓ |
| peak mem | see stats logs | no OOM |

Pair-batch formal FAIL (P95~5.5s) — reverted before merge tip.
