# Capacity evidence: list settle + run-full after #180 (2026-10-12)

**Result:** Isolated list after settle **PASS**; same-mkt `run-full-noseed` **FAIL-STOP at complete** (grant / checks / http_fail). Capacity勾空; 非 RC. Do **not** tick. Ask architect for **next knife** / 勾.

## Baseline tip
- Master tip under test: `5e665f02d13efaa7334a00a2e0396503ec67a13f` (= Merge #180 docs after run-full-after-178 FAIL-STOP)
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused existing `perf/state.json` (p1; tokenUsers=2000)
- Caps locked: Tomcat120; Redisson≤32/1s/1; memcg **1536M not raised**; Δ gate **≤20 ms not lowered**
- MySQL memcg ≈989–1008MiB / 1GiB during soaks (near ceiling; RestartCount=2 from earlier OOM earlier same day; this knife **did not** raise MySQL limit)

## Prerequisites this slice
| Step | Result |
|---|---|
| #180 tip `536f717` CI | all green (web / e2e / openapi-backend / deploy-smoke / server≈12m21s) |
| #180 merge | `5e665f0` (勾空 preserved; 非 RC) |
| Same-mkt settle | MySQL quiet×3 (cpu≈4%) then curl/list.js warm; **no** wipe/reseed |
| Prior run-full-after-178 | FAIL-STOP at list dropped≈0.319/s (P95≈121.41ms) |

## Isolated list (settle → warm → formal 5m)

| metric | value | gate |
|---|---|---|
| result | **PASS** | — |
| P95 `http_req_duration{name:list,scenario:list}` | **≈139.22 ms** | ≤300 ✓ |
| `dropped_iterations{scenario:list}` | **0** (rate 0) | rate&lt;0.05 ✓ |
| `http_req_failed{scenario:list}` | **0** | ==0 ✓ |
| `checks{scenario:list}` | **100%** (300000) | &gt;0.99 ✓ |
| arrival | ≈465.56/s | target 500 QPS constant-arrival |

Wall: settle ready 13:47:24 CST → LIST_END 13:53:07 CST.  
Logs: `/workspace/r6-capacity-logs/list-settle-after-180/`  
Summary: `perf/reports/2026-10-12/list-settle-after-180.summary.json` (box-local)

## run-full-noseed after list PASS (same mkt, fail-stop)

Runner: `perf/run-full-noseed.sh` `SKIP_COMPOSE=1` `LOGDIR=/workspace/r6-capacity-logs/run-full-after-180`  
Wall: 13:58:49 → 14:15:47 CST (FAIL-STOP)

| stage | result | P95 (named) | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **PASS** | **≈96.20 ms** (list) ✓ | **0** ✓ | 100% | 0 | ≈465.6/s; confirms settle PASS under run-full |
| **advance** | **PASS** | progress ≈163.34 ms; callback ≈31.26 ms | 1 ≈0.003/s ✓ | 100% | 0 | — |
| **complete** | **FAIL** | grant ≈443.29 ms | 0 | **94.91%** ✗ | **≈5.08%** ✗ | thresholds: checks / grant_business_ok / http_req_failed; grant http200 ≈94% (14620/15403); data_exhausted=0 |
| track…risk-delta | **not run** | — | — | — | — | fail-stop |

Note: an earlier same-day attempt aborted mid-list with k6 signal (MySQL memcg OOM history Restart=2); **not** counted as threshold FAIL. Current run list+advance completed cleanly; complete failed on business/http thresholds with MySQL still up (~1006MiB).

## Caps confirmed on mkt (post-FAIL)
- portal `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal/admin memcg 1.5GiB (1536M **not** raised)
- Redisson product lock ≤32/1s/1 (**not** changed)
- **禁降 Δ; 禁抬 1536M; 禁 revert #178**

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- List dropped residual from after-178 **cleared** on settle retest (dropped 0).
- New gap: **complete grant residual** under same throwaway (http_fail≈5% / checks≈94.9%).
- Evidence-only. Ask architect: **next knife** (complete residual diagnose vs 勾 / other).

## Artifacts
- `/workspace/r6-capacity-logs/list-settle-after-180/` (isolated list PASS)
- `/workspace/r6-capacity-logs/run-full-after-180/` (`k6-list.log`, `k6-advance.log`, `k6-complete.log`, `run-full.log`, `run-full-result.txt`, `stage-summary.txt`, `caps-*.txt`, `monitor-during-list.txt`)
