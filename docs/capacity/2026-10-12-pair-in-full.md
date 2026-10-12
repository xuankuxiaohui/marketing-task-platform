**Product disposition (Architect ~09:30):** 产品不合 — evidence-only merge. Oneshot+V9 **stripped**; must not enter master via this PR. Next: singleflight + dual-IN + V9 + TTL3s.

# Capacity: tuple IN 单次打满 + V9 (2026-10-12)

**Status:** formal list **FAIL** (勾空; 非 RC). 先测后合 — **do not merge** until architect 批合.  
**Caps:** DEC-006 list 500 QPS, P95≤300ms, http_fail==0, checks>0.99, dropped&lt;0.05.  
**Locks:** Tomcat120, Redisson≤32/1s/1, 1536M **not raised**; no cycle_key-only; no pair128 / dual-IN×128; no FORCE; no ≤64 serial chunking / PAIR_IN_BATCH.

## Change
- `listByUserTaskCycles` one-shot `(task_id, cycle_key) IN (...)` — **no** PAIR_IN_BATCH=64 serial.
- **V9** drop `idx_user_cycle` (EXPLAIN-justified; live count=0).
- #170 pair≤64 FAIL evidence-only (product stripped).

## EXPLAIN (throwaway `mkt`, post-V9)
- published pairs ≈ **1042**
- tuple IN 64: `uk_user_task_cycle` range, **rows=64**, Using index
- tuple IN one-shot 1042: `uk_user_task_cycle` range, **rows=1042**, Using index
- `idx_user_cycle` count = **0**

## Warm (curl sample, pre-formal)
- settle done; warm-curl: **ok=120 fail=0** avg≈0.48s p95≈0.56s (cold-ish sample; not DEC-006 gate)

## Formal list 5m (DEC-006 recipe: `perf/list.js` via nginx, 500 QPS, 2026-10-12 ~09:08–09:14 CST)

| metric | value | gate | result |
|--------|-------|------|--------|
| http_req_duration p95 `{name:list,scenario:list}` | **≈2080.6 ms** | ≤300 ms | **FAIL** |
| dropped_iterations `{scenario:list}` | **≈191.73 /s** (count 64495) | rate&lt;0.05 | **FAIL** |
| http_req_failed `{scenario:list}` | **0** | ==0 | PASS |
| checks `{scenario:list}` | **100%** (171010/171010) | &gt;0.99 | PASS |

- k6 exit: thresholds crossed (`dropped_iterations{scenario:list}`, `http_req_duration{name:list,scenario:list}`)
- During list: MySQL ≈100% CPU; portals near 1.5GiB heap cap
- Digest (post-list): `task_instance` list SELECT count≈91597, **avg≈155.7 ms**

### vs prior knives (same mkt stack)
| knife | P95 | dropped/s | note |
|-------|-----|-----------|------|
| #170 pair IN ≤64 serial | ≈8.09 s | ≈376 | 产品不合; evidence-only |
| **#171 one-shot + V9** | **≈2.08 s** | **≈192** | better than ≤64, still **FAIL** gates |

## Verdict
**FAIL** — do **not** merge without architect批. run-full / mtr_* / seed **not** started.

## Evidence
Logs: `/workspace/r6-capacity-logs/pair-in-full/` (`explain.txt`, `warm-curl.log`, `list-formal-5m.log`, `list-formal-result.txt`, `list-pair-in-full.summary.json`, docker-stats-*, digest-*).
