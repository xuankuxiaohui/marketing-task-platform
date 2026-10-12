# Capacity evidence: skip-renew + instances TTL 5s — isolated list PASS + run-full FAIL-STOP at advance (#186) (2026-10-12)

**Result:** Product knife #186 (list-family GET Sa-Token skip-renew + `CURRENT_INSTANCES_TTL` 3s→5s) on same throwaway `mkt` (**no reseed**): isolated list formal **5m PASS**; then `run-full-noseed` **FAIL-STOP at advance** (progress P95 + dropped). Capacity勾空; 非 RC. Do **not** tick. **Ask architect for next knife** (advance residual after skip-renew list PASS). Do **not** invent next product knife here.

## Product tip under test
- Branch: `feat/capacity-skip-renew-list-ttl5-2026-10-12`
- Tip: **`21fc981`** (product) + this docs commit
- Master base: `5cb8dbe` (= Merge #185 list-settle-dropped evidence)
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused existing `perf/state.json` (p1)
- Caps locked: Tomcat120; `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`; portal/admin memcg **1536M not raised**; Redisson ≤32/1s/1; Δ gate **≤20 ms not lowered**; daily limit **not raised**; **禁 revert #178**; **禁 Tomcat200–250 / cycle_key-only / pair128 / FORCE / Redisson bump**

## Product change (this PR)
1. **Portal list-family pure GET Sa-Token skip-renew** — `SessionAuthFilter` skips `updateLastActiveToNow` + `renewTimeout` Redis writes on optional-auth **GET** (task/list, task detail, activity, ad positions). Auth bind / kick / UserContext unchanged. Write paths + required-auth GET still renew.
2. **`CURRENT_INSTANCES_TTL` 3s→5s** (2–5s band); keep singleflight + dual-IN + V9. **Does not** touch admin `#178` COUNT TTL.

## (A) Isolated list formal 5m after true settle — PASS

| metric | after #184 baseline | **skip-renew+TTL5** | gate |
|---|---|---|---|
| result | FAIL | **PASS** | — |
| list P95 `http_req_duration{name:list,scenario:list}` | ≈156.4 ms | **≈94.3 ms** | ≤300 ✓ |
| `dropped_iterations{scenario:list}` | ≈0.397/s | **0** | rate&lt;0.05 ✓ |
| `checks{scenario:list}` | 100% | **100%** | >0.99 ✓ |
| `http_req_failed{scenario:list}` | 0% | **0%** | ==0 ✓ |
| QPS | ≈465 | ≈465.5 | — |

Host logs: `/workspace/r6-capacity-logs/skip-renew-knife/` (`list-formal-5m.log`, `list-skip-renew-ttl5.summary.json`, `verdict.txt`).

## (B) run-full-noseed after isolated list PASS — FAIL-STOP at advance

Runner: `/workspace/r6-capacity-logs/skip-renew-knife/run-full/run-full-noseed.sh` (no reseed) on same `mkt`  
Wall: START 17:16:37 CST → settle **SETTLE_TIMEOUT proceeding** (24×15s; MySQL oscillated) → list START 17:23:40 → DONE list ec=0 → advance START 17:29:19 → **FAIL-STOP 17:34:56 CST**  
Tip in logs: `21fc981`

| stage | result | named P95 | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **PASS** | **≈88.95 ms** ≤300 ✓ | **0** ✓ | **100%** | **0%** | QPS≈465.6 |
| **advance** | **FAIL** | progress **≈218.11 ms** ✗ (≤200); callback ≈176.85 ms ≤200 ✓ | scenario **≈21 / 0.0627/s** ✗ (rate&lt;0.05); overall 41 / 0.122/s | **100%** | **0%** | crossed `http_req_duration{name:/internal/task/progress,scenario:advance}` + `dropped_iterations{scenario:advance}` |
| complete…risk-delta | **not run** | — | — | — | — | fail-stop |

k6 error: `thresholds on metrics 'dropped_iterations{scenario:advance}, http_req_duration{name:/internal/task/progress,scenario:advance}' have been crossed`  
Summaries: `perf/reports/2026-10-12/list-skip-renew-ttl5.summary.json`, `advance-skip-renew-ttl5.summary.json`  
Logs: `/workspace/r6-capacity-logs/skip-renew-knife/run-full/` (`run-full-result.txt`, `run-full.log`, `k6-list.log`, `k6-advance.log`).

## Caps confirmed on mkt
- portal `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal/admin memcg 1.5GiB (1536M **not** raised) — `portal1_mem=1610612736`
- Redisson product lock ≤32/1s/1 (**not** changed)
- **禁降 Δ; 禁抬 1536M; 禁抬阈值; 禁抬日限; 禁 revert #178; 禁 Tomcat200–250 / cycle_key-only / pair128 / FORCE / Redisson bump**

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Skip-renew+TTL5 **closed the list dropped residual** under isolated list and under run-full list stage (dropped **0**; P95≈89 ms).
- New gap under run-full: **advance residual** — progress P95≈218 ms vs ≤200, and advance dropped≈0.063/s vs &lt;0.05 (checks/http_fail green).
- **Ask architect for next knife** — do **not** invent product scope here. Do **not** lower Δ / raise 1536M / raise thresholds / raise daily limit / revert #178 / Tomcat200–250 / cycle_key-only / pair128 / FORCE / Redisson bump.
- **Do not merge** until architect批「CI绿就合」(CI on tip with product was all green; docs tip will re-run CI).

## Artifacts
- `/workspace/r6-capacity-logs/skip-renew-knife/` (isolated list)
- `/workspace/r6-capacity-logs/skip-renew-knife/run-full/` (run-full-noseed)
