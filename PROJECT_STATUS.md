# PROJECT_STATUS
> 阶段：**编组 H 进行中** / 当前任务：**38.1 portal 会话+个人中心，已交付** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52 均未合 master）
- 进行中：无。**编组 H（36–39）进行中，任务 38.1 已交付**
- 下一步：下一会话从本分支 tip 开 `task/38-portal-task` 做任务 38.2（portal 任务浏览与参与）。**禁止在本分支继续写 38.2+。禁止 merge / push / force-push master**
- Git：工作分支 `task/38-portal-session`（基线 `origin/task/37-admin-risk-track` @ `ebf963f` / 其上叠 37.3 → 37.2 → 37.1 → 36 → 35 → 34 → 33 → 32 → 31 → 30 → 29）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 门户 H5 落地 `web/apps/client`：Vant 4 + unplugin-auto-import，底栏首页/我的（R33.1）
- 登录态 Bearer + `X-Device-Id`（UUID v4 持久化）；401 四码文案区分（R32.5 / D-02），未登录访问保留回跳
- 任务 21 未交付的 C 端档案/改密按 design §4.9.1 补齐：`GET/PUT /api/common/auth/profile`、`PUT /api/common/auth/password`（不进审计表）；OpenAPI `portal.json` 同步
- 首页任务列表 / 我的任务真列表 / 奖品 / 积分 / 埋点 SDK 属 38.2 / 38.3，本任务只放入口、空态与进行中角标读取 `GET /api/common/task/mine`
- 签到入口展示为 P1 占位，不发明 `/api/common/signin/**`

## 改过的核心文件

- `web/apps/client/**`：H5 骨架、登录/注册/档案/改密/退出、底栏、空态
- `server/domain-identity`：门户档案与改密
- `web/packages/shared/openapi/portal.json` + 生成物
- `.kiro/specs/platform-v2/tasks.md`（任务 38.1 勾选）

## 测试与验证

- 命令与结果：`cd web && pnpm lint && pnpm test` 本机 exit 0（client 23 tests + admin 60 + shared 2）；`cd server && mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：verification-matrix 任务 38.1（R32 / R33 验证码 / 协议 / 空态 Vitest）。关键用例 = 验证码刷新与单独提示、注册协议未勾选不可提交、空态文案、401 四码区分
- 未跑项及原因：后端 `*IT`（含矩阵 `AnonymousBoundaryIT` / `LogoutImmediateIT`）留给 CI（未削弱断言，未用 H2 / Embedded Redis）；Playwright 与 OpenAPI 全量契约门禁属任务 39；任务列表/奖品/积分页属 38.2 / 38.3

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37.1 → 37.2 → 37.3 → 38.1
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
- 不要在 `task/38-portal-session` 上继续写任务 38.2+
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET/PUT /admin/risk/rules` 或 `GET /admin/reward/records`
- 不要做任务列表领取推进、奖品积分页、埋点 SDK（38.2 / 38.3）

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/38-portal-task`（未合则叠在 38.1 上），做任务 38.2 portal 任务浏览与参与
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 PR #48 → 37.1 PR #49 → 37.2 PR #50 → 37.3 PR #51 → 38.1 PR #52
3. **停止本会话。不要在本分支写 38.2+。**
