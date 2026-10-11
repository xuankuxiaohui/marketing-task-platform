# R6 capacity list — residual dropped cut after #146 — **PASS** — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `df190bd` (Merge #146; formal 5m after #144 evidence) |
| Branch | `fix/r6-capacity-list-dropped-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~18:20–18:35 CST |
| Prior evidence | [#146 / 5m after #144](2026-10-11-r6-capacity-list-5m-after-144.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (gate value still `rate<0.05`); DEC **not** reopened |
| Capacity checklist | **stays unchecked** — list formal PASS; checkbox decision left to architect |
| R6 / RC | **进行中 / 非 RC** |
| Advance callback | **not started** (architect: leave capacity/advance decision) |

## Goal

Architect (#146): residual **dropped** only — prefer warmup timing / drop window / Tomcat·nginx scheduling. **No** threshold value change, **no** advance until list PASS; capacity unchecked; not RC.

## Diagnosis (from #146 formal 5m)

| Observation | Reading |
|---|---|
| HTTP/s ~499.4; P95 ~4.13 ms; checks 100%; http_fail 0% | Not compute/DB saturation |
| dropped_iterations 174 (~0.580/s) vs gate rate&lt;0.05 | Only failing gate |
| Smoke 30s: dropped **0** | Cold open alone not enough to explain 5m FAIL |
| VU pool stayed **80** with ~1–3 active for most of the run | Steady-state fine |
| ~**4m16s**: active **211** / pool **214→243**; max latency ~1.87s | Drops cluster with **VU scale-up** after an outlier stall |
| `#144` set `TASK_PUBLISHED_INDEX` TTL **5m** | Formal 5m window can hit synchronized L1/L2 index expiry mid-run → catalog refresh stampede → brief stall → VU ramp drops |
| Cold smoke under this cut | portal-2 heap → **~1.4 GiB** while concurrent threads re-parsed ~1MB catalog JSON |

**Drop window (split):** early/steady ≈0 drops; late spike (~4m16s) carries essentially all 174 drops while k6 grows the pool from `preAllocatedVUs=80` toward 243.

## Changes in this PR

1. **`perf/list.js`** — `warm` (200 QPS × 30s) then formal `list` (500 QPS × `DURATION`, `startTime` 35s); formal `preAllocatedVUs` **250**; thresholds scoped to `scenario:list` (**values unchanged**).
2. **`CacheNamespace.TASK_PUBLISHED_INDEX` TTL 5m → 30m** — published-index past a 5m formal window; publish still evicts.
3. **portal Tomcat** — threads max **300**, min-spare 50, accept-count **200** (Compose `mkt` env on portal×2).
4. **`deploy/nginx/nginx.conf`** — `upstream portal_app` `keepalive 64` + `/api/` keepalive headers; **re-declare** `proxy_set_header` in `/api/` (any `proxy_set_header` clears inheritance — otherwise Host=`portal_app` and Tomcat 11 rejects `_` → HTTP 400).
5. **`TaskPortalAppService.fromCatalog`** — synchronized single-flight parse so cold L1 fill does not N-way re-parse ~1MB JSON under list QPS.

## Formal 5m list retest (bot box Compose `mkt`)

```bash
# after mvn package + compose build/up portal×2 + nginx reload
# gentle curl warm both portals, then:
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -u 0 -w /scripts \
  -e BASE_URL=http://nginx -e DURATION=5m -e STATE_FILE=/scripts/state.json \
  grafana/k6:0.54.0 run --summary-export=/scripts/reports/2026-10-11/list-dropped-5m.summary.json \
  /scripts/list.js
# wall-clock ≈ 5m35s (warm 30s + gap + formal 5m)
```

### Outcomes vs DEC-006 list gate (and vs #146)

| Metric | DEC-006 gate | #146 after #144 (5m) | **This cut formal 5m** | Verdict |
|---|---|---|---|---|
| Formal list HTTP/s | 500 QPS | ~499.4 | **150001 / 5m ≈ 500.0** | **PASS** |
| list P95 | ≤300 ms | ~4.13 ms | **~4.34 ms** | **PASS** |
| dropped_iterations | rate &lt; 0.05 /s | ~0.580/s (174) | **0 /s (0)** `{scenario:list}` | **PASS** |
| checks | &gt;99% | 100% | **100%** (300002/300002 formal) | **PASS** |
| http_req_failed | ==0 | 0.00% | **0.00%** (0/150001 formal) | **PASS** |
| Thresholds crossed | — | dropped only | **none** | **overall PASS** |

During 5m: portals ~32–51% CPU, heap ~370→412 MiB / 1.5 GiB; MySQL ~22–25% (one sample spike ~100%); Redis ~10–13%. max latency ~289 ms (no 1.87s class outlier). `vus_max` stayed at prealloc 330 (warm 80 + list 250) — no mid-run VU stampede.

**Not run**: advance / complete / risk / p1 / `run-full.sh` / threshold value edits / DEC-006 reopen.

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides despite list PASS).
2. **Not** DEC-006 reopen / k6 threshold **value** change (scoping to formal scenario = drop window only).
3. **Not** advance-callback / complete / risk-on this slice.
4. **Not** RC / R6 通过 / full P0 suite PASS.
5. Host logs under `/workspace/r6-capacity-logs/` (box-local); summary JSON gitignored.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this formal 5m list **PASS** linked; **checkbox remains unchecked** pending architect. R6 stays **进行中** / **非 RC**. **Do not** start advance in this slice.
