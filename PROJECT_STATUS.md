# PROJECT_STATUS
> 阶段：**编组 H 进行中** / 当前任务：**37.1 admin 系统管理页，已交付** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49 均未合 master）
- 进行中：无。**编组 H（36–39）进行中，任务 37.1 已交付**
- 下一步：下一会话从本分支 tip 开 `task/37.2-<slug>` 做任务 37.2（admin 任务+奖励页）。**禁止在本分支继续写 37.2+。禁止 merge / push / force-push master**
- Git：工作分支 `task/37-admin-system`（基线 `origin/task/36-admin-skeleton` @ `2ad1c7d` / 其上叠 36 → 35 → 34 → 33 → 32 → 31 → 30 → 29）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 按 design §4.10 落地系统 9 页：`system/user|role|session|portal-user|internal-app|dict|config|cache|audit`。任务/奖励/风控/埋点页仍走 `ComingSoonPage`（任务 37.2 / 37.3）
- 掩码配置 PUT：列表值为 `******`；编辑未改值则报文省略 `value`（R8.2）；空串/null 不提交
- 停用二次确认是前端确认框，再打既有 disable 端点（用户 / 门户用户 / internal-app）。会话踢下线文案固定「将下线该账号全部会话」（R6.2）；不提供单会话踢出
- 缓存清理前端拦截 `identity:session`（含 key/prefix 落在该空间），粒度字段与后端一致：KEY=namespace+key、PREFIX=namespace+prefix、NAMESPACE=仅 namespace。踢人走 R6，不 evict session
- 审计页只读，无删除入口（R10.5）。字典项列表只消费 `GET .../dict-types/{code}/entries`（启用项、无 id），因此本页只建项、不画无 id 的更新/删除
- 写请求仍走 HttpOnly Cookie + `X-CSRF-Token`；按钮权限码只用来自附录 B / 后端的 `v-auth`

## 改过的核心文件

- `web/apps/admin/src/views/system/**`：9 个系统管理页与关键 Vitest
- `web/apps/admin/src/api/{identity,system,http}.ts`：对接 §4.2 / §4.3 端点；GET 查询参数压缩空值
- `web/apps/admin/src/utils/{config-update,cache-evict,datetime,permission-tree}.ts`
- `.kiro/specs/platform-v2/tasks.md`（任务 37.1 勾选）

## 测试与验证

- 命令与结果：`cd web && pnpm lint && pnpm test` 本机 exit 0（admin 40 tests）；`cd server && mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：verification-matrix 任务 37.1（R2–R10 系统管理页交互）。关键用例 = 掩码配置省略 value、停用/踢下线二次确认、缓存禁 session、审计无删除
- 未跑项及原因：后端 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）；Playwright 与 OpenAPI 全量契约门禁属任务 39；任务/奖励页属 37.2；风控/埋点页属 37.3

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37.1
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
- 不要在 `task/37-admin-system` 上继续写任务 37.2+
- 不要做多轮代码评审
- 不要开 `task/37.2` 分支（下一会话再开）
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/37.2-<slug>`（未合则叠在 37.1 上），做任务 37.2 admin 任务+奖励页
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 PR #48 → 37.1 PR #49
3. **停止本会话。不要在本分支写 37.2+。不要开 task/37.2 分支**
