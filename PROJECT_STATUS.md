# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**19（已勾选）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–19**
- 进行中：CI 修复分支 `bug/1-flyway-v1it`（PR #2 → main）。上一轮 FlywayV1IT / OutboxIdempotentInsertIT 已绿且未回退。本轮修 `CaseHandleAuditIT`：INSERT 同时写 `created_at=start`，查询仍按 `created_at`，`total==1` 未削弱
- 下一步：等 PR #2 CI。不要做任务 20/21，除非人类明确要求
- 代码实况：`domain-tracking` 已实现 `POST /api/common/track/batch`；服务端事件仍经 Outbox → `EvtEventLogWriter`。portal-app 已装配 `domain-tracking`。未做元数据 CRUD / 调试查询（任务 20）
- Git：分支 `bug/1-flyway-v1it`（基于 `main` @ `90b6d85`）。未合 main / master

## 关键技术决策（本轮新发生的）

- `GET /admin/risk/hits` 时间窗按 `created_at`（design-schema `idx_user_time` / `idx_rule_time`，R27.3/R27.4；`RiskHitLogMapper.xml queryWhere`）。测试种子必须写 `created_at`，不能只写 `occurred_at` 指望 `CURRENT_TIMESTAMP` 落进 `start±1s`
- C-12 `ListConcurrentDecisionIT` 本轮不改：`Future.get(8s)` 超时发生时该轮已超过 design §7.4「单轮 <5s」；ROUNDS=20、DECISIONS=200、仅 PASS/REJECT 均为规格，不放松。CI 里 RedisTimeout / connection pool 视为环境偶发，留给下一轮 CI

## 改过的核心文件

- `server/domain-risk/src/test/java/com/mkt/risk/CaseHandleAuditIT.java`
- 上一轮未回退：`FlywayV1IT` / `TinyintFlags` / `TinyintOneOrTrueTest`；`EvtEventLogWriter` + `EvtEventLogWriterTest`

## 测试与验证

- `cd server; mvn -q -pl domain-risk -am -DskipITs test` → 通过
- `RiskCaseAppServiceTest`：8 tests, 0 failures（含 `pageHitsFilters`）
- `ListDecisionPropertyTest` / `RuleDeterminismPropertyTest`：通过
- `CaseHandleAuditIT` / `ListConcurrentDecisionIT` / `FlywayV1IT` / `OutboxIdempotentInsertIT`：**本机无 Docker，留给 CI**；IT 断言仍完整（hits.total==1；C-12 无第三态 + 每轮 <5s）

## 已知问题（只写已证实）

- 任务 18 是风控路径，仍需人类评审，AI 不得宣称可合
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- `ListConcurrentDecisionIT` 在 run 32247731854 于 `decided.get(8s)` 超时；未改该文件

## 尝试过但失败的方案

- 依赖复合主键 `(id, server_time)` 去重：`consume` 每次用 `clock.instant()`，重试写第二行
- 给 `id` 加唯一约束：分区表不允许不含 `server_time` 的 UNIQUE，未加 V5
- 未把 hits 查询改成 `occurred_at`：规格索引口径是 `created_at`

## 明确禁止下一会话做的事

- 不要做任务 20+，除非人类明确要求
- 不要做 `/admin/track/metadata` 与调试查询（任务 20）
- 不要改 V1–V4，不要为幂等去分区
- 不要削弱 `OutboxIdempotentInsertIT`、`CaseHandleAuditIT.total==1`、C-12 无第三态 / ROUNDS / DECISIONS 断言
- 不要把 `pageHits` 时间窗改成 `occurred_at`
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要建 `web/`、P1 域、`domain-points`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要合 main / master
- 不要自审自合任务 18

## 下一步开发顺序（最多 3 步）

1. 等 PR #2 CI（CaseHandleAuditIT + 观察 ListConcurrentDecisionIT 是否仍 Redis 超时）
2. 人类评审任务 18（风控判定链）
3. 任务 20：domain-tracking 元数据与调试（人类明确要求后再做）
