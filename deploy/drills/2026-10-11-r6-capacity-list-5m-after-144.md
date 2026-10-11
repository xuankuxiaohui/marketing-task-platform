# R6 capacity list 5m retest after #144 — FAIL (dropped only) — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `80482e7` (Merge #144; onUserList + listByUser + parse reuse + INDEX TTL 5m) |
| Branch | `docs/r6-capacity-list-5m-after-144-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~17:55–18:03 CST |
| Prior evidence | [#143 retest FAIL](2026-10-11-r6-capacity-list-retest.md); [#144 hot-path](2026-10-11-r6-capacity-list-hotpath.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged**; DEC **not** reopened |
| Capacity checklist | **stays unchecked** (overall list gate **FAIL**) |
| R6 / RC | **进行中 / 非 RC** |
| Advance callback | **not started** (architect: 过了再 advance; list still FAIL → fail-stop) |

## Goal

After #144 merge: rebuild portal on Compose `mkt`, run **formal 5m list** (same gates as DEC-006 / `perf/list.js`), document honest PASS/FAIL vs #143, then **fail-stop** on list (do **not** start advance). Evidence PR for architect review first.

## Commands run

```bash
# HEAD == origin/master == 80482e776675f0054ea3659a62ee52f321cf190e
ROOT=/workspace/marketing-task-platform
COMPOSE=(docker compose --env-file deploy/.env -f deploy/docker-compose.yml -p mkt)
cd "$ROOT/server" && mvn -DskipTests -pl portal-app -am package
# log: /workspace/r6-capacity-logs/mvn-package-post144.log

"${COMPOSE[@]}" build portal-app-1 portal-app-2
# log: /workspace/r6-capacity-logs/rebuild-portal-post144.log
# no down -v; cleaned stale recreate conflict then:
"${COMPOSE[@]}" up -d portal-app-1 portal-app-2
# both healthy ~30s; seed reused perf/state.json (p0; 200 users; list probe code=0 total≈967)

K6_IMAGE=grafana/k6:0.54.0
NETWORK=mkt_default
# smoke 30s + formal 5m via docker run --network mkt_default … list.js
# logs: list-post144-smoke-30s.log / list-post144-5m.log
# summary: perf/reports/2026-10-11/list-post144-5m.summary.json (gitignored)
```

**Not run**: advance / complete / risk / p1 / `run-full.sh` / threshold edits / DEC-006 reopen.

## Outcomes vs DEC-006 list gate (and vs #143)

| Metric | DEC-006 gate | #143 after #142 (5m) | **#144 formal 5m** | Verdict |
|---|---|---|---|---|
| Effective HTTP/s | 500 QPS | ~116.1 | **~499.4** | ≈ gate → **PASS** |
| list P95 | ≤300 ms | ~30 s | **~4.13 ms** | ≪ gate → **PASS** |
| dropped_iterations | rate &lt; 0.05 /s | ~338/s (111577) | **~0.580/s (174)** | still &gt; 0.05 → **FAIL** |
| checks | &gt;99% | ~38.7% | **100%** (299654/299654) | **PASS** |
| http_req_failed | ==0 | ~61.3% | **0.00%** (0/149827) | **PASS** |
| Thresholds crossed | — | checks, dropped, list P95, http_req_failed | **dropped_iterations only** | **overall FAIL** |

### Smoke 30s (same rebuild; not a gate claim)

| Metric | Smoke 30s |
|---|---|
| http_reqs | ~499.9/s |
| list P95 | ~44.0 ms |
| checks | 100% |
| http_req_failed | 0% |
| dropped_iterations | **0** |
| Thresholds | **all green** (smoke only) |

Smoke cleared every list gate; formal 5m then crossed **only** `dropped_iterations`.

### Suite log markers (5m)

```text
✓ list http 200 / list code 0 → 100%
checks 100%; dropped_iterations 174 (~0.580/s)
http_req_failed 0.00%; http_reqs ~499.41/s
{ name:list } p(95)≈4.13ms (max≈1.87s)
thresholds on metrics 'dropped_iterations' have been crossed
```

## Reading (measured; no threshold change)

Huge improvement vs #143: throughput ~116→~499/s, P95 ~30s→~4ms, http_fail ~61%→0%, checks 38%→100%. Portal heap during 5m stayed ~0.96–1.06 / 1.5 GiB; MySQL CPU ~24–26% (no longer pegged). Residual FAIL is **scheduler drops**: 174 dropped arrivals over 5m (~0.58/s) vs gate `rate<0.05`. VU max climbed to 243 near end around a ~1.87s outlier; drops cluster with VU scale-up, not mass 5xx.

**Preferred next cut (architect; analysis only):** profile remaining drop spikes / Tomcat maxThreads / outlier tail without relaxing DEC-006; **or** explicitly decide whether `dropped_iterations rate<0.05` (absolute /s) remains the intended gate at 500 arrival/s. **Do not** tick capacity; **do not** start advance until list overall PASSes (or architect overrides).

## Resource facts (during 5m samples)

From `/workspace/r6-capacity-logs/list-post144-resources-during-5m.txt` (CST):

- portals ~36–41% CPU, mem ~960 MiB → ~1.06 GiB / 1.5 GiB
- MySQL ~24–26% of 1 CPU, mem ~819 MiB / 1 GiB
- Redis ~10–15% CPU, ~13 MiB / 256 MiB
- After 5m: portals idle ~0.4% CPU, ~1.06 GiB; MySQL ~3.5%

## Explicit non-claims

1. **Not** capacity checklist PASS / checkbox tick (list overall **FAIL** on dropped).
2. **Not** DEC-006 reopen / k6 threshold change.
3. **Not** advance-callback / complete / risk-on work this slice.
4. **Not** RC / R6 通过 / full P0 suite PASS.
5. Host logs remain under `/workspace/r6-capacity-logs/` (box-local); not committed.
6. Smoke 30s all-green is **not** a formal gate claim.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this formal 5m list evidence linked; **checkbox remains unchecked** (dropped_iterations still crosses). R6 stays **进行中** / **非 RC**. Architect: fail-stop on list; next is still list (or explicit override) before advance.
