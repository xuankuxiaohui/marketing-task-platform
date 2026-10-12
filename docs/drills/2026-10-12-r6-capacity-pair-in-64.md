# R6 capacity — pair IN ≤64 + V9 — list FAIL — 2026-10-12

| Field | Value |
|---|---|
| Master base | `ad5f999` (Merge #169 FORCE evidence-only) |
| Branch / PR | `feat/capacity-pair-in-64-2026-10-12` / [#170](https://github.com/xuankuxiaohui/marketing-task-platform/pull/170) tip `b5d0105` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~08:34–09:00 CST |
| Isolation | throwaway Compose `mkt` (no wipe; same p1 seed) |
| Capacity checklist | **勾空** |
| R6 / RC | **进行中 / 非 RC** |

## Sequence
1. #169 CI green → merge tip `fc60e9a` → master `ad5f999`.
2. Rebase #170 onto master → tip `b5d0105` (tuple IN≤64 + V9).
3. EXPLAIN post-V9 → uk rows=64.
4. Settle calm×3 → curl warm 40 users ×3 → k6 list.js 5m.
5. **FAIL** — stop; ask architect before merge.

## Per-scene

| Scene | Result | Notes |
|---|---|---|
| EXPLAIN | uk after V9 | idx_user_cycle dropped |
| settle + curl warm | ok=120 fail=0 | docker-run curl overhead ~0.5s |
| **list formal 5m** | **FAIL** | P95~8.09s; dropped~376/s; checks 100%; http_fail 0 |
| run-full | **not run** | fail-stop |

## Gates (unchanged)
P95≤300ms · http_fail==0 · checks>0.99 · dropped&lt;0.05 · 500 QPS · 1536M not raised
