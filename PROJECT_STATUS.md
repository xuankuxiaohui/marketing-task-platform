# PROJECT_STATUS
> 阶段：**编码（编组 F 任务 26 已开 PR、未合）** / 当前任务：**26 任务定义聚合与表达式引擎，未合 master** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–26**（任务 22/23/24/25/26 仅在任务链分支，未合 master）
- 进行中：无（任务 26 已开 PR #28；一轮评审 #29 已修）
- 下一步：任务 27 发布版本与定时发布（叠在 `task/26-task-definition`）；**禁止 merge**
- Git：工作分支 `task/26-task-definition`（叠在 PR #23 / `task/25-audit-session` 上），PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- save-aggregate 先内存校验再落库；`@Transactional` 包定义 + 子表替换
- PUBLISHED/SCHEDULED 再保存只改编辑态并置 `pending_revision=1`，主状态不变（D-01；发布是任务 27）
- 表达式自研 DSL 解析器做 AST 白名单；Aviator 只作锁死 Feature 的依赖，不吃 AND/in 原文
- 属性函数缺失走 `NULL_ATTR`（比较一律 false）；布尔函数 hasTag / registerWithinDays / inCrowd 缺失直接 false
- gray.type、action.scope/platform/actionType 校验后写回大写，避免混写丢掉动作或撞 CHECK
- REWARD 保存只要求 prizeId；启用奖品校验留给发布（任务 27）
- admin-app 生产装配 domain-task；portal-app 本任务不装配（任务 28）

## 改过的核心文件

- `server/domain-task/`：聚合保存、表达式沙箱、互斥组/人群包、admin 控制器
- `server/admin-app/pom.xml`：domain-task 从 test scope 改为生产依赖
- `.kiro/specs/platform-v2/tasks.md`（任务 26 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` **通过**（exit 0）
- 矩阵覆盖：`ExpressionSandboxMaliciousTest`（M-01~M-15）、`TaskGraphAcyclicPropertyTest`、`TaskDefinitionAppServiceTest`、`TaskAggregateAtomicIT`（failsafe / CI）
- 未跑项及原因：本机无 Docker，`TaskAggregateAtomicIT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 26 PR #28 一轮评审 #29 已修；测试 #26、运维 #27 只记账不修；禁止 merge
- 任务 25 PR #23 一轮评审 #24/#25 已修；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须修项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已修；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已修，测试 #12 / 运维 #13 只记账；禁止 merge
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证

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
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27
- 不要从 `origin/master` 开新任务分支（夜间不合，必须叠任务链）
- 不要做任务 28（可见性/领取），除非 27 已完整交付

## 下一步开发顺序（最多 3 步）

1. 任务 27：发布版本与定时发布（叠在 `task/26-task-definition` / PR #28）
2. CI 绿且人类确认后 squash 合 `master`（PR #9 / #14 / #18 / #23 / #28）
3. 任务 28：可见性与领取
