# PROJECT_STATUS
> 阶段：**编码（编组 E 任务 25 已开 PR、未合）** / 当前任务：**25 审计 AOP 与会话管理端点，未合 master** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–25**（任务 22/23/24/25 仅在任务链分支，未合 master）
- 进行中：无（任务 25 已开 PR #23；一轮评审 #24/#25 已修）
- 下一步：任务 26 任务定义聚合与表达式引擎（叠在 `task/25-audit-session`）；**禁止 merge**
- Git：工作分支 `task/25-audit-session`（叠在 PR #18 / `task/24-dict-config-cache` 上），PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- `@Audited` AOP 写 Outbox `audit.log`；服务层已手工 append 的路径 `AuditOnce` 跳过，禁止同一写操作两行
- 失败登录仍走 `LoginAuditAppender`：`operator_id=NULL`、`operator_name`=提交用户名
- 拦截器 403 仍由 `SessionAuthFilter` 补审计；`AuditOnce` 在过滤器 finally 与 AOP 入口清掉，避免线程复用漏记
- 脱敏：JsonUtil → Hutool `DesensitizedUtil`（password/token/secret/phone）→ 2000 + `...(truncated)`
- 调度 9 `sched:audit-clean`：`retention.audit-days`（默认 180），分批 5000
- 会话列表/踢下线按 `accountType`+`account`；kick 写 `KickReason.ADMIN`；`searchData` 用 SCAN 列 key
- `GET /admin/system/audits` 只读分页（R10.4）；GET 不标 `@Audited`

## 改过的核心文件

- `server/domain-identity/`：`AuditedAspect`、会话列表/踢人、审计查询、`AuditCleanScheduler`
- `server/platform-kernel/`：`AuditOnce`
- `server/platform-infra/`：`SaTokenDaoKeyValue.searchData` + `KeyValueStore.keysByPattern`
- `.kiro/specs/platform-v2/tasks.md`（任务 25 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` **通过**（exit 0）
- 矩阵覆盖：`AuditedAspectTest`（含不双写）、`AuditedPlacementArchTest`、`KickoutConsistencyIT`（failsafe）、`AuditCompletenessIT`（集成，CI）
- 未跑项及原因：本机无 Docker，`AuditCompletenessIT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 25 PR #23 一轮评审 #24/#25 已修；测试 #21、运维 #22 只记账不修；禁止 merge
- 任务 24 PR #18 一轮评审无必须修项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已修；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已修，测试 #12 / 运维 #13 只记账；禁止 merge
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证

## 尝试过但失败的方案

- 过滤器里 `setTokenValueToStorage(raw)`：Sa-Token 上下文过滤器 `@Order(-104)` 更晚，存储绑定被 `SaTokenContextException` 吞掉；改为向下游暴露剥前缀 cookie
- 用 `SaTokenDaoKeyValue` 在单测里 login 再搜会话：Sa-Token 缺 JSON 转换器（`未实现具体的 json 转换器`）；单测 login 仍用 `SaTokenDaoDefaultImpl`，`searchData` 单独测 Redis DAO

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22
- 不要从 `origin/master` 开新任务分支（夜间不合，必须叠任务链）

## 下一步开发顺序（最多 3 步）

1. 任务 26：任务定义聚合与表达式引擎（叠在 `task/25-audit-session`）
2. CI 绿且人类确认后 squash 合 `master`（PR #9 / #14 / #18 / #23）
3. 任务 27：发布版本与定时发布
