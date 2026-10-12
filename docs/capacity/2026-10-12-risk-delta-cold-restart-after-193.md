# Capacity evidence: Δ paired cold-restart after #193 — on/off PASS; Δ FAIL (2026-10-12)

**Result:** Architect knife **Δ 配对冷对称卫生** (per-leg `clear_today_claim_counts` + settle READY; **between legs cold-restart portal-app**; then isolated Δ formal). Both legs **PASS**; **Δ FAIL** (Δ≈**213.5** ms ≫ 20). **run-full-noseed not started**. Capacity **勾空**; **非 RC**. Residual numbers for product knife — **do not invent product scope**; **do not lower Δ gate**.

## Context
- #193 evidence-only → master tip **`9a3d594`** (risk-on/off PASS after #192; prior Δ≈103 FAIL; architect: warming order artifact).
- Branch harness (measurement only, no product code): `perf/risk-delta-cold-restart-after-193-2026-10-12` tip **`5433a9f`**
  - adds `perf/run-risk-delta-paired.sh`, `perf/seed/cold_restart_portals.sh`
  - wires cold-restart into `perf/run-full-noseed.sh` risk section
- Caps held: 禁降Δ / 禁抬日限·频率·1536M / 禁放宽 exhausted·http_fail·checks / 禁重seed / 禁revert #178 / Tomcat 禁200–250 / 禁 Redisson 加码 / 禁发明产品刀.

## Protocol (same-mkt, no reseed)
1. Hygiene before risk-on: `clear_today_claim_counts` (users=2800; instances_today 0→0)
2. `seed.py --risk on`; settle on → **SETTLE_TIMEOUT** (30 samples; proceeded)
3. Isolated risk-on k6 5m (`VARIANT=on`, `RISK_POOL=shared`)
4. **Cold-restart** `mkt-portal-app-{1,2}` → both healthy; `COLD_RESTART_EXIT=0`
5. Hygiene before risk-off: `clear_today_claim_counts` (instances_today 1987→0; users_at_limit 0)
6. `seed.py --risk off`; settle off → **SETTLE_READY** sample=23
7. Isolated risk-off k6 5m; restore `--risk on`
8. Δ gate: on−off ≤20 (unchanged)

## Numbers (formal paired RESULT `2026-10-12T22:56:36+0800`)

| stage | result | key metrics |
|---|---|---|
| **risk-on** | **PASS** | grant P95≈**295.73** ms ≤500; exhausted **0**; http_fail **0**; checks **100%**; ec=0 |
| cold-restart | **PASS** | portals restarted; health ready; exit 0 |
| **risk-off** | **PASS** | grant P95≈**82.23** ms ≤500; exhausted **0**; http_fail **0**; checks **100%**; ec=0 |
| **Δ (on−off)** | **FAIL** | Δ≈**213.50** ms (**gate ≤20**) |

Compact: `perf/reports/2026-10-12/risk-on.json` p95=295.731; `risk-off.json` p95=82.234.

Versus prior #193 retest (no cold-restart): on≈405 / off≈302 / Δ≈103. After cold-restart: on≈296 / off≈82 / Δ≈213 — off cooled further; **Δ widened**, still structural FAIL.

## Disposition
- Both legs healthy (exhausted0 / http_fail0 / checks100%). Fail is **Δ gate only**.
- **run-full-noseed not started** (architect: PASS Δ≤20 → continue; still FAIL → numbers back for product knife).
- **勾空; 非 RC.** Do **not** tick R31 boxes.
- Ask architect next (product) knife. Do **not** invent product scope; do **not** relax exhausted/http_fail/checks/Δ; do **not** raise daily limit / 1536M / frequency; do **not** revert #178; do **not** reseed; do **not** lower Δ gate; Tomcat 禁200–250; 禁 Redisson 加码.

## Artifacts
- `/workspace/r6-capacity-logs/delta-cold-restart-after-193/` (`paired-result.txt`, `paired.log`, `k6-risk-on.log`, `k6-risk-off.log`, `cold-restart.log`, hygiene/settle logs)
- summaries: `perf/reports/2026-10-12/risk-{on,off}-cold-restart-after-193.summary.json` (box-local)
- RESULT: `FAIL at risk-delta-gate … on=295.73 off=82.23 delta=213.50`
