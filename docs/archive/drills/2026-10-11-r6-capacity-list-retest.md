# R6 capacity list retest after #142 — FAIL — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `6ac4133` (Merge #142; list-path cache + Hikari) |
| Branch | `docs/r6-capacity-list-retest-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~16:57–17:06 CST |
| Prior evidence | [#139 P0 FAIL](2026-10-11-r6-capacity-dec006.md); [#142 list-path fix](2026-10-11-r6-capacity-list-path.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged**; DEC **not** reopened |
| Capacity checklist | **stays unchecked** |
| R6 / RC | **进行中 / 非 RC** |
| Advance callback | **not started** (architect: advance **by result**; list still FAIL → fail-stop) |

## Goal

After #142 merge: rebuild portal images on Compose `mkt`, run **targeted list** re-measure (30s smoke + 5m), document honest PASS/FAIL vs #139, then **fail-stop** on list (do **not** start advance).

## Commands run

```bash
# HEAD == origin/master == 6ac4133c1d901c7747be45c1c605329d929995a5
ROOT=/workspace/marketing-task-platform
COMPOSE=(docker compose --env-file deploy/.env -f deploy/docker-compose.yml -p mkt)
cd "$ROOT"

# package (portal jar for image rebuild)
# log: /workspace/r6-capacity-logs/mvn-package-list-path.log

# rebuild images (leave stack UP; no down -v)
"${COMPOSE[@]}" build admin-app portal-app-1 portal-app-2
# log: /workspace/r6-capacity-logs/rebuild-portal-list-path.log

"${COMPOSE[@]}" up -d --force-recreate admin-app portal-app-1 portal-app-2
# log: /workspace/r6-capacity-logs/recreate-portal-list-path.log
# wait healthy; seed state reused from #139 (perf/state.json; ~1044 PUBLISHED)

K6_IMAGE=grafana/k6:0.54.0
NETWORK=mkt_default
run_list () {
  local dur="$1" out="$2"
  docker run --rm --network "$NETWORK" \
    -v "$ROOT/perf:/scripts" -u 0 \
    -e BASE_URL=http://nginx -e INTERNAL_URL=http://portal-app-1:8081 \
    -e STATE_FILE=/scripts/state.json -e DURATION="$dur" \
    -w /scripts "$K6_IMAGE" run /scripts/list.js | tee "$out"
}

