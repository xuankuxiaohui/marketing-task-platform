# R6 capacity — 重建至 DEC-006 p1 规模后 list 5m — **FAIL** — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `f460dc7` (Merge #157; settle list FAIL; capacity boxes empty) |
| Branch | `docs/r6-capacity-rebuild-list-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~02:16–02:41 CST |
| Prior | [#157](https://github.com/xuankuxiaohui/marketing-task-platform/pull/157) / architect: CI 绿就合 → 下一刀 **重建至 DEC-006 p1 规模**（仅 throwaway `mkt`）→ 沉降 → list 5m；PASS 再续 run-full；**不降门槛**；容量勾仍空；非 RC |
| Isolation | Compose project `mkt` (throwaway; **`down -v` wipe** then fresh up; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (no k6 gate value edits); DEC **not** reopened |
| Capacity checklist | **both boxes stay empty** — this slice does **not** tick capacity |
| R6 / RC | **进行中 / 非 RC** |

## Goal

1. Merge #157 when CI green (sha-pinned tip `e3b336f`).
2. **Full recreate** throwaway Compose `mkt` only (`down -v` + `up -d --build`).
3. **Honest p1 seed** (`perf/seed/seed.py --scale p1`; 2000 portal login users via #156 captcha path; 500k instances / 1M archive users / 5M events).
4. **Settle** until MySQL CPU calm; smoke list HTTP 200 / code 0.
5. **List-only formal 5m** at DEC-006 gates. Fail-stop if FAIL.
6. If list PASS → continue `run-full.sh` residual scenes. Evidence PR; **capacity勾仍空**; **非 RC**.

## Step A — merge #157

| Item | Result |
|---|---|
| Head | `e3b336f1a3d2f469eeb24cb5e75fc2dac30fa5fd` |
| CI | web / openapi-backend / e2e / deploy-smoke / server — all **SUCCESS** |
| Merge | `gh pr merge 157 --merge --match-head-commit e3b336f…` |
| Master tip | **`f460dc726a6b88872b2c9ed7e2efdc1e8487d127`** (Merge pull request #157) |

## Step B — rebuild + p1 seed

| Item | Detail |
|---|---|
| Wipe | `docker compose -p mkt down -v` (mysql-data + prometheus-data removed; **only** project `mkt`) |
| Up | `up -d --build` → portal×2 + admin×1 + mysql + redis + nginx healthy; empty DB (users/instances/events = 0) |
| Seed | `SEED_SCALE=p1 python3 perf/seed/seed.py --scale p1` |
| tokenUsers | **2000**/2000 (real captcha→register/login; 0 captcha-miss fail-stops) |
| Live capacity | users=**1,002,000** / instances=**500,150** / events=**5,002,150** |
| Seed wall | ≈15.7 min (CST 02:17:53 → 02:33:40) |
| `perf/state.json` | scale=`p1`; seededAt present |

## Step C — settle + smoke

| Item | Detail |
|---|---|
| Pre-list MySQL | after 3 consecutive calm polls: CPU **≈0.44%**, mem ≈684 MiB / 1 GiB |
| Portals | portal×2 + admin×1 via nginx; healthy |
| Token smoke | `GET /api/common/task/list` → HTTP **200** / `code=0` |

## Environment

| Item | Detail |
|---|---|
| Compose | `deploy/docker-compose.yml` + `deploy/.env`; project **`mkt`** fresh volumes |
| Apps | **portal×2 + admin×1** via nginx |
| Base | k6 `BASE_URL=http://nginx` on `mkt_default` |
| k6 | `grafana/k6:0.54.0` |
| Command | list-only: `DURATION=5m` `list.js` (built-in warm 200 QPS 30s → formal 500 QPS 5m @ startTime 35s) |
| Host log | `/workspace/r6-capacity-logs/rebuild-list/` (box-local) |

## Per-scene table

| Scene | Result | Notes |
|---|---|---|
| rebuild + p1 seed | **PASS** | fresh volume; 2000 login sessions; DEC-006 p1 archive scale |
| settle | **done** | MySQL CPU ≈0.44% before list |
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
| arrival ~500/s | ≈**343.7**/s overall http_reqs (formal completed ≈**398.5**/s of 500 target) | under |
| P95 ≤300 ms | **≈1580.2 ms** | **FAIL** |
| http_fail rate == 0 | **≈1.64%** (1957 / 119537 formal) | **FAIL** |
| dropped_iterations rate <0.05 | **≈83.4/s** (30458) | **FAIL** |
| checks rate >0.99 | ≈**0.9836** | **FAIL** |

k6 exit **99**. Thresholds crossed: `checks{scenario:list}`, `dropped_iterations{scenario:list}`, `http_req_duration{name:list,scenario:list}`, `http_req_failed{scenario:list}`.

### Diagnosis (list after rebuild+p1)

- Fresh `down -v` + honest p1 seed removed the prior double-bulk archive (~10.9M events / ~1.02M instances from #156 stacking). New capacity is exactly one p1 pass.
- Settle removed post-bulk MySQL pressure (CPU ≈0.44% before start).
- Under formal 500 QPS, portals peaked ~**200% CPU** and MySQL ~**105% CPU**; timeouts + VU starvation → P95≈1.58s, dropped≈83/s, http_fail≈1.6%, checks≈98.4%.
- Relative to settle FAIL (#157: http_fail≈64% / dropped≈157/s / P95≈176ms OK): rebuild+p1 improved fail/drop rates and effective QPS, but **still fails all four formal gates** (P95 now also fails).
- **No** threshold value edits attempted. **No** fake tokens. **No** capacity tick. **Not** RC. **Not** DEC-002 reopen.

## Overall verdict

**FAIL / blocked at list after full recreate + p1 seed + settle**. Architect cut executed honestly; subsequent `run-full.sh` scenes **not** started (fail-stop).

## Artifacts

| Artifact | Location |
|---|---|
| In-repo aggregate | [perf/reports/2026-10-12/list-rebuild.json](../../perf/reports/2026-10-12/list-rebuild.json) |
| k6 summary | `perf/reports/2026-10-12/list-rebuild.summary.json` (large; often gitignored) |
| Host logs | `/workspace/r6-capacity-logs/rebuild-list/` (seed, settle, formal log, docker stats) |

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides; boxes stay empty).
2. **Not** RC / release signing.
3. **Not** a claim that `run-full.sh` overall PASS.
4. **Not** DEC-006 / DEC-002 reopen; **no** k6 threshold edits.
5. **Not** a fake captcha/token bypass — all 2000 users used real captcha→login/register sessions on a fresh volume.
6. Subsequent scenes after list were **not** run (fail-stop).

## Checklist pointer

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: evidence linked; **checkbox remains unchecked**. R6 stays **进行中** / **非 RC**.
