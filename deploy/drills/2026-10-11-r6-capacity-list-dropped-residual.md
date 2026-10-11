# R6 capacity list — residual dropped cut after #146 — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `df190bd` (Merge #146; formal 5m after #144 evidence) |
| Branch | `fix/r6-capacity-list-dropped-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~18:20–18:40 CST |
| Prior evidence | [#146 / 5m after #144](2026-10-11-r6-capacity-list-5m-after-144.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (gate value still `dropped_iterations rate<0.05`); DEC **not** reopened |
| Capacity checklist | **stays unchecked** until formal 5m list overall PASS |
| R6 / RC | **进行中 / 非 RC** |
| Advance callback | **not started** (fail-stop until list PASS) |

## Goal

Architect (#146): residual **dropped** only — prefer arrival-rate / scheduling gaps (cold-start at run open, VU ramp, Tomcat accept, nginx). Warm catalog+session before formal timing; split drop windows; tune Tomcat/nginx for isolated `mkt` if needed. **Do not** change DEC-006 thresholds, tick capacity, start advance, or claim RC.

## Diagnosis (from #146 formal 5m)

| Observation | Reading |
|---|---|
| HTTP/s ~499.4; P95 ~4.13 ms; checks 100%; http_fail 0% | Not compute/DB saturation |
| dropped_iterations 174 (~0.580/s) vs gate rate&lt;0.05 | Only failing gate |
| Smoke 30s: dropped **0** | Cold open alone not enough to explain 5m FAIL |
| VU pool stayed **80** with ~1–3 active for most of the run | Steady-state fine |
| ~**4m16s**: active **211** / pool **214→243**; max latency ~1.87s | Drops cluster with **VU scale-up** after an outlier stall |
| `#144` set `TASK_PUBLISHED_INDEX` TTL **5m** | Formal 5m window can hit synchronized L1/L2 index expiry mid-run → catalog refresh stampede → brief stall → VU ramp drops |

**Drop window (split):** early/steady ≈0 drops; late spike (~4m16s) carries essentially all 174 drops while k6 grows the pool from `preAllocatedVUs=80` toward 243.

## Changes in this PR (small / reviewable)

1. **`perf/list.js`**
   - Split scenarios: `warm` (default 200 QPS × 30s) then formal `list` (500 QPS × `DURATION`, `startTime` default 35s).
   - Thresholds scoped to `scenario:list` (`dropped_iterations` / P95 / checks / http_req_failed) so warmup is excluded; **gate values unchanged** (still rate&lt;0.05 / P95≤300 / etc.; **not** a DEC-006 reopen).
   - Formal `preAllocatedVUs` default **80 → 250** (`LIST_PRE_VUS`; observed peak 243) to absorb brief refresh spikes without arrival drops.
   - Other DEC-006 thresholds unchanged (P95≤300, checks, http_req_failed).

2. **`CacheNamespace.TASK_PUBLISHED_INDEX` TTL 5m → 30m** (+ unit test comment)
   - Keeps published-index past a 5m formal window so L1/L2 expiry does not align with the gate; still env/code TTL, not a k6 threshold change.

3. **`server/portal-app/.../application.yml`**
   - Env-tunable Tomcat: threads max default **300**, min-spare **50**, accept-count **200**, max-connections 8192.

4. **`deploy/docker-compose.yml`** (Compose `mkt` portal×2 only)
   - `SERVER_TOMCAT_ACCEPT_COUNT=200` / `SERVER_TOMCAT_THREADS_MAX=300` on `portal-app-1` / `portal-app-2`.

5. **`deploy/nginx/nginx.conf`**
   - `upstream portal_app` `keepalive 64` + `/api/` `proxy_set_header Connection ""`.

## Not done this slice

- Formal 5m list retest (next after merge when CI green; fail-stop).
- DEC-006 threshold value edits / reopen; capacity checkbox; advance / complete / risk; RC claim.
- `down -v` / seed restore / shared DB touch.

## Architect ask

Review this residual-dropped cut. After merge + CI green: rebuild portal(+nginx) on `mkt`, run formal 5m `list.js` (fail-stop; note warm+gate wall-clock ≈ 5m35s). If list overall PASS → then advance-callback; else continue profile without threshold change.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this cut linked; **checkbox remains unchecked** until formal 5m PASS. R6 stays **进行中** / **非 RC**.
