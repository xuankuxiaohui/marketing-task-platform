# R6 capacity `run-full.sh` residual — after #155 — **FAIL** (list) — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `18798ae` (Merge #155; cold/hot 分列; capacity boxes empty) |
| Branch | `fix/r6-capacity-run-full-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~01:02–01:31 CST |
| Prior | [#155](https://github.com/xuankuxiaohui/marketing-task-platform/pull/155) / architect: CI 绿就合 → 下一刀 **run-full.sh residual**（先修清/诚实处理 p1 captcha；fail-stop；不勾容量；非 RC） |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (no k6 gate value edits); DEC **not** reopened |
| Capacity checklist | **both boxes stay empty** — this slice does **not** tick capacity |
| R6 / RC | **进行中 / 非 RC** |

## Goal

1. Merge #155 when CI green.
2. Fix / honestly handle **p1 captcha** so `seed.py --scale p1` can complete **~2000 portal login users** (real sessions; no fake tokens).
3. Run official `bash perf/run-full.sh` on isolated Compose `mkt` (SEED_SCALE=p1, DURATION=5m).
4. Fail-stop; open evidence PR; **capacity勾仍空**; **非 RC**.

## Captcha root-cause + fix

### What broke (#152)

Official `python3 perf/seed/seed.py --scale p1` died at ~**1020/2000** portal logins with:

`captcha missing in redis for portal` (SEED_EXIT=1).

`captcha()` did GET `/api/common/captcha` then `redis_get(captcha:portal:{id})` via **`docker compose exec` redis-cli** every user. Redis DB index was already **2** (correct). Seed already raised `ratelimit.login.ip/account.per-minute` to 100000. Captcha TTL default 120s. No evidence of wrong DB or invented codes — the HTTP issue returned `code===0` + `captchaId`, but the subsequent Redis GET was empty under sustained seed load.

### Fix (this PR — `perf/seed/seed.py`)

| Change | Why |
|---|---|
| Cache compose redis container id; read via `docker exec … redis-cli --no-auth-warning -n 2` | Avoid per-call `docker compose exec` YAML/API overhead that produced empty GETs under ~1k+ captcha reads |
| `captcha()` polls Redis briefly then **re-issues** up to 6 attempts | Honest retry; never fabricate captcha codes |
| Portal register/login loop retries on captcha miss (3×) | Fail-stop only after real retries |
| `put_config(auth.captcha.ttl-seconds, 600)` during seed | Headroom for poll/re-issue storms |

**Not done:** lowering user count; faking tokens without login; changing DEC-006 thresholds.

### Seed completeness this run

| Item | Result |
|---|---|
| Precheck | 500/500 `register_user` stress OK (~199s) before `run-full` |
| Official seed in `run-full.sh` | **`tokenUsers=2000`** / listTasks=1000 / instancesScale=500000 / archiveUsers=1M / events=5M |
| Captcha miss log lines | **0** |
| Live capacity after seed | users≈1,009,363 / instances≈1,015,362 / events≈10,885,356 |
| `perf/state.json` | scale=`p1`; seededAt present; 2000 token users with real login sessions |

## Environment

| Item | Detail |
|---|---|
| Compose | `deploy/docker-compose.yml` + `deploy/.env`; project **`mkt`** UP (`SKIP_COMPOSE=1`) |
| Apps | **portal×2 + admin×1** via nginx (healthy) |
| Base | k6 `BASE_URL=http://nginx` on `mkt_default`; host seed `http://127.0.0.1:18080` |
| k6 | `grafana/k6:0.54.0` |
| Command | `REPORT_DATE=2026-10-12 SKIP_COMPOSE=1 SEED_SCALE=p1 DURATION=5m COMPOSE_NETWORK=mkt_default bash perf/run-full.sh` |
| Host log | `/workspace/r6-capacity-logs/run-full/run-full-2026-10-12.log` (box-local) |

## Per-scene table (`run-full.sh`)

| Scene | Result | Notes |
|---|---|---|
| **seed p1** | **PASS** | 2000/2000 portal login users; captcha path fixed |
| **list** | **FAIL** | formal 5m; see metrics below; **fail-stop here** |
| advance | **not run** | fail-stop |
| complete (risk off→on) | **not run** | fail-stop |
| track | **not run** | fail-stop |
| admin-list | **not run** | fail-stop |
| ad | **not run** | fail-stop |
| risk-on / risk-off delta | **not run** | fail-stop |
| capacity_check / explain_hot | **not run** | fail-stop |

### list formal 5m (FAIL)

| Gate (DEC-006 / list.js) | Observed | Verdict |
|---|---|---|
| arrival ~500/s | ≈**418.6**/s overall http_reqs | under target |
| P95 ≤300 ms | **≈920.8 ms** | **FAIL** |
| http_fail rate == 0 | **≈0.236%** (346) | **FAIL** |
| dropped_iterations rate <0.05 | **≈8.75/s** (3194) | **FAIL** |
| checks rate >0.99 | ≈**0.9976** | PASS |

k6: thresholds crossed on `dropped_iterations{scenario:list}`, `http_req_duration{name:list,scenario:list}`, `http_req_failed{scenario:list}`.

## Overall residual verdict

**FAIL / blocked at list** after honest p1 seed (2000 login sessions). Captcha residual from #152 is **closed**. Full matrix residual remains **open** because `run-full.sh` did not complete past list.

### Diagnosis (list)

- Immediately after seed bulk, MySQL was ~**909 MiB / 1 GiB** and ~**100% CPU**; Redis ~41 MiB / 256 MiB.
- Fresh bulk in this seed pushed instances≈1.02M and events≈10.9M (prior live archive was already large; this run added another 500k instances + 5M events on top of existing rows via INSERT / growth).
- Prior **list hot PASS** in [cold-hot](2026-10-12-r6-capacity-cold-hot.md) (#155) was warm→formal on existing archive **without** this same-session heavy bulk+soak coupling.
- **No** threshold value edits attempted.

## Artifacts

| Artifact | Location |
|---|---|
| In-repo aggregate | [perf/reports/2026-10-12/run-full.json](../../perf/reports/2026-10-12/run-full.json) |
| k6 list summary | `perf/reports/2026-10-12/list.summary.json` (large; often gitignored) |
| Host log | `/workspace/r6-capacity-logs/run-full/run-full-2026-10-12.log` |
| Seed fix | `perf/seed/seed.py` |

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides; boxes stay empty).
2. **Not** RC / release signing.
3. **Not** a claim that `run-full.sh` overall PASS.
4. **Not** DEC-006 / DEC-002 reopen; **no** k6 threshold edits.
5. **Not** a fake captcha/token bypass — all 2000 users used real captcha→login/register sessions.
6. Subsequent scenes after list were **not** run (fail-stop).

## Checklist pointer

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: p1 captcha/login-session completeness demonstrated; `run-full.sh` residual **FAIL at list**; **checkbox remains unchecked**. Cite prior Outbox ([outbox](2026-10-11-r6-capacity-outbox.md)) + cold/hot ([cold-hot](2026-10-12-r6-capacity-cold-hot.md)). R6 stays **进行中** / **非 RC**.
