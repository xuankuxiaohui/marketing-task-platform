# PROJECT_STATUS
> 阶段：**编码（编组 E 任务 22 已开 PR、未合）** / 当前任务：**22 RBAC 与权限树，未合 master** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–22**（任务 22 仅在 `task/22-rbac-permission-tree`，未合 master）
- 进行中：PR #9 一轮代码评审缺陷 #10/#11 已修并 push；测试 #12、运维 #13 只记账不修；禁止 merge
- 下一步：CI 绿且人类确认后 squash 合 `master`；然后任务 23 用户管理三组端点
- Git：工作分支 `task/22-rbac-permission-tree`，PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 角色/权限写路径走 `IdentityAuditAppender` Outbox（全量 `@Audited` AOP 仍是任务 25）
- `rbac:permission` 用户权限码集，变更 `afterCommit` `evictNamespace`
- 内置 `super-admin`：不可删、不可改权限集、不可停用；P0 操作码并集 = 附录 B 目录 + 树上 ENABLED OPERATION（V1 种子只有 §4.10 菜单）
- 拦截器 403（R2.3）由过滤器补 `audit.log`；只认 `NotPermissionException` 打的 request 标记，CSRF 403 不记 permission-denied
- Cookie 对外仍是 `admin:<raw>`；下游 `SaInterceptor` 看到剥前缀后的 cookie，避免 DAO 键对不上

## 改过的核心文件

- `server/domain-identity/`：`RoleAdminController` / `PermissionAdminController` / `AdminAuthController` menus+profile
- `server/domain-identity/`：`RoleAppService` / `PermissionAppService` / `RbacPermissionCache` / `AdminStpInterface` 经 `AdminUserStore`
- `server/domain-identity/`：`SessionAuthFilter` 剥前缀暴露给 Sa-Token；`SaTokenExceptionHandler` 403/401
- `.kiro/specs/platform-v2/tasks.md`（任务 22 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` **通过**（exit 0）
- 矩阵覆盖：`PermissionUnionPropertyTest`；`PermissionImmediateEffectIT`（集成，CI）
- 未跑项及原因：本机无 Docker，`*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 22 PR #9 评审 #10/#11 已修，测试 #12 / 运维 #13 只记账；禁止 merge 直至人类确认与 CI 通过
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- 任务 23+（用户管理、字典配置缓存、全量 `@Audited` AOP、会话列表踢人管理端）未做

## 尝试过但失败的方案

- 过滤器里 `setTokenValueToStorage(raw)`：Sa-Token 上下文过滤器 `@Order(-104)` 更晚，存储绑定被 `SaTokenContextException` 吞掉；改为向下游暴露剥前缀 cookie

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要做任务 23+，除非人类明确说「继续」
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）

## 下一步开发顺序（最多 3 步）

1. CI 绿且人类确认后 squash 合 `master`（PR #9）
2. 任务 23：用户管理三组端点
3. 任务 24：字典 / 配置 / 缓存管理
