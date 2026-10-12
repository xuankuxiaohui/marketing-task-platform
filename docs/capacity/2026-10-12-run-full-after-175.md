# Capacity evidence: run-full after #175 (2026-10-12)

**Result: run-full PASS-ALL** (all k6 stages ec=0 + risk-delta-gate PASS). Capacity勾空; 非 RC. **No** product knife; **1536M not raised; Δ gate not lowered.**

## Context
- Prior: [cooled risk-delta retest](2026-10-12-risk-delta-retest.md) PASS (Δ≈−6.06 ms) after hot run-full-after-173 FAIL (Δ≈+99.95 ms). Architect (~11:09 / ~11:30 CST): evidence merge; restore `state.json` if miss (not capacity FAIL); continue **same-mkt 不重seed** `run-full-noseed.sh` fail-stop; 容量勾评估 only if full PASS (勾 may stay empty).
- This drill: full suite on cooled throwaway `mkt` after #175 merge (`run-full-noseed.sh` EOF fix on master).

## Baseline tip
- Master tip under test: `ab0256262e03737cbf39cbb7ec5cd3b1452fd27f` (= merge #175)
- Isolation: compose project `mkt`; **no** `seed.py --scale` reseed; reused `perf/state.json`
- Caps: Tomcat120, Redisson≤32/1s/1, portal memcg **1536M (1.5GiB) not raised**; Δ gate **≤20 ms not lowered**
- Wall: 11:29:54 → 12:13:17 CST (`PASS-ALL`)
- Harmless mid-stage `KeyError: MYSQL_ROOT_PASSWORD` from `seed.py set_risk` (env not exported in script path) — run continued; **not** restarted for that alone

## run-full stage table (5m each, fail-stop)

| stage | result | P95 (named) | dropped | checks | http_fail | notes |
|---|---|---|---|---|---|---|
| **list** | **PASS** | **95.59 ms** (list) | **2** (≈0.006/s) | 100% (311998) | 0 / 155999 | overall p95 94.97 ms |
| **advance** | **PASS** | **106.15 ms** (progress) | **0** | 100% (270003) | 0 / 93002 | callback p95 12.47 ms; overall 104.46 ms |
| **complete** | **PASS** | **477.37 ms** (grant) | — | 100% (33501) | ~0.0125% (3/23906) | overall p95 406.26 ms; grant ≤500; exhausted 0 |
| **track** | **PASS** | **31.42 ms** | **0** | 100% (108003) | 0 / 36001 | — |
| **admin-list** | **PASS** | **795.79 ms** (instances) | — | 100% (23566) | 0 / 11783 | definitions 98.60 / grants 296.35; overall 694.57; gates ≤800 |
| **ad** | **PASS** | **2.13 ms** | **3** (≈0.010/s) | 100% (179996) | 0 / 89998 | — |
| **risk-on** | **PASS** | **485.53 ms** (grant) | — | 100% (18954) | 0 / 9477 | grant ≤500 |
| **risk-off** | **PASS** | **477.83 ms** (grant) | — | 100% (19452) | 0 / 9726 | — |
| **risk-delta-gate** | **PASS** | on 485.53 − off 477.83 = **Δ ≈ 7.70 ms** | — | — | — | NFR 性能4: Δ ≤20 ms **ok** |

No stage emitted k6 threshold abort. Script exited `PASS-ALL 2026-10-12T12:13:17+0800`.

## Caps confirmed on mkt (post-run)
- `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal memcg 1610612736 B (1536M **not** raised)

## Disposition
- **勾空; 非 RC.** Do **not** tick capacity. Do **not** claim R6 RC.
- Evidence-only — no product / no 1536M raise / no Δ gate change / no risk-grant knife.
- Full-suite PASS closes the prior `run-full` residual gap for this cooled same-mkt path; **other** DEC-006 gaps (Outbox numeric/recovery already partially drilled; cold/hot package; second checklist box 另议) still support **leaving both 勾 empty**. See checkbox-eval update.

## Artifacts
`/workspace/r6-capacity-logs/run-full-after-175/`  
(`k6-*.log`, `run-full.log`, `stdout.log`, `run-full-result.txt`, `risk-delta.txt`, `master-tip.txt`, `merge-175.txt`)  
Summaries: `perf/reports/2026-10-12/{list,advance,complete,track,admin-list,ad,risk-on,risk-off}.summary.json` + `risk-on.json` / `risk-off.json`
