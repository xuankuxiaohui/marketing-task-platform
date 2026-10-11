# R6 capacity — Redis/Sa-Token session stabilize experiment — **FAIL / evidence-only** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `2e26d4d` (Merge #161) / experiment ran on `caa36ba` |
| Branch / PR | `feat/redis-session-stabilize-2026-10-12` / **#162 evidence-only** (product levers **not** landed) |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~04:21–04:33 CST |
| Architect | #162 **产品杠杆不合**（Redisson netty/timeout 加码 → **heap OOM**；Redis 几乎空闲）→ 改 **证据-only**；`mkt` 回退；下一刀 list 堆压减负 |
| Isolation | throwaway `mkt` |
| DEC-006 | **unchanged** |
| Portal memory on master | **1536M** |
| Tomcat on master | **120** |
| Redisson on master | netty **32** / timeout **1s** / retry **1** |
| Redis compose on master | **0.5 CPU / 256M** (experiment bump stripped) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Hypothesis (from #161)

`RedisResponseTimeout` on `satoken:client:session` → nginx upstream closed/timeout. Tried Redis capacity + Redisson headroom + optional-auth skip-renew.

## Experiment package (runtime only; **not** merged)

| Lever | Tried |
|---|---|
| Redis compose | cpus 0.5→**2.0**, mem 256M→**512M**; io-threads 4; no RDB/AOF; maxmemory 400mb |
| RedissonFactory | timeout 1s→**2s**; retry 1→**2**; netty 32→**48** |
| SessionAuthFilter | optional-auth skip renew/updateLastActive |
| Kept | Tomcat 120, portal 1536M, list.js PRE 250 |

## Formal list 5m — **FAIL**

| Gate | c0f4795 | #161 list2 | experiment |
|---|---|---|---|
| QPS | ≈499.7 | ≈342.8 | ≈**238.7** |
| P95 | ≈88.9 | ≈155 | ≈**1265** **FAIL** |
| http_fail | 0 | ≈0.04% | ≈**30.9%** **FAIL** |
| checks | 100% | ≈99.96% | ≈**0.691** **FAIL** |
| dropped/s | ≈0.304 | ≈80.4 | ≈**188** **FAIL** |
| peak portal | ≈1.46 | ≈1.49 | ≈1.48/1.50 |
| Redis mem | — | — | ≈**11 MiB / 512 MiB** (idle) |
| Dominant | — | RedisTimeout | **`Java heap space` OOM** (OOMKilled=false; portal2 Restarts=2) |

**Diagnosis:** Redisson netty/timeout/retry bump coincided with **heap OOM**; Redis itself was nearly empty. Product levers **reverted**. Next: list **heap-relief** with Redisson kept ≤32/1s/1; optional A/B skip-renew alone.

## Product disposition

This PR keeps **evidence only**. Master product remains Redisson 32/1s/1, Redis 0.5CPU/256M, Tomcat 120, 1536M.

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-redis-session.json](../../perf/reports/2026-10-12/list-redis-session.json) |
| Host logs | `/workspace/r6-capacity-logs/redis-session/` |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006 reopen. 4. No Redisson/Redis product land. 5. Honest FAIL OK.
