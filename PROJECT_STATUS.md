# PROJECT_STATUS
> 阶段：**编组 F 待验收** / 当前任务：**31 实例管理与平台动作，未合 master** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–31**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 本分支均未合 master）
- 进行中：无。**编组 F（任务 26–31）代码已交付、待验收**
- 下一步：CI 绿且人类确认后按 29 → 30 → 31 顺序 squash 合 `master`。**禁止做任务 32+。禁止 merge / push / force-push master**
- Git：工作分支 `task/31-instance-admin`（基线 `task/30-internal-callback` @ `8401367`，其上叠 29 @ `cbe2c39`）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 后台实例：`GET /admin/task/instances`（`task:instance:query`，taskId/userId/status/simulated/from/to）；详情含步骤明细 + 事件时间线（pending `sys_outbox` ∪ 已消费 `evt_event_log`，按 outbox id 去重）
- 运营终止 `POST /admin/task/instances/{id}/abandon`（`task:instance:abandon` + CSRF + `@Audited`）；`abandonSource=ADMIN`；非 IN_PROGRESS 幂等返回当前状态。C 端 `POST /api/common/task/instances/{id}/abandon`：`abandonSource=USER`，归属校验，过期/冻结与 click 同前置，不标 `@Audited`
- 调度 2 `sched:instance-expire`（admin-app，1min，`tryLock(0)`）：`IN_PROGRESS ∧ expire_at IS NOT NULL ∧ expire_at<=now` CAS 转 EXPIRED；NULL `expire_at` 不翻转（列生产 NOT NULL；计算侧缺窗/未下线/无周期 = +∞，全空则 base=now）
- 动作合并回退链：步骤级(端)→任务级(端)→步骤级(WEB)→任务级(WEB)→NONE 占位；`actionType=NONE` 视为命中停链。实例详情只读绑定快照。`X-Client-Platform` 非法/缺失按 WEB 并计 unknown-platform

## 改过的核心文件

- `server/domain-task/`：`TaskInstanceAdminController`、`TaskInstanceAppService`、`InstanceExpireScheduler`、`ActionMerger` NONE 占位、`ClientPlatformResolver`、portal abandon、实例 Mapper 分页/CAS
- `.kiro/specs/platform-v2/tasks.md`（任务 31 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`ActionMergePropertyTest`、`ActionFallbackPropertyTest`、`ClientPlatformResolverTest`、`TaskInstanceAppServiceTest`、`InstanceExpireSchedulerTest`；IT：`ActionSnapshotFixityIT`、`ExpiredFinalityIT`（含调度翻转，本机无 Docker 留给 CI）
- 未跑项及原因：其余 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 编组 F 评审必须项 #41 已在本分支修：领取/推进事务 READ COMMITTED、uk PersistenceException 转既有实例 200、步骤冻结走 userSummary BLACK。待 CI 绿后关 #41
- 任务 29 PR #38、任务 30 PR #39、任务 31 本 PR 均未合 master；叠链 29 → 30 → 31
- 任务 28 已合 master（#36）；评审 #37、测试 #34、运维 #35 只记账不修
- 任务 27 PR #32 一轮评审 #33 已处理；测试 #30、运维 #31 只记账不修
- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，依赖 Testcontainers 的 `*IT` 只能在 CI 验证
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
- 不要做任务 32+；编组 F 待验收
- 不要开任务 32 分支
- 不要做多轮代码评审

## 下一步开发顺序（最多 3 步）

1. 人类验收编组 F；CI 绿后按 29 PR #38 → 30 PR #39 → 31 本 PR squash 合 `master`
2. 合入前不要从过期 master 另开分支
3. **停止。不要做任务 32+**
