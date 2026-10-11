# R6 capacity list formal 5m after #144 — near-PASS / FAIL dropped — 2026-10-11

| Field | Value |
|---|---|
| Repo tip | `80482e7` (Merge #144; list hot-path) |
| Branch (evidence) | `docs/r6-capacity-list-post144-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~17:55–18:03 CST |
| Prior | [#142 retest FAIL](2026-10-11-r6-capacity-list-retest.md) ~116/s http_fail~61%; [#144 hot-path](2026-10-11-r6-capacity-list-hotpath.md) smoke ~493/s |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged**; DEC **not** reopened |
| Capacity checklist | **stays unchecked** (dropped_iterations rate still above gate) |
| R6 / RC | **进行中 / 非 RC** |
| Advance callback | **not started** (list not clearly PASS all gates) |

## Goal

Architect: after #144 merge → rebuild portal on isolated mkt → formal **5m list** (fail-stop). list PASS → advance; else continue list (Tomcat/GC), still no callback. 非 RC.

## Commands run

```bash
ROOT=/workspace/marketing-task-platform
COMPOSE=(docker compose --env-file deploy/.env -f deploy/docker-compose.yml -p mkt)
cd "$ROOT"   # HEAD 80482e7
# mvn -DskipTests -pl portal-app -am package
"${COMPOSE[@]}" build portal-app-1 portal-app-2
"${COMPOSE[@]}" up -d --force-recreate portal-app-1 portal-app-2
# wait healthy; no DB wipe; seed state reused (perf/state.json)

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
run_list 30s /workspace/r6-capacity-logs/list-post144-smoke-30s.log
run_list 5m  /workspace/r6-capacity-logs/list-post144-5m.log
```

**Not run**: advance / complete / risk / p1 / `run-full.sh` / threshold edits / DEC-006 reopen.

## Outcomes vs DEC-006 list gate

| Metric | DEC-006 gate | #142 retest 5m | **#144 formal 5m** | Verdict |
|---|---|---|---|---|
| Effective HTTP/s | 500 QPS | ~116.1 | **~499.4** | ≈ target (k6 did not flag throughput) |
| list P95 | ≤300 ms | ~30 s | **~4.13 ms** | **PASS** |
| checks | >99% | ~38.7% | **100%** | **PASS** |
| http_req_failed | ==0 | ~61.3% | **0.00%** (0/149827) | **PASS** |
| dropped_iterations | rate &lt; 0.05 | ~338/s | **174 (~0.58/s)** | still &gt; 0.05 → **FAIL** |
| Thresholds crossed | — | checks, dropped, duration, http_fail | **`dropped_iterations` only** | **FAIL overall** |

### Smoke 30s (cold after recreate; not a gate claim)

| Metric | Smoke 30s |
|---|---|
| http_reqs | ~360.1/s |
| list P95 | ~1.66 s |
| checks | 100% |
| http_req_failed | 0% |
| dropped_iterations | ~139.8/s |
| Thresholds | `dropped_iterations`, `http_req_duration{name:list}` |

Cold smoke then warmed into the 5m steady state above.

### Suite log markers (5m)

```text
✓ list http 200 / list code 0 → 100%
checks 100%; dropped_iterations 174 (~0.58/s)
http_req_failed 0.00%; http_reqs ~499.41/s
{ name:list } p(95)~4.13ms (max~1.87s)
thresholds on metrics 'dropped_iterations' have been crossed
```

### Resources (during 5m samples)

Portals ~36–41% CPU, heap ~0.96–1.06 / 1.5 GiB; MySQL ~23–26% CPU; Redis ~10–15% CPU. No wipe; mkt left UP.

## Explicit non-claims

1. **Not** capacity checklist PASS / checkbox tick / RC (dropped rate still fails gate).
2. **Not** DEC-006 reopen or threshold change.
3. **Not** advance-callback / complete / risk-on path work this run.
4. ~499.4 HTTP/s is reported honestly; overall still **FAIL** until dropped &lt; 0.05.

## Next (architect default)

1. Review this evidence.
2. Further list slice if needed (Tomcat maxThreads / GC / arrival-rate warm-up drops) — do **not** relax thresholds.
3. Only if architect accepts list as PASS (or a follow-up clears dropped) → open advance-callback saturation.
