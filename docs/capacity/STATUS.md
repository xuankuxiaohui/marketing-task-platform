# Capacity status (living)

**Last update:** 2026-10-13 (Top4 D5 archive triage; posture unchanged after #196 → master `33a1155`)

| Posture | Value |
|---|---|
| R31 capacity boxes | **勾空** (both unchecked) |
| Release candidate | **非 RC** |
| DEC-006 Δ | **收口** — cold risk-on FAIL on http_fail≈0.27% with exhausted=0 (stripe OK) |
| Default knives | **STOP** — no further capacity expand / gate relax / reseed / CntWindow |
| Residual http_fail | ≈**0.27%** parked as **P2 selective backlog** (not a capacity-pool issue) |
| Evidence layout | **2026-10-13 Top4** — dated FAIL/PASS pages moved under [`docs/archive/`](../archive/); this page + [`ARCHIVE-INDEX.md`](./ARCHIVE-INDEX.md) stay living |

## Latest evidence

- Stripe expand →5000 + cold Δ FAIL: PR **#196** → merge `33a1155`
- Detail: [`2026-10-12-stripe-expand-4500-delta-after-195.md`](../archive/capacity/2026-10-12-stripe-expand-4500-delta-after-195.md)
- Replan hand-off: [`../replan/2026-10-13-project-health-checklist-draft.md`](../replan/2026-10-13-project-health-checklist-draft.md)
- Owner backlog: [`../replan/2026-10-13-replan-backlog.md`](../replan/2026-10-13-replan-backlog.md)

## Historical drills

See [`ARCHIVE-INDEX.md`](./ARCHIVE-INDEX.md) for archived capacity/drill evidence under `docs/archive/capacity/` and `docs/archive/drills/`. Those docs are **archive**; this page is the live status.

## R31 binding

- `[ ]` 性能报告…满足 DEC-006… — stays empty (**另议**; architect tick only)
- `[ ]` 容量场景与正常/异常业务回归… — stays empty (**另议**)
- Checklist: [`../../deploy/R31-go-live-checklist.md`](../../deploy/R31-go-live-checklist.md)

## Non-goals

- Lowering Δ / raising dailyLimit / relaxing http_fail·exhausted·checks
- Big-bang rewrite / Redisson加码 / 抬 1536M / revert #178
- Inventing more capacity knives after Δ收口
