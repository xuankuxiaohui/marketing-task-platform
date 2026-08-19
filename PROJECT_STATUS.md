# PROJECT_STATUS

> 阶段：**编码（编组 C）** / 当前任务：**13（已验收，迁移需人类评审）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–13**
  - 1–12：spike、骨架、kernel、contract
  - 13：`V1__sys_baseline.sql`（sys 12 表 + 超管/菜单 28 / 附录 A 52 / 字典 6 类含 portal_route 8）
- 进行中：无
- 下一步：任务 14 — V2–V4 全量建表（依赖 13）
- 代码实况：Flyway 仅 admin-app `enabled=true`；portal `false`。无 `web/`、无 P1 域、无 `domain-points`
- Git：分支 `task/13-db-v1-sys`（本提交）。上一提交 `task/12-contract-ports` @ `a7ad1d4`

## 关键技术决策（本轮新发生的）

- DDL 按 design §3.2 逐字 + 表级 `utf8mb4_0900_ai_ci`
- 超管 `username=admin`，`password_hash=''`，`must_change_password=1`；启动 `InitAdminPasswordRunner` 读 `MKT_INIT_ADMIN_PASSWORD` 写 BCrypt cost 12
- 权限树本任务只种 §4.10 的 28 条 MENU（OPERATION 码留给 identity 任务）
- Testcontainers 2.x 坐标：`testcontainers-junit-jupiter` / `testcontainers-mysql`（Boot 4.1 BOM 2.0.5）
- enforcer 反选型规则里对 `spring-security-crypto` 的 `<exclude>` 实际会把它禁掉，已删掉该 exclude，并把它加入直接依赖白名单

## 改过的核心文件

- `server/platform-db/src/main/resources/db/migration/V1__sys_baseline.sql`
- `server/platform-db/pom.xml`、`V1BaselineScriptTest`、`FlywayV1IT`
- `server/admin-app/`（Flyway/数据源 yml、`InitAdminPasswordRunner`、测试 profile 关 DataSource）
- `server/portal-app/src/main/resources/application.yml`（flyway.enabled=false）
- `server/pom.xml`（enforcer 放行 flyway-mysql / mysql-connector-j / testcontainers 2 / crypto）
- `deploy/.env.example`、`dependency-matrix.md`、`tasks.md`（仅 13 已勾）

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -DskipITs test` → 通过
- `V1BaselineScriptTest` 4、`InitAdminPasswordRunnerTest` 3、`FlywayAdminOnlyTest` 1
- `FlywayV1IT`：**本机无 Docker，留给 CI**；断言保持完整（12 表、52 配置、28 菜单、6 字典类型、8 路由、二次 migrate=0、validate）

## 已知问题（已核实）

- **迁移路径：需人类评审，AI 不得宣称可合**
- ArchUnit 仍不扫 portal-app 生产类
- 本机 `mvn verify` 会跑 `FlywayV1IT`，无 Docker 会失败；本地请用 `-DskipITs`
- 对账 `CALLBACK_FAILED` / `MANUAL` 未进 §5.11

## 尝试过但失败的方案

- Testcontainers 1.x 的 `junit-jupiter` / `mysql` 在 Boot 4.1 BOM 里无版本；改用 2.x `testcontainers-*`
- enforcer 第二规则把 crypto 写在 `<exclude>` 会禁掉直接依赖；删掉该 exclude 才过

## 明确禁止下一会话做的事

- 不要做任务 15+（缓存/域实现）
- 不要改已落盘的 `V1__sys_baseline.sql` 内容（RL-09）；修正只加更高版本
- 不要建 `web/`、P1 域、`domain-points`、compile 装配 domain
- 不要用 H2 让 FlywayV1IT 本地变绿
- 不要 commit / push，除非人类明确要求

## 下一步开发顺序（最多 3 步）

1. 任务 14：V2–V4 业务表（依赖 13）。开工前切 `task/14-<slug>`
2. 任务 15：`platform-infra` 缓存 / 限流 / 降级
3. 任务 16：Outbox Relay
