# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**19（已勾选）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–19**
- 进行中：CI 修复分支 `bug/1-flyway-v1it`（PR #2 → main）。FlywayV1IT 已绿；本轮修 `OutboxIdempotentInsertIT`（`EvtEventLogWriter` 先按 id 查询再插入）
- 下一步：等 PR #2 CI。不要做任务 20，除非人类明确要求
- 代码实况：`domain-tracking` 已实现 `POST /api/common/track/batch`；服务端事件仍经 Outbox → `EvtEventLogWriter`。portal-app 已装配 `domain-tracking`。未做元数据 CRUD / 调试查询（任务 20）
- Git：分支 `bug/1-flyway-v1it`（基于 `main` @ `90b6d85`）。未合 main / master

## 关键技术决策（本轮新发生的）

- `evt_event_log` 按 `server_time` 月 RANGE 分区，主键是 `(id, server_time)`。同 `id`、不同 `server_time` 的第二次 INSERT **不会** `DuplicateKeyException`，于是变成 2 行
- 不能加 `UNIQUE(id)`：MySQL 分区表唯一键必须含分区键。未加 V5，未改 V1–V4
- 幂等改为先 `COUNT(*) WHERE id=?`，已有行则当成功；`DuplicateKeyException` 仍当成功（同毫秒碰撞）

## 改过的核心文件

- `server/platform-db/src/test/java/com/mkt/db/FlywayV1IT.java`、`TinyintFlags.java`、`TinyintOneOrTrueTest.java`（上一提交，Fixes #1）
- `server/platform-infra/src/main/java/com/mkt/infra/outbox/EvtEventLogWriter.java`
- `server/platform-infra/src/test/java/com/mkt/infra/outbox/EvtEventLogWriterTest.java`

## 测试与验证

- `cd server; mvn -q -pl platform-infra -am -DskipITs test` → 通过
- `EvtEventLogWriterTest`：3 tests, 0 failures（含已存在则跳过 INSERT）
- `platform-db` 单测：`TinyintOneOrTrueTest` / `FullSchemaScriptTest` / `V1BaselineScriptTest` 通过
- `OutboxIdempotentInsertIT` / `FlywayV1IT`：**本机无 Docker，留给 CI**；IT 断言仍是 `COUNT(*) = 1`

## 已知问题（只写已证实）

- 任务 18 是风控路径，仍需人类评审，AI 不得宣称可合
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证

## 尝试过但失败的方案

- 依赖复合主键 `(id, server_time)` 去重：`consume` 每次用 `clock.instant()`，重试写第二行
- 给 `id` 加唯一约束：分区表不允许不含 `server_time` 的 UNIQUE，未加 V5

## 明确禁止下一会话做的事

- 不要做任务 20+，除非人类明确要求
- 不要做 `/admin/track/metadata` 与调试查询（任务 20）
- 不要改 V1–V4，不要为幂等去分区
- 不要削弱 `OutboxIdempotentInsertIT` 断言
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要建 `web/`、P1 域、`domain-points`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要合 main / master
- 不要自审自合任务 18

## 下一步开发顺序（最多 3 步）

1. 等 PR #2 CI（FlywayV1IT + OutboxIdempotentInsertIT）
2. 人类评审任务 18（风控判定链）
3. 任务 20：domain-tracking 元数据与调试（人类明确要求后再做）
