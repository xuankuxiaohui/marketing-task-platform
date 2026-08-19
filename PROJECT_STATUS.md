# PROJECT_STATUS
> 阶段：**编码（编组 E 任务 21 待人类评审）** / 当前任务：**21 已交付、未合** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–21**（任务 21 仅在 `task/21-admin-auth-session`，未合 master）
- 进行中：无（等人类评审任务 21 会话路径）
- 下一步：人类评审任务 21（AI 不得宣称可合）；然后任务 22 RBAC
- Git：工作分支 `task/21-admin-auth-session`，PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 双 `StpLogic` 用静态持有者 `StpAdmin` / `StpClient`，不注册为 Spring Bean（spike 2）
- 令牌对外带 `admin:` / `client:` 前缀；过滤器剥前缀再查会话
- 后台 Cookie 手写 `Set-Cookie`：会话 HttpOnly+Secure+SameSite=Strict；CSRF Cookie 非 HttpOnly + SameSite=Strict
- 门户注册/登录接已有 `RiskCheckPort`（名单段，REGISTER/LOGIN 不跑 R-a–R-f）
- 登录审计本任务经 Outbox `audit.log` + `AuditLogConsumer` 落 `sys_audit_log`；全量 `@Audited` AOP 仍是任务 25
- 并发上限登录时读附录 A 键（`ConfigService.getInt`），热调下次登录生效
- 踢下线写 `session:kick-reason:{loginType}:{token}`（TTL 60s），过滤器读一次即删；后台 401 统一 `auth.session.invalid`，门户四码 D-02

## 改过的核心文件

- `server/platform-kernel/`：`SessionErrorCodes`、`RateLimitedException`、全局处理器 `Retry-After`
- `server/platform-infra/`：`SaTokenDaoKeyValue`、双 StpLogic、`KickReasonListener`、KeyValue TTL
- `server/domain-identity/`：登录/注册/验证码/CSRF/改密/会话过滤器/审计消费者
- `server/admin-app/pom.xml`、`server/portal-app/pom.xml`：装配 `domain-identity`；门户加 `domain-risk`（RiskCheckPort）
- `.kiro/specs/platform-v2/tasks.md`（任务 21 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` **通过**（exit 0）
- 矩阵覆盖：`LoginLockPropertyTest`（jqwik，200 tries）已跑绿；`LoginAuditIT`、`PasswordChangeSessionIT`、`NamespaceIsolationIT` 已写完整断言
- 未跑项及原因：本机无 Docker，`*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 21 是**会话**路径，需人类评审，AI 不得宣称可合
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- 任务 22+（RBAC 端点、用户管理、全量 `@Audited` AOP、会话列表踢人管理端）未做

## 尝试过但失败的方案

- `Cookie.setAttribute("SameSite","Strict")` 在 MockMvc 的 Set-Cookie 头里不出现，改为手写 `Set-Cookie` 头

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要做任务 22+，除非人类明确说「继续」
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要自审自合任务 21 / 18 / 资金 / 会话 / 风控 / 迁移
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`

## 下一步开发顺序（最多 3 步）

1. 人类评审任务 21（会话 / CSRF / 双 StpLogic / 401 原因码）
2. 任务 22：RBAC 与权限树
3. 人类评审任务 18（风控判定链）
