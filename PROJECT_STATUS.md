# PROJECT_STATUS

> 阶段：**编码（编组 C）** / 当前任务：**16（已验收）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–16**（编组 C 契约/数据/基础设施收口）
- 进行中：无
- 下一步：任务 17 — domain-risk 名单
- 代码实况：infra 含缓存/锁/限流/Outbox。两应用已设 `mkt.outbox.producer`。portal 补了 datasource（flyway 仍 false）。无 `web/`、无 P1 域、无 `domain-points`、未 compile 装配 domain
- Git：分支 `task/16-infra-outbox`（本提交）。任务 15 已提交 @ `658ce68`

## 关键技术决策（本轮新发生的）

- infra **不**依赖 platform-contract：路由表字符串写在 `OutboxRoutes`（与 D-05 对齐）
- `risk:cnt` / `sys_audit_log` 消费 Bean 本任务未实现（18 / 25）；声明了方向但缺 Bean → 不 DELETE，重试 + `outbox.missing.consumer`
- 本任务只落 `EvtEventLogWriter`（id = outbox.id，主键冲突当成功）
- Relay 5s 调度；锁 `outbox:relay:{admin|portal}`；SELECT 必带 `producer=:self`

## 改过的核心文件

- `server/platform-infra/src/main/java/com/mkt/infra/outbox/`
- `server/platform-infra/src/main/java/com/mkt/infra/InfraAutoConfiguration.java`
- `server/admin-app` / `portal-app` `application.yml`（producer；portal 数据源）
- `tasks.md`（仅 16 已勾）

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -DskipITs test` → 通过
- 单测：`EventPublisherTest`、`OutboxRelayTest`、`OutboxRoutesTest`、`OutboxBackoffTest`、`EvtEventLogWriterTest` 等
- `OutboxRollbackIT` / `OutboxRelayRetryIT` / `OutboxIdempotentInsertIT`：**本机无 Docker，留给 CI**

## 已知问题（已核实）

- **会话路径（任务 15）与迁移（13/14）仍需人类评审**
- 缺 `risk:cnt` / `sys_audit_log` Bean 时对应事件会重试至 DEAD（等 18 / 25）
- ArchUnit 仍不扫 portal-app 生产类
- 对账 `CALLBACK_FAILED` / `MANUAL` 未进 §5.11

## 尝试过但失败的方案

- `AwaitOutboxDrain` / `MemoryOutboxStore` 用 `Instant.now()` 触发 AT-C01；改为注入 `Clock`

## 明确禁止下一会话做的事

- 不要做任务 18+（判定链 / identity / task）
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要建 `web/`、P1 域、`domain-points`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要 commit / push，除非人类明确要求

## 下一步开发顺序（最多 3 步）

1. 任务 17：domain-risk 名单（依赖 14、15、16）。开工前切 `task/17-<slug>`
2. 任务 18：风控判定链
3. 任务 19：domain-tracking 上报
