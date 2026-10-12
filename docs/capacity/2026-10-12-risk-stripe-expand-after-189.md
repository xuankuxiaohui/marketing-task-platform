# Capacity evidence: risk stripe expand after #189 (2026-10-12)

**Result: risk-off + risk-on + Δ PASS** after expanding shared risk stripe (users 2000→2800, riskTasks=20, capacity≈52600). Capacity勾空; 非 RC. Ask architect merge-when-CI-green + next knife (run-full-noseed already kicked off on same `mkt`; do not invent product scope).

## Context
- Prior [#189](https://github.com/xuankuxiaohui/marketing-task-platform/pull/189) / [claim-pool hygiene after #188](2026-10-12-claim-pool-hygiene-after-188.md): isolated risk-off **FAIL** `grant_data_exhausted=1787` (finite half-split pool ~18100; P95≈149; Δ not run).
- Architect knife (on #189): **expand risk formal stripe** — more users / riskTaskIds so riskCapacity ≥ formal 5m@current VUs.
- Bans held: 禁缩短 formal; 禁删/放宽 `grant_data_exhausted` count==0; 禁抬日限; 禁整库重seed; 禁降Δ; 禁Tomcat/Redisson/1536M; 禁revert #178; 勾空; 非RC.

## Measurement change (not product)
| Path | Does | Does not |
|---|---|---|
| `perf/seed/expand_risk_stripe.py` | Incremental register users + keep riskTasks; rewrite `perf/state.json` | Wipe / full `seed.py --scale` |
| `perf/risk-delta.js` | Default `RISK_POOL=shared` (full fresh); `half` = legacy | Raise `dailyLimitPerUser` (stays 20) |
| `perf/run-full-noseed.sh` | `clear_today` between shared-pool risk on/off | Relax exhausted gate / lower Δ |

Applied on live `mkt` (**no reseed**): users **2000→2800**, riskTasks=**20**, formal≈2630, k6 capacity≈**52600**. Hygiene `clear_today` between off→on (HYGIENE_EXIT=0). Settle: `SETTLE_READY`.

## Paired risk formal 5m (VARIANT off then on)

| stage | result | grant P95 `{name:grant,scenario:risk}` | checks | http_fail | `grant_data_exhausted` | gate |
|---|---|---|---|---|---|---|
| **risk-off** | **PASS** (ec=0) | ≈**484.70 ms** | **100%** | **0%** | **0** | P95≤500; exhausted count==0 |
| **risk-on** | **PASS** (ec=0) | ≈**382.64 ms** | **100%** | **0%** | **0** | same |
| **risk-delta-gate** | **PASS** | on−off **Δ ≈ −102.06 ms** | — | — | — | NFR 性能4: Δ ≤20 ms **ok** (on faster than off) |

vs prior post-#188 hygiene risk-off: exhausted **1787** / checks≈95.29% / Δ not run → after stripe expand: exhausted **0** / checks 100% / Δ PASS.

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Ask architect: **CI绿就合** this evidence/measurement PR; next knife = continue/observe same-mkt `run-full-noseed` (already started after PASS; do not invent product knife here).
- Caps held: 禁降Δ / 禁抬1536M / 禁抬频率 / 禁抬日限 / 禁revert #178 / 禁Tomcat200–250 / Redisson加码 / 禁放宽 exhausted / 禁整库重seed / 禁缩短 formal.

## Artifacts
`/workspace/r6-capacity-logs/risk-stripe-hygiene-after-188/`
- `result.txt` (`PASS risk-off+on+delta — next run-full-noseed`), `pipeline.log`, `risk-off-exit.txt` / `risk-on-exit.txt` / `delta-exit.txt` (all 0), `k6-risk-off.log`, `k6-risk-on.log`, `capacity-calc.txt`, `expand-apply.log`
- summaries: `perf/reports/2026-10-12/risk-off-stripe-after-188.summary.json`, `risk-on-stripe-after-188.summary.json`, `risk-off.json`, `risk-on.json`
