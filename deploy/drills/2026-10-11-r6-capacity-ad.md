# R6 capacity ad (性能6 / P1) — after #150 — **PASS** — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `72710c7` (Merge #150; risk-on/off hygiene PASS) |
| Branch | `fix/r6-capacity-ad-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~21:48–21:54 CST |
| Prior evidence | [#150 / risk-on](2026-10-11-r6-capacity-risk-on.md) |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (性能6: 300 QPS; P95 ≤100 ms; http_fail==0; checks>0.99; dropped rate<0.05); DEC **not** reopened |
| Capacity checklist | **stays unchecked** — ad formal PASS; checkbox decision left to architect |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Architect (#150): after risk-on/off PASS → **formal 5m ad** (DEC-006 P1 / 性能6) on isolated Compose `mkt`; capacity checkbox empty; not RC. Remaining later: admin-list p1 / Outbox etc.

## Environment

- Compose `mkt` already UP from prior risk-on work (portal×2 + admin + nginx + mysql + redis). #150 was seed/risk-delta/docs — **no app rebuild** required; verified nginx `/healthz` ok, both portals readiness UP, `GET /api/common/ad/positions/home_banner` → `code===0`.
- Seed state present (`adCodes` = home_banner / home_popup / home_float / app_splash / home_image; users=1200; scale=p0). Re-seed not required for this read path.

## Formal 5m ad (bot box Compose `mkt`)

```bash
# master tip 72710c7; Compose mkt UP; state.json has adCodes
# optional warm (DURATION=30s) then formal 5m — same script, no threshold edits
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -w /scripts \
  -e BASE_URL=http://nginx -e DURATION=30s -e STATE_FILE=/scripts/state.json \
  grafana/k6:0.54.0 run \
  --summary-export=/scripts/reports/2026-10-11/ad-warmup-30s.summary.json \
  /scripts/ad.js
docker run --rm --network mkt_default -v "$PWD/perf:/scripts" -w /scripts \
  -e BASE_URL=http://nginx -e DURATION=5m -e STATE_FILE=/scripts/state.json \
  grafana/k6:0.54.0 run \
  --summary-export=/scripts/reports/2026-10-11/ad-formal-5m.summary.json \
  /scripts/ad.js
```

### Outcomes vs DEC-006 性能6

| Metric | DEC-006 / ad.js gate | Warm 30s | Formal 5m | Verdict |
|---|---|---|---|---|
| HTTP/s | 300 | ≈299.85 | **≈299.98** | **PASS** |
| ad P95 (`{name:ad}`) | ≤100 ms | ≈1.94 ms | **≈1.84 ms** | **PASS** |
| checks | rate>0.99 | 100% (18002/18002) | **100%** (180002/180002) | **PASS** |
| http_req_failed | rate==0 | 0.00% (0/9001) | **0.00%** (0/90001) | **PASS** |
| dropped_iterations | rate<0.05 | 0 | **0** | **PASS** |
| Thresholds crossed | — | none | none | **overall PASS** |

No residual dropped near-miss → **no** warm→formal script reshape / `preAllocatedVUs` bump required (existing `preAllocatedVUs: 60` sufficient). Threshold **values** untouched.

In-repo tiny artifact: `perf/reports/2026-10-11/ad.json`. Large `*.summary.json` + host logs under `/workspace/r6-capacity-logs/ad-*.log` remain **box-local** (gitignored).

## Explicit non-claims

1. **Not** capacity checklist tick (architect decides; list+advance+complete+risk+ad formal PASS still leave checkbox empty).
2. **Not** DEC-006 reopen / k6 threshold **value** change.
3. **Not** full `run-p0.sh` / `run-full.sh` / admin-list **p1** / Outbox backlog recovery re-run in this evidence file.
4. **Not** RC / R6 通过.
5. Host logs under `/workspace/r6-capacity-logs/` (box-local).

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 —「性能报告…满足 DEC-006」: this formal 5m ad **PASS** linked; **checkbox remains unchecked** pending architect. R6 stays **进行中** / **非 RC**. Remaining capacity work includes admin-list at p1 scale / Outbox etc.
