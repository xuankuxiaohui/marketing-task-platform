# R6 capacity — 沉降后 list 单场景复测 — **FAIL** — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `79c2a86` (Merge #156; run-full residual FAIL at list; captcha 2000 OK; capacity boxes empty) |
| Branch | `docs/r6-capacity-list-settle-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~01:47–01:57 CST |
| Prior | [#156](https://github.com/xuankuxiaohui/marketing-task-platform/pull/156) / architect: CI 绿就合 → 下一刀 **沉降后 list 单场景复测**; PASS 再续 run-full 后续; **不叠 bulk**; 不降门槛; 容量勾仍空; 非 RC |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (no k6 gate value edits); DEC **not** reopened |
| Capacity checklist | **both boxes stay empty** — this slice does **not** tick capacity |
| R6 / RC | **进行中 / 非 RC** |

## Goal

1. Merge #156 when CI green (sha-pinned).
2. **Settle** after prior p1 bulk: wait MySQL CPU/mem headroom; **do not** re-run full p1 seed/bulk.
3. **List-only** formal 5m (warm→formal in `list.js`; gates unchanged).
4. If list PASS → continue remaining `run-full.sh` scenes without another bulk seed. If FAIL → fail-stop + evidence PR.

## Step A — merge #156

| Item | Result |
|---|---|
| Head | `23fcc28bc64a18d006299ccbe7c842a67801c7e7` |
| CI | web / openapi-backend / e2e / deploy-smoke / server — all **SUCCESS** |
| Merge | `gh pr merge 156 --merge --match-head-commit 23fcc28…` |
| Master tip | **`79c2a86c2fea8084131666c9ccff7e0c202e7ba5`** (Merge pull request #156) |

## Step B — settle (no bulk)

| Item | Detail |
|---|---|
| Bulk seed | **NOT run** (architect: 不叠 bulk) |
| State reused | `perf/state.json` scale=`p1`; **tokenUsers=2000** (real #156 captcha→login sessions) |
| Live capacity | users≈1,009,363 / instances≈1,015,362 / events≈10,885,356 |
| Pre-list MySQL | after waiting out admin `mtr_task_funnel_d` agg spike: CPU **≈0.46%**, mem ≈91.9% of 1 GiB (working set; not bulk-insert pressure) |
| Portals | portal×2 + admin×1 via nginx; healthy |
| Token smoke | `GET /api/common/task/list` → HTTP 200 / `code=0` |

## Environment

| Item | Detail |
|---|---|
| Compose | `deploy/docker-compose.yml` + `deploy/.env`; project **`mkt`** UP |
| Apps | **portal×2 + admin×1** via nginx |
| Base | k6 `BASE_URL=http://nginx` on `mkt_default` |
| k6 | `grafana/k6:0.54.0` |
| Command | list-only: `DURATION=5m` `list.js` (built-in warm 200 QPS 30s → formal 500 QPS 5m @ startTime 35s); **no** `seed.py --scale p1` |
| Host log | `/workspace/r6-capacity-logs/list-settle/` (box-local) |

## Per-scene table

| Scene | Result | Notes |
|---|---|---|
| settle | **done** | no bulk; reused 2000 tokens + p1-scale data |
| **list** | **FAIL** | formal 5m; see metrics; **fail-stop here** |
| advance | **not run** | fail-stop |
| complete (risk off→on) | **not run** | fail-stop |
| track | **not run** | fail-stop |
| admin-list | **not run** | fail-stop |
| ad | **not run** | fail-stop |
| risk-on / risk-off delta | **not run** | fail-stop |

### list formal 5m (FAIL)

| Gate (DEC-006 / list.js) | Observed | Verdict |
|---|---|---|
| arrival ~500/s | ≈**270.4**/s overall http_reqs | under target |
| P95 ≤300 ms | **≈175.9 ms** | **PASS** |
| http_fail rate == 0 | **≈64.14%** (59474 failed / 92719 formal) | **FAIL** |
| dropped_iterations rate <0.05 | **≈156.8/s** (57253) | **FAIL** |
| checks rate >0.99 | ≈**0.3586** | **FAIL** |

k6 exit **99**. Thresholds crossed: `checks{scenario:list}`, `dropped_iterations{scenario:list}`, `http_req_failed{scenario:list}`. P95 gate alone **passed**.

### Diagnosis (list after settle)

- Settle removed the immediate post-bulk MySQL 100% idle-wait coupling from #156 run-full; quiet window confirmed before start.
- Under formal 500 QPS, portals climbed to ~**100% mem / high CPU** and MySQL returned to ~**100% CPU**; many request **timeouts** → high http_fail + VU starvation → massive dropped_iterations.
- Successful-path latency can look fine (med≈0.32 ms; P95≈176 ms) while a large fraction of arrivals never complete — classic saturation, not a threshold-calibration issue.
- **No** threshold value edits attempted. **No** fake tokens. **No** capacity tick. **Not** RC.

## Overall verdict

**FAIL / blocked at settled list-only**. Architect cut (沉降后 list) executed honestly; subsequent `run-full.sh` scenes **not** started (fail-stop).

## Artifacts

| Artifact | Location |
|---|---|
| In-repo aggregate | [perf/reports/2026-10-12/list-settle.json](../../perf/reports/2026-10-12/list-settle.json) |
| k6 summary | `perf/reports/2026-10-12/list-settle.summary.json` (large; often gitignored) |
| Host logs | `/workspace/r6-capacity-logs/list-settle/` (formal log, settle notes, docker stats) |

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides; boxes stay empty).
2. **Not** RC / release signing.
3. **Not** a claim that list / `run-full.sh` PASS.
4. **Not** DEC-006 reopen; **no** k6 threshold edits.
5. **Not** a bulk re-seed before list.
6. Subsequent scenes after list were **not** run (fail-stop).

## Checklist pointer

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: settled list-only formal **FAIL** (P95 OK; http_fail/checks/dropped crossed); **checkbox remains unchecked**. R6 stays **进行中** / **非 RC**.
