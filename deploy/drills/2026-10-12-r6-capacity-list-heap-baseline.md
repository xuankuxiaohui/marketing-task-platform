# R6 capacity — list heap baseline after #162 — **FAIL / evidence-only** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `0ca4014` (Merge #162) |
| Branch / PR | `feat/capacity-list-heap-relief` / **evidence-only** (no product levers) |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~05:08–05:17 CST |
| Architect | #162 → next: list **堆压减负**；先确认 client baseline 再 formal list；prefer evidence-only until hygiene proven；skip-renew **另开** |
| Isolation | throwaway `mkt` (left UP) |
| DEC-006 | **unchanged** |
| Portal memory | **1536M** (not raised) |
| Tomcat | **120** (ban 200–250; not raised) |
| Redisson | netty **32** / timeout **1s** / retry **1** |
| Redis compose | **0.5 CPU / 256M** |
| skip-renew | **OFF** (clean master `SessionAuthFilter`; aborted mid-run skip-renew A/B at k6 137) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Goal this knife

Confirm stable client baseline after #162 strip, then formal list **5m**. If still heap-OOM / heavy FAIL → document evidence; product hygiene only if proven (this PR: **evidence-only**).

## Baseline confirm

| Lever | Runtime |
|---|---|
| `SERVER_TOMCAT_THREADS_MAX` | 120 |
| `JAVA_TOOL_OPTIONS` | MaxRAMPercentage=75 G1 Xss512k |
| portal memcg | 1536M |
| Redis | NanoCpus=5e8 Memory=256MiB |
| RedissonFactory (source/jar) | 32 / 1000ms / 1 |
| SessionAuthFilter | renew + updateLastActive **always** (no optional-auth skip) |

Portals rebuilt ~05:09 CST from clean master jars (domain-identity sha256 matched host). An earlier skip-renew-on list was **aborted** (k6 exit 137 ~05:07) and is **not** this formal.

## Formal list 5m — **FAIL**

| Gate | c0f4795 near-PASS | #162 experiment | **this baseline** |
|---|---|---|---|
| QPS | ≈499.7 | ≈238.7 | ≈**242.8** |
| P95 (list) | ≈88.9 | ≈1265 | ≈**548.4** **FAIL** |
| http_fail | 0 | ≈30.9% | ≈**27.5%** **FAIL** |
| checks | 100% | ≈0.691 | ≈**0.725** **FAIL** |
| dropped/s (list) | ≈0.304 | ≈188 | ≈**177** **FAIL** |
| peak portal | ≈1.46 | ≈1.48/1.50 | ≈**1.484 / 1.5** |
| Redis mem | — | ≈11 MiB / 512 | ≈**12 MiB / 256** (idle) |
| Dominant | residual dropped | **Java heap space** | **`Java heap space` OOM** (OOMKilled=false; Restarts=0) |

k6 exit **99** (thresholds crossed). fail-stop → **no** `run-full`.

**Diagnosis:** With product caps held at #159/#161 baseline, formal list still **heap-OOM + heavy FAIL**. Redis remains nearly idle (~12MiB). Session path still shows `RedisTimeoutException` / netty pending under load (timeout=1s retry=1), but dominant failure mode is **Java heap space** while memcg stays under 1536M (not OOMKilled).

## Attribution correction

A concurrent draft briefly labeled these same formal numbers as “skip-renew alone”. That is **incorrect**: list2 ran after the clean rebuild with skip-renew **OFF**. Stale `env-confirm` from 05:04 (skip-renew=ON) precedes the 05:09 rebuild. See `perf/reports/2026-10-12/heap-baseline-logs/ATTRIBUTION.txt`.

## Product disposition

This PR keeps **evidence only**. No raise of 1536M / Tomcat / Redisson netty|timeout|retry. No skip-renew land. No Tomcat 200–250.

## Suggested next (architect)

1. **skip-renew alone** (SessionAuthFilter optional-auth; unit tests; **no** other levers) — separate PR; or
2. Small **allocation/response/buffer hygiene** on list path (reversible; still no cap raises) after a candidate is identified.

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-heap-baseline.json](../../perf/reports/2026-10-12/list-heap-baseline.json) |
| Host logs | `/workspace/r6-capacity-logs/heap-relief/` (`list2-formal-5m.log`, `env-confirm-baseline.txt`) |
| Report copies | [perf/reports/2026-10-12/heap-baseline-logs/](../../perf/reports/2026-10-12/heap-baseline-logs/) |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006 reopen. 4. No product lever land. 5. Honest FAIL OK. 6. skip-renew not validated this cycle.
