# PROJECT_STATUS
> 阶段：**编码（编组 F 任务 29 已交付、未合 master）** / 当前任务：**29 步骤引擎，未合 master** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–29**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 仅在 `task/29-step-engine`，未合 master）
- 进行中：无（任务 29 在 `task/29-step-engine`）
- 下一步：任务 30 internal 回调端点（从本 tip 开 `task/30-internal-callback`）；**禁止 merge**
- Git：工作分支 `task/29-step-engine`（基线 `origin/master` / #36）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 四入口前置（§5.1.1）：过期先拒 `task.instance.expired`；已完成 click/callback 200 幂等；INACTIVE/SKIPPED/乱序/类型不符 400 `task.step.state-mismatch`；progress 打已完成步骤 400
- 完成权 CAS：`UPDATE ... WHERE id=? AND version=? AND status='ACTIVE'`，自旋 ≤3；click/callback 耗尽 `task.step.processing`，progress 耗尽 `task.progress.processing`（HTTP 400）
- 级联：PASSIVE 自动完成；CLICK/CALLBACK/PROGRESS 激活后停；REWARD 三分支 = GRANTED/WON 完成续推 / `RetryableGrantException` 保持 ACTIVE / `PermanentGrantException` → SKIPPED + `GRANT_PERMANENT_FAILED`
- 进度：同事务 INSERT `task_progress_report` uk_dedup，冲突幂等不累加；`value ∈ [1,1000]`
- 调度 7 只在 admin-app：`sched:progress-clean`，每日 04:00 UTC+8，删 7 天前，分批 5000
- `RewardPort` 真实现不在本任务（任务 40）；单测用 `MemoryRewardPort`；portal-app 仍未装配 `domain-reward`
- internal HMAC / nonce / 限流端点属任务 30；本任务只提供 `TaskStepAppService.callback/progress` 与 C 端 click

## 改过的核心文件

- `server/domain-task/`：`StepEngine`、`ClaimEnterEngine` 委托、`TaskStepAppService`、click 端点、progress 去重仓储、`ProgressCleanScheduler`
- `server/domain-task/.../TaskInstanceStepMapper.xml`：completeCas / skipCas / addProgressCas
- `.kiro/specs/platform-v2/tasks.md`（任务 29 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`StepEngineTest`、`TaskStepAppServiceTest`、`StepStateMachinePropertyTest`、`ProgressCleanSchedulerTest`、`ClaimEnterEngineTest`；IT：`StepAdvanceExactlyOnceIT`（C-2）、`ProgressDedupIT`（C-3）、`ExpiredFinalityIT`（R14.4）
- 未跑项及原因：本机无 Docker，`*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 28 已合 master（#36）；评审 #37、测试 #34、运维 #35 只记账不修
- 任务 27 PR #32 一轮评审 #33 已处理；测试 #30、运维 #31 只记账不修
- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- HMAC internal 回调端点属任务 30；实例过期翻转调度 2 / 后台实例管理属任务 31
- portal-app 未装配 `domain-reward`（发奖任务 40）；identity `RewardPortStub.grant` 仍抛 `UnsupportedOperationException`
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
- 不要从 `origin/master` 开任务 30（29 未合，必须从 `task/29-step-engine` tip 叠出）
- 不要把任务 30/31 写进本分支；不要做任务 32+
- 不要做多轮代码评审

## 下一步开发顺序（最多 3 步）

1. 任务 30：internal 回调端点（HMAC、appId 限流、nonce、A-01~A-06），从 `task/29-step-engine` tip 开 `task/30-internal-callback`
2. 任务 31：实例管理与平台动作，从 30 tip 开 `task/31-instance-admin`；做完停
3. CI 绿且人类确认后 squash 合 `master`（任务 29 PR）
