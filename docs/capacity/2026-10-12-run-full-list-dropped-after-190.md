# Capacity evidence: run-full-noseed FAIL-STOP at list after #190 stripe expand (2026-10-12)

**Result:** Same-mkt `run-full-noseed` after risk stripe expand (#190) **FAIL-STOP at list** solely on `dropped_iterations{scenario:list}` ≈**20.706/s** (10221). P95 / checks / http_fail all green. Capacity勾空; 非 RC. Do **not** tick. Ask architect for **list dropped residual** next knife (evidence-only; **禁发明产品刀**).

## Baseline tip
- Master under test at kick: branch tip `2fac34d` (clear_today in run-full-noseed; then docs tip `5d2fe3f` → merge **#190** `8b99adb`)
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; live stripe users=**2800** / riskTasks=20 / capacity≈52600 after expand
- Caps locked: Tomcat120; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`; portal/admin memcg **1536M not raised**; Δ gate **≤20 ms not lowered**; daily limit **not raised**; **禁 revert #178**

## Prerequisites this slice
| Step | Result |
|---|---|
| #190 tip `5d2fe3f` risk-off/on/Δ | **PASS** (exhausted0; Δ≈−102ms); architect **CI绿就合勾空** |
| #190 CI run 38248979218 | web/e2e/openapi/deploy-smoke/server **all SUCCESS** → merge SHA **`8b99adb`** |
| Pre-hygiene before run-full | `clear_today`: instances_today 2933→0; users_at_limit 0 |
| Prior list dropped (after #183/#184) | ≈0.31–0.40/s — this residual is **orders of magnitude worse** |

## run-full-noseed — FAIL-STOP at list

Runner: `perf/run-full-noseed.sh` (`SKIP_COMPOSE=1` `DURATION=5m`) on same `mkt`  
Wall: START **20:20:34 CST** → FAIL-STOP at list **20:28:51 CST**  
LOGDIR: `/workspace/r6-capacity-logs/risk-stripe-hygiene-after-188/run-full/`  
flock PID ~2952631 (completed; lock free)

| stage | result | named P95 | dropped `{scenario:list}` | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **FAIL** (ec=99) | **≈91.48 ms** ≤300 ✓ | **≈10221 / 20.706/s** ✗ (rate&lt;0.05) | **100%** | **0%** | crossed `dropped_iterations{scenario:list}` only |
| advance…risk-delta | **not run** | — | — | — | — | fail-stop |

Arrival / overall: http_reqs ≈140951 / ≈**285.5/s** (target list scenario 500 iters/s arrival not met — large dropped). Overall dropped_iterations ≈15051 / 30.49/s.  
Summary: `perf/reports/2026-10-12/list.summary.json`.  
Logs: `run-full-result.txt`, `run-full.log`, `k6-list.log`, `launcher.stdout`, `pre-hygiene.log`.

## Context note (facts only)
- Immediately preceded by paired risk-off/on formal 5m + Δ on same `mkt` (no dedicated settle gate between Δ PASS and run-full kick).
- vs historical list dropped after cool-complete / settle (~0.3–0.4/s): this run ≈**20.7/s** with P95 still green — arrival-rate inability dominant, not latency gate.

## Caps confirmed on mkt (post-run snapshot)
- portal/admin memcg 1.5GiB (1536M **not** raised)
- portal1 ≈1010MiB / portal2 ≈893MiB / admin ≈907MiB / mysql ≈765MiB
- **禁降 Δ; 禁抬 1536M; 禁抬阈值; 禁抬日限; 禁放宽 exhausted; 禁整库重seed; 禁 revert #178; 禁发明产品刀**

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- #190 stripe expand measurement closed (off/on/Δ PASS) and merged.
- New gap under run-full after expand: **list dropped residual** ≈20.7/s (FAIL-STOP; later stages not reached).
- Evidence-only. Ask architect: **next knife** for list dropped residual — do **not** invent product knife / lower Δ / raise 1536M / raise thresholds / raise daily limit / relax exhausted / full reseed / revert #178.

## Artifacts
`/workspace/r6-capacity-logs/risk-stripe-hygiene-after-188/run-full/`
