# Capacity evidence: hold-lock knife after #187 — isolated advance **PASS** (勾空; 非 RC) (2026-10-12)

**Result:** After merge **#187** (`628e35d`) on same throwaway `mkt` (**no reseed**): product tip `b181217` deployed (portal×2 + admin); `MKT_METRICS_AGGREGATE_ENABLED=false` on admin; true settle → isolated `advance.js` warm→formal **5m PASS**. Capacity勾空; 非 RC. Do **not** tick. Continuations: **run-full-noseed** (fail-stop by stage).

## Product knife (architect-approved)
1. **Main:** `TaskStepAppService` progress/callback — `locateSoft` + preload attrs/crowds/snapshot (+ frozen) **before** `getByIdForUpdate`; Caffeine TTL **3s**; `lockInstance`; click/resume `locate()` unchanged; same-TX outbox (**DEC-002 unchanged**).
2. **Companion hygiene:** `mkt.metrics.aggregate.enabled` / env `MKT_METRICS_AGGREGATE_ENABLED` gates admin `MetricsScheduler` (default **true**; formal measurement uses **false** on throwaway mkt).

YAML note: nest `metrics.aggregate.enabled` under the existing root `mkt:` (duplicate root key broke admin boot — fixed in tip `b181217`).

## Caps / bans (held)
禁降Δ / 禁抬频率·日限·1536M / 禁revert #178 / 禁Tomcat200–250 / Redisson加码 / cycle_key-only / pair128 / FORCE. 勾空; 非 RC.

## Isolated advance formal 5m — PASS

Settle: quiet samples → **SETTLE_READY sample=7** (~18:35 CST). Formal start 18:35:03 → end 18:40:40 CST.

| metric | after #186 isolated FAIL | **hold-lock after #187** | gate |
|---|---|---|---|
| result | FAIL | **PASS** | — |
| progress P95 `{name:/internal/task/progress,scenario:advance}` | ≈101.23 ms ✓ | ≈**41.55 ms** ✓ | ≤200 |
| callback P95 `{name:/internal/task/callback,scenario:advance}` | ≈307.81 ms ✗ | ≈**48.54 ms** ✓ | ≤200 |
| `dropped_iterations{scenario:advance}` | ≈0.1134/s (38) ✗ | **0** ✓ | rate&lt;0.05 |
| checks | 100% | **100%** | — |
| http_fail | 0% | **0%** | — |
| advance_business_ok | — | 100% (90001) | — |

k6: `ADVANCE_EXIT=0`; thresholds not crossed; warm✓ then advance✓.

Host logs: `/workspace/r6-capacity-logs/hold-lock-knife/` and `/workspace/r6-capacity-logs/advance-hold-lock-after-187/` (`advance-formal-5m.log`, summary json, `verdict.txt=PASS`).

## Unit tests (local)
- `TaskStepAppServiceTest` 31 PASS
- `MetricsSchedulerTest` (incl. `skipsWhenDisabled`) + `MetricsConfigurationTest` (incl. `wiresSchedulerDisabledShortCircuit`) PASS
- Command: `mvn -pl domain-task,admin-app -am test -Dtest='MetricsSchedulerTest,MetricsConfigurationTest,*TaskStep*' -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true`


## run-full-noseed (same mkt; no reseed) — through complete **PASS** (续跑中)

Started ~18:41 CST tip `b181217`; fail-stop by stage. Host: `/workspace/r6-capacity-logs/hold-lock-knife/run-full/`.

| stage | result | notes |
|---|---|---|
| list | **PASS** | list P95≈15.0ms; scenario dropped **0**; checks 100%; http_fail 0% |
| advance | **PASS** | progress P95≈44.5ms; callback≈25.9ms; dropped **0** |
| complete | **PASS** | grant P95≈**362.6**ms ≤500; checks 100%; http_fail 0%; grant_first_ok 25311 |
| track… | in progress | suite continues (admin-list / ad / risk-on/off) |

**勾空; 非 RC.** Caps/bans unchanged.

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Isolated advance **PASS** → **run-full-noseed** through **complete PASS** (grant P95≈362.6); suite continues (track…).
- Ask architect: **CI green → merge #188**; keep 勾空; confirm next after run-full stage outcome.
- Caps unchanged; bans held.

## Artifacts
- `/workspace/r6-capacity-logs/hold-lock-knife/` (primary formal run)
- `/workspace/r6-capacity-logs/advance-hold-lock-after-187/` (mirrored summary)
- PR tip: `b181217` on `feat/capacity-hold-lock-progress-callback-2026-10-12`
- Master base: `628e35d` (#187)
