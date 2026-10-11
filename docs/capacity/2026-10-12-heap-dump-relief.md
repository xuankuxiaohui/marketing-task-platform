# Capacity evidence: list heap-dump定点减负 (2026-10-12)

**Result: FAIL** (Capacity勾空; 非 RC). Architect #164 架构通过已合入; 本刀 dump 定点减负。

## #164 merge
- Tip requested: `70337c99d1bf71ab5e1ca8976307f0cc776b1a58`
- Master merge SHA: `8af22225b69db3c868ea9b273f32df380f23eda2`

## Dump findings
See `/workspace/r6-capacity-logs/heap-dump-relief/DUMP-FINDINGS.txt`.

Near-ceiling histogram: `[B` ~263MB, `[[B`/ByteArrayRow ~500k, LocalDate/Time ~1.37M,
`TaskInstanceEntity` ~343k. Cause: `listByUser(userId=1)` loading ~500k seed rows under list.

## Targeted code (dump-cited)
- Stop unbounded `listByUser` on list current-instance path.
- `TaskInstanceMapper.selectByUserTaskIdsAndCycleKeys` + store +
  `TaskPortalAppService` Java pair post-filter (after tuple-IN latency FAIL).
- Caps unchanged; **skip-renew not landed**; **1536M not raised**.

## Formal list 5m (after second fix)
| gate | value | pass |
|---|---|---|
| P95 list | 277.54 ms | ✓ |
| http_fail | 0 | ✓ |
| checks | 100% | ✓ |
| dropped rate | 0.0955/s (32 drops) | ✗ (<0.05) |
| OOM / restarts | none | ✓ |
| peak mem | ~1.33 GiB / 1.5 GiB | headroom |

## 1536M raise?
**No — not on this dump evidence.** OOM was unbounded listByUser; bounded path peaks ~1.33/1.5 GiB.
Residual FAIL is dropped_iterations with MySQL CPU ~98%, not heap ceiling. Architect
raise-limit拍板 only if a later dump shows bounded-path exhaustion.

## Artifacts
`/workspace/r6-capacity-logs/heap-dump-relief/` (histograms, hprof refs, formal1/2 logs, stats).
