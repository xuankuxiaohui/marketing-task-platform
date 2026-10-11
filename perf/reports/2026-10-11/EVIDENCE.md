# DEC-006 capacity evidence — 2026-10-11 (bot-box `mkt`)

**Verdict: evidence only — capacity gates NOT fully met; do NOT treat as capacity PASS / RC.**

| Field | Value |
|---|---|
| Master base tip | `e795167a6510753dc656d17ea4dd80ca2478f8f7` (Merge #138) |
| Git dirty during run | harness fixes only (`perf/lib.js`, `run-*.sh`, `seed.py`); no app code |
| Date | 2026-10-11 ~14:46–15:30 CST (UTC 06:46–07:30) |
| Isolation | Compose project `mkt` (throwaway); **no** shared/prod DB |
| Topology | portal×2 + admin×1 via nginx (`name: mkt`) |
| Seed | `SEED_SCALE=p0` (1000 list tasks, 200 token users); **p1 not run** (resource ceiling) |
| Duration | `DURATION=5m` per scenario |
| Runners | `perf/run-p0.sh` scenarios (seed once, then k6 suite); `run-full.sh` / ad / explain **not** run |

## Host / container resources

| Item | Recorded |
|---|---|
| Host | Debian bot box; 8 CPU; 15 GiB RAM; no swap |
| Baseline (pre-suite) | available ~6.1 GiB; portals ~300 MiB each after restart |
| During list | portals ~1.42–1.44 / 1.5 GiB; MySQL ~100% of 1.0 CPU / ~575 MiB; host available ~3.8 GiB |
| After suite | portals ~1.44–1.46 / 1.5 GiB; MySQL ~872 MiB / 1 GiB |
| Compose limits | apps 2.0 CPU / 1536M; MySQL 1.0 CPU / 1G; Redis 0.5 CPU / 256M |
| Hikari | no `maximum-pool-size` in app yml → Spring Boot default **10** |
| MySQL `max_connections` | **151** |
| Redis `maxclients` | **10000**; `maxmemory` 0 (noeviction) |

## Data scale (p0 after seed + soak)

| Metric | Count |
|---|---|
| task_definition | 1043 |
| sys_portal_user (deleted=0) | 201 |
| task_instance | 3463 |
| evt_event_log | 105404 |
| sys_outbox PENDING (post-suite) | **267110** |
| Innodb_row_lock_waits (status) | 14589 |
| Innodb_row_lock_time (ms, status) | 1207037 |

## Cold vs hot

- **Cold-ish**: portals restarted immediately before the suite (`COLD_START_AFTER_PORTAL_RESTART=1`); first scenario (`list`) hit cold L1 with empty heap, then rapidly saturated to memory limit.
- **Hot / degraded**: later scenarios ran with warm caches but **heap already at ~1.5 GiB limit** after list — not a clean independent hot steady-state column.
- Harness does not split k6 summaries into cold/hot windows; values below are **whole-run 5m** figures. Do not treat as dual-column PASS.

## Per-scenario results (facts)

| Scenario | DEC-006 gate | Effective throughput | P95 | Drops / biz | Gate met? |
|---|---|---|---|---|---|
| list | 500 QPS; P95≤300ms; drop rate&lt;0.05; biz≈0 | **54.5 HTTP/s** | **16780 ms** | dropped_iterations 413.7/s; checks 100%; http_fail 0 | **NO** |
| advance | 300 QPS; P95≤200ms; drop&lt;0.05 | **293.7 HTTP/s** | progress **38.6 ms**; callback **3090 ms** | drop 6.28/s; http_fail 0 | **NO** (callback P95 + drops) |
| complete | grant chain P95≤500ms; biz≈0 | 13.3 HTTP/s (4000 reqs) | tagged grant **43.7 ms** | http_fail **100%**; checks 0% (exhausted / business) | **NO** |
| track | ≥6000 events/s; P95≤100ms; drop&lt;0.1% | accepted **6000.0/s**; HTTP 120/s | **11.43 ms** | drop 0; business_ok 100% | **YES (this run)** |
| admin-list | P95≤800ms (spec: p1 data) | ~1039 HTTP/s | **36.4 ms** overall | http_fail 0; checks 100% | **YES on p0 data only** (not p1 scale) |
| risk on vs off | \|ΔP95\| gate: on−off ≤20ms | — | on **56.6 ms**; off **164.0 ms**; Δ **-107.5 ms** | risk-on http_fail 100% (business); numeric Δ OK | numeric Δ OK; **on-variant business FAIL** |

In-repo artifacts: `risk-on.json`, `risk-off.json`, `risk-delta.txt`, this `EVIDENCE.md`. Large `*.summary.json` remain box-local under this directory / gitignored; key metrics are inlined in the deploy drill.

## What was not run

- `SEED_SCALE=p1` / `perf/run-full.sh` / `ad.js` / `capacity_check.py` / `explain_hot.py` — skipped: portals already at memory limit under p0; escalating would not be a fair DEC-006 topology run on this box.
