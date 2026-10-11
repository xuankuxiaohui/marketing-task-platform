# R6 capacity admin-list p1 (性能7 / P1) — after #151 — **PASS** — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `6313377` (Merge #151; ad formal 5m PASS) |
| Branch | `fix/r6-capacity-admin-list-p1-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~22:44–23:16 CST |
| Prior evidence | [#151 / ad](2026-10-11-r6-capacity-ad.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (性能7: admin typical lists at p1 data scale; P95 ≤800 ms for admin-instances / admin-definitions / admin-grants; http_fail==0; business code===0); DEC **not** reopened |
| Capacity checklist | **stays unchecked** — admin-list p1 formal PASS; checkbox + remaining-gap assessment left to architect |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Architect (#151): after ad formal PASS → **admin-list p1** (fail-stop; do **not** change threshold values) on isolated Compose `mkt`; capacity checkbox empty; not RC. After PASS → open capacity勾评估 noting remaining gaps (Outbox etc.); after FAIL → stay on admin-list p1.

## Environment

- Compose `mkt` already UP (portal×2 + admin + nginx + mysql + redis + prometheus). Verified nginx healthy; `GET` admin instances/definitions/points transactions → `code===0` with live p1 totals.
- Host: ~15 GiB RAM / no swap; MySQL compose limit **1 GiB** (observed ~780–835 MiB during seed/run); disk ~81 GiB free — **no** volume wipe.
- Seed: p1 data scale already present in `mkt` MySQL from prior bulk (users≈1.01M / instances≈515k / events≈5.79M). Official `python3 perf/seed/seed.py --scale p1` re-run (with brace fix) completed list tasks + ~1020/2000 login users then **failed** `captcha missing in redis for portal` (SEED_EXIT=1). Admin session refreshed; `state.json` scale=`p1` with live capacity counts. **admin-list.js does not require portal token users** — only admin cookie/CSRF.

### Seed note / brace fix

`perf/seed/seed.py` `bulk_events` CAST JSON literal must use **doubled braces** inside the f-string (`[{{"code":...}}]`) so Python does not treat `"props":{"route":"/home"}` as a format specifier (`ValueError: Invalid format specifier '"/home"'`). Required for p1 event bulk; included in this PR.

Live capacity (post-refresh, Asia/Shanghai):

| Metric | Count |
|---|---|
| sys_portal_user (deleted=0) | ≈1,005,960 |
| task_instance | 515,212 |
| evt_event_log | ≈5,788,862 |
| task_definition | 1,042 |
| pnt_transaction | 14,762 |

## Formal 5m admin-list (bot box Compose `mkt`)

```bash
# master tip 6313377; Compose mkt UP; state.json scale=p1 + admin auth
# optional warm (DURATION=30s) then formal 5m — same script, no threshold edits
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -w /scripts \
  -e BASE_URL=http://nginx -e DURATION=30s -e STATE_FILE=/scripts/state.json \
  grafana/k6:0.54.0 run \
  --summary-export=/scripts/reports/2026-10-11/admin-list-p1-warmup-30s.summary.json \
  /scripts/admin-list.js
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -w /scripts \
  -e BASE_URL=http://nginx -e DURATION=5m -e STATE_FILE=/scripts/state.json \
  grafana/k6:0.54.0 run \
  --summary-export=/scripts/reports/2026-10-11/admin-list-p1-formal-5m.summary.json \
  /scripts/admin-list.js
```

### Outcomes vs DEC-006 性能7

| Metric | DEC-006 / admin-list.js gate | Warm 30s | Formal 5m | Verdict |
|---|---|---|---|---|
| admin-instances P95 | ≤800 ms | ≈691.76 ms | **≈699.82 ms** | **PASS** |
| admin-definitions P95 | ≤800 ms | ≈100.18 ms | **≈99.64 ms** | **PASS** |
| admin-grants P95 | ≤800 ms | ≈202.04 ms | **≈200.82 ms** | **PASS** |
| checks | business http200 + code===0 | 100% (2652/2652) | **100%** (26668/26668) | **PASS** |
| http_req_failed | rate==0 | 0.00% (0/1326) | **0.00%** (0/13334) | **PASS** |
| http_reqs | (informational; constant-vus 10) | ≈43.83/s | **≈44.40/s** | — |
| Thresholds crossed | — | none | none | **overall PASS** |

Threshold **values** untouched (`p(95)<=800`, `http_req_failed rate==0`).

In-repo tiny artifact: `perf/reports/2026-10-11/admin-list-p1.json`. Large `*.summary.json` + host logs under `/workspace/r6-capacity-logs/admin-list-p1-*.log` / `seed-admin-list-p1-2026-10-11.log` remain **box-local** (gitignored).

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides; list+advance+complete+risk+ad+admin-list-p1 formal PASS still leave checkbox empty pending 勾评估 + gaps).
2. **Not** DEC-006 reopen / k6 threshold **value** change.
3. **Not** full `run-p0.sh` / `run-full.sh` / Outbox backlog recovery / production CA.
4. **Not** RC / R6 通过.
5. **Not** a claim that official seed completed 2000 fresh login users this run (captcha miss at ~1020; p1 **archive** scale verified live in MySQL).
6. Host logs under `/workspace/r6-capacity-logs/` (box-local).

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this formal 5m admin-list **p1 PASS** linked; **checkbox remains unchecked** pending architect 勾评估 (remaining: Outbox recovery numbers, etc.). R6 stays **进行中** / **非 RC**.

## Suggested next cut (architect)

After CI green + merge: open **capacity勾评估** noting remaining gaps (Outbox backlog recovery / run-full residual / any other DEC-006 leftovers). Do **not** auto-tick checkbox. Still **非 RC**.
