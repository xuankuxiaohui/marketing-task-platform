# PROJECT_STATUS
> 阶段：**编组 G 进行中** / 当前任务：**32 奖品配置与库存，未合 master** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–32**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 本分支均未合 master）
- 进行中：无。**任务 32 代码已交付、待验收**
- 下一步：等人类确认后再开任务 33。**禁止做任务 33+。禁止 merge / push / force-push master**
- Git：工作分支 `task/32-prize-inventory`（基线 `origin/task/31-instance-admin` @ `78624a2`，其上叠 31 → 30 @ `8401367` → 29 @ `cbe2c39`）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 奖品分类：`GET/POST/PUT/DELETE /admin/reward/prize-categories`（`reward:category:*` + CSRF + `@Audited` 写路径）；内置 7 类 V3 种子不可删；须对账三类（支付宝红包/微信红包/话费）种子 `reconActionPolicy=REVIEW`；启用后仍可改政策
- 奖品：CRUD / 停用二次确认（`confirm=false` 返回 `affectedTaskCount`+`inFlightInstanceCount`）/ enable / `stock-replenish`；草稿保存时从分类拷贝 `rewardTarget`/`fulfillmentMode`；启用后冻结分类与成本算法字段；`reconActionPolicy` 可空（空=继承分类）启用后仍可改
- 成本校验：POINTS 必填正整数 `points`；FACE_VALUE 必填 `faceFen`；FIXED_UNIT 必填 `unitCostFen`；第三方缺适配器 `reward.prize.adapter-required`
- §5.7：`UPDATE remaining_stock-1 WHERE remaining>=1`；限领 COUNT 在同一 READ COMMITTED 事务、持有奖品行锁之后执行；`rwd_stock_log` GRANT/ROLLBACK/REPLENISH 前后值链
- `RewardPort.prizeEnabled` 真实现装配 admin-app；`grant` 仍抛（任务 33）；portal-app **未**装配 `domain-reward`

## 改过的核心文件

- `server/domain-reward/`：分类/奖品 CRUD、库存扣减、`RewardPortImpl.prizeEnabled`、C-4/C-5/R17.3 IT
- `server/admin-app/pom.xml`：`domain-reward` 升为生产依赖
- `.kiro/specs/platform-v2/tasks.md`（任务 32 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`PrizeCategoryAppServiceTest`、`PrizeAppServiceTest`、`PrizeStockServiceTest`、`PrizeCostsTest`、`ReconPoliciesTest`；IT：`StockNoOversellIT`（C-4）、`ClaimLimitConcurrentIT`（C-5）、`CostSnapshotIT`（R17.3，本机无 Docker 留给 CI）
- 未跑项及原因：其余 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 本 PR 均未合 master；叠链 29 → 30 → 31 → 32
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
- portal-app 未装配 `domain-reward`（发奖任务 33/40）；identity `RewardPortStub.grant` 仍抛 `UnsupportedOperationException`
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
- 不要做任务 33+；等人类再说继续
- 不要开任务 33 分支
- 不要做多轮代码评审

## 下一步开发顺序（最多 3 步）

1. 人类验收任务 32；CI 绿后按 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 本 PR squash 合 `master`
2. 合入前不要从过期 master 另开分支
3. **停止。不要做任务 33+，等人类再说继续**
