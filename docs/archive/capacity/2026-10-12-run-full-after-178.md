# Capacity evidence: run-full after #178 / #179 (2026-10-12)

**Result: run-full FAIL-STOP at list** (`dropped_iterations{scenario:list}`). Capacity勾空; 非 RC. Do **not** tick. Ask architect for **next knife**.

## Baseline tip
- Master tip under test: `83a5af0fa8f582bc0ae83bc01cb1de38ee21e509` (= Merge #179 docs confirm/勾评估 after #178)
- Product on `mkt`: #178 admin instances COUNT TTL3s singleflight (admin rebuilt post-#178); portals unchanged since earlier dual-IN + V9 + TTL live tip
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused existing `perf/state.json` (p1; tokenUsers=2000)
- Caps locked: Tomcat120; Redisson≤32/1s/1; memcg **1536M not raised**; Δ gate **≤20 ms not lowered**
- Wall: 13:22:23 → 13:28:02 CST (FAIL-STOP)
- Runner: `perf/run-full-noseed.sh` with `SKIP_COMPOSE=1` `LOGDIR=/workspace/r6-capacity-logs/run-full-after-178`

## Prerequisites this slice
| Step | Result |
|---|---|
| #179 tip `37d40a7` CI | all green (e2e / server / openapi-backend / web / deploy-smoke) |
| #179 merge | `83a5af0` (勾空 preserved; 非 RC) |
| Prior admin-list confirm after #178 | PASS instances P95≈116.62 ms |
| Prior run-full-after-#175 | PASS-ALL (list dropped **2** ≈0.006/s; Δ≈7.70 ms) |

## run-full stage table (5m each, fail-stop)

| stage | result | P95 (named) | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **FAIL** | **121.41 ms** (list) ✓ ≤300 | **107** scenario ≈**0.319/s** ✗ (rate&lt;0.05); overall 154 ≈0.460/s | 100% (311694) | 0 / 155847 | ~465.1 QPS; threshold: `dropped_iterations{scenario:list}` |
| **advance** | **not run** | — | — | — | — | fail-stop |
| **complete** | **not run** | — | — | — | — | fail-stop |
| **track** | **not run** | — | — | — | — | fail-stop |
| **admin-list** | **not run** | — | — | — | — | fail-stop |
| **ad** | **not run** | — | — | — | — | fail-stop |
| **risk-on** | **not run** | — | — | — | — | fail-stop |
| **risk-off** | **not run** | — | — | — | — | fail-stop |
| **risk-delta-gate** | **N/A** | — | — | — | — | never reached |

vs after-#175 PASS list (P95 95.59 ms; dropped **2** ≈0.006/s): this run list P95 still under gate but **dropped regressed** past rate&lt;0.05.

## Caps confirmed on mkt (post-FAIL)
- portal `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal/admin memcg 1.5GiB (1536M **not** raised); OOMKilled=false; RestartCount=0
- MySQL post-run ≈971MiB / 1GiB (near memcg ceiling; CPU≈30% shortly after FAIL)

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Evidence-only. **禁** lower Δ gate; **禁** raise 1536M.
- **Ask architect: next knife** (list dropped residual on same throwaway after #178/#179; vs after-175 PASS).

## Artifacts
`/workspace/r6-capacity-logs/run-full-after-178/`  
(`k6-list.log`, `run-full.log`, `run-full-result.txt`, `list-metrics.txt`, `caps-pre.txt`, `caps-post.txt`, `master-tip.txt`, `merge-sha.txt`, `ci-all-green.txt`, `post-fail-state.txt`)  
Summary: `perf/reports/2026-10-12/list.summary.json` (box-local / gitignored)
