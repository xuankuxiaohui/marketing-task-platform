# R6 capacity 勾评估（DEC-006 清单行）— after #178 merge + admin-list confirm — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master) | `5695191` (Merge #178 admin-list instances COUNT TTL3s singleflight) |
| Confirm tip under test | same `5695191` on throwaway `mkt` (admin rebuilt post-merge) |
| Date (Asia/Shanghai) | 2026-10-12 12:50–12:58 CST (merge→confirm) |
| DEC-006 thresholds | **unchanged** — 禁降 Δ gate; 禁抬 1536M |
| Capacity checklist | **both boxes stay unchecked** in this PR (default; do **not** tick unless architect says) |
| RC / R6 | **非 RC** / **非 R6 通过** — R6 stays 进行中 |

## Purpose

Architect (~12:34 CST): after #178 tip `24e799a` approved and CI all green (incl. server) → **merge**; then same-mkt **定向 admin-list 确认**; then open **勾评估** with R31 capacity **both boxes default still unchecked** / 勾空. 非 RC.

## Fresh evidence this slice

| Item | Result | Cite |
|---|---|---|
| #178 CI | all green (e2e / server / openapi-backend / web / deploy-smoke) | tip `24e799a` |
| #178 merge | `5695191` | this master tip |
| Post-merge admin-list formal 5m | **PASS** — instances P95 **116.62 ms**; definitions 92.55; grants 283.51; checks 100%; http_fail 0% | [confirm doc](../../docs/capacity/2026-10-12-admin-list-confirm-after-178.md); logs `/workspace/r6-capacity-logs/admin-list-confirm-after-178/` |
| Prior run-full-after-#175 | PASS-ALL (Δ≈7.70 ms); admin-instances then ≈795.78 ms near ceiling | [run-full-after-175](../../docs/capacity/2026-10-12-run-full-after-175.md) |
| Pre-merge relief formal | PASS — instances P95 ≈118.07 ms | [relief](../../docs/capacity/2026-10-12-admin-list-instances-relief.md) |

**Also closed since early 勾评估 (#153 era):** Outbox backlog/recovery; cold/hot 分列; run-full residual (PASS-ALL after #175).

## Remaining gaps / cautions (why default 勾空)

| Item | Note |
|---|---|
| Second checklist box 「容量场景与正常/异常业务回归」 | Architect: historically 另议 — **do not tick** here |
| Checklist tick ownership | Suite green + relief confirmed, but **tick is architect-owned** |
| Optional run-full-noseed after #178 | Architect may order later; **not** claimed here |
| Prometheus / prod CA / scheduled backup | Separate R31 rows — out of scope |

## Recommendation (default)

**Leave unchecked:**

1. `- [ ] 性能报告…满足 DEC-006…` — **do not tick** unless architect explicitly says.
2. `- [ ] 容量场景与正常/异常业务回归…` — **do not tick** (另议).

Still **非 RC** / **非 R6 通过**. No threshold value changes. No 1536M raise. 禁降 Δ.

## Explicit non-claims

1. **Not** a capacity checklist tick (either box) unless architect follows up.
2. **Not** DEC-006 reopen / k6 threshold **value** change.
3. **Not** RC / R6 通过.
4. **Not** a scale reseed / wipe / migration / product knife in this docs PR.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 — both capacity-related boxes **remain empty**; this eval linked. R6 stays **已开 / 进行中** / **非 RC**.
