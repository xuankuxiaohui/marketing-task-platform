# PROJECT_STATUS
> 阶段：**编组 H 待验收** / 当前任务：**39 前端测试与类型门禁，已交付** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1、38.2、38.3、39**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR 待填 均未合 master）
- 进行中：无。**编组 H（36–39）待验收**
- 下一步：下一会话从本分支 tip 开 `task/40-scenario-matrix` 做编组 I 第一题（ScenarioMatrixIT）。**禁止在本分支继续写 40+。禁止 merge / push / force-push master**
- Git：工作分支 `task/39-frontend-gates`（基线 `origin/task/38-portal-prize` @ `0cb394a` / 其上叠 38.3 → 38.2 → 38.1 → 37.3 → 37.2 → 37.1 → 36 → 35 → 34 → 33 → 32 → 31 → 30 → 29）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- Vitest 补齐 design §7.9 门户组件点位：R34.2 TaskCard、R35.2/R35.1 PrizeCard 倒计时、R32.2 CaptchaField / 登录刷新与单独提示 / 密码明暗 / 提交 loading
- Playwright 只落 `web/e2e/journey-core.spec.ts` 与 `journey-admin.spec.ts` 的 `test.skip` 骨架；正式旅程与 CI 阻断属任务 43
- CI `ci/check-openapi-types.sh`：`gen:api` 后 `git diff --exit-code`；Vitest 红灯样例证明契约变、未重生 TS 则 diff

## 改过的核心文件

- `web/apps/client/src/components/{CaptchaField,PrizeCard,TaskCard}.spec.ts`、`views/login/LoginPage.spec.ts`
- `web/e2e/journey-core.spec.ts`、`web/e2e/journey-admin.spec.ts`、`web/playwright.config.ts`
- `web/packages/shared/src/openapi-diff.spec.ts`、`e2e-skeleton.spec.ts`
- `ci/check-openapi-types.sh`、`.github/workflows/ci.yml`
- `.kiro/specs/platform-v2/tasks.md`（任务 39 勾选）

## 测试与验证

- 命令与结果：`cd web && pnpm lint && pnpm test` 本机 exit 0（client 80 tests + admin 60 + shared 6）；`bash ci/check-openapi-types.sh` exit 0；`cd server && mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：verification-matrix 任务 39（可维护性 2 OpenAPI 类型无 diff / openapi-typescript CI；红灯样例 `openapi-diff.spec.ts`）
- 未跑项及原因：后端 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）；Playwright 浏览器与 staging compose 全链路属任务 43，骨架 `test.skip` 不进本任务 CI 阻断

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR 待填 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37.1 → 37.2 → 37.3 → 38.1 → 38.2 → 38.3 → 39
- `GET/PUT /admin/risk/rules` 未在后端/OpenAPI 导出；规则页不发明读写契约（R26.6）
- `GET /admin/reward/records` 未在后端/OpenAPI 导出；发放页不发明列表契约
- portal `PrizeCardView.sourceTaskId` / `PointsPortalTxView.sourceTaskId` 后端现返回 null；有值才跳转，本任务不改发放/积分后端
- 编组 F 评审必须项 #41 已在任务 31 分支修；待 CI 绿后关 #41
- 任务 28 已合 master（#36）；评审 #37、测试 #34、运维 #35 只记账不修
- 任务 27 PR #32 一轮评审 #33 已处理；测试 #30、运维 #31 只记账不修
- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，依赖 Testcontainers 的 `*IT` 只能在 CI 验证
- 当前 `admin-app` 可执行 jar 启动存在 `GrantAppService` ↔ `GrantStepResumer` ↔ `TaskStepAppService` 循环依赖，完整进程启动需处理该环；本任务未改 admin 启动装配。本机 8080 / 8081 旧进程未杀
- `GET /admin/system/dict-types/{code}/entries` 不回 id，字典项更新/删除端点存在但列表无法定位；本任务不发明列表接口

## 尝试过但失败的方案

- 用 Aviator 直接 compile AND/in 原文：Aviator 不认 `AND`，函数调用后的 `in` 也失败。白名单改由自研解析器执行。
- `FEATURE_SET` 空集 + 把 AND 替换成 && 再交给 Aviator：`in` 列表仍无法稳定编译。

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27、任务 27 的 #30 #31、任务 28 的 #34 #35
- 不要在 `task/39-frontend-gates` 上继续写任务 40+
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET/PUT /admin/risk/rules` 或 `GET /admin/reward/records`
- 不要发明 portal `claimMode` 或改发放/积分后端只为补来源任务 ID
- 不要把 Playwright 骨架改成任务 43 的全链路 CI 阻断

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/40-scenario-matrix`（未合则叠在 39 上），做任务 40 ScenarioMatrixIT
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 PR #48 → 37.1 PR #49 → 37.2 PR #50 → 37.3 PR #51 → 38.1 PR #52 → 38.2 PR #53 → 38.3 PR #54 → 39 本 PR
3. **停止本会话。不要在本分支写 40+。不要合 master。**
