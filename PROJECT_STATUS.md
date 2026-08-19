# PROJECT_STATUS

> 阶段：**编码（编组 C）** / 当前任务：**14（已验收，迁移需人类评审）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–14**
  - 1–12：spike、骨架、kernel、contract
  - 13：`V1__sys_baseline.sql`（sys 12 表 + 超管/菜单 28 / 附录 A 52 / 字典 6 类）
  - 14：V2–V4（task 12 + rwd 7 + pnt 2 + risk 4 + evt 2 = 39 表；分类 7；R-a–R-f；附录 D 元数据 36；首月分区）
- 进行中：无
- 下一步：任务 15 — `platform-infra` 缓存 / 锁 / 限流
- 代码实况：Flyway 仅 admin-app 执行；portal `false`。无 `web/`、无 P1 域表、无 `domain-points`
- Git：分支 `task/14-db-v2-v4`（本提交）。任务 13 已提交于 `task/13-db-v1-sys` @ `f58be8e`

## 关键技术决策（本轮新发生的）

- DDL 按 design §3.3–§3.7 逐字 + 表级 `utf8mb4_0900_ai_ci`（与 V1 同口径）
- MySQL 不支持 `CHECK (...) COMMENT`：`task_crowd.item_count` 只保留表达式，去掉 design 片段里的 COMMENT
- 附录 A 未给 `risk_rule_config.action`：六行种子一律 `REJECT`（列 NOT NULL；R26 拦截语义）；`updated_by=1`（V1 超管）；R-e `window_seconds=NULL`
- `evt_event_log` 首月分区用迁移时 UTC 动态 `pYYYYMM`（`RANGE COLUMNS(server_time) VALUES LESS THAN` 次月 1 日），不写死月份
- 附录 D 全量含 P1 登记行：signin 3 + ad.`<form>` 展开 10（carousel/splash/popup/float/image × exposure/click）；`audit.log` 不进元数据
- `FlywayV1IT` 加 `.target("1")`，避免 classpath 出现 V2–V4 后把 V1 IT 绑死在 4 次 migrate

## 改过的核心文件

- `server/platform-db/src/main/resources/db/migration/V2__task_core.sql`
- `server/platform-db/src/main/resources/db/migration/V3__reward_points.sql`
- `server/platform-db/src/main/resources/db/migration/V4__risk_tracking.sql`
- `server/platform-db/src/test/java/com/mkt/db/FlywayFullIT.java`
- `server/platform-db/src/test/java/com/mkt/db/FullSchemaScriptTest.java`
- `server/platform-db/src/test/java/com/mkt/db/FlywayV1IT.java`（仅 `.target("1")`）
- `server/platform-db/src/test/java/com/mkt/db/V1BaselineScriptTest.java`（读脚本时归一 CRLF，未改 V1 SQL）
- `tasks.md`（仅任务 14 已勾）

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -DskipITs test` → 通过
- `FullSchemaScriptTest` 6、`V1BaselineScriptTest` 4
- `FlywayFullIT` / `FlywayV1IT`：**本机无 Docker，留给 CI**；断言保持完整（39 表、关键 uk、分区 `pYYYYMM`、分类 7、须对账三类 REVIEW、R-a–R-f、元数据 36、`rwd_prize` 无 version、二次 migrate=0）

## 已知问题（已核实）

- **迁移路径：需人类评审，AI 不得宣称可合**（任务 13 + 14）
- ArchUnit 仍不扫 portal-app 生产类
- 本机 `mvn verify` 会跑 `*IT`，无 Docker 会失败；本地请用 `-DskipITs`
- 对账 `CALLBACK_FAILED` / `MANUAL` 未进 §5.11

## 尝试过但失败的方案

- 用 `doesNotContain("version")` 断言 `rwd_prize` 无乐观锁列：表 COMMENT 含 `task_version_snapshot`，误伤；改为列名行正则

## 明确禁止下一会话做的事

- 不要做任务 16+（Outbox / 域实现）
- 不要改已落盘的 `V1`–`V4` 内容（RL-09）；修正只加更高版本
- 不要建 `web/`、P1 域表（sgn/act/ad）、`domain-points`、compile 装配 domain
- 不要用 H2 让 `FlywayFullIT` 本地变绿
- 不要 commit / push，除非人类明确要求

## 下一步开发顺序（最多 3 步）

1. 任务 15：`platform-infra` 缓存 / 锁 / 限流 / 降级（依赖 11）。开工前切 `task/15-<slug>`
2. 任务 16：Outbox Relay
3. 任务 17：domain-risk 名单
