# PROJECT_STATUS
> 阶段：**编码（编组 E 任务 23 已开 PR、未合）** / 当前任务：**24 字典 / 配置 / 缓存管理，未合 master** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–23**（任务 22/23 仅在任务链分支，未合 master）
- 进行中：任务 24 字典 / 配置 / 缓存管理；从 `task/23-user-management` 叠出，禁止从 `origin/master` 开
- 下一步：实现 §4.3 字典 / 配置 / 缓存端点；**禁止 merge**
- Git：工作分支 `task/24-dict-config-cache`（叠在 PR #14 / `task/23-user-management` 上），PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 门户档案全量覆盖：`province` / `userLevel` / `userRole` / `tags` / `orgId` 用 `FieldStrategy.ALWAYS`，省略字段落 NULL（R5.3）
- `UserAttributes.orgId` 是 string（R5.3 / §5.10 `orgId() → string`），合法值如 `org-north_01`
- 种子超管 `username=admin`：PUT 不可拆掉内置角色；disable/delete 不看角色是否已空（R3.4）
- R5.6 详情聚合走 `RewardPort` / `RiskCheckPort` / `TaskReadPort`（reward/task 替身；禁止直查他域表）
- internal-app secret AES-256-GCM，主密钥 `MKT_INTERNAL_APP_AES_KEY`；会话失效走 `StpLogic.logout(userId)`，未 evict `identity:session`

## 改过的核心文件

- `server/domain-identity/`：admin-user / portal-user / internal-apps 三组端点与 `UserAttributePortImpl`
- `server/platform-contract/`：`UserAttributes.orgId` Long → String
- `.kiro/specs/platform-v2/tasks.md`（任务 23 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` **通过**（exit 0）
- 矩阵覆盖：`AdminUsernameUniqueIT`、`ProfileEffectVisibilityIT`、`InternalAppSecretIT`（集成，CI）
- 未跑项及原因：本机无 Docker，`*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 23 PR #14 一轮评审 #17 已修并 push；测试 #15、运维 #16 只记账不修；禁止 merge 直至人类确认与 CI 通过
- 任务 22 PR #9 评审 #10/#11 已修，测试 #12 / 运维 #13 只记账；禁止 merge
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- 任务 24+（字典配置缓存、全量 `@Audited` AOP、会话列表踢人管理端）未做完

## 尝试过但失败的方案

- 过滤器里 `setTokenValueToStorage(raw)`：Sa-Token 上下文过滤器 `@Order(-104)` 更晚，存储绑定被 `SaTokenContextException` 吞掉；改为向下游暴露剥前缀 cookie

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要做任务 25+，除非人类明确说「继续」或夜间循环已完成 24
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16

## 下一步开发顺序（最多 3 步）

1. 任务 24：字典 / 配置 / 缓存管理（叠在 `task/23-user-management`）
2. 任务 25：审计 AOP 与会话管理端点
3. CI 绿且人类确认后 squash 合 `master`（PR #9 / #14 / 后续）
