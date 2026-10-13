# Capacity evidence: run-full after #175 (2026-10-12)

**Result: run-full PASS-ALL** (all k6 stages ec=0 + risk-delta-gate PASS). Capacity勾空; 非 RC. Do **not** tick unless architect says.

## Baseline tip
- Master tip under test: `ab0256262e03737cbf39cbb7ec5cd3b1452fd27f` (= Merge #175 cooled risk-delta retest + `perf/run-full-noseed.sh` EOF fix)
- Live product on `mkt`: #173 dual-IN + V9 + TTL3s singleflight (unchanged; no rebuild this run)
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused existing `perf/state.json` (restored after transient missing-mount env FAIL — **not** a capacity gate FAIL)
- Caps: Tomcat120, Redisson≤32/1s/1, memcg **1536M not raised**; Δ gate **≤20 ms not lowered**
- Wall: 11:29:54 → 12:13:17 CST
- Runner: `perf/run-full-noseed.sh` (heredoc `if ! …; then …; fi` — no EOF after risk-off)

## run-full stage table (5m each, fail-stop)

| stage | result | P95 (named) | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **PASS** | **95.59 ms** (list) | **2** (≈0.006/s &lt;0.05) | 100% (311998) | 0 / 155999 | ~465.6 QPS |
| **advance** | **PASS** | **106.15 ms** (progress) | **0** | 100% (270003) | 0 / 93002 | callback p95 12.46 ms |
| **complete** | **PASS** | **477.37 ms** (grant) | — | 100% (33501) | scenario 0% (raw 3/23906) | grant ≤500 |
| **track** | **PASS** | **31.41 ms** | **0** | 100% (108003) | 0 / 36001 | — |
| **admin-list** | **PASS** | **795.78 ms** (instances) | — | 100% (23566) | 0 / 11783 | definitions 98.6 / grants 296.34; gates ≤800 |
| **ad** | **PASS** | **2.12 ms** | **3** (≈0.01/s &lt;0.05) | 100% (179996) | 0 / 89998 | — |
| **risk-on** | **PASS** | **485.53 ms** (grant) | — | 100% (18954) | 0 | grant ≤500 |
| **risk-off** | **PASS** | **477.82 ms** (grant) | — | 100% (19452) | 0 | — |
| **risk-delta-gate** | **PASS** | on 485.53 − off 477.83 = **Δ ≈7.70 ms** | — | — | — | NFR 性能4: Δ ≤20 ms **ok** |

No stage emitted k6 `thresholds on metrics` / abort. Script completed with `PASS-ALL` (gate ran in-process; no manual recompute).

## Caps confirmed on mkt (post-run)
- `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal memcg 1.5GiB (1536M **not** raised)

## Env note (not a gate FAIL)
First attempt FAIL-STOP at list with k6 `GoError` missing `/scripts/state.json` (mount/checkout mismatch). Architect confirmed **not** a capacity FAIL. Restored existing `perf/state.json` (same throwaway, 不重seed) and re-ran → this PASS-ALL.

## Disposition
- **勾空; 非 RC.** Do **not** tick capacity unless architect says. Do **not** claim R6 RC.
- Evidence + 勾评估 opened separately/together — 勾 stays empty by default.
- **禁** lower Δ gate; **禁** raise 1536M.

## Artifacts
`/workspace/r6-capacity-logs/run-full-after-175/`  
(`k6-*.log`, `run-full.log`, `run-full-result.txt`, `risk-delta.txt`, `master-tip.txt`)  
Summaries: `perf/reports/2026-10-12/*.summary.json` (box-local / gitignored)