# resource snapshots: list-retest-resources-{before,after-smoke,before-5m,during-5m,after-5m}.txt
run_list 30s /workspace/r6-capacity-logs/list-retest-smoke-30s.log
run_list 5m  /workspace/r6-capacity-logs/list-retest-5m.log
```

**Not run**: advance / complete / risk / p1 / `run-full.sh` / threshold edits / DEC-006 reopen.

## Outcomes vs DEC-006 list gate (and vs #139)

| Metric | DEC-006 gate | #139 list 5m (pre-#142) | #142 rebuild retest 5m | Verdict |
|---|---|---|---|---|
| Effective HTTP/s | 500 QPS | **~54.5** | **~116.1** | better, still ≪ 500 → **FAIL** |
| list P95 | ≤300 ms | **~16.8 s** | **~30 s** | worse / still ≫ gate → **FAIL** |
| dropped_iterations | rate &lt; 0.05 | **132543** (~413/s) | **111577** (~338/s) | still huge → **FAIL** |
| checks | &gt;99% | **100%** | **~38.7%** | collapsed → **FAIL** |
| http_req_failed | ==0 | **0.00%** (0/17457) | **~61.3%** (23497/38329) | new failure mode → **FAIL** |
| Thresholds crossed | — | `dropped_iterations`, `http_req_duration{name:list}` | `checks`, `dropped_iterations`, `http_req_duration{name:list}`, `http_req_failed` | **FAIL** |

### Smoke 30s (same rebuild; not a gate claim)

| Metric | Smoke 30s |
|---|---|
| http_reqs | ~90.8/s |
| list P95 | ~8.5 s |
| checks | ~99.0% |
| http_req_failed | ~0.97% |
| dropped_iterations | ~365.6/s |
| Thresholds | `dropped_iterations`, `http_req_duration{name:list}`, `http_req_failed` crossed |

Smoke already missed latency/drop gates; 5m then degraded into mass HTTP failure.

### Suite log markers (5m)

```text
✗ list http 200 / list code 0 → 38% (✓ 14832 / ✗ 23497)
checks ~38.69%; dropped_iterations 111577 (~338/s)
http_req_failed ~61.30%; http_reqs ~116.15/s
{ name:list } p(95)=30s (max=1m0s); expected_response:true p(95)~7.88s
thresholds on metrics 'checks, dropped_iterations, http_req_duration{name:list}, http_req_failed' have been crossed
```

## http_fail ~61% vs #139 ≈0% (timeouts vs 5xx)

|#139| Retest after #142|
|---|---|
| All completed list HTTP returned **200** / business `code=0` (checks 100%); FAIL was **slow + dropped arrivals** only | Majority of attempts **did not** return success |

Measured taxonomy (bot box, same window as 5m; do not invent):

| Source | Observation |
|---|---|
| k6 warnings | **109** `Request Failed` … **`request timeout`** (client-side; aligns with p95=30s / max=60s on failed path) |
| nginx access (path `task/list`, ~08:59–09:06Z) | **21400× 502**, **2025× 500**, **195× 499**, **17790× 200** |
| Prometheus `http_server_requests` (portal, list URI, range) | sustained **status=500** increases; **200** collapses mid-run; some **503** |
| portal logs (during 5m) | Dominant: **`CannotGetJdbcConnectionException` / `SQLTransientConnectionException` / Hikari `Connection is not available`** (thousands). Secondary: **`RedisTimeoutException` / `RedisResponseTimeoutException`** (Sa-Token / Redis under CPU pressure; tens–low hundreds) |

**Reading**: #139 saturated into multi-second **successful** responses. Post-#142, higher attempted throughput (~116/s) pushed portals into **pool exhaustion + upstream 502/500**, so k6 `http_req_failed` flipped from 0% → ~61%. Failures are **mostly server/proxy errors and pool waits**, not a pure client-timeout-only story (timeouts are present but secondary by count).

## Resource / saturate facts (measured)

### Docker stats (during 5m)

From `/workspace/r6-capacity-logs/list-retest-resources-during-5m.txt` (CST):

- Early: portals **~150–200% CPU**, heap climbing **~0.4 → ~1.45 GiB / 1.5 GiB**
- MySQL: quickly **~70–100% of 1 CPU**, mem **~1 GiB / 1 GiB**
- Mid/late: portal CPU often drops to **~0–5%** while MySQL stays **~99–102%** and portals stay **~1.45–1.48 GiB** (threads blocked / timing out while DB still pegged)
- After 5m: portals **~1.45–1.47 GiB / 1.5 GiB**; MySQL **~99.6% CPU**, **1020 MiB / 1 GiB**

### Redis published-index / snapshots

| Fact | Value |
|---|---|
| Namespace | `task:published-index` TTL **30s** (`CacheNamespace.TASK_PUBLISHED_INDEX`) |
| Snapshot keys after soak | **1044** `task:snapshot:*` present in Redis DB 2 (= published catalog size) |
| `task:published-index:all` at doc time (idle post-run) | **absent** (TTL expiry expected; not proof of miss-during-run) |
| Global Redis (post-soak counters) | `keyspace_hits=3936356`, `keyspace_misses=2905213` (whole DB 2 lifetime — **not** list-only hit rate) |
| L1/L2 singleflight | `TwoLevelPlatformCache.loadMerged` has per-key inflight merge (**process-local**); dual portal × TTL 30s can still dual-load on expiry |

### Hikari / JDBC (Prometheus range during 5m)

| Fact | Value |
|---|---|
| portal `hikaricp_connections_max` | **40** (matches #142 yml) |
| During load | `active` pegged **40**; `pending` up to **~148–159** |
| `hikaricp_connections_timeout_total` rate | **~4–7 /s per portal** during peak |
| portal logs | thousands of Hikari **connection not available** → maps to HTTP 500 |

### GC / Tomcat

| Fact | Value |
|---|---|
| GC during window | elevated **Copy** + **MarkSweepCompact** pause rates on both portals (heap near cgroup limit) |
| Tomcat worker queue metrics | **not exposed** in Prometheus (only `tomcat_sessions_*`); cannot claim queue depth — only infer from VU saturation + 502/timeout |

### DB still pegged?

**Yes** under list load: MySQL container CPU ~100% of its 1-CPU limit and mem at 1 GiB cap throughout mid/late 5m. Post-idle Threads_running low — saturation is **load-correlated**, not a stuck global lock leftover (`Innodb_row_lock_waits` was 0 at idle probe).

## Code reading → preferred next cut (analysis; no code in this PR)

Even when `TASK_PUBLISHED_INDEX` / L1 hits, `TaskPortalAppService.fromCatalog` still does:

```text
for each CachedPublishedCatalog.Entry (~1044):
  JsonUtil.fromJson(entry.snapshotJson(), SnapshotContent.class)
```

So a **cache hit still re-parses ~1000 snapshot JSON strings per list request**, then runs visibility/sort/page in-heap. That matches:

- portal heap → **1.5 GiB ceiling**
- GC pressure
- CPU burn before/while Hikari waits
- why QPS only doubled (~54 → ~116) and P95/errors got **worse** under the same 500 arrival-rate

**Preferred next cut (architect):**

1. **Stop per-request re-parse**: keep parsed `SnapshotContent` (or prebuilt card projections) in L1 alongside / instead of raw JSON strings; Redis may still store JSON once.
2. **Stampede / TTL**: soft-TTL or longer published-index TTL + keep singleflight; avoid dual-portal simultaneous `listPublished`+full snapshot hydrate every 30s under load.
3. **Only after list PASS**: consider Tomcat thread sizing / Redis Sa-Token chatter — secondary to (1).
4. **Do not** relax k6 / DEC-006 thresholds; **do not** tick capacity checkbox; **do not** start advance until list gate PASSes (or architect explicitly overrides).

## Explicit non-claims

1. **Not** capacity checklist PASS / checkbox tick.
2. **Not** DEC-006 reopen / k6 threshold change.
3. **Not** advance-callback / complete / risk-on work this slice.
4. **Not** RC / R6 通过.
5. Host logs remain under `/workspace/r6-capacity-logs/` (box-local); not committed.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: retest evidence linked; **checkbox remains unchecked**. R6 stays **进行中** / **非 RC**. Architect: fail-stop on list; next cut per analysis above.
