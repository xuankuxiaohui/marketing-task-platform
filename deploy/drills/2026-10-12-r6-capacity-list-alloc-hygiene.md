# R6 capacity — list 分配/响应卫生 — **FAIL** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `35eae57` (Merge #163) |
| Branch / tip | `feat/capacity-list-alloc-hygiene-2026-10-12` / product + evidence |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~05:37–05:50 CST |
| Architect | #163 通过后下一刀 **list 分配/响应卫生**；暂缓 skip-renew；Redisson≤32/1s/1 Tomcat120 1536M |
| Isolation | throwaway `mkt` |
| DEC-006 | **unchanged** |
| Portal / Tomcat / Redisson / Redis | **1536M / 120 / 32·1s·1 / 0.5CPU·256M** (locked) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## Hypothesis

Reversible list hot-path allocation/response hygiene cuts Java heap pressure without raising caps:
top-K `PendingCard` (retain offset+pageSize of ~1042 published), `VisibilityEvaluator.visible()` (no per-task reasons),
`VisibilityResult.ALLOW` singleton, `ExpressionEngine.compileCached`, precomputed `RewardPreviewView`,
DAILY/MONTHLY cycle-key memo. **Skip-renew not landed.**

## Timeline (CST)

| Time | Event |
|---|---|
| ~05:37 | #163 CI all green → merge → master `35eae57` |
| ~05:39 | Unit tests PASS (42: TaskPortal/Visibility/Expression/CycleKey) |
| ~05:40 | `mvn package` portal-app; commit product hygiene |
| ~05:41–05:42 | Docker daemon restart mid-rebuild; recovered; no-cache portal images |
| ~05:43 | `mkt` healthy; env locks confirmed |
| ~05:43–05:49 | Formal list 5m → **FAIL** |

## Formal list 5m — **FAIL**

| Gate | c0f4795 | #163 skip-renew | **alloc hygiene** |
|---|---|---|---|
| QPS | ≈499.7 | ≈242.8 | ≈**246** **FAIL** |
| P95 (list) | ≈88.9 | ≈548 | ≈**1477** **FAIL** |
| http_fail | 0 | ≈27.5% | ≈**14.8%** **FAIL** |
| checks | 100% | ≈72.5% | ≈**85.2%** **FAIL** |
| dropped/s | ≈0.304 | ≈177 | ≈**179** **FAIL** |
| peak portal | ≈1.46 | ≈1.48 | ≈**1.49 / 1.50** |
| Redis | — | ≈12/256 idle | ≈**14/256 idle** |
| OOM | — | Java heap | **Java heap space** (OOMKilled=false; portal2 Restarts=3) |
| k6 exit | — | 99 | **99** |

**Diagnosis:** Hygiene did **not** clear list heap OOM under locked caps. http_fail/checks slightly better than #163 skip-renew alone; P95 worse; dropped similar. Redis idle — not Redis-bound. Fail-stop — **no CPU-blind cut**; **no cap raise**.

## Product disposition

Product hygiene is on the branch for architect review (reversible, unit-tested). Formal gate **FAIL** — do **not** tick capacity; **非 RC**. Architect may land, strip to evidence-only, or choose next cut.

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-alloc-hygiene.json](../../perf/reports/2026-10-12/list-alloc-hygiene.json) |
| Repo logs | [perf/reports/2026-10-12/list-alloc-hygiene-logs/](../../perf/reports/2026-10-12/list-alloc-hygiene-logs/) |
| Host logs | `/workspace/r6-capacity-logs/list-alloc-hygiene/` |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006 change. 4. No skip-renew land. 5. No cap raises. 6. Honest FAIL OK. 7. No CPU cut.
