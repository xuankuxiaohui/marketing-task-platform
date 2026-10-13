# R6 capacity — settle+warm list after #167 (2026-10-12)

| Field | Value |
|---|---|
| Merge | #167 → `16168b741fc868f18d427123f08bf76ccf7e89e3` |
| Warm | settle MySQL quiet + curl warm both portals (120 OK) + list.js warm |
| Formal list | **FAIL** P95 1.58s / dropped 33.2/s / http_fail0 / checks100% |
| Digest | dual-IN avg 71ms vs burst 53ms; mtr_* 11.7M-row aggs during run |
| Caps | Tomcat120 / 1536M / Redisson≤32/1s/1; no forbidden levers |
| Capacity | **勾空**; **非 RC** |
