# R6 capacity admin-list instances 减压 — after run-full-after-#175 — **PASS** — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (base) | `41801bf` (Merge #177) / product change on relief branch |
| Branch | `feat/capacity-admin-list-instances-relief-2026-10-12` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~12:21–12:33 CST |
| Isolation | Compose project `mkt` (throwaway; **not** prod) |
| Shared/prod DB touched | **No** |
| DEC-006 thresholds | **unchanged** (性能7 P95≤800; http_fail==0); **禁降 Δ**; **禁抬 1536M** |
| Caps | Tomcat120; Redisson≤32; memcg **1536M** |
| Capacity checklist | **stays unchecked** (勾空; 非 RC) |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Architect (~12:21 CST): after #177 approved — confirm R31 capacity boxes unchecked; merge #177 when CI green; next knife **admin-list instances 减压** (prior P95≈796 ms near 800). Same `mkt`, 不重seed. Implement → EXPLAIN/warm → formal admin-list → publish → **ask architect 批合** (先测后合; do not auto-merge product).

## Knife

`TaskInstanceAppService.page`: Caffeine **TTL 3s** + singleflight on `countAdmin` (filter-key record). List query unchanged.

Evidence: [docs/archive/capacity/2026-10-12-admin-list-instances-relief.md](../capacity/2026-10-12-admin-list-instances-relief.md). Box logs `/workspace/r6-capacity-logs/admin-list-relief/`.

## Outcomes

| Metric | Before | After | Verdict |
|---|---|---|---|
| admin-instances P95 | 795.78 ms | **118.07 ms** | **PASS** |
| admin-definitions P95 | 98.6 ms | 92.59 ms | **PASS** |
| admin-grants P95 | 296.34 ms | 208.37 ms | **PASS** |
| checks / http_fail | 100% / 0% | 100% / 0% | **PASS** |

## Explicit non-claims

1. **Not** capacity checklist tick (either R31 box).
2. **Not** threshold / Δ / 1536M change.
3. **Not** run-full / RC.
4. **Not** merge without architect 批合.

## Post-merge confirm
Merged as #178 (`5695191`). Same-mkt formal admin-list confirm **PASS** (instances P95≈116.62 ms). See [confirm](../capacity/2026-10-12-admin-list-confirm-after-178.md) and logs `/workspace/r6-capacity-logs/admin-list-confirm-after-178/`. **勾仍空; 非 RC.**
