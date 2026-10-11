# Capacity evidence: MySQL list query/index hygiene (2026-10-12)

**Result: list formal 5m FAIL** (Capacity勾空; 非 RC). Architect #165 架构通过已合入后下一刀。
**Do not treat aborted mid-run metrics as PASS.**

## #165 merge
- Tip requested: `7f38f10a9fb55ebb0cc790c239bd17c8754b0d37`
- Master merge SHA: `5243ceb90d31870d461a8a180059505f6adb9191`

## Problem (post-#165 residual)
Formal2 after bounded `task_id IN` + `cycle_key IN`: dropped≈0.0955/s only FAIL; MySQL CPU~98%.
Digest: instance SELECT avg **~53ms** with ~1k `task_id IN` (rows examined near-zero → plan/parse cost).

## Cut (this PR)
1. **Flyway V8** `idx_user_cycle (user_id, cycle_key)`.
2. List path: `selectByUserCycleKeys` / `listByUserCycleKeys` + Java published pair filter.
3. Caps unchanged: Redisson≤32/1s/1, Tomcat**120**, **1536M**. skip-renew **not** landed. **1536M not raised**.

## Formal list 5m (DEC-006) — authoritative = formal3 full completion

| run | note | P95 | http_fail | checks | dropped | verdict |
|---|---|---|---|---|---|---|
| formal1 | portal recreate mid-window | 31.9ms* | 4.14% | 95.9% | 0.87/s | FAIL (contaminated) |
| formal2 | **SIGTERM @48%** — not a gate | ~33.9ms | 0 | 100% | 0 | **invalid** (aborted) |
| **formal3** | full 5m35s EXIT99 | **1.05s** | **0** | **100%** | **0.654/s (219)** | **FAIL** |

\*formal1 list P95 includes failed reqs mix; contaminated by portal restart ~22:40:33Z.

### formal3 gates
| gate | value | pass |
|---|---|---|
| P95 `{name:list,scenario:list}` | **1.05 s** | ≤300ms ✗ |
| http_req_failed | **0.00%** | ✓ |
| checks | **100%** | ✓ |
| dropped `{scenario:list}` | **0.654/s** (219) | <0.05 ✗ |
| peak portal mem | ~772 / 623 MiB of 1536M | no OOM |
| Restarts/OOM | 0 / false | ✓ |
| MySQL CPU (stats) | avg~87% max~105% | still hot |

Digest after formal3 (not clean — concurrent write noise observed): `task_instance` cycle SELECT still present; also heavy `COMMIT` avg~25ms, `risk_list_item`, progress/event inserts, and one `mtr_ad_material_d` scan ~40M rows. Query/index cut removed the ~53ms dual-IN plan cost (solo/burst digest ~0.4ms) but full 5m gate still FAIL on P95 + dropped.

## Caps confirmed
- Tomcat 120; MaxRAMPercentage=75; memcg 1536M; Redisson init 1+24

## run-full
**Not started** by this executor after authoritative FAIL. A premature complete.js from an invalid PASS claim was stopped.

## Artifacts
`/workspace/r6-capacity-logs/mysql-dropped/` — list-formal3-5m.log, digests, stats, flyway-v8.txt
