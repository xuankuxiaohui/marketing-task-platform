# Capacity evidence: cooled risk-delta retest (2026-10-12)

**Result: risk-delta-gate PASS** (Δ ≈ −6.06 ms ≤ 20 ms). Capacity勾空; 非 RC. **No** risk-grant product knife.

## Context
- Prior [run-full after #173](2026-10-12-run-full-after-173.md) FAIL-STOP at risk-delta on a **hot** stack: on 490.49 − off 390.54 = **Δ +99.95 ms**.
- Architect (~11:09 CST): isolated cooled retest confirmed PASS (Δ ≈ −6.1 ms) → **no** risk-grant product knife; open evidence; fix `run-full-noseed.sh` EOF; then continue/re-run run-full 不重seed.
- This drill: **risk-on / risk-off only** on cooled throwaway `mkt` (no scale reseed).

## Baseline tip
- Tip under test: `da0d086` master (+ live #173 product dual-IN + V9 + TTL3s singleflight already deployed on `mkt`)
- Isolation: compose project `mkt`; **no** `seed.py --scale`; reused `perf/state.json`
- Caps: Tomcat120, Redisson≤32/1s/1, memcg **1536M not raised**; Δ gate **≤20 ms not lowered**
- Wall: 10:56:26 → ~11:08 CST

## risk-delta table (5m each)

| stage | result | grant P95 | checks | http_fail | notes |
|---|---|---|---|---|---|
| **risk-on** | **PASS** | **480.23 ms** | 100% | 0 | ec=0 |
| **risk-off** | **PASS** | **486.29 ms** | 100% | 0 | ec=0 |
| **risk-delta-gate** | **PASS** | on−off **Δ ≈ −6.06 ms** | — | — | NFR 性能4: Δ ≤20 ms **ok** |

vs prior hot run-full: on 490.49 / off 390.54 / Δ +99.95 **FAIL** (hot stack noise — not a product regression requiring a risk knife).

## Script fix (EOF heredoc)
Ad-hoc `run-full-noseed.sh` (and the risk-delta-only twin) put `|| { … }` between `<<'PY'` and the heredoc body → bash `syntax error: unexpected end of file` after risk-off, forcing a manual gate. Fixed in-repo as [`perf/run-full-noseed.sh`](../../perf/run-full-noseed.sh): use `if ! python3 … <<'PY' … PY; then …; fi` (same pattern as `perf/run-full.sh`).

## Caps confirmed on mkt
- `SERVER_TOMCAT_THREADS_MAX=120`
- `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`
- portal memcg 1.5GiB (1536M **not** raised)

## Disposition
- **勾空; 非 RC.** Do **not** tick capacity. Do **not** claim R6 RC.
- **No** risk-grant product knife (cooled PASS confirms prior FAIL was hot-stack).
- **禁** lower Δ gate; **禁** raise 1536M.
- Next: continue/re-run **run-full** fail-stop 不重seed on same `mkt` (must clear risk-delta-gate; prefer full stage PASS). 容量勾评估 only if full PASS.

## Artifacts
`/workspace/r6-capacity-logs/risk-delta-retest/`  
(`SUMMARY.txt`, `result.txt`, `risk-delta.txt`, `k6-risk-on.log`, `k6-risk-off.log`, `run.log`, `console.log`)  
Tiny summaries already on box: `perf/reports/2026-10-12/risk-on.json`, `risk-off.json` (p95 480.23 / 486.29).
