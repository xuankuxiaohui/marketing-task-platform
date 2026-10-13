# Capacity evidence: cool → isolated complete PASS + run-full FAIL-STOP at list after #183 (2026-10-12)

**Result:** Same-mkt cool → isolated complete (risk-off) **PASS** (grant P95≈315.43 ms ≤500); then same-mkt `run-full-noseed` **FAIL-STOP at list** (dropped≈0.310/s). Capacity勾空; 非 RC. Do **not** tick. Ask architect for **next knife** (list dropped residual after cool-complete PASS).

## Baseline tip
- Master tip under test: `1fabc0933d7ed7996e751ce304605514edacd129` (= Merge #183 claim-pool hygiene evidence)
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused existing `perf/state.json` (p1)
- Caps locked: Tomcat120; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`; portal/admin memcg **1536M not raised**; Δ gate **≤20 ms not lowered**; daily limit **not raised**; **禁 revert #178**

## Prerequisites this slice
| Step | Result |
|---|---|
| #183 tip `fb750e5` CI | all green → merge SHA **`1fabc09`** |
| Architect (≈15:16 CST) | CI绿就合；勾空；next knife cool→isolated complete — P95≤500续run-full / still>500开 grant product knife |
| Prior isolated complete (#183 evidence) | FAIL grant P95≈510.51 ms (daily-limit cleared) |
| Same-mkt cool + claim-pool hygiene reclear | cool ~15:36–15:47 CST; **no** wipe/full reseed; **no** raise daily limit |

## (A) Cool → isolated complete (risk-off) — PASS

| metric | value | gate |
|---|---|---|
| result | **PASS** | — |
| grant P95 `http_req_duration{name:grant,scenario:complete}` | **≈315.43 ms** | ≤500 ✓ |
| `checks{scenario:complete}` | **100%** (67248/67248) | >0.99 ✓ |
| `http_req_failed{scenario:complete}` | **0%** | ==0 ✓ |
| `grant_business_ok` | **100%** (22416) | >0.99 ✓ |
| grant_first_ok / exhausted | 22416 / 0 | ✓ |

Verdict: `THRESHOLDS_CROSSED False` / `VERDICT PASS`.  
Logs: `/workspace/r6-capacity-logs/cool-complete-after-183/` and `/workspace/r6-capacity-logs/claim-pool-hygiene-recomplete-after-183/` (`complete-result.txt`, `verdict.txt`, `k6-complete.log`).

## (B) run-full-noseed after cool-complete PASS — FAIL-STOP at list

Runner: `perf/run-full-noseed.sh` (no reseed) on same `mkt`  
Wall: START 15:55:47 CST → FAIL-STOP at list 16:01:27 CST  
Master tip in logs: `1fabc09`

| stage | result | named P95 | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **FAIL** | **≈197.96 ms** ≤300 ✓ | **≈104 / 0.310/s** ✗ (rate&lt;0.05) | **100%** | **0%** | crossed `dropped_iterations{scenario:list}` |
| advance…risk-delta | **not run** | — | — | — | — | fail-stop |

Arrival / overall: list named P95 OK; sole crossed threshold is list dropped rate.  
Logs: `/workspace/r6-capacity-logs/run-full-after-183/` (`run-full-result.txt`, `run-full.log`, `k6-list.log`, `caps-pre.txt`).

## Caps confirmed on mkt
- portal `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal/admin memcg 1.5GiB (1536M **not** raised) — `portal1_mem=1610612736`
- Redisson product lock ≤32/1s/1 (**not** changed)
- **禁降 Δ; 禁抬 1536M; 禁抬阈值; 禁抬日限; 禁 revert #178**

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Cool-complete closed the prior grant P95≈510 gap (now ≈315 ≤500).
- New / recurring gap under run-full: **list dropped residual** ≈0.31/s (same order as after-178 ≈0.319/s).
- Evidence-only. Ask architect: **next knife** (list dropped residual after cool-complete PASS) — do **not** lower Δ / raise 1536M / raise thresholds / raise daily limit / revert #178.

## Artifacts
- `/workspace/r6-capacity-logs/cool-complete-after-183/`
- `/workspace/r6-capacity-logs/claim-pool-hygiene-recomplete-after-183/`
- `/workspace/r6-capacity-logs/run-full-after-183/`
