# PROJECT_STATUS
> 阶段：**编组 G 进行中** / 当前任务：**34 领取与过期，已交付、待验收** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–34**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 本分支均未合 master）
- 进行中：无。**任务 34 代码已交付、待验收**
- 下一步：下一会话从本分支 tip 开 `task/35-<slug>` 做任务 35（积分域）。**禁止在本分支继续写 35。禁止 merge / push / force-push master**
- Git：工作分支 `task/34-claim-recon`（基线 `origin/task/33-grant-engine` @ `d9df22c`，其上叠 33 → 32 @ `ce2e34c` → 31 @ `78624a2` → 30 @ `8401367` → 29 @ `cbe2c39`）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- claim 前置 `status IN (WON, RETRY_PENDING)`；Redisson `lock:rwd-claim:{id}` 退化纯 CAS（BUSY → `reward.claim.conflict`）
- CLAIMING 超时调度 4 回滚 WON 并 `retry_count+1`；调度 5 扫 WON/RETRY_PENDING 过期；调度 10 履约重试 / `TIMEOUT` 关单 / 跨日 SENDING 入对账池
- `POST /internal/reward/fulfillment/callback` HMAC 四头；后台 fulfill-confirm / fulfill-retry；`MANUAL` 关单后回调/confirm HTTP 200 不改态
- 对账平台集含 `FULFILL_FAILED`；核渠门禁：TIMEOUT/SENDING 未 CONFIRMED → `review-required`；`REJECTED` 仅 ABSORB；AUTO 总闸默认关，仅 CHANNEL_REJECT/ADAPTER_ERROR 可自动 REFULFILL，永不 MANUAL_GRANT
- 补发成功同事务关原单 `FULFILL_FAILED`+`MANUAL`；原 `fulfillmentRef` 留原单
- C 端 prize PENDING tab = WON/CLAIMING/RETRY_PENDING 或 GRANTED+(SENDING|FULFILL_FAILED)

## 改过的核心文件

- `server/domain-reward/`：`ClaimAppService` / 履约回调与调度 4/5/10 / `ReconAppService` / spend / C 端 prize 端点 / 对账 Mapper
- `server/domain-reward/RewardAdminSupportAutoConfiguration.java`：调度 4/5/10
- `server/domain-reward/RewardPortalAutoConfiguration.java`：portal + internal 控制器
- `.kiro/specs/platform-v2/design-algorithm.md`：§5.11 未列组合显式 `action-forbidden`
- `.kiro/specs/platform-v2/tasks.md`（任务 34 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`ClaimAppServiceTest`、`FulfillmentOpsTest`、`ReconAppServiceTest`、`ReconGatesTest`、`SpendAppServiceTest`、`PrizePortalAppServiceTest`；IT：`PrizeClaimExactlyOnceIT`（C-7 / R19.1）、`PrizeExpireIT`（R19.2）、`ReconExhaustiveIT`（R37.1）、`ReconReissueIT`（R37.2）、`ReconReviewGateIT`（R37.3）本机无 Docker 留给 CI
- 未跑项及原因：其余 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 本 PR 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34
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
- 不要在 `task/34-claim-recon` 上继续写任务 35
- 不要做多轮代码评审

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/35-<slug>`（未合则叠在 34 上），做任务 35 积分域
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 本 PR
3. **停止本会话。不要在本分支写 35**
