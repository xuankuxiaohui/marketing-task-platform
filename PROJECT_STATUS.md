# PROJECT_STATUS
> 阶段：**编码（编组 F 任务 27 已交付、未合 master）** / 当前任务：**27 发布版本与定时发布，未合 master** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–27**（任务 22/23/24/25/26/27 仅在任务链分支，未合 master）
- 进行中：无（任务 27 在 `task/27-publish-schedule`）
- 下一步：任务 28 可见性与领取（叠在 `task/27-publish-schedule`）；**禁止 merge**
- Git：工作分支 `task/27-publish-schedule`（叠任务 22–26）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- D-01 单套编辑态 + `pending_revision`；PUBLISHED/SCHEDULED 保存不改主状态
- SCHEDULED 且 `early` 缺省只清修订；`early=true` 与到点调度同样校验 + 快照 + version+1
- 发布 CAS：`UPDATE … WHERE status/version/pending_revision` 匹配才固化
- REWARD 启用检查走 `RewardPort.prizeEnabled`（D-13 只读），task 不直查 `rwd_prize`
- 掩码配置提交 `******` 视为未改 value（R8.2）
- 两级缓存 L1 `expireAfterWrite` = 该 ns 的 TTL；Redis 库号钉死 2
- OpenAPI 三分组隔离测在 `platform-kernel`；`gen:api` 仍打两应用命名空间，任务 36 才建 `web/`

## 改过的核心文件

- `server/domain-task/`：发布、快照、调度 1、CAS、RewardPort 查找
- `server/platform-contract/RewardPort.java`：`prizeEnabled`
- `server/platform-infra/`：L1 TTL、Redis DB 2
- `server/domain-identity/`：掩码写回、`ClientIp` 只用 remoteAddr
- `.kiro/specs/platform-v2/tasks.md`（任务 27 勾选）

## 测试与验证

- 命令与结果：见本会话 `mvn -q -DskipITs test`
- 矩阵覆盖：`SnapshotImmutabilityIT`、`RevisionIsolationIT`、`BatchPublishAtomicIT`（5 合法 + 5 类非法）、`TaskPublishAppServiceTest`、`ExpressionSandboxMaliciousTest`
- 未跑项及原因：本机无 Docker，`*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修；禁止 merge
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账；禁止 merge
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- `SnapshotImmutabilityIT` / `RevisionIsolationIT` 断言快照行与绑定 id；C 端按快照渲染/推进属任务 28/29
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
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27
- 不要从 `origin/master` 开新任务分支（夜间不合，必须叠任务链）
- 不要做任务 28（可见性/领取）以外的后续任务，除非 27 已完整交付

## 下一步开发顺序（最多 3 步）

1. CI 绿且人类确认后 squash 合 `master`（PR #9 / #14 / #18 / #23 / #28 及任务 27 PR）
2. 任务 28：可见性与领取（叠在 `task/27-publish-schedule`）
3. 任务 29：步骤引擎
