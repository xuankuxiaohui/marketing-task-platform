# list 堆压减负 — skip-renew alone A/B

Date: 2026-10-12 (CST)
Branch: feat/capacity-list-heap-relief
Base master: 2e26d4d (#161 merged)

## Product levers (LOCKED stable — no raise)
- Redisson: NETTY_THREADS=32, COMMAND_TIMEOUT_MS=1000, RETRY_ATTEMPTS=1
- Tomcat threads max=120, acceptCount=200
- Portal memory=1536M, JAVA MaxRAMPercentage=75 G1 Xss512k
- Redis compose: 0.5 CPU / 256M (reverted from #162 experiment 2CPU/512M)

## Cut under test (skip-renew alone)
SessionAuthFilter: for portal optional-auth paths (incl. GET list), skip
`updateLastActiveToNow` + `renewTimeout` Redis writes. Auth bind still runs.
Goal: cut Redis write churn / related object pressure without raising heap or Tomcat.

## Not in this cut
- No Redis CPU/mem bump
- No Redisson netty/timeout/retry bump
- No Tomcat / 1536M raise
- Capacity checklist boxes remain empty; 非 RC; no DEC-006 threshold change

## Formal list
See list-formal-5m.log / list-metrics.txt
