# PROJECT_STATUS
> 阶段：**编组 I 待验收** / 当前任务：**43 Playwright E2E + k6，已交付** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1、38.2、38.3、39、40、41、42、43**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR #55、任务 40 PR #56、任务 41 PR #57、任务 42 PR #58、任务 43 PR #59 均未合 master）
- 进行中：无。**编组 I（40–43）待验收**
- 下一步：编组 I 验收后，下一组第一题从本链 tip 开 `task/44-signin`（禁止在本分支写 44+）。**禁止 merge / push / force-push master**
- Git：工作分支 `task/43-e2e-k6`（基线 `origin/task/42-deploy-compose` @ `0637a01` / 其上叠 42 → 41 → 40 → 39 → 38.3 → … → 29）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- Playwright `journey-core` / `journey-admin` 对 staging compose：Vite 反代 `E2E_API_BASE`（默认 `127.0.0.1:18080`），验证码从 compose Redis DB 2 读取，不发明免登
- E2E 阻断进 CI `e2e` job（`ci/e2e-compose.sh`）；k6 按 §7.1 **不进例行 CI**，发布签署走 `perf/run-p0.sh` + `perf/reports/<日期>/`
- k6 P0 子集：`list.js` `advance.js` `complete.js` `risk-delta.js` `track.js` `admin-list.js`（性能 1–5、7）；性能 6/8 与 `perf/ad.js` 留给任务 49
- k6 跑在 compose 网内：公网 `http://nginx`，internal 直连 `portal-app-1:8081`（Nginx 仍 404 `/internal`）
- 种子 `perf/seed/seed.py`：`--scale p0|nfr`；nfr 对齐 1000 任务 / 50 万实例；internal 限流 seed 调至 5000

## 改过的核心文件

- `web/e2e/journey-core.spec.ts`、`web/e2e/journey-admin.spec.ts`、`web/e2e/global-setup.ts`、`web/e2e/helpers/*`
- `web/playwright.config.ts`、`web/apps/*/vite.config.ts`（preview.proxy + process.env 反代）
- `perf/*.js`、`perf/seed/seed.py`、`perf/run-p0.sh`
- `ci/e2e-compose.sh`、`.github/workflows/ci.yml`
- `server/admin-app/src/test/java/com/mkt/admin/PerfBaselineTest.java`
- `.kiro/specs/platform-v2/tasks.md`（任务 43 勾选）
- `deploy/R31-go-live-checklist.md`（k6 签署项指向脚本）

## 测试与验证

- 命令与结果：`cd server && mvn -q -DskipITs test` 与 `cd web && pnpm test` 本机跑；Playwright / 完整 k6 5 分钟加压留给 CI `e2e` job 与 staging `perf/run-p0.sh`
- 矩阵覆盖：verification-matrix 任务 43（R32–R35 旅程 + NFR 性能 1–5、7）
- 未跑项及原因：本机 3308/6379/8080/8081 在跑，未杀；compose 口 18080；k6 稳定加压 ≥5 分钟不进例行 CI（design §7.1）；未削弱断言，未用 H2 / Embedded Redis

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR #55、任务 40 PR #56、任务 41 PR #57、任务 42 PR #58、任务 43 PR #59 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37.1 → 37.2 → 37.3 → 38.1 → 38.2 → 38.3 → 39 → 40 → 41 → 42 → 43
- `GET/PUT /admin/risk/rules` 未在后端/OpenAPI 导出；规则页不发明读写契约（R26.6）；k6 性能 4 用 SQL 切换 `risk_rule_config.enabled`
- `GET /admin/reward/records` 未在后端/OpenAPI 导出；k6 后台列表用已有 `/admin/task/instances` `/admin/task/definitions` `/admin/points/transactions`
- portal `PrizeCardView.sourceTaskId` / `PointsPortalTxView.sourceTaskId` 后端现返回 null；有值才跳转
- 编组 F 评审必须项 #41 已在任务 31 分支修；待 CI 绿后关 #41
- 任务 28 已合 master（#36）；评审 #37、测试 #34、运维 #35 只记账不修
- 任务 27 PR #32 一轮评审 #33 已处理；测试 #30、运维 #31 只记账不修
- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机 8080 / 8081 / MySQL 3308 / Redis 6379 旧进程未杀
- `GET /admin/system/dict-types/{code}/entries` 不回 id，字典项更新/删除端点存在但列表无法定位
- Grafana 看板后置；Redis 哨兵/≥4GB 是生产目标（R31.5），P0 compose 用单实例 Redis 7
- 备份演练脚本已交付；staging 真人 PITR 签字在上线清单

## 尝试过但失败的方案

- 用 Aviator 直接 compile AND/in 原文：Aviator 不认 `AND`，函数调用后的 `in` 也失败。白名单改由自研解析器执行。
- `FEATURE_SET` 空集 + 把 AND 替换成 && 再交给 Aviator：`in` 列表仍无法稳定编译。
- `SpringApplicationBuilder.properties(REDIS_HOST=…)` 不压过本机 env `REDIS_HOST`；改为 initializer 注册 Redis bean。

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27、任务 27 的 #30 #31、任务 28 的 #34 #35
- 不要在 `task/43-e2e-k6` 上继续写任务 44+
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET/PUT /admin/risk/rules` 或 `GET /admin/reward/records`
- 不要发明 portal `claimMode` 或改发放/积分后端只为补来源任务 ID
- 不要把 k6 5 分钟全量灌进例行 PR CI（§7.1 发布签署，不进例行 CI）
- 不要在 compose 里把 `/internal` 暴露到公网 Nginx
- 不要把真实密钥写进 `.env.example`
- 不要跑 P1 C-9 / C-10 / C-11
- 不要建 P1 域（signin / activity / ad）

## 下一步开发顺序（最多 3 步）

1. 编组 I 验收；合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 PR #48 → 37.1 PR #49 → 37.2 PR #50 → 37.3 PR #51 → 38.1 PR #52 → 38.2 PR #53 → 38.3 PR #54 → 39 PR #55 → 40 PR #56 → 41 PR #57 → 42 PR #58 → 43 PR #59
2. 下一组开发前从本链 tip 开 `task/44-signin`（编组 J 第一题）
3. **停止本会话。不要在本分支写 44+。不要合 master。**
