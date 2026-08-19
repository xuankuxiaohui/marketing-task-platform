# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**20（已勾选）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–20**
- 进行中：无
- 下一步：任务 21（admin 认证与双账号会话）
- 代码实况：`domain-tracking` 已有 `/admin/track/metadata` CRUD（与 V4 `evt_event_metadata` 种子联动）、`GET /admin/track/events/debug`（编码/用户/来源/设备/时间；`track.query.sample-ratio-percent` 抽样 + 独立限流桶 `track-query:{operatorId}`）、元数据写路径 `@Audited` + 同事务 `audit.log` Outbox。admin-app 才扫 `controller.admin`。未做任务 21 认证接线
- Git：分支 `task/20-tracking-metadata`（基于 `origin/main` @ `90b6d85`）。任务 18 仍待人类评审

## 关键技术决策（本轮新发生的）

- 调试查询按 `id % 100 < sampleRatio` 抽样（1% × 10,000 行 = 100）；`eventCode` 过滤同时匹配行级 `event_code` 与 JSON 批次 `events[*].code`（透明展开）
- 查询限流独立于上报：`RateLimitDim.USER` + key `track-query:{operatorId}`；附录 A 无专用键，进程内默认 60/min（`mkt.track.query-rate-limit-per-minute`），热切等任务 24
- 元数据 `eventCode` 更新不可改；重复 `track.metadata.duplicate-code`；限流 `track.query.rate-limited`（429）
- 审计与任务 17 相同：`@Audited` 已标，AOP 在任务 25；本任务另写 `TrackAuditAppender` → Outbox `audit.log`

## 改过的核心文件

- `server/domain-tracking/**`（metadata CRUD、debug query、审计、Mapper XML、测试）
- `server/domain-tracking/pom.xml`（补 `sa-token-spring-boot4-starter`）
- `server/admin-app/src/main/resources/application.yml`
- `server/portal-app/src/main/resources/application.yml`
- `.kiro/specs/platform-v2/tasks.md`

## 测试与验证

- `JAVA_HOME` = Temurin 26.0.2；`cd server; mvn -q -DskipITs test` → 通过
- `mvn -pl domain-tracking -am -DskipITs test` → domain-tracking 39 tests, 0 failures（含 metadata CRUD、debug 抽样/展开/限流、EventImmutabilityArchTest）
- 矩阵类：`DebugQueryNoSideEffectIT`（R29.1：查询前后行数+内容哈希一致；1% 抽样 10,000 → total=100）
- `DebugQueryNoSideEffectIT`：**本机无 Docker，留给 CI**

## 已知问题（只写已证实）

- 任务 18 是风控路径，仍需人类评审，AI 不得宣称可合
- 策略/抽样热切等任务 24；当前为进程内 `mkt.track.*` 默认
- 登录身份依赖 `UserContext`（任务 21 接线后才有真实会话）；调试查询限流按 operatorId
- `@Audited` AOP 未接线（任务 25）；本任务写路径同时走 Outbox `audit.log`
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审

## 尝试过但失败的方案

- 无

## 明确禁止下一会话做的事

- 不要做任务 21+，除非人类明确要求
- 不要建 `web/`、P1 域、`domain-points`
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要给门户 `/api/common/track/**` 或 internal 加 `@Audited`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要 push / 合 main，除非人类明确要求
- 不要自审自合任务 18

## 下一步开发顺序（最多 3 步）

1. 任务 21：admin 认证与双账号会话
2. 人类评审任务 18（风控判定链）
3. 任务 22：RBAC 与权限树
