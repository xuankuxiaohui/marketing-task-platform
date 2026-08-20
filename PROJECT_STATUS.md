# PROJECT_STATUS
> 阶段：**编组 G 待验收** / 当前任务：**35 积分域，已交付、待验收** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–35**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 本分支均未合 master）
- 进行中：无。**编组 G（32–35）代码已交付、待验收**
- 下一步：下一会话从本分支 tip 开 `task/36-<slug>` 做任务 36（admin 前端骨架，编组 H 第一题）。**禁止在本分支继续写 36。禁止 merge / push / force-push master**
- Git：工作分支 `task/35-points`（基线 `origin/task/34-claim-recon`，其上叠 34 → 33 @ `d9df22c` → 32 @ `ce2e34c` → 31 @ `78624a2` → 30 @ `8401367` → 29 @ `cbe2c39`）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- `PointsPort.earn` 真账本：`INSERT IGNORE pnt_account` 懒创建，再原子 `UPDATE balance = balance + delta WHERE balance + delta >= 0`，同事务写 `pnt_transaction.balance_after`
- 致负：`points.account.insufficient-balance` 且不留流水；无账户扣减：`points.account.not-found`；adjust 必填原因：`points.account.reason-required`
- 正 adjust / earn / 查余额才 INSERT IGNORE；负 adjust 不预建账户
- `RewardPort.userSummary`（D-13）：无账户余额 0（不 insert）；`prizeSummary.won/granted` 按 `rwd_grant_record` 状态计数
- 过期调度 6：每日 UTC+8 00:05，锁 `sched:points-expire`，批 5000；`EXPIRE.biz_id` 反指 EARN id；扣减截断至零写 remark
- C 端 `GET /api/common/points/balance|transactions`；后台 accounts / adjust / transactions

## 改过的核心文件

- `server/domain-reward/`：`PointsAppService` / `PointsPortImpl` / `RewardPortImpl.userSummary` / 调度 6 / admin+portal 积分端点 / `pnt_*` Mapper
- `server/domain-reward/RewardAutoConfiguration.java`：真实 `PointsPort` 替换 stub
- `server/domain-reward/RewardAdminSupportAutoConfiguration.java`：调度 6
- `.kiro/specs/platform-v2/tasks.md`（任务 35 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`PointsAppServiceTest`、`PointsLedgerPropertyTest`（R20.2）、`RewardPortImplTest.userSummary`、admin/portal 控制器单测、`PointsExpireSchedulerTest`；IT：`PointsNonNegativeIT`（C-8 / R20.1）本机无 Docker 留给 CI
- 未跑项及原因：其余 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 本 PR 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35
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
- 不要在 `task/35-points` 上继续写任务 36
- 不要做多轮代码评审
- 不要开 `task/36` 分支（下一会话再开编组 H）

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/36-<slug>`（未合则叠在 35 上），做任务 36 admin 前端骨架（编组 H）
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 本 PR
3. **停止本会话。不要在本分支写 36。不要开 task/36 分支**
