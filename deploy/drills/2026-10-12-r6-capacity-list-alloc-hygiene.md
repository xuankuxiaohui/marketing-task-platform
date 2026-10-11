# R6 capacity — list 分配/响应卫生 — **FAIL / evidence** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `35eae57` (Merge #163) / prior #162 `0ca4014` |
| Branch / PR | `feat/capacity-list-alloc-hygiene-2026-10-12` / evidence + product cut |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~05:22–05:50 CST |
| Architect | #163 架构通过 → 选 **list 分配/响应卫生**；**暂缓** skip-renew；Redisson≤32/1s/1、Tomcat**120**、**1536M**；勾空；非 RC |
| Isolation | throwaway `mkt` |
| DEC-006 | **unchanged** |
| Portal / Tomcat / Redisson / Redis | **1536M / 120 / 32·1s·1 / 0.5CPU·256M** (locked) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Hypothesis

Reduce list-path heap allocation / response object churn (visibility reasons, filter compile, rewardPreview, cycle-key formatting) without raising caps and without skip-renew.

## Product changes (landed on branch)

| Change | Why |
|---|---|
| `VisibilityEvaluator.visible()` fail-fast boolean | List only needs allow/deny — drop per-task `ArrayList` + `VisibilityResult` |
| `VisibilityResult.ALLOW` singleton; evaluate allocates reasons only on deny | Allow-path zero alloc for result object |
| `ExpressionEngine.compileCached` (bounded 256) | Filter expr parse once per distinct string |
| `PublishedBundle.rewardPreviews` + `SnapshotViews.EMPTY_REWARD` | Precompute reward DTO at catalog parse; share empty |
| DAILY/MONTHLY cycle-key memo per list request | Cut N× `DateTimeFormatter` allocs |

**Deferred:** skip-renew (architect: 不解堆 OOM). Caps unchanged.

## Environment

| Item | Detail |
|---|---|
| Compose | `mkt` UP; portal×2 rebuilt from `4b85a3e`; MySQL/Redis volumes retained |
| Verified | JAVA_TOOL_OPTIONS MaxRAM75 G1 Xss512k; Tomcat 120/25/200; portal memcg 1536M; Redis 0.5/256M; Redisson 32/1s/1 |
| skip-renew | **OFF** (renew + updateLastActive still on optional-auth) |
| Data | tokenUsers=2000; list smoke total≈966 |
| Host logs | `/workspace/r6-capacity-logs/alloc-hygiene/` |

## Formal list 5m — **FAIL**

| Gate | #163 skip-renew alone | **alloc/response hygiene** |
|---|---|---|
| QPS (http_reqs) | ≈242.8 | ≈**245.9** **FAIL** |
| P95 (list) | ≈548 | ≈**1477** **FAIL** |
| http_fail | ≈27.5% | ≈**17.3%** **FAIL** |
| checks | ≈72.5% | ≈**82.7%** **FAIL** |
| dropped/s | ≈177 | ≈**181** **FAIL** |
| peak portal | ≈1.48 / 1.48 | ≈**1.487 / 1.499** |
| Redis | ≈12/256 idle | ≈**14/256 idle** |
| OOMKilled | false | **false** |
| Restarts | 0/0 | **0 / 3** (portal-2) |
| Java heap space | yes (prior) | **yes** (both portals; p2 restarted) |
| k6 exit | 99 | **99** |

**Diagnosis:** Alloc/response hygiene alone does **not** clear list heap story under 1536M. Heap still pegs ceiling; Redis idle; portal-2 thrash-restarted thrice on Java heap space. Fail-stop — **no** cap raise, **no** skip-renew land, **no** CPU-blind cut, **no** gate lower.

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006 change. 4. Skip-renew still deferred. 5. No cap raises. 6. Honest FAIL OK.

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-alloc-hygiene.json](../../perf/reports/2026-10-12/list-alloc-hygiene.json) |
| Host logs | `/workspace/r6-capacity-logs/alloc-hygiene/` |
