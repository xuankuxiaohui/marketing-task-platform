# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**19（已勾选）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–19**
- 进行中：无
- 下一步：任务 20（domain-tracking 元数据与调试）
- 代码实况：`domain-tracking` 已实现 `POST /api/common/track/batch`（部分接受、登录/匿名、直写 `evt_event_log` 一行 JSON）、未登记/停用策略（进程内附录 A 默认，不直读 `sys_config`）、admin-app 分区调度 8（`sched:evt-partition`）。服务端事件仍只经 Outbox → `EvtEventLogWriter`（任务 16）。portal-app 已装配 `domain-tracking`；admin-app 运行时依赖改为正式（调度 8）。未做元数据 CRUD / 调试查询（任务 20）
- Git：分支 `task/19-tracking-events`（未提交，基于 `task/18-risk-check` @ `839f541`）。任务 18 仍待人类评审

## 关键技术决策（本轮新发生的）

- 客户端批次一行：`source=CLIENT`，`events` 为 `[{code,props,clientTime}]`；行级 `event_code` = 首条接受事件；任一条未登记则 `registered=0`
- `track.unregistered-policy` / `track.disabled-event-policy` / 批大小 / payload / 限流走 `mkt.track.*`（默认附录 A），不直读 `sys_config`（RL-11 / 任务 24）
- AutoConfiguration 拆分：portal 才扫 `controller.portal`；admin 才挂 `EvtPartitionScheduler`；避免 admin 注册 `/api/common/track/**`
- 调度 8 用 `information_schema` 预建当前+未来 3 个月，按 `retention.event-days` `DROP PARTITION`；不碰 `sys_audit_log`
- 整批非法 JSON：`GlobalExceptionHandler` 映射 `HttpMessageNotReadableException` → `common.param-invalid`（§4.9.3）

## 改过的核心文件

- `server/domain-tracking/**`（新建实现与测试）
- `server/domain-tracking/pom.xml`
- `server/portal-app/pom.xml`、`server/portal-app/src/main/resources/application.yml`
- `server/admin-app/pom.xml`、`server/admin-app/src/main/resources/application.yml`
- `server/platform-kernel/src/main/java/com/mkt/kernel/web/GlobalExceptionHandler.java`
- `.kiro/specs/platform-v2/tasks.md`

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -pl domain-tracking,admin-app -am -DskipITs test` → 通过（domain-tracking 行覆盖 78%）
- 矩阵类：`TrackOutageNonBlockingIT`、`EventImmutabilityArchTest`、`ServerEventTransactionalIT`
- 单测：部分接受、overflow、未登记 accept/reject、停用 drop-count/keep、限流整批拒绝、匿名/登录身份、非法 JSON、分区 ADD/DROP SQL
- `TrackOutageNonBlockingIT` / `ServerEventTransactionalIT`：**本机无 Docker，留给 CI**

## 已知问题（只写已证实）

- 任务 18 是风控路径，仍需人类评审，AI 不得宣称可合
- 策略热切等任务 24；当前为进程内默认
- 登录身份依赖 `UserContext`（任务 21 接线后才有真实会话）
- 行级 `event_code` 只取批次首条；按编码查询透明展开是任务 20
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审

## 尝试过但失败的方案

- 用 `MemoryKeyValueStore.eval` 测滑窗限流：内存实现抛「Lua requires Redis」，限流 fail-open；单测改为 mock `SlidingWindowRateLimiter`

## 明确禁止下一会话做的事

- 不要做任务 20+，除非人类明确要求
- 不要做 `/admin/track/metadata` 与调试查询（任务 20）
- 不要删 `sys_audit_log`、不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要给门户 `/api/common/track/batch` 加 `@Audited`
- 不要建 `web/`、P1 域、`domain-points`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要 commit / push，除非人类明确要求
- 不要自审自合任务 18

## 下一步开发顺序（最多 3 步）

1. 任务 20：domain-tracking 元数据与调试
2. 人类评审任务 18（风控判定链）
3. 任务 21：admin 认证与双账号会话
