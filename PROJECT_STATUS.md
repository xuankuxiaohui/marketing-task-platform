# PROJECT_STATUS
> 阶段：**编组 H 进行中** / 当前任务：**38.2 portal 任务浏览与参与，已交付** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1、38.2**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53 均未合 master）
- 进行中：无。**编组 H（36–39）进行中，任务 38.2 已交付**
- 下一步：下一会话从本分支 tip 开 `task/38-portal-prize` 做任务 38.3（portal 奖品积分 + 埋点 SDK）。**禁止在本分支继续写 38.3+。禁止 merge / push / force-push master**
- Git：工作分支 `task/38-portal-task`（基线 `origin/task/38-portal-session` @ `8b381ee` / 其上叠 38.1 → 37.3 → 37.2 → 37.1 → 36 → 35 → 34 → 33 → 32 → 31 → 30 → 29）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 首页 `GET /api/common/task/list`：字典 `task_category` tab 含「全部」、下拉刷新、触底分页；卡片按钮按 `userStatus` 映射（领取 / 继续 / 终态置灰）
- 详情三分渲染：NOT_STARTED 预览+领取；IN_PROGRESS 时间线+当前步骤动作+放弃；终态 / OFFLINE 展示「已完成 / 已放弃 / 已过期 / 任务已结束」
- 冻结不预置：列表/详情仍渲染「继续」，推进返回 `task.instance.frozen` 提示「账号受限」
- 外链图标失败统一占位，不阻塞列表/详情
- 进度只在进入详情与手动刷新拉取，页面停留不轮询
- 请求头 `X-Client-Platform: WEB`；埋点 SDK / `task.card.exposure` 属 38.3

## 改过的核心文件

- `web/apps/client/**`：首页列表、任务详情、我的任务、状态机/时间线/占位图
- `.kiro/specs/platform-v2/tasks.md`（任务 38.2 勾选）

## 测试与验证

- 命令与结果：`cd web && pnpm lint && pnpm test` 本机 exit 0（client 50 tests + admin 60 + shared 2）；`cd server && mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：verification-matrix 任务 38.2（R34.2–R34.4 按钮/时间线/反馈 Vitest；外链图失败占位）。关键用例 = 五态按钮映射、时间线 done/current/idle + 进度 x/N、点击完成奖励反馈、冻结 toast、OFFLINE 空态
- 未跑项及原因：后端 `*IT`（含矩阵 `ListStateConsistencyIT`）留给 CI（未削弱断言，未用 H2 / Embedded Redis）；Playwright 与 OpenAPI 全量契约门禁属任务 39；奖品/积分页与埋点 SDK 属 38.3

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37.1 → 37.2 → 37.3 → 38.1 → 38.2
- `GET/PUT /admin/risk/rules` 未在后端/OpenAPI 导出；规则页不发明读写契约（R26.6）
- `GET /admin/reward/records` 未在后端/OpenAPI 导出；发放页不发明列表契约
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
- 不要在 `task/38-portal-task` 上继续写任务 38.3+
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET/PUT /admin/risk/rules` 或 `GET /admin/reward/records`
- 不要把奖品积分页或埋点 SDK 写进本分支（38.3）

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/38-portal-prize`（未合则叠在 38.2 上），做任务 38.3 portal 奖品积分 + 埋点 SDK
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 PR #48 → 37.1 PR #49 → 37.2 PR #50 → 37.3 PR #51 → 38.1 PR #52 → 38.2 PR #53
3. **停止本会话。不要在本分支写 38.3+。**
