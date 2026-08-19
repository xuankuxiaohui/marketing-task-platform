# PROJECT_STATUS

> 阶段：**编码（编组 C）** / 当前任务：**15（已验收；会话降级/踢人键需人类评审）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–15**
  - 13–14：Flyway V1–V4（39 表）
  - 15：`platform-infra` — `PlatformCache`（Caffeine L1 + Redis L2 + `cache:evict`）、R9.1 十个命名空间、`afterCommit` evict、锁键族、Lua 滑窗双桶、kick-reason、§6.8 降级矩阵
- 进行中：无
- 下一步：任务 16 — Outbox Relay
- 代码实况：infra 未 compile 装配进两应用（本任务只落模块）。无 `web/`、无 P1 域表、无 `domain-points`
- Git：分支 `task/15-infra-cache-lock-rl`（本提交）。任务 14 已提交 @ `e31cc9c`

## 关键技术决策（本轮新发生的）

- R9.2 码 `system.cache.session-forbidden` 要求 `system` 域：已写入 kernel `ErrorSegments`（requirements > design §3.9 未列 system）
- `ad:position` 只登记：put/evict 空操作，stats=0/N/A，不写 L2
- `identity:session` 任何 evict 抛 `BusinessException(SESSION_FORBIDDEN)`，stats=N/A
- Redis 默认 DB **2**（`InfraRedisProperties` 把 ≤0 收成 2）
- Redisson 手动装配 + `StringCodec`（与 spike 1 一致）；Lua 脚本 `infra/rate-limit.lua`
- 领取锁 Redis 不可用 → `LockAcquire.DEGRADED`（调用方 CAS）；限流 fail-open；nonce / 会话 Redis 故障拒绝
- 未接 `@RateLimit` 拦截器 / ConfigService 热读（无端点，留给 21/24）

## 改过的核心文件

- `server/platform-infra/`（cache / lock / ratelimit / session / nonce / degrade / redis）
- `server/platform-kernel/.../ErrorSegments.java` + `ErrorCodeFormatTest`
- `server/pom.xml`（enforcer 放行 caffeine / spring-tx）
- `dependency-matrix.md`、`tasks.md`（仅 15 已勾）

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -DskipITs test` → 通过
- infra 单测 17（命名空间、session 禁 evict、ad 占位、afterCommit、锁、kick-reason、nonce、降级矩阵、限流 fail-open）
- `CacheEvictBroadcastIT` / `RateLimitLuaIT` / `DegradeMatrixIT`：**本机无 Docker，留给 CI**；断言保持完整（双实例广播、10 次恰 5 过、登录双桶、pause Redis 后会话拒/限流放/nonce 拒）

## 已知问题（已核实）

- **会话路径：需人类评审，AI 不得宣称可合**（kick-reason + 会话 Redis 故障拒绝）
- **迁移路径：任务 13/14 仍需人类评审**
- ArchUnit 仍不扫 portal-app 生产类
- 本机 `mvn verify` 会跑 `*IT`，无 Docker 会失败
- 对账 `CALLBACK_FAILED` / `MANUAL` 未进 §5.11

## 尝试过但失败的方案

- `TransactionSynchronizationManager.initSynchronization()` 不会让 `isActualTransactionActive()` 为 true；测试需再 `setActualTransactionActive(true)`
- `RBucket.setIfAbsent(value, long, TimeUnit)` 在 Redisson 4.6.1 不存在，改为 `Duration`

## 明确禁止下一会话做的事

- 不要做任务 17+（域实现）
- 不要改已落盘的 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2 / 接广告
- 不要建 `web/`、P1 域、`domain-points`、compile 装配 domain
- 不要用 Embedded Redis / H2 让 IT 本地变绿
- 不要 commit / push，除非人类明确要求

## 下一步开发顺序（最多 3 步）

1. 任务 16：Outbox Relay（依赖 12、13、15）。开工前切 `task/16-<slug>`
2. 任务 17：domain-risk 名单
3. 任务 18：风控判定链
