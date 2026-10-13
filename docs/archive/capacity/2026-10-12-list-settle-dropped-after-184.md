# Capacity evidence: same-mkt settle → isolated list 5m FAIL (dropped residual) after #184 (2026-10-12)

**Result:** Same-mkt settle (timeout→proceed) → isolated `list.js` formal **5m FAIL** solely on `dropped_iterations{scenario:list}` ≈**0.397/s** (133). P95 / checks / http_fail all green. **Did not** continue `run-full-noseed` (FAIL branch). Capacity勾空; 非 RC. Do **not** tick. Ask architect for **dropped residual product knife** scope (this PR is **evidence-only**).

## Baseline tip
- Master tip under test: `a6db07d17bd82619e45cb92d9f04bee8adc559e7` (= Merge #184 cool-complete + run-full-after-183 evidence)
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused existing `perf/state.json` (p1)
- Caps locked: Tomcat120; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`; portal/admin memcg **1536M not raised**; Δ gate **≤20 ms not lowered**; daily limit **not raised**; **禁 revert #178**

## Prerequisites this slice
| Step | Result |
|---|---|
| #184 tip `6a880df` CI | all green → merge SHA **`a6db07d`** |
| Architect (≈16:08 CST) | CI绿就合；勾空；next knife same-mkt settle → isolated list 5m — PASS续run-full / FAIL开 dropped residual |
| Prior run-full after #183 | FAIL-STOP at list dropped≈0.310/s (104); list P95≈197.96ms |

## Settle
- Runner: `/workspace/r6-capacity-logs/list-settle-after-183/run-list-settle.sh`
- Gate: MySQL CPU&lt;8% **and** portal1&lt;15% for **3 consecutive** samples, min 6 samples; else timeout after 24×20s
- Outcome: **`SETTLE_TIMEOUT proceeding`** (never held 3 consecutive under gate; MySQL CPU oscillated 0.5%↔101%)
- Wall: settle START ≈16:20:55 CST → settle END ≈16:29:57 CST
- Post-settle docker stats (snapshot): mysql CPU≈33.6% MEM 879MiB/1GiB; portal1 CPU≈1.75% MEM≈990MiB/1.5GiB; portal2≈11.3%/1008MiB

## Isolated list formal 5m — FAIL

| metric | value | gate |
|---|---|---|
| result | **FAIL** | — |
| list P95 `http_req_duration{name:list,scenario:list}` | **≈156.40 ms** | ≤300 ✓ |
| `dropped_iterations{scenario:list}` | **≈133 / 0.397/s** | rate&lt;0.05 ✗ |
| `checks{scenario:list}` | **100%** (299736/299736) | >0.99 ✓ |
| `http_req_failed{scenario:list}` | **0%** | ==0 ✓ |
| http_reqs / throughput | 155869 / ≈465/s | — |
| VUs | max≈438 (list scenario 358 formal) | — |

Verdict: `THRESHOLDS_CROSSED True` / `VERDICT FAIL` / `LIST_EXIT=99`.  
Wall: list START ≈16:29:57 CST → END ≈16:35:35 CST (warm 30s + formal 5m0s).  
k6: `grafana/k6:0.54.0` on `mkt_default`; summary `perf/reports/2026-10-12/list-settle-after-183.summary.json`.  
Logs: `/workspace/r6-capacity-logs/list-settle-after-183/` (`list-formal-5m.log`, `list-formal-result.txt`, `verdict.txt`, `settle-poll.txt`).

## run-full
**Not run** — architect FAIL branch: open dropped residual; do **not** continue run-full.

## Caps confirmed on mkt
- portal `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal/admin memcg 1.5GiB (1536M **not** raised)
- Redisson product lock ≤32/1s/1 (**not** changed)
- **禁降 Δ; 禁抬 1536M; 禁抬阈值; 禁抬日限; 禁 revert #178**

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Isolated list after #184 settles the same dropped residual order seen under run-full-after-183 (≈0.31/s) and after-178 (≈0.319/s); here ≈**0.397/s** with P95 still green.
- **Evidence-only.** Ask architect: **dropped residual product knife** — concrete product change scope (this PR does **not** ship product code). Do **not** lower Δ / raise 1536M / raise thresholds / raise daily limit / revert #178.

## Artifacts
- `/workspace/r6-capacity-logs/list-settle-after-183/`
