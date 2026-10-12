# Capacity evidence: run-full after #173 (2026-10-12)

**Result: run-full FAIL-STOP at risk-delta-gate** (individual k6 stages all ec=0). Capacity勾空; 非 RC.

## Baseline tip
- Master tip under test: `a20b7633967640d9f2a157590561e3b62b6a1a93` (= merge #173 dual-IN + V9 + TTL3s singleflight)
- Origin master moved to `da0d086` (#172 evidence-only) after run started; suite did **not** re-deploy
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused `perf/state.json`
- Caps: Tomcat120, Redisson≤32/1s/1, memcg **1536M not raised**
- Wall: 10:04:53–~10:50 CST (risk-off DONE); risk-delta gate run manually after script EOF syntax error

## run-full stage table (5m each, fail-stop)

| stage | result | P95 (named) | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **PASS** | **63.87 ms** (list) | **0** | 100% (312004) | 0 / 156002 | ~465.6 QPS; overall p95 63.39 ms |
| **advance** | **PASS** | **73.36 ms** (progress) | 21 overall (0.063/s); scenario gate ok | 100% (270000) | 0 / 92979 | callback p95 16.33 ms; overall 73.19 ms |
| **complete** | **PASS** | **411.2 ms** (grant) | — | 100% (22800) | 0% (overall raw 1/15486; scenario gate ok) | overall p95 399.12 ms; grant ≤500 |
| **track** | **PASS** | **34.68 ms** | **0** | 100% (108003) | 0 / 36001 | — |
| **admin-list** | **PASS** | **698.81 ms** (instances) | — | 100% (28542) | 0 / 14271 | definitions 99.19 / grants 107.14; overall 601.04; gates ≤800 |
| **ad** | **PASS** | **2.3 ms** | **0** | 100% (180002) | 0 / 90001 | — |
| **risk-on** | **PASS** | **490.48 ms** (grant) | — | 100% (7842) | 0 | max≈1m49s tail; grant ≤500 |
| **risk-off** | **PASS** | **390.54 ms** (grant) | — | 100% (11556) | 0 | — |
| **risk-delta-gate** | **FAIL** | on 490.49 − off 390.54 = **Δ 99.95 ms** | — | — | — | NFR 性能4: Δ ≤20 ms **broken** |

No stage emitted k6 `thresholds on metrics` / abort. Script `run-full-noseed.sh` hit `syntax error: unexpected end of file` after risk-off (heredoc/`||` layout); gate recomputed from `perf/reports/2026-10-12/risk-{on,off}.json`.

## Caps confirmed on mkt (post-run)
- `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal memcg 1.5GiB (1536M **not** raised); MySQL ≈1007MiB / 1GiB

## Disposition
- **勾空; 非 RC.** Do **not** tick capacity. Do **not** claim R6 RC.
- Evidence-only PR — no product / no 1536M raise.
- Await architect for next knife.



## Follow-up: cooled risk-delta retest (same day)
Isolated cooled retest on same `mkt` (no scale reseed) → **PASS** Δ≈−6.06 ms. See [2026-10-12-risk-delta-retest.md](2026-10-12-risk-delta-retest.md). Architect: **no** risk-grant product knife; prior FAIL attributed to hot stack. `run-full-noseed.sh` EOF heredoc fixed in `perf/run-full-noseed.sh`.

## Artifacts
`/workspace/r6-capacity-logs/run-full-after-173/`  
(`k6-*.log`, `run-full-console.log`, `run-full.log`, `run-full-result.txt`, `risk-delta.txt`, `master-tip.txt`)  
Summaries: `perf/reports/2026-10-12/*-after-173.summary.json`, `risk-on.json`, `risk-off.json`
