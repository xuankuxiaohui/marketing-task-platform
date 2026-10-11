# R6 capacity DEC-006 cold/hot 分列 — after #154 — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `960e373` (Merge #154; Outbox gates pinned; capacity boxes empty) |
| Branch | `docs/r6-capacity-cold-hot-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~00:26–00:41 CST |
| Prior | [#154](https://github.com/xuankuxiaohui/marketing-task-platform/pull/154) / architect: after Outbox → **冷热分列** |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (list 500 QPS / P95≤300ms / dropped&lt;0.05; ad 300 QPS / P95≤100ms / dropped&lt;0.05); DEC **not** reopened |
| Capacity checklist | **both boxes stay empty** — this slice does **not** tick capacity |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Satisfy DEC-006 wording: reports must separately show **冷启动** (L1/L2 flushed / portals restarted or equivalent) vs **热稳态** (warmed); hotspot ~20/80 where relevant; **must not** only report hot path.

Minimum package this PR: **list** + **ad** cold|hot columns. Honest scope note for other scenes that prior formal 5m were warm/hot-only.

## Environment

| Item | Detail |
|---|---|
| Compose | `deploy/docker-compose.yml` + `deploy/.env`; project **`mkt`** UP |
| Apps | **portal×2 + admin×1** via nginx |
| Base | k6 `BASE_URL=http://nginx` on `mkt_default`; host `http://127.0.0.1:18080` |
| k6 | `grafana/k6:0.54.0` (cached; no Docker Hub thrash) |
| Data | existing seed/`perf/state.json` (users=1200; adCodes=5; capacity users≈1.01M / instances≈515k / events≈5.79M) |
| Master tip | `960e37349fba0f9417ddc05bd4a7b2459ecded8d` |

## Cold procedure

```bash
# harness: /workspace/r6-capacity-logs/cold-hot/run-cold-hot.sh (box-local)
# Redis DB 2 SCAN+DEL patterns, then portal restart (clear L1)

# list cold
flush: task:published-index:* (deleted=1)
      task:snapshot:* (deleted=1042)
      task:crowd:* (deleted=0)
restart portal-app-1 portal-app-2 → healthy
k6 list.js DURATION=90s LIST_WARM_DURATION=1s LIST_WARM_QPS=1 LIST_GATE_START=2s
  → near-cold (minimal warm so formal window sees flush/restart)

# list hot (caches already filled by cold run)
k6 list.js DURATION=5m   # default warm 30s@200 then formal 500 QPS

# ad cold
flush: ad:position:* (deleted=0 — L2 already empty / TTL 60s)
restart portals → healthy   # L1 clear is the effective cold
k6 ad.js DURATION=90s

# ad hot
k6 ad.js DURATION=5m
```

Host logs (box-local, **not** committed): `/workspace/r6-capacity-logs/cold-hot/`.  
In-repo tiny artifact: [`perf/reports/2026-10-12/cold-hot.json`](../../perf/reports/2026-10-12/cold-hot.json). Large `*.summary.json` stay gitignored / box-local.

## Cold | hot table

| Scene | Mode | Duration | HTTP rate | P95 | checks | http_fail | dropped | k6 exit / note |
|---|---|---|---|---|---|---|---|---|
| **list** | **冷** (near-cold) | 90s | **≈476.8**/s | **≈815.4 ms** | **100%** | **0%** | **≈12.28**/s (1130) | 99 — P95+dropped crossed (expected near-cold) |
| **list** | **热** | 5m (+30s warm@200) | arrival **500**/s (overall incl. warm ≈465.6/s) | **≈9.2 ms** | **100%** | **0%** | **0** | **0 PASS** |
| **ad** | **冷** (L1 restart; L2 flush deleted=0) | 90s | **≈299.6**/s | **≈3.06 ms** | **100%** | **0%** | **≈0.322**/s (29) | 99 — dropped only (P95/checks/http_fail OK) |
| **ad** | **热** | 5m | **≈300.0**/s | **≈2.22 ms** | **100%** | **0%** | **0** | **0 PASS** |

### Reading the 分列

- **List**: cold path clearly worse (P95 hundreds of ms + material dropped) vs hot steady **PASS** at DEC-006 list gates. Demonstrates why DEC-006 forbids reporting only the hot path.
- **Ad**: even cold P95 stays ≪100 ms; cold penalty shows mainly as **dropped_iterations** during L1 refill; hot formal matches prior #151 PASS shape (P95≈2.2 ms, dropped 0).
- **Hotspot ~20/80**: `list.js` hits the shared published catalog page; `ad.js` uses **uniform** `pick(adCodes)` over 5 codes. This package does **not** inject a weighted 20/80 sampler — DEC-006 hotspot wording is treated as production skew expectation on these shared keys, not a k6 weight change this slice.

## Scope honesty (other scenes)

| Scene | This package | Prior formal evidence (warm/hot-only) |
|---|---|---|
| advance | **not** cold/hot 分列 here | [advance-dropped](2026-10-11-r6-capacity-advance-dropped.md) formal 5m **PASS** (warm→formal) |
| complete | **not** cold/hot 分列 here | [complete](2026-10-11-r6-capacity-complete.md) formal 5m **PASS** (warm→formal) |
| admin-list p1 | **not** cold/hot 分列 here | [admin-list-p1](2026-10-11-r6-capacity-admin-list-p1.md) formal 5m **PASS** (warm→formal) |
| track / risk | **not** re-run | prior drills; warm/hot context |
| Outbox | **not** re-opened | [outbox](2026-10-11-r6-capacity-outbox.md) after #154 |

A parallel box harness also started `advance-cold` after ad-hot; **not** required for this PR minimum and **not** claimed here.

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides; boxes stay empty).
2. **Not** DEC-006 reopen / k6 threshold **value** change.
3. **Not** full `run-full.sh` / p1 captcha/login-session residual.
4. **Not** Outbox / DEC-002 reopen.
5. **Not** RC / R6 通过.
6. **Not** claiming every scene has cold|hot columns — only **list** + **ad** in this package.
7. Host logs / large `*.summary.json` remain box-local under `/workspace/r6-capacity-logs/cold-hot/` and gitignored reports.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: cold/hot 分列 evidence linked; **checkbox remains unchecked**. Remaining gaps still include **`run-full.sh` residual** + **p1 captcha/login-session**. R6 stays **进行中** / **非 RC**.
