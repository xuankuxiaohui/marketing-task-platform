# PROJECT_STATUS
> 阶段：**编组 H 进行中** / 当前任务：**36 admin 前端骨架，已交付** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 本分支均未合 master）
- 进行中：无。**编组 H（36–39）已开，任务 36 已交付**
- 下一步：下一会话从本分支 tip 开 `task/37.1-<slug>` 做任务 37.1（admin 系统管理页）。**禁止在本分支继续写 37+。禁止 merge / push / force-push master**
- Git：工作分支 `task/36-admin-skeleton`（基线 `origin/task/35-points` @ `3942f8c` / PR #47，其上叠 35 → 34 → 33 @ `d9df22c` → 32 @ `ce2e34c` → 31 @ `78624a2` → 30 @ `8401367` → 29 @ `cbe2c39`）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 前端落在 `web/` pnpm workspace：`apps/admin` + `packages/shared`。本任务不建 `apps/client`（任务 38.1）
- 动态路由消费 `GET /admin/auth/menus`（种子 = design §4.10 28 行）。`/login` 为静态公开路由，不进侧栏；未交付业务页走 `ComingSoonPage` 占位，不实现 37.x 页面
- 权限指令 `v-auth` 对照登录/资料下发的操作权限码（附录 B），不发明前端权限码
- 后台会话只走 HttpOnly Cookie；前端不存 JWT。写请求带 `X-CSRF-Token`（Cookie `csrfToken` 为权威载体）
- OpenAPI：`pnpm --filter @mkt/shared gen:api` 从两应用命名空间 JSON 生成类型（`/admin/v3/api-docs/admin`、`/api/v3/api-docs/portal|internal`），**不用** spike/5 与 kernel 冒烟 JSON。admin 生产包不 import internal 生成物
- `packages/shared` 提供 `isOk` / `isFail`；成功 `code` 为数字 `0`

## 改过的核心文件

- `web/`：pnpm workspace、admin 骨架（登录/工作台/动态路由/`v-auth`/多标签）、`@mkt/shared` 类型守卫与 OpenAPI 生成管线
- `.github/workflows/ci.yml`：增加 `web` job（lint / gen:api diff / test）
- `.kiro/specs/platform-v2/tasks.md`（任务 36 勾选）

## 测试与验证

- 命令与结果：`cd web && pnpm lint && pnpm test` 本机 exit 0；`cd server && mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：任务 36 无 §7.3 属性行。本任务测试 = 登录页 Vitest（`LoginPage.spec.ts`）+ 路由守卫单测（`guards.spec.ts`）；另有动态路由 / CSRF / `v-auth` / `isOk`/`isFail` 单测
- 未跑项及原因：后端 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）；Playwright 与 OpenAPI 全量契约门禁属任务 39；admin 业务页属 37.1–37.3

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 本 PR 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36
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
- 规则管理 `GET/PUT /admin/risk/rules` 不在任务 18 勾选，R26.6 仍是需求缺口
- 当前 `admin-app` 可执行 jar 启动存在 `GrantAppService` ↔ `GrantStepResumer` ↔ `TaskStepAppService` 循环依赖，完整进程启动需处理该环；本任务未改后端。本机 8080 旧进程未杀

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
- 不要在 `task/36-admin-skeleton` 上继续写任务 37+
- 不要做多轮代码评审
- 不要开 `task/37.1` 分支（下一会话再开）
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/37.1-<slug>`（未合则叠在 36 上），做任务 37.1 admin 系统管理页
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 本 PR
3. **停止本会话。不要在本分支写 37+。不要开 task/37.1 分支**
