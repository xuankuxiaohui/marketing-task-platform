# R6 capacity — clear residual dropped experiment — **FAIL / evidence-only** — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `f6ff7e3` (Merge #159; list-under-p1 mem; near-PASS dropped-only on tip `c0f4795`) |
| Branch / PR | `feat/clear-dropped-2026-10-12` / **#160 evidence-only** (product config **not** landed) |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~03:50–04:00 CST |
| Architect | #160 **不合** Tomcat160/list.js 卫生包；证据可改 **证据-only** 再合；下一刀回退 threads=120 复测 + CPU/热路径 |
| Isolation | Compose `mkt` (throwaway) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** |
| Memory limit | **1536M unchanged** |
| Tomcat on master | **120** (this PR does **not** change it) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Goal (experiment)

Attempt residual dropped clear after #159 near-PASS (dropped≈0.304/s only) via Tomcat **120→160** + heavier k6 prealloc/warm + nginx keepalive. **Result: full-gate regression.** Product levers **reverted**; this PR keeps **evidence only**.

## Baseline (c0f4795 near-PASS)

| Gate | c0f4795 formal |
|---|---|
| ~500 QPS | ≈499.7/s |
| P95 | ≈88.9 ms **PASS** |
| http_fail | 0 **PASS** |
| checks | 100% **PASS** |
| dropped | ≈**0.304**/s (103) **FAIL** (only gate) |
| peak mem | ≈1.46 / 1.5 GiB; Restarts 0 |

## Experiment package (runtime only; **not** merged to master)

| Change | Tried |
|---|---|
| `SERVER_TOMCAT_THREADS_MAX` | 120→**160** |
| accept / min-spare | 200→400 / 25→30 |
| nginx keepalive | 64→128 |
| list.js | PRE 350 / MAX 500 / warm 45s / gate 50s |
| Kept | `-Xss512k`, G1, lean catalog, 1536M |

## Formal list 5m — **FAIL** (regressed)

| Gate | Observed | Verdict |
|---|---|---|
| ~500 QPS | ≈**286.7**/s | under |
| P95 ≤300 ms | **≈3098 ms** | **FAIL** |
| http_fail == 0 | **≈7.05%** | **FAIL** |
| checks >0.99 | ≈**0.930** | **FAIL** |
| dropped &lt;0.05 | **≈130.3**/s (49532) | **FAIL** |

k6 exit **99**. Peak ≈**1.49 / 1.5 GiB**; OOMKilled **false**; Restarts portal1=**2** portal2=**1**.

**Diagnosis:** Tomcat 160 + heavier VU prealloc/warm **did not** clear residual dropped; run **regressed** vs `c0f4795`. Do **not** land as product config. Next: restore threads=120 on `mkt`, retest near-PASS, then **CPU/hot-path** residual (no more threads / prealloc / 1536M).

## Product disposition

This PR restores all product files to **#159 / master** values (threads=**120**, list.js PRE 250/MAX 400, nginx keepalive 64). Only drill + FAIL numbers + checklist pointers remain.

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-clear-dropped.json](../../perf/reports/2026-10-12/list-clear-dropped.json) |
| Host logs | `/workspace/r6-capacity-logs/clear-dropped/` |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006/DEC-002 reopen. 4. No 1536M raise. 5. No Tomcat 160 (or 200–250) on master. 6. Honest FAIL OK.
