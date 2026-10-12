# Capacity evidence: measurement retest after #192 — risk-on/off PASS; Δ FAIL (2026-10-12)

**Result:** Architect-approved measurement-only retest after #192 merge. Isolated **risk-on PASS**, **risk-off PASS**, **Δ FAIL** (Δ≈**103.1** ms ≫ 20). **run-full not started**. Capacity **勾空**; **非 RC**. Ask architect next knife — **do not invent product scope**.

## #192 merge
- tip `9e9bcca` → master tip **`138d14d`** (勾空; CI green)
- Architect next knife: measurement retest only（禁发明产品刀）

## Attempt-1 same-mkt (launcher tip at start `aebe150`; master already `138d14d` docs-only)

Hygiene: `clear_today_claim_counts` before on and before off; settle READY sample=14 (mysql&lt;8 ×3 after ≥6). users=2800; no reseed.

| stage | result | key metrics |
|---|---|---|
| **risk-on** | **PASS** | grant P95≈**405.43** ≤500; exhausted **0**; http_fail **0**; checks **100%** |
| **risk-off** | **PASS** | grant P95≈**302.33** ≤500; exhausted **0**; http_fail **0**; checks **100%** |
| **Δ (on−off)** | **FAIL** | Δ≈**103.10** ms (**gate ≤20**) |

Compact: `perf/reports/2026-10-12/risk-on.json` p95=405.434; `risk-off.json` p95=302.336.

## Disposition
- **Structural Δ**, not http/check flake. Attempt-2 was **not** completed (stopped: brief says flake-retry only for occasional http/check; Δ≫20 → evidence + await architect).
- **勾空; 非 RC.** Do **not** tick R31 boxes.
- **Ask architect next knife.** Do **not** invent product scope; do **not** relax exhausted/http_fail/checks/Δ gates; do **not** raise daily limit / 1536M / frequency; do **not** revert #178; do **not** reseed; do **not** lower Δ gate.
- Caps held: 禁降Δ / 禁抬日限·频率·1536M / 禁放宽exhausted·http_fail·checks / 禁重seed / 禁revert#178 / 禁发明产品刀.

## Artifacts
- `/workspace/r6-capacity-logs/risk-on-retest-after-192/` (`result-attempt-1.txt`, `risk-delta-a1.txt`, `k6-risk-on-a1.log`, `k6-risk-off-a1.log`, `pipeline.log`, `attempt-2-stopped.txt`)
- summaries: `perf/reports/2026-10-12/risk-{on,off}-retest-after-192-a1.summary.json` (box-local)
