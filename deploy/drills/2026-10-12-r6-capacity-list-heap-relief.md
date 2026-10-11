# R6 capacity — list 堆压减负 (skip-renew alone A/B) — **FAIL / evidence-only** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `0ca4014` (Merge #162) / post-#161 `2e26d4d` |
| Branch / PR | `feat/capacity-list-heap-relief` / **#163 evidence-only** (skip-renew **not** landed) |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~04:50–05:17 CST |
| Architect | #162 产品杠杆不合 → evidence-only；`mkt` 回退；下一刀 **list 堆压减负**（Redisson ≤32/1s/1；可选单独验 skip-renew；不抬 1536M/Tomcat） |
| Isolation | throwaway `mkt` |
| DEC-006 | **unchanged** |
| Portal / Tomcat / Redisson / Redis | **1536M / 120 / 32·1s·1 / 0.5CPU·256M** (locked) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Hypothesis

Optional-auth **skip-renew alone** cuts Sa-Token renew/lastActive Redis writes on GET list → lower portal heap pressure without raising caps.

## Timeline (attribution)

| Time CST | Event |
|---|---|
| ~04:51 | Built portal images with skip-renew ON (optional-auth) |
| ~05:04 | env-confirm: skip-renew=ON; Redisson 32/1s/1; Redis 0.5/256 |
| ~05:06–05:07 | **Run1** list aborted k6_exit=137 (~57s); portal1→1.487GiB |
| ~05:08 | Container recreate only (**no** jar rebuild) — still skip-renew images |
| ~05:10–05:17 | **Run2 formal 5m** on skip-renew ON images → **FAIL** (numbers below) |
| ~05:19 | Master renew-ON rebuild (post-formal; leave mkt clean) |

A later draft briefly re-labeled Run2 as “skip-renew OFF baseline”. That is **incorrect**: `rebuild-master-renew.log` is **05:19** (after formal). Formal = skip-renew alone.

## Formal list Run2 — **FAIL**

| Gate | c0f4795 | #161 list2 | #162 expt | **skip-renew alone** |
|---|---|---|---|---|
| QPS | ≈499.7 | ≈342.8 | ≈238.7 | ≈**242.8** **FAIL** |
| P95 (list) | ≈88.9 | ≈155 | ≈1265 | ≈**548** **FAIL** |
| http_fail | 0 | ≈0.04% | ≈30.9% | ≈**27.5%** **FAIL** |
| checks | 100% | ≈99.96% | ≈69% | ≈**72.5%** **FAIL** |
| dropped/s | ≈0.304 | ≈80.4 | ≈188 | ≈**177** **FAIL** |
| peak portal | ≈1.46 | ≈1.49 | ≈1.48–1.50 | ≈**1.48 / 1.48** |
| Redis | — | — | ≈11/512 idle | ≈**12/256 idle** |
| OOMKilled | — | false | false | **false** Restarts=0 |
| k6 exit | — | 99 | 99 | **99** |

**Diagnosis:** skip-renew alone does **not** clear list heap/session story. Heap near 1536M ceiling; Redis idle; fail-stop — **no CPU-blind cut**.

## Product disposition

**Evidence only.** Master keeps renew on optional-auth. Skip-renew **not** landed.

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-heap-relief.json](../../perf/reports/2026-10-12/list-heap-relief.json) |
| Host logs | `/workspace/r6-capacity-logs/heap-relief/` (`list2-formal-5m.log`, `rebuild-skip-renew.log` 04:51, `rebuild-master-renew.log` 05:19) |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006 change. 4. No skip-renew product land. 5. No cap raises. 6. Honest FAIL OK. 7. No CPU cut.
