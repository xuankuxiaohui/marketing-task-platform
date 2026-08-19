# PROJECT_STATUS
> 阶段：**编码（编组 E 任务 24 已开 PR、未合）** / 当前任务：**24 字典 / 配置 / 缓存管理，未合 master** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–24**（任务 22/23/24 仅在任务链分支，未合 master）
- 进行中：无（任务 24 已开 PR #18；本会话按指令不启动任务 25）
- 下一步：任务 25 审计 AOP 与会话管理端点（叠在 `task/24-dict-config-cache`）；**禁止 merge**
- Git：工作分支 `task/24-dict-config-cache`（叠在 PR #14 / `task/23-user-management` 上），PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 字典启用项走 `dict` 缓存；类型停用或缺失两侧查询都是空列表（R7.3）
- 配置读写只经 `com.mkt.identity.config`（RL-11）；`getInt` 走 `config` 缓存；写路径 afterCommit evict
- 掩码配置列表回显固定串 `******`；PUT 无 `value` 字段保持原值，null/空串 `common.param-invalid`（R8.2）
- 配置审计摘要带 old→new；仍为掩码项则两边都是掩码串（R8.4 / §6.5）
- 缓存 evict：KEY/PREFIX/NAMESPACE 字段组合校验；`identity:session` → `system.cache.session-forbidden`；`ad:position` 空操作合法
- 全量 `@Audited` AOP 仍是任务 25；本任务写路径仍经 Outbox `audit.log` + 方法上 `@Audited` 标记

## 改过的核心文件

- `server/domain-identity/`：Dict/Config/Cache 管理端点、门户 `GET /api/common/dict/{typeCode}`、`MybatisConfigService` 缓存
- `.kiro/specs/platform-v2/tasks.md`（任务 23、24 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` **通过**（exit 0）
- 矩阵覆盖：`DictCacheConsistencyIT`、`ConfigCacheConsistencyIT`、`CacheEvictConsistencyIT`（集成，CI）
- 未跑项及原因：本机无 Docker，`*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 24 PR #18 一轮评审无必须修项；测试 #19、运维 #20 只记账不修；禁止 merge
- 任务 23 PR #14 一轮评审 #17 已修；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已修，测试 #12 / 运维 #13 只记账；禁止 merge
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- 任务 25（全量 `@Audited` AOP、会话列表踢人）未做

## 尝试过但失败的方案

- 过滤器里 `setTokenValueToStorage(raw)`：Sa-Token 上下文过滤器 `@Order(-104)` 更晚，存储绑定被 `SaTokenContextException` 吞掉；改为向下游暴露剥前缀 cookie

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要做任务 25+，除非人类明确说「继续」
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20
- 不要从 `origin/master` 开新任务分支（夜间不合，必须叠任务链）

## 下一步开发顺序（最多 3 步）

1. 任务 25：审计 AOP 与会话管理端点（叠在 `task/24-dict-config-cache`）
2. CI 绿且人类确认后 squash 合 `master`（PR #9 / #14 / #18）
3. 任务 26：任务定义聚合与表达式引擎
