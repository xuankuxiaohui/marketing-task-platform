# Capacity evidence: settle → isolated list PASS; run-full FAIL-STOP at risk-on after #191 (2026-10-12)

**Result:** Same-mkt true settle → isolated list formal **5m PASS**; continued `run-full-noseed` **FAIL-STOP at risk-on** (single HTTP/check miss). Capacity **勾空**; **非 RC**. Ask architect next knife — **do not invent product scope**.

## #191 merge
- tip `61ada0b` → master tip **`aebe150`** (勾空; CI green)

## Isolated list after true settle (不重seed)

| metric | value | gate |
|---|---|---|
| settle | **SETTLE_READY** sample=15 (mysql&lt;8 ×3) | — |
| list P95 | ≈**58.75** ms | ≤200 ✓ |
| dropped `{scenario:list}` | ≈**0.024**/s (8) | &lt;0.05 ✓ |
| checks / http_fail | 100% / 0% | ✓ |

vs prior run-full-after-190 list FAIL (dropped≈20.7/s): settle cleared residual.

## run-full-noseed (tip `aebe150`, same mkt)

| stage | result | key metrics |
|---|---|---|
| list | **PASS** | P95≈46.87; dropped **0**; checks 100%; http_fail 0 |
| advance | **PASS** | progress P95≈68.89; callback P95≈16.10; dropped scenario **0**; checks 100% |
| complete | **PASS** | grant P95≈400.51; exhausted 0; checks 100%; http_fail 0 |
| track | **PASS** | P95≈33.11; dropped 0 |
| admin-list | **PASS** | overall P95≈404.47; checks 100% |
| ad | **PASS** | P95≈1.74; dropped 0 |
| **risk-on** | **FAIL-STOP** | grant P95≈**337.14** ≤500 ✓; exhausted **0** ✓; **http_fail≈3.48e-5** (1/28755) ✗ rate==0; **checks≈99.9965%** (2 fails) ✗ rate==1 |
| risk-off / Δ | not run | — |

Sole crossed thresholds on risk-on: `checks{scenario:risk}`, `http_req_failed{scenario:risk}`. One grant missed HTTP 200 / accepted (28754/28755). **Not** pool exhausted; **Not** P95; **Not** daily-limit raise candidate without architect nod.

## Disposition
- **勾空; 非 RC.** Do **not** tick R31 boxes.
- **Ask architect next knife** (flake vs product vs measurement hygiene — await). Do **not** invent product scope; do **not** relax exhausted/http_fail/checks gates; do **not** raise daily limit / 1536M / frequency; do **not** revert #178; do **not** reseed.
- Caps held: 禁降Δ / 禁抬日限·频率·1536M / 禁放宽exhausted / 禁重seed / 禁revert#178.

## Artifacts
- Isolated: `/workspace/r6-capacity-logs/list-settle-after-191/` (`verdict.txt` PASS, `list-formal-5m.log`, `settle-poll.txt`)
- run-full: `/workspace/r6-capacity-logs/list-settle-after-191/run-full/` (`run-full-result.txt`, `k6-*.log`, `stage-metrics-full.txt`)
- summaries: `perf/reports/2026-10-12/{list,advance,complete,track,admin-list,ad,risk-on}.summary.json` (box-local)
