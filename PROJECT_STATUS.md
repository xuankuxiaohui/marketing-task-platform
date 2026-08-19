# PROJECT_STATUS
> 阶段：**编码（编组 E 任务 21 评审修复已 push、未合）** / 当前任务：**21 修 PR #4 issue #6/#7，未合 master** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–21**（任务 21 仅在 `task/21-admin-auth-session`，未合 master）
- 进行中：PR #4 评审缺陷 #6 / #7 已修并 push；禁止 merge
- 下一步：对 PR #4 做下一轮代码评审；通过后再 squash 合 `master`；然后任务 22 RBAC
- Git：工作分支 `task/21-admin-auth-session`，PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 双 `StpLogic` 用静态持有者 `StpAdmin` / `StpClient`，不注册为 Spring Bean（spike 2）
- 令牌对外带 `admin:` / `client:` 前缀；过滤器剥前缀再查会话
- 后台 Cookie 手写 `Set-Cookie`：会话 HttpOnly+Secure+SameSite=Strict；CSRF Cookie 非 HttpOnly + SameSite=Strict
- 门户注册/登录接已有 `RiskCheckPort`（名单段，REGISTER/LOGIN 不跑 R-a–R-f）
- 登录审计本任务经 Outbox `audit.log` + `AuditLogConsumer` 落 `sys_audit_log`；全量 `@Audited` AOP 仍是任务 25
- 并发上限登录时读附录 A 键（`ConfigService.getInt`），热调下次登录生效
- 踢下线写 `session:kick-reason:{loginType}:{token}`（TTL 60s），必登路径读一次即删；可选鉴权只 peek 不删
- 锁期满 `failedAttempts` 清零再计（R1.3）
- 纯匿名入口（login/captcha/register/username-available）忽略被踢/过期/无效同侧令牌；可选鉴权（track/batch、ad/positions）有效同侧令牌 `UserContext.set` 并续期，被踢/过期当匿名放行，交叉令牌仍 401
- 活跃后台请求同时刷新 satoken 与现有 csrfToken 的 Max-Age=1800，不发新 CSRF 值（R1.8 / R1.13）
- 用户名写入 Sa-Token terminal extra + account session，过滤器构造 `UserPrincipal` 第三字段为用户名
- 失败登录 `operator_id` 为空；摘要 `JsonUtil`；锁定期先 423、不消耗验证码

## 改过的核心文件

- `server/domain-identity/`：`AnonymousPaths`、`SessionAuthFilter` 及对应测试
- `server/platform-infra/`：`KickReasonStore.peek` / `peekQuiet`
- `server/portal-app/`：`NamespaceIsolationIT`（增 `ad/positions` 交叉令牌 401，未削弱原断言）
- `.kiro/specs/platform-v2/tasks.md`（任务 21 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` **通过**（exit 0）
- 矩阵覆盖：`LoginLockPropertyTest`（含锁期满清零、时钟前进）；`LoginAuditIT`、`PasswordChangeSessionIT`、`NamespaceIsolationIT` 交叉令牌 401 未削弱
- 未跑项及原因：本机无 Docker，`*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 21 PR #4 评审 #6/#7 已修，禁止 merge 直至评审与 CI 通过
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- 任务 22+（RBAC 端点、用户管理、全量 `@Audited` AOP、会话列表踢人管理端）未做

## 尝试过但失败的方案

- `Cookie.setAttribute("SameSite","Strict")` 在 MockMvc 的 Set-Cookie 头里不出现，改为手写 `Set-Cookie` 头
- `StpLogic.getExtra` 仅 JWT 模式可用；用户名改写 terminal extra + account session

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master，直到两轮评审结束且 CI 绿
- 不要做任务 22+，除非人类明确说「继续」
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 `NamespaceIsolationIT` 交叉令牌 401 断言
- 不要削弱 CSRF 双重提交断言

## 下一步开发顺序（最多 3 步）

1. 对 PR #4 继续代码评审；有问题开 issue 并修
2. CI 绿且评审通过后 squash 合 `master`
3. 任务 22：RBAC 与权限树
