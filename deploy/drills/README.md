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
| 2026-10-12 | R6 capacity **冷热分列** after #154（list+ad cold\|hot；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-cold-hot.md](2026-10-12-r6-capacity-cold-hot.md) |
| 2026-10-12 | R6 capacity **run-full.sh residual** after #155（p1 captcha→2000 OK；list formal **FAIL** fail-stop；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-run-full.md](2026-10-12-r6-capacity-run-full.md) |
| 2026-10-12 | R6 capacity **沉降后 list 单场景复测** after #156（不叠 bulk；list formal **FAIL** fail-stop；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-list-settle.md](2026-10-12-r6-capacity-list-settle.md) |
| 2026-10-12 | R6 capacity **重建至 DEC-006 p1** after #157（down -v + p1 seed 2000；list formal **FAIL** fail-stop；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-rebuild-list.md](2026-10-12-r6-capacity-rebuild-list.md) |
| 2026-10-12 | R6 capacity **list-under-p1 内存补强** after #158（1536M 不抬；OOM cleared；list formal **FAIL**；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-list-under-p1-mem.md](2026-10-12-r6-capacity-list-under-p1-mem.md) |
| 2026-10-12 | R6 capacity **clear residual dropped** 实验 after #159（Tomcat160 卫生包 **FAIL**；#160 **证据-only**；产品仍 120；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-clear-dropped.md](2026-10-12-r6-capacity-clear-dropped.md) |
| 2026-10-12 | R6 capacity **threads=120 revert retest** after #160 strip（近PASS **未**恢复；CPU 刀未开；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-threads120-retest.md](2026-10-12-r6-capacity-threads120-retest.md) |
| 2026-10-12 | R6 capacity **Redis/session stabilize** 实验（Redisson 加码 **heap OOM**；#162 **证据-only**；产品回退；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-redis-session.md](2026-10-12-r6-capacity-redis-session.md) |
| 2026-10-12 | R6 capacity **list 堆压减负** skip-renew alone A/B（**FAIL**；heap≈1.48GiB Redis idle；**证据-only**；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-list-heap-relief.md](2026-10-12-r6-capacity-list-heap-relief.md) |
| 2026-10-12 | R6 capacity **MySQL list 查询/索引** after #165（cycle_key-only 初刀 formal **FAIL**；后 #166 回退；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-mysql-list-index.md](2026-10-12-r6-capacity-mysql-list-index.md) |
| 2026-10-12 | R6 capacity **dual-IN restore + V8 keep** (#166；list formal **PASS** P95≈174 dropped0；pair128 回退；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-dual-in-restore.md](2026-10-12-r6-capacity-dual-in-restore.md) |
| 2026-10-12 | R6 capacity **run-full after #166**（no re-seed；list formal **FAIL-STOP** P95≈804 dropped≈0.58/s；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-run-full-after-166.md](2026-10-12-r6-capacity-run-full-after-166.md) |
| 2026-10-11 | R6 browser/static SPA：真实 dist + Playwright 登录/刷新/领取/完成/领奖 **PASS**（进行中；非 RC；容量仍 FAIL） | [2026-10-11-r6-browser-static-spa.md](2026-10-11-r6-browser-static-spa.md) |

- [2026-10-11-r6-capacity-list-hotpath.md](2026-10-11-r6-capacity-list-hotpath.md) — post-#142 MySQL/GC list hot-path (非 RC)

| 2026-10-12 | R6 capacity **list settle + run-full after #180**（isolated list **PASS** dropped0；run-full **FAIL-STOP at complete**；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-list-settle-after-180.md](2026-10-12-r6-capacity-list-settle-after-180.md) |
| 2026-10-12 | R6 capacity **complete residual after #181**（fail-code `task.claim.daily-limit`；isolated complete risk-off **FAIL** checks≈93.3% http_fail≈6.7%；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-complete-residual-after-181.md](2026-10-12-r6-capacity-complete-residual-after-181.md) |
| 2026-10-12 | R6 capacity **claim-pool hygiene after #182**（daily-limit cleared；isolated complete **FAIL** grant P95≈510.51；checks100% http_fail0；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-claim-pool-hygiene-after-182.md](2026-10-12-r6-capacity-claim-pool-hygiene-after-182.md) |
| 2026-10-12 | R6 capacity **cool complete + run-full after #183**（cool→isolated complete **PASS** grant P95≈315.43；run-full **FAIL-STOP at list** dropped≈0.310/s；**勾仍空**；非 RC） | [2026-10-12-r6-capacity-cool-complete-run-full-after-183.md](2026-10-12-r6-capacity-cool-complete-run-full-after-183.md) |

Related F16 evidence lives under `deploy/backup/drills/`, `deploy/nginx/drills/`, and `scripts/drills/`.

- [2026-10-12 list alloc/response hygiene FAIL](2026-10-12-r6-capacity-list-alloc-hygiene.md)
