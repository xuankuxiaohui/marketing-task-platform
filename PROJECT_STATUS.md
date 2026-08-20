# PROJECT_STATUS
> 阶段：**编码（编组 F 任务 28 已交付、未合 master）** / 当前任务：**28 可见性与领取，未合 master** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–28**（任务 22/23/24/25/26/27/28 仅在任务链分支，未合 master）
- 进行中：无（任务 28 在 `task/28-visibility-claim`）
- 下一步：任务 29 步骤引擎（叠在 `task/28-visibility-claim`）；**禁止 merge**
- Git：工作分支 `task/28-visibility-claim`（叠任务 22–27）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 分桶：`md5(userId + ":" + taskId)` 前 8 字节大端 `remainderUnsigned(100)`；向量 (1,1)→81、(1,2)→0、(10001,88)→32
- 领取链顺序钉死：`lockAndGet` → 幂等短路（任意终态 200）→ 可见性 → 风控 → 互斥 → 每日上限 → INSERT `expire_at` → enter → `task.instance.start`
- 灰度 CROWD = 允许包 AND NOT 排除包；过滤/灰度/列表只走 `UserAttributePort.attributes`，task 不查 `sys_portal_user`
- 投放列表 overlay 只认**当前 cycleKey** 的 `IN_PROGRESS`；详情优先当前 cycle，否则回退该任务任意进行中实例
- portal-app 装配 `domain-task`（RL-05 的 task 半边）；`domain-reward` 仍未装配
- `TaskReadPort.instanceCounts` 替换 identity 替身（admin 用户详情聚合）

## 改过的核心文件

- `server/domain-task/`：GrayBucket、VisibilityEvaluator、ExpireAtCalculator、TaskClaimAppService、TaskPortalAppService、ClaimEnterEngine、C 端 `/api/common/task/**`
- `server/portal-app/pom.xml`：装配 `domain-task`
- `server/domain-identity/.../TaskReadPortStub.java`：仅 classpath 无 task 时占位
- `server/domain-risk/.../RiskRejectIdempotentIT.java`：R13.4 CI
- `.kiro/specs/platform-v2/tasks.md`（任务 28 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`GrayBucketTest`、`GrayStabilityPropertyTest`、`VisibilityEvaluatorTest`、`TaskClaimAppServiceTest`、`TaskPortalAppServiceTest`、`TaskReadPortImplTest`、`ExpireAtCalculatorTest`、`ClaimEnterEngineTest`
- 未跑项及原因：本机无 Docker，`InstanceUniquenessIT` / `TrackingNonBlockingIT` / `RiskRejectIdempotentIT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 28 PR #36 一轮评审 #37 已处理；测试 #34、运维 #35 只记账不修；禁止 merge
- 任务 27 PR #32 一轮评审 #33 已处理；测试 #30、运维 #31 只记账不修；禁止 merge
- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修；禁止 merge
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账；禁止 merge
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- 步骤 click/callback/progress CAS 与 REWARD 发放属任务 29/40
- 规则管理 `GET/PUT /admin/risk/rules` 不在任务 18 勾选，R26.6 仍是需求缺口

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
- 不要从 `origin/master` 开新任务分支（夜间不合，必须叠任务链）
- 不要做任务 29（步骤引擎）以外的后续任务，除非 28 已完整交付

## 下一步开发顺序（最多 3 步）

1. CI 绿且人类确认后 squash 合 `master`（PR #9 / #14 / #18 / #23 / #28 / #32 / #36）
2. 任务 29：步骤引擎（叠在 `task/28-visibility-claim`）
3. 任务 30：internal 回调端点
