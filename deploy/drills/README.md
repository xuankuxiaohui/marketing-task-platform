# Deploy drills / R6 evidence

Isolation drills and first-release evidence linked from [R31-go-live-checklist.md](../R31-go-live-checklist.md).

| Date | Topic | Report |
|---|---|---|
| 2026-10-11 | R6 first slice: empty-DB Flyway + dual-app assembly（已开 / 进行中；非 RC） | [2026-10-11-r6-empty-flyway-dual-app.md](2026-10-11-r6-empty-flyway-dual-app.md) |
| 2026-10-11 | R6 journey slice: deploy-smoke 业务流（登录/领取/完成/领奖；进行中；非 RC） | [2026-10-11-r6-full-journey-deploy-smoke.md](2026-10-11-r6-full-journey-deploy-smoke.md) |
| 2026-10-11 | R6 dual-instance fault Continuity（单节点退出仍可服务；进行中；非 RC） | [2026-10-11-r6-dual-instance-fault-continuity.md](2026-10-11-r6-dual-instance-fault-continuity.md) |
| 2026-10-11 | R6 capacity DEC-006 seed+k6 P0（FAIL overall；track/admin-list PASS；进行中；非 RC；勾未勾） | [2026-10-11-r6-capacity-dec006.md](2026-10-11-r6-capacity-dec006.md) |
| 2026-10-11 | R6 restore/deploy：隔离 PITR **PASS** + 首次发布失败恢复策略（docs-only；进行中；非 RC） | [2026-10-11-r6-deploy-failure-recovery.md](2026-10-11-r6-deploy-failure-recovery.md)（PITR 见 [../backup/drills/2026-10-11-r6-isolated-restore.md](../backup/drills/2026-10-11-r6-isolated-restore.md)） |
| 2026-10-11 | R6 browser/static SPA：真实 dist + Playwright 登录/刷新/领取/完成/领奖 **PASS**（进行中；非 RC；容量仍 FAIL） | [2026-10-11-r6-browser-static-spa.md](2026-10-11-r6-browser-static-spa.md) |

Related F16 evidence lives under `deploy/backup/drills/`, `deploy/nginx/drills/`, and `scripts/drills/`.
