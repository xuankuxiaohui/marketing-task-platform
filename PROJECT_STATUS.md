# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**19（已勾选）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–19**
- 进行中：CI 修复分支 `bug/1-flyway-v1it`（PR #2 → main）。FlywayV1IT / OutboxIdempotentInsertIT / CaseHandleAuditIT / ListConcurrentDecisionIT（C-12）已绿且未回退。本轮修 `OpenApiGroupsIT`：无 DataSource，排除 risk/tracking 自动配置
- 下一步：等 PR #2 CI。不要做任务 20/21，除非人类明确要求
- 代码实况：`domain-tracking` 已实现 `POST /api/common/track/batch`；服务端事件仍经 Outbox → `EvtEventLogWriter`。portal-app 已装配 `domain-tracking`。未做元数据 CRUD / 调试查询（任务 20）
- Git：分支 `bug/1-flyway-v1it`（基于 `main` @ `90b6d85`）。未合 main / master

## 关键技术决策（本轮新发生的）

- `OpenApiGroupsIT` 只断言三组 OpenAPI JSON，不需要风控/埋点/DB。用 `spring.autoconfigure.exclude` 排除 `RiskAutoConfiguration`、`RiskAdminAutoConfiguration`、`TrackingAutoConfiguration`、`TrackingAdminAutoConfiguration`、`TrackingPortalAutoConfiguration`
- 未改三个 group 的断言（admin 仍不含 `/api/` `/internal/`），未改名单判定，未回退 Flyway/Outbox/C-12，未改 AutoConfiguration 实现

## 改过的核心文件

- `server/admin-app/src/test/java/com/mkt/admin/OpenApiGroupsIT.java`
- `server/portal-app/src/test/java/com/mkt/portal/OpenApiGroupsIT.java`

## 测试与验证

- `cd server; mvn -q -pl admin-app,portal-app -am test-compile -DskipTests` → 通过
- `OpenApiGroupsIT` / 其它 `*IT`：**本机无 Docker，留给 CI**
- 三个 group 断言未削弱

## 已知问题（只写已证实）

- 任务 18 是风控路径，仍需人类评审，AI 不得宣称可合
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- run 32252606577：`OpenApiGroupsIT` 因 Boot 4 `OnBean`+`ComponentScan` 起不来上下文
- 随后去掉类级 OnBean 只留扫描：无 DataSource 仍扫进 `RiskHitLogMapper`，要 `sqlSessionFactory`
- PersistenceScan `@Import` 在 Boot 4 OnBean 未成立时仍可能扫进 application store，Mapper 却没建出 → `MybatisRiskHitLogStore` 缺 `RiskHitLogMapper`

## 尝试过但失败的方案

- 依赖复合主键 `(id, server_time)` 去重：`consume` 每次用 `clock.instant()`，重试写第二行
- 给 `id` 加唯一约束：分区表不允许不含 `server_time` 的 UNIQUE，未加 V5
- 未把 hits 查询改成 `occurred_at`：规格索引口径是 `created_at`
- 未放宽 C-12 的 ROUNDS / DECISIONS / 仅 PASS/REJECT / 单轮 <5s，也未把 `Future.get` 调松当绿
- 去掉类级 `@ConditionalOnBean(DataSource)`、扫描留在 AutoConfiguration：无 DataSource 仍注册 Mapper，OpenApiGroupsIT 要 sqlSessionFactory
- `RiskAutoConfiguration` `@Import(RiskPersistenceScan)` 指望 OnBean 挡住扫描：Boot 4 OnBean 未成立时仍可能扫进 store、扫不出 mapper

## 明确禁止下一会话做的事

- 不要做任务 20+，除非人类明确要求
- 不要做 `/admin/track/metadata` 与调试查询（任务 20）
- 不要改 V1–V4，不要为幂等去分区
- 不要削弱 `OutboxIdempotentInsertIT`、`CaseHandleAuditIT.total==1`、C-12 无第三态 / ROUNDS / DECISIONS / 单轮 <5s
- 不要把 `pageHits` 时间窗改成 `occurred_at`
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要建 `web/`、P1 域、`domain-points`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要合 main / master
- 不要自审自合任务 18
- 不要回退 05df7e0 / 3671389 / 120647e / 1987478 / 6d7ecd6 / a7e5ef6
- 不要再给带 `@ComponentScan` 的配置类加类级 `@ConditionalOnBean`
- 不要再改名单判定 / 批查
- 不要回退 PersistenceScan 后再把扫描加回 AutoConfiguration
- 不要削弱三个 OpenAPI group 断言（含 admin 不含 `/api/` `/internal/`）

## 下一步开发顺序（最多 3 步）

1. 等 PR #2 CI（`OpenApiGroupsIT` 排除 risk/tracking 后能起上下文）
2. 人类评审任务 18（风控判定链）
3. 任务 20：domain-tracking 元数据与调试（人类明确要求后再做）
