# R6 capacity — list 堆压减负 (skip-renew alone A/B) — **FAIL / evidence-only** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `0ca4014` (Merge #162 evidence-only) / post-#161 `2e26d4d` |
| Branch / PR | `feat/capacity-list-heap-relief` / evidence-only (**skip-renew not landed**) |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~04:50–05:17 CST |
| Architect | #162 产品杠杆不合 → evidence-only；`mkt` 回退；下一刀 **list 堆压减负**（Redisson ≤32/1s/1；可选单独验 skip-renew；不抬 1536M/Tomcat） |
| Isolation | throwaway `mkt` |
| DEC-006 | **unchanged** |
| Portal memory | **1536M** (not raised) |
| Tomcat | **120** (not raised) |
| Redisson | netty **32** / timeout **1s** / retry **1** (locked) |
| Redis compose | **0.5 CPU / 256M** (reverted from #162 experiment) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Hypothesis

Optional-auth **skip-renew alone** (no Redisson/Redis bump) cuts Sa-Token `renewTimeout` + `updateLastActive` Redis writes on GET list → less object/session churn → lower portal heap pressure under list, without raising 1536M or Tomcat.

## Locked stack (mkt)

| Lever | Value |
|---|---|
| RedissonFactory | 32 / 1000ms / 1 |
| Tomcat | threads max 120, accept 200, minSpare 25 |
| JAVA_TOOL_OPTIONS | MaxRAMPercentage=75 G1 Xss512k |
| Portal mem | 1536M |
| Redis | 0.5 CPU / 256M |

## Cut under test (runtime only; **not** merged)

`SessionAuthFilter`: when `optional` (portal optional-auth, incl. list), skip `updateLastActiveToNow` + `renew`. Auth bind still runs.

No catalog/buffer/object-churn product change this slice (investigated; kept minimal = skip-renew A/B only).

## Formal list

### Run1 (aborted)

k6 **exit 137** (~57s wall) mid-list; portal1 mem → **1.487GiB**; Redis idle. Archived under `/workspace/r6-capacity-logs/heap-relief/*aborted137*`.

### Run2 formal 5m after clean recreate — **FAIL**

| Gate | c0f4795 near-PASS | #161 list2 | #162 expt | **skip-renew alone** |
|---|---|---|---|---|
| QPS | ≈499.7 | ≈342.8 | ≈238.7 | ≈**242.8** **FAIL** |
| P95 (list) | ≈88.9 | ≈155 | ≈1265 | ≈**548** **FAIL** |
| http_fail | 0 | ≈0.04% | ≈30.9% | ≈**27.5%** **FAIL** |
| checks | 100% | ≈99.96% | ≈69% | ≈**72.5%** **FAIL** |
| dropped/s | ≈0.304 | ≈80.4 | ≈188 | ≈**177** **FAIL** |
| peak portal | ≈1.46 | ≈1.49 | ≈1.48–1.50 | ≈**1.48 / 1.48** |
| Redis mem | — | — | ≈11/512MiB | ≈**12 / 256MiB** (idle) |
| OOMKilled | — | false | false (Restarts=2) | **false** Restarts=0 |
| k6 exit | — | 99 | 99 | **99** |

**Diagnosis:** skip-renew alone does **not** clear list heap/session story. Portals sit at the 1536M ceiling; Redis stays nearly idle; timeouts dominate. Fail-stop — **no CPU-blind cut**.

## Product disposition

This PR is **evidence only**. Master keeps renew on optional-auth. Skip-renew **not** landed (A/B negative for list gates).

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-heap-relief.json](../../perf/reports/2026-10-12/list-heap-relief.json) |
| Snippets | [perf/reports/2026-10-12/heap-relief-logs/](../../perf/reports/2026-10-12/heap-relief-logs/) |
| Host logs | `/workspace/r6-capacity-logs/heap-relief/` |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006 reopen/threshold change. 4. No skip-renew product land. 5. No 1536M/Tomcat/Redisson raise. 6. Honest FAIL OK. 7. No CPU cut (fail-stop).
