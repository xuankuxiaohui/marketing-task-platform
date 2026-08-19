# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**19（已勾选）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–19**
- 进行中：CI 修复分支 `bug/1-flyway-v1it`（PR #2 → main）。FlywayV1IT / OutboxIdempotentInsertIT / CaseHandleAuditIT 已绿且未回退。本轮修 `ListConcurrentDecisionIT.noThirdStateWhileMutating`：第一轮 `decided.get(8s)` 超时
- 下一步：等 PR #2 CI。不要做任务 20/21，除非人类明确要求
- 代码实况：`domain-tracking` 已实现 `POST /api/common/track/batch`；服务端事件仍经 Outbox → `EvtEventLogWriter`。portal-app 已装配 `domain-tracking`。未做元数据 CRUD / 调试查询（任务 20）
- Git：分支 `bug/1-flyway-v1it`（基于 `main` @ `90b6d85`）。未合 main / master

## 关键技术决策（本轮新发生的）

- C-12 第一轮 >8s **不是**判定死锁 / MySQL 行锁：`getByUk` 是普通 SELECT（MVCC），决定线程不等待 import/remove 事务。CI 无 `risk:list redis get failed` 日志，因为 `Future.get(8s)` 先于 Redisson 默认 3000ms×4 次重试抛出
- 实测路径：每轮 200 次 `decide()` × 6 维，串行 Redis GET + `DriverManagerDataSource` 每次 `getByUk` 新建 JDBC 连接。约 1200 次握手在 GHA 上 ≥8s。`InfraRedisProperties` 第 4 参是 Redis DB index（默认 2），不是连接池大小；Redisson 默认 `connectionPoolSize=64` 恰好等于 lost-window 64 路
- 修复（未改 PASS/REJECT 规则，未减 ROUNDS/DECISIONS，未放宽 <5s）：Redisson 池 128 + 命令超时 1s/重试 1 次；名单快照 1 次 MGET + 1 次 UK IN；risk IT 改 Hikari（maximumPoolSize=64）

## 改过的核心文件

- `server/platform-infra/src/main/java/com/mkt/infra/redis/RedissonFactory.java`
- `server/platform-infra/src/main/java/com/mkt/infra/redis/KeyValueStore.java`
- `server/platform-infra/src/main/java/com/mkt/infra/redis/RedissonKeyValueStore.java`
- `server/domain-risk/src/main/java/com/mkt/risk/support/RiskListProjection.java`
- `server/domain-risk/src/main/java/com/mkt/risk/support/ListLookup.java`
- `server/domain-risk/src/main/resources/mapper/risk/RiskListItemMapper.xml`
- `server/domain-risk/src/test/java/com/mkt/risk/it/RiskITSupport.java`
- 上一轮未回退：`FlywayV1IT` / `TinyintFlags`；`EvtEventLogWriter`；`CaseHandleAuditIT` 种子 `created_at`

## 测试与验证

- `cd server; mvn -q -pl platform-infra,domain-risk -am -DskipITs test` → 通过
- `platform-infra`：34 tests, 0 failures（含 `RedissonFactoryTest`、`MemoryKeyValueStoreGetManyTest`）
- `domain-risk`：87 tests, 0 failures（含 `RiskListProjectionTest` 4 tests、`ListLookupTest`、`ListDecisionPropertyTest`、`RuleDeterminismPropertyTest`）
- `ListConcurrentDecisionIT` / 其它 `*IT`：**本机无 Docker，留给 CI**；C-12 断言仍完整（ROUNDS=20、DECISIONS=200、仅 PASS/REJECT、单轮 <5s）

## 已知问题（只写已证实）

- 任务 18 是风控路径，仍需人类评审，AI 不得宣称可合
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证
- `ListConcurrentDecisionIT` 在 run 32247731854 / 32249438595 于 `decided.get(8s)` 超时；本轮已按上列根因修改，待 CI 复验

## 尝试过但失败的方案

- 依赖复合主键 `(id, server_time)` 去重：`consume` 每次用 `clock.instant()`，重试写第二行
- 给 `id` 加唯一约束：分区表不允许不含 `server_time` 的 UNIQUE，未加 V5
- 未把 hits 查询改成 `occurred_at`：规格索引口径是 `created_at`
- 未放宽 C-12 的 ROUNDS / DECISIONS / 仅 PASS/REJECT / 单轮 <5s，也未把 `Future.get` 调松当绿

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
- 不要回退 05df7e0 / 3671389 / 120647e

## 下一步开发顺序（最多 3 步）

1. 等 PR #2 CI（`ListConcurrentDecisionIT` 单轮 <5s）
2. 人类评审任务 18（风控判定链）
3. 任务 20：domain-tracking 元数据与调试（人类明确要求后再做）
