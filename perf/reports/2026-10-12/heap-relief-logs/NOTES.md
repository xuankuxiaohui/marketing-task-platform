# CORRECTION

The formal list2 numbers (QPS≈243 / P95≈548 / http_fail≈27.5% / dropped≈177/s / Java heap space)
were measured with **skip-renew OFF** (clean master SessionAuthFilter) after portal rebuild
2026-10-12 ~05:09 CST. See deploy/drills/2026-10-12-r6-capacity-list-heap-baseline.md.

A skip-renew-on attempt was aborted earlier (k6 exit 137); it did not produce a formal 5m result.
