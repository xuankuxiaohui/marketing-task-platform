# Capacity evidence: hold-lock knife after #187 — isolated advance PASS + run-full FAIL-STOP at risk-off (勾空; 非 RC) (2026-10-12)

**Result:** After merge **#187** (`628e35d`) on same throwaway `mkt` (**no reseed**): product tip `b181217` deployed (portal×2 + admin); `MKT_METRICS_AGGREGATE_ENABLED=false` on admin; true settle → isolated `advance.js` warm→formal **5m PASS**. Then same-mkt `run-full-noseed` **FAIL-STOP at risk-off** (`grant_data_exhausted` + checks&lt;100%; Δ gate **not reached**). Capacity勾空; 非 RC. Do **not** tick. **Ask architect for next knife** (risk-off grant-pool residual). Do **not** invent product scope here.

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

## run-full-noseed (same mkt; no reseed) — FAIL-STOP at risk-off

Runner: `/workspace/r6-capacity-logs/hold-lock-knife/run-full/run-full-noseed.sh` (no reseed) on same `mkt`  
Wall: START **18:41:49 CST** tip `b181217` → list…ad/risk-on PASS → **FAIL-STOP at risk-off 19:25:20 CST**  
Δ gate **not reached** (risk-off failed first). Docs tip on PR: `3d02cad` (evidence commits; product still `b181217`).

| stage | result | named P95 / key | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **PASS** | list ≈**14.95 ms** ≤300 ✓ | scenario **0** ✓ (overall 359/1.07/s warm) | **100%** | **0%** | QPS healthy |
| **advance** | **PASS** | progress ≈**44.46 ms**; callback ≈**25.94 ms** ≤200 ✓ | **0** ✓ | **100%** | **0%** | hold-lock validated under suite |
| **complete** | **PASS** | grant ≈**362.6 ms** ≤500 ✓ | **0** ✓ | **100%** | **0%** | grant_first_ok 25311 |
| **track** | **PASS** | track ≈**36.66 ms** | **0** ✓ | **100%** | **0%** | — |
| **admin-list** | **PASS** | defs≈93.3 / grants≈385.2 / inst≈136.8 | — | **100%** | **0%** | mtr gate=false hygiene |
| **ad** | **PASS** | ad ≈**1.95 ms** | **0** ✓ | **100%** | **0%** | — |
| **risk-on** | **PASS** | grant ≈**403.48 ms** ≤500 ✓ | exhausted **0** ✓ | **100%** | **0%** | — |
| **risk-off** | **FAIL** | grant ≈**297.32 ms** ≤500 ✓ | — | **99.62%** ✗ | **0%** ✓ | `grant_data_exhausted` **122** (count==0 ✗); check「pool not exhausted」0% ✓0/✗122 |
| risk-delta Δ | **not run** | — | — | — | — | fail-stop before gate |

k6 error: `thresholds on metrics 'checks{scenario:risk}, grant_data_exhausted{scenario:risk}' have been crossed`  
Summaries: `perf/reports/2026-10-12/risk-on.summary.json`, `risk-off.summary.json` (+ earlier stage `*.summary.json`)  
RESULT: `/workspace/r6-capacity-logs/hold-lock-knife/run-full/run-full-result.txt`

### Residual (for architect — do not invent knife)
- **Stage:** risk-off (VARIANT=off) after cumulative complete + risk-on grant load on same noseed mkt.
- **Signal:** prize/claim **grant pool exhausted** mid risk-off (`grant_data_exhausted=122`; checks rate 99.62% vs required 100%). Latency gate still green (grant P95≈297 ms).
- **Hold-lock product goal closed** under isolated advance **and** under run-full advance (progress/callback P95 well under 200; dropped 0).
- **Not** a progress/callback / Tomcat / Redisson / Δ-gate residual. Do **not** lower Δ / raise 1536M / 频率 / 日限 / revert #178 / Tomcat200–250 / Redisson bump / raise thresholds.

## Caps confirmed on mkt
- portal `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal/admin memcg 1536M **not** raised
- Redisson product lock ≤32/1s/1 (**not** changed)
- **禁降 Δ; 禁抬 1536M; 禁抬频率; 禁抬日限; 禁 revert #178; 禁 Tomcat200–250 / cycle_key-only / pair128 / FORCE / Redisson bump**

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Hold-lock knife **closed advance residual** (isolated + run-full advance PASS).
- New gap under run-full: **risk-off grant-pool residual** — ask architect for next knife / whether hygiene-only vs product cut. Do **not** invent scope here.
- CI on tip `3d02cad` (run 38239800168): web/e2e/openapi/deploy-smoke/server **all SUCCESS**. **Do not merge** until architect 批合 (勾空); FAIL-STOP evidence first — architect may still 批合 product if residual is out-of-scope hygiene, or direct next knife before merge.

## Artifacts
- `/workspace/r6-capacity-logs/hold-lock-knife/` (isolated advance formal)
- `/workspace/r6-capacity-logs/hold-lock-knife/run-full/` (run-full-noseed; `run-full-result.txt`, `k6-*.log`)
- `/workspace/r6-capacity-logs/advance-hold-lock-after-187/` (mirrored isolated summary)
- PR tip: product `b181217` + docs on `feat/capacity-hold-lock-progress-callback-2026-10-12`
- Master base: `628e35d` (#187)
