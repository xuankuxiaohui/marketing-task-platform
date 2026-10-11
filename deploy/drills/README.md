# Deploy drills / R6 evidence

Isolation drills and first-release evidence linked from [R31-go-live-checklist.md](../R31-go-live-checklist.md).

| Date | Topic | Report |
|---|---|---|
| 2026-10-11 | R6 first slice: empty-DB Flyway + dual-app assembly（已开 / 进行中；非 RC） | [2026-10-11-r6-empty-flyway-dual-app.md](2026-10-11-r6-empty-flyway-dual-app.md) |
| 2026-10-11 | R6 journey slice: deploy-smoke 业务流（登录/领取/完成/领奖；进行中；非 RC） | [2026-10-11-r6-full-journey-deploy-smoke.md](2026-10-11-r6-full-journey-deploy-smoke.md) |
| 2026-10-11 | R6 dual-instance fault Continuity（单节点退出仍可服务；进行中；非 RC） | [2026-10-11-r6-dual-instance-fault-continuity.md](2026-10-11-r6-dual-instance-fault-continuity.md) |
| 2026-10-11 | R6 capacity DEC-006 seed+k6 P0（FAIL overall；track/admin-list PASS；进行中；非 RC；勾未勾） | [2026-10-11-r6-capacity-dec006.md](2026-10-11-r6-capacity-dec006.md) |
| 2026-10-11 | R6 restore/deploy：隔离 PITR **PASS** + 首次发布失败恢复策略（docs-only；进行中；非 RC） | [2026-10-11-r6-deploy-failure-recovery.md](2026-10-11-r6-deploy-failure-recovery.md)（PITR 见 [../backup/drills/2026-10-11-r6-isolated-restore.md](../backup/drills/2026-10-11-r6-isolated-restore.md)） |
| 2026-10-11 | R6 capacity list-path：published-index 缓存 + Hikari 池（补强；**未**重跑 k6；容量仍 FAIL；非 RC） | [2026-10-11-r6-capacity-list-path.md](2026-10-11-r6-capacity-list-path.md) |
| 2026-10-11 | R6 capacity list retest after #142（**FAIL**；~116/s vs 500；http_fail~61%；fail-stop；勾未勾；非 RC） | [2026-10-11-r6-capacity-list-retest.md](2026-10-11-r6-capacity-list-retest.md) |
| 2026-10-11 | R6 capacity list hot-path（#144；onUserList / listByUser / parse reuse；勾仍空；非 RC） | [2026-10-11-r6-capacity-list-hotpath.md](2026-10-11-r6-capacity-list-hotpath.md) |
| 2026-10-11 | R6 capacity list formal 5m after #144（~499/s P95~4ms http_fail0；**dropped~0.58/s FAIL**；勾未勾；非 RC；未进 advance） | [2026-10-11-r6-capacity-list-post144.md](2026-10-11-r6-capacity-list-post144.md) |
| 2026-10-11 | R6 capacity list 5m after #144（**FAIL** dropped≈0.58/s；P95≈4ms / ~499/s / http_fail 0%；fail-stop；勾未勾；非 RC） | [2026-10-11-r6-capacity-list-5m-after-144.md](2026-10-11-r6-capacity-list-5m-after-144.md) |
| 2026-10-11 | R6 capacity list residual dropped after #146（**PASS** list 5m；dropped 0；勾仍空；非 RC；未进 advance） | [2026-10-11-r6-capacity-list-dropped-residual.md](2026-10-11-r6-capacity-list-dropped-residual.md) |
| 2026-10-11 | R6 capacity advance residual dropped after #147（**PASS** advance 5m；dropped 0；勾仍空；非 RC；未进 complete） | [2026-10-11-r6-capacity-advance-dropped.md](2026-10-11-r6-capacity-advance-dropped.md) |
| 2026-10-11 | R6 capacity complete after #148（**PASS** formal 5m grant P95≈498ms；勾仍空；非 RC；risk-on 另开） | [2026-10-11-r6-capacity-complete.md](2026-10-11-r6-capacity-complete.md) |
| 2026-10-11 | R6 capacity risk-on/off after #149（**PASS** formal 5m ΔP95≈−4ms；双变体业务成功；勾仍空；非 RC） | [2026-10-11-r6-capacity-risk-on.md](2026-10-11-r6-capacity-risk-on.md) |
| 2026-10-11 | R6 capacity ad after #150（**PASS** formal 5m；P95≈1.84ms；勾仍空；非 RC；未 tick 容量） | [2026-10-11-r6-capacity-ad.md](2026-10-11-r6-capacity-ad.md) |
| 2026-10-11 | R6 capacity admin-list **p1** after #151（**PASS** formal 5m；instances P95≈700ms≤800；勾仍空；非 RC；未 tick 容量） | [2026-10-11-r6-capacity-admin-list-p1.md](2026-10-11-r6-capacity-admin-list-p1.md) |
| 2026-10-11 | R6 capacity **勾评估** after #152（汇总已绿 formal；缺口：Outbox/冷热/run-full/p1 captcha；**两勾仍空**；非 RC） | [2026-10-11-r6-capacity-checkbox-eval.md](2026-10-11-r6-capacity-checkbox-eval.md) |
| 2026-10-11 | R6 capacity **Outbox** backlog/recovery numbers after #153（稳态 PENDING + 最老延迟 + 10k 排空；**勾仍空**；不重开 DEC-002；非 RC） | [2026-10-11-r6-capacity-outbox.md](2026-10-11-r6-capacity-outbox.md) |
| 2026-10-11 | R6 browser/static SPA：真实 dist + Playwright 登录/刷新/领取/完成/领奖 **PASS**（进行中；非 RC；容量仍 FAIL） | [2026-10-11-r6-browser-static-spa.md](2026-10-11-r6-browser-static-spa.md) |

- [2026-10-11-r6-capacity-list-hotpath.md](2026-10-11-r6-capacity-list-hotpath.md) — post-#142 MySQL/GC list hot-path (非 RC)

Related F16 evidence lives under `deploy/backup/drills/`, `deploy/nginx/drills/`, and `scripts/drills/`.
