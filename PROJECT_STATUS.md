# PROJECT_STATUS
> 阶段：**编码（编组 F 任务 30 已交付、未合 master）** / 当前任务：**30 internal 回调，未合 master** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–30**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 在 `task/29-step-engine` PR #38，未合；任务 30 仅在 `task/30-internal-callback`，未合 master）
- 进行中：无（任务 30 在 `task/30-internal-callback`）
- 下一步：任务 31 实例管理与平台动作（从本 tip 开 `task/31-instance-admin`）；**禁止 merge**
- Git：工作分支 `task/30-internal-callback`（基线 `task/29-step-engine` @ `cbe2c39`）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- HMAC §4.8：`stringToSign = METHOD\nPATH?query\nTIMESTAMP\nNONCE\nHEX(SHA-256(body))`；空 body = SHA-256(空串)；`X-Sign` 小写 hex；`X-Trace-Id` 不进签名串
- 时间戳容差 `internal.timestamp.tolerance-seconds`（默认 ±300s）；nonce `SETNX` + TTL `internal.nonce.ttl-seconds`（默认 600s）；Redis 故障 nonce **拒绝**（不 fail-open）；比较 `MessageDigest.isEqual`
- `sys_internal_app`：未知 `internal.app.not-found`、DISABLED `internal.app.disabled`；轮换双密钥 24h 窗口内新旧同验签；portal 内存缓存 5min
- appId 限流 `ratelimit.internal.accesskey.per-second`（默认 200/s，Redis 故障 fail-open）；超限 429 `common.rate-limited` + `Retry-After`
- `/internal/**` 跳过会话鉴权，HMAC 过滤器拦截；`POST /internal/task/callback|progress` 不标 `@Audited`
- `last_biz_no` 多次 CALLBACK 覆盖为最新（含已完成幂等重投）

## 改过的核心文件

- `server/domain-identity/`：`InternalHmacVerifier`、`InternalAuthFilter`、`InternalAppSecretCache`、`AnonymousPaths` 放行 `/internal/**`、portal 装配 HMAC 过滤器
- `server/domain-task/`：`TaskInternalController`（callback / progress）
- `server/portal-app/src/test/resources/internal-attack/` + `ReplayAttackIT`（A-01~A-06）
- `.kiro/specs/platform-v2/tasks.md`（任务 30 勾选）

## 测试与验证

- 命令与结果：`cd server; mvn -q -DskipITs test` 本机 exit 0
- 矩阵覆盖：`InternalHmacVerifierTest`、`InternalAuthFilterTest`、`InternalHmacsTest`、`TaskInternalControllerTest`；IT：`ReplayAttackIT`（A-01~A-06，本机 failsafe 9/9）
- 未跑项及原因：其余 `*IT` 留给 CI（未削弱断言，未用 H2 / Embedded Redis）

## 已知问题（只写已证实）

- 任务 29 PR #38 未合 master；任务 30 叠在 29 tip
- 任务 28 已合 master（#36）；评审 #37、测试 #34、运维 #35 只记账不修
- 任务 27 PR #32 一轮评审 #33 已处理；测试 #30、运维 #31 只记账不修
- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机无 Docker，依赖 Testcontainers 的 `*IT` 只能在 CI 验证
- 实例过期翻转调度 2 / 后台实例管理属任务 31
- portal-app 未装配 `domain-reward`（发奖任务 40）；identity `RewardPortStub.grant` 仍抛 `UnsupportedOperationException`
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
- 不要从 `origin/master` 开任务 31（30 未合，必须从 `task/30-internal-callback` tip 叠出）
- 不要把任务 31 写进本分支；不要做任务 32+
- 不要做多轮代码评审

## 下一步开发顺序（最多 3 步）

1. 任务 31：实例管理与平台动作，从 `task/30-internal-callback` tip 开 `task/31-instance-admin`；做完停
2. CI 绿且人类确认后 squash 合 `master`（先 29 PR #38，再 30）
3. 编组 F 待验收；不要做 32+
