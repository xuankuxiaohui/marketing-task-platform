# DEC-006 capacity evidence — 2026-10-11 (bot-box `mkt`)

**Verdict: early-day P0 FAIL retained below; risk 性能4 re-cut after #149 is PASS (see drill). Capacity checkbox still empty; do NOT treat as RC.**

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
| admin-list | P95≤800ms (spec: p1 data) | ~44.4 HTTP/s (10 VUs) | instances **≈699.8 ms**; definitions ≈99.6; grants ≈200.8 | http_fail 0; checks 100% | **PASS at p1 scale** (drill after #151; see `admin-list-p1.json`) |
| ad | 300 QPS; P95≤100ms; drop&lt;0.05; biz≈0 | **≈299.98 HTTP/s** | **≈1.84 ms** | drop 0; checks 100%; http_fail 0 | **PASS** (drill after #150) |
| risk on vs off | \|ΔP95\| ≤20ms + both HTTP200+code0 | — | on **381.1 ms**; off **385.1 ms**; Δ **-4.0 ms** | on/off http_fail 0; checks 100% (hygiene after #149) | **PASS** (drill `2026-10-11-r6-capacity-risk-on.md`); earlier numeric-only FAIL superseded |

In-repo artifacts: `risk-on.json`, `risk-off.json`, `risk-delta.txt`, this `EVIDENCE.md`. Large `*.summary.json` remain box-local under this directory / gitignored; key metrics are inlined in the deploy drill.

## What was not run

- Early-day note above skipped `ad.js`; **re-cut after #150**: formal 5m ad **PASS**. **admin-list p1 after #151**: formal 5m **PASS** (see drill `2026-10-11-r6-capacity-admin-list-p1.md` / `admin-list-p1.json`). Still **not** `run-full.sh` / Outbox recovery; capacity checkbox **empty**.

## Outbox backlog / recovery (after #153)

See [outbox-backlog-recovery.json](outbox-backlog-recovery.json) and [deploy/drills/2026-10-11-r6-capacity-outbox.md](../../deploy/drills/2026-10-11-r6-capacity-outbox.md). Idle PENDING=0; keep-up@50/s peak PENDING≈800 / oldest≤19s; 10k drain ≈119s (~83 eps); DEAD=0. Capacity checkbox still empty; DEC-002 not reopened.
