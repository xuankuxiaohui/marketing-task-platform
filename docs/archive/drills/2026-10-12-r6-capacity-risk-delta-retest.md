# R6 capacity — cooled risk-delta retest — PASS — 2026-10-12

| Field | Value |
|---|---|
| Tip under test | `da0d086` (+ live #173 product on `mkt`) |
| Result | risk-on / risk-off / **risk-delta-gate PASS** (on≈480.23 / off≈486.29 / **Δ≈−6.06 ms** ≤20) |
| vs run-full-after-173 | hot Δ≈+99.95 FAIL → cooled retest PASS (no product knife) |
| Capacity勾 | **空** |
| RC | **非 RC** |
| 1536M / Δ gate | **not raised** / **not lowered** |
| Script | `perf/run-full-noseed.sh` EOF heredoc fix (see capacity doc) |
| Next | run-full 不重seed continue; 勾评估 only if full PASS |
