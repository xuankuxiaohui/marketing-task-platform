# PROJECT_STATUS
> 阶段：**编组 G 进行中** / 当前任务：**33 发放引擎，已交付、待验收** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–33**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 本分支均未合 master）
- 进行中：无。**任务 33 代码已交付、待验收**
- 下一步：下一会话从本分支 tip 开 `task/34-<slug>` 做任务 34（领取、履约、对账与过期）。**禁止在本分支继续写 34/35。禁止 merge / push / force-push master**
- Git：工作分支 `task/33-grant-engine`（基线 `origin/task/32-prize-inventory` @ `ce2e34c`，其上叠 32 → 31 @ `78624a2` → 30 @ `8401367` → 29 @ `cbe2c39`）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- `RewardPort.grant` 真实现：幂等键 `(grantSource, sourceId, prizeId)`；GRANTED 短路；RETRY_PENDING 不短路并 CAS 提升；WON/CLAIMING/EXPIRED/PERMANENT_FAILED 按现态返回
- 规则链：`UserAttributePort.attributes` 非 ACTIVE → `USER_INVALID`；奖品状态 → 库存 → 当日/累计限领（重试排除自身占位）→ 地域/等级/标签（同一份属性，禁止直查用户表）→ `RiskCheckPort` GRANT。风控拒绝零副作用（无发放行、库存回滚、无积分调用）
- AUTO → GRANTED 同事务 `startFulfillment`：INSTANT POINTS 调 `PointsPort.earn`（任务 35 未到，生产装配可替换 `PointsPortStub`）；其余 INSTANT 记 ARRIVED；ASYNC 记 SENDING 并 afterCommit 写桩 `fulfillment_ref`。写入 `costFen`/`faceFen`/`recon_status`
- MANUAL → WON（`expire_at`、履约 NONE）。可重试失败 REQUIRES_NEW 留 `RETRY_PENDING` + `reward.grant.failed` Outbox 再回滚主事务；`next_retry_at`；达 `reward.grant.retry-max` 转 PERMANENT_FAILED。永久失败同样独立留痕
- 调度 3 `sched:grant-retry`（admin-app，锁恰一）。retry / manual-grant 端点（`reward:record:retry` / `reward:record:manual-grant` + CSRF + `@Audited`）。补发仅可绕过 REGION/LEVEL/TAG
- 重试成功：`GrantStepResumer` 按 sourceId 反查步骤 → CAS 完成 → 续级联；实例已终态则权益保留不复活。portal-app 装配 `domain-reward`（RL-05）

## 改过的核心文件

- `server/domain-reward/`：`GrantAppService` / `GrantFailureLedger` / `FulfillmentService` / `RewardPortImpl.grant` / 调度 3 / retry+manual-grant 端点 / `PointsPort` 替身
- `server/domain-task/`：`StepEngine.resumeFromReward`（终态不复活）
- `server/admin-app/`：`GrantResumeConfiguration` 把成功重试接回步骤 CAS
- `server/portal-app/pom.xml`：生产依赖 `domain-reward`
- `.kiro/specs/platform-v2/tasks.md`（任务 33 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`GrantAppServiceTest`、`GrantLimitsTest`、`GrantRetrySchedulerTest`、`GrantRecordAdminControllerTest`、`PointsPortStubTest`、`RewardPortImplTest`；IT：`GrantExactlyOnceIT`（C-6 / R18.1）、`GrantZeroSideEffectIT`（R18.2，本机无 Docker 留给 CI）
- 未跑项及原因：其余 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 本 PR 均未合 master；叠链 29 → 30 → 31 → 32 → 33
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
- 积分真账本未实现（任务 35）；INSTANT POINTS 走可替换 `PointsPort` / 测试替身
- claim / 履约回调 / 对账 / 过期调度 4/5/10 = 任务 34
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
- 不要在 `task/33-grant-engine` 上继续写任务 34/35
- 不要做多轮代码评审

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/34-<slug>`（未合则叠在 33 上），做任务 34 领取/履约/对账/过期
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 本 PR
3. **停止本会话。不要在本分支写 34/35**
