# R6 capacity — clear residual dropped after #159 — **FAIL** — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `f6ff7e3` (Merge #159; list-under-p1 mem; near-PASS dropped-only on tip `c0f4795`) |
| Branch | `feat/clear-dropped-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~03:50–03:57 CST |
| Prior | [#159](https://github.com/xuankuxiaohui/marketing-task-platform/pull/159) / architect: **合入+清 dropped**；小幅 Tomcat **120→160** 可；**禁 200–250**；不抬 1536M；不降门禁；容量勾空；非 RC |
| Isolation | Compose `mkt` (throwaway; reused p1; portal+nginx recreate) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** |
| Memory limit | **1536M unchanged** |
| Tomcat 200–250 | **not done** (banned) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Goal

1. Merge #159 at tip `c0f4795` (CI green).
2. Residual dropped hygiene: keep P95/http_fail/checks from near-PASS; get dropped rate &lt;0.05.
3. Allowed Tomcat **≤160**; keep `-Xss512k` / lean catalog.
4. Formal list 5m; fail-stop; evidence PR.

## Step A — merge #159

| Item | Result |
|---|---|
| Head | `c0f479555949afb865336107675bb7b070ef0afa` |
| CI | all **SUCCESS** |
| Master tip | **`f6ff7e3f5bc92af22ab20cac669c5be0c51f79a1`** |

### Baseline (c0f4795 near-PASS, for comparison)

| Gate | c0f4795 formal |
|---|---|
| ~500 QPS | ≈499.7/s |
| P95 | ≈88.9 ms **PASS** |
| http_fail | 0 **PASS** |
| checks | 100% **PASS** |
| dropped | ≈**0.304**/s (103) **FAIL** (only gate) |
| peak mem | ≈1.46 / 1.5 GiB; Restarts 0 |

## Hygiene package (this cut)

| Change | Value |
|---|---|
| `SERVER_TOMCAT_THREADS_MAX` | **120→160** (≤160; never 200–250) |
| `SERVER_TOMCAT_MIN_SPARE` | 25→30 |
| `SERVER_TOMCAT_ACCEPT_COUNT` | 200→400 |
| nginx `upstream portal_app` keepalive | 64→128 |
| `perf/list.js` | PRE_VUS 250→**350**; MAX_VUS 400→**500**; warm 30s→**45s**; gate start 35s→**50s** |
| Kept | `-Xss512k`, G1, lean `CachedPublishedCatalog`, **1536M** |

Hypothesis (honest): c0f4795 saw formal peak active VUs≈258 and mid-run VU pool growth → arrival drops; more Tomcat threads (160×2) + larger k6 prealloc + longer warm should absorb stalls.

## list formal 5m — **FAIL** (regressed vs c0f4795)

| Gate | Observed | Verdict |
|---|---|---|
| ~500 QPS | ≈**286.7**/s | under |
| P95 ≤300 ms | **≈3098 ms** | **FAIL** |
| http_fail == 0 | **≈7.05%** | **FAIL** |
| checks >0.99 | ≈**0.930** | **FAIL** |
| dropped &lt;0.05 | **≈130.3**/s (49532) | **FAIL** |

k6 exit **99**. All four formal gates crossed (not dropped-only).

### Memory / restarts

| Item | Value |
|---|---|
| OOMKilled | **false** / **false** |
| Restarts during/after window | portal-1 **2**; portal-2 **1** (readiness churn under load — not memcg OOM) |
| Peak mem samples | portal-1 ≈**1.488 GiB**; portal-2 ≈**1.497 GiB** / 1.5 GiB |

**Diagnosis:** Hygiene combo (Tomcat 160 + heavier k6 prealloc/warm + nginx keepalive) **did not** clear residual dropped; run **regressed** vs `c0f4795` near-PASS. Fail-stop — **no** run-full. **Do not** raise Tomcat to 200–250. **Do not** raise 1536M. **Do not** lower DEC-006 gates.

## Per-scene

| Scene | Result |
|---|---|
| clear-dropped package | applied |
| **list** | **FAIL** |
| advance…risk | **not run** (fail-stop) |

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-clear-dropped.json](../../perf/reports/2026-10-12/list-clear-dropped.json) |
| Host logs | `/workspace/r6-capacity-logs/clear-dropped/` |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006/DEC-002 reopen. 4. No 1536M raise. 5. No Tomcat 200–250. 6. run-full not started.

## Checklist pointer

[R31-go-live-checklist.md](../R31-go-live-checklist.md) — capacity boxes **remain empty**.
