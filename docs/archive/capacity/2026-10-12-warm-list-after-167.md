# Capacity evidence: settle+warm → list after #167 (2026-10-12)

**Result: list formal 5m FAIL.** Capacity勾空; 非 RC. No forbidden levers applied.

## #167 merge
- Tip merged: `e2205feb65bd8ce1934104c45eff3297bfb3ad39` (continuum from approved `860ad97`)
- Master merge SHA: `16168b741fc868f18d427123f08bf76ccf7e89e3`

## Warm method (#155-style settle + cache fill)
1. **Settle:** wait MySQL CPU &lt;8% for 3 consecutive samples (no re-seed; stop stray k6).
2. **Curl warm:** 40 users × (`nginx` + `portal-app-1` + `portal-app-2`) authenticated `GET /api/common/task/list` → **120/120 HTTP 200**, avg ~14ms (fill published-index / portal caches on both instances).
3. **Formal:** `list.js` built-in warm 200 QPS × 30s → list 500 QPS × 5m (`startTime` 35s). Gates unchanged.

## List 5m (FAIL)

| gate | value | pass |
|---|---|---|
| P95 `{name:list,scenario:list}` | **1.58 s** | ≤300 ✗ |
| http_fail | **0.00%** | ✓ |
| checks | **100%** | ✓ |
| dropped `{scenario:list}` | **33.2/s** (11127) | &lt;0.05 ✗ |
| peak portal mem | p1 **1534 MiB** / p2 **1513 MiB** of 1536M | near ceiling |
| Restarts | p1 **1** / p2 0; OOMKilled=false | |

## MySQL digest contrast vs dual-IN near-PASS / pre-merge PASS

| source | dual-IN shape | COUNT | avg_ms | rows_examined | notes |
|---|---|---|---|---|---|
| Pre-merge burst (mysql-dropped, near-PASS era) | `user_id=? AND task_id IN (…) AND cycle_key IN (…)` | 15911 | **53.44** | **1256** | plan/parse cost; few rows |
| Pre-merge formal2 PASS (dual-in-restore) | same dual-IN | — | — | — | k6: P95 **174ms**, dropped **0** |
| **This warm formal FAIL** | same dual-IN | 144873 | **71.19** | **244061** | slower + more rows examined |
| Concurrent noise (this run) | `INSERT mtr_task_funnel_d` agg | 1 | **216895** | **11.7M** | admin metrics scan during list |
| | `INSERT mtr_ad_material_d` | 1 | **90101** | **11.7M** | same |
| MySQL CPU during list | — | — | avg **96%** max **105%** | — |

**Read:** warm/settle did **not** restore pre-merge PASS. Dual-IN query remains the hot path and is slower under load; portals sat on the **1536M** ceiling (p1 restart); admin `mtr_*` full-table aggs added MySQL noise. Architect-forbidden levers (cycle_key-only / pair128 / raise 1536M / relax gates / skip-renew) **not** applied — evidence only.

## Caps / rules
Redisson≤32/1s/1, Tomcat120, 1536M **not raised**, skip-renew deferred, cycle_key-only forbidden, pair128 forbidden.

## Artifacts
`/workspace/r6-capacity-logs/warm-list-after-167/` (settle, warm-curl, list-formal-5m.log, digest-after-warm-list.txt)
