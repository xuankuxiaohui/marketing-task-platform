# PROJECT_STATUS

> 阶段：**编码（编组 C）** / 当前任务：**12（已验收）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–12**
  - 1–8：编组 A 冒烟（`spike/`）
  - 9–11：server 骨架、ArchUnit/CI、`platform-kernel`
  - 12：`platform-contract` 三写端口 + D-13 只读门面 + D-05 事件常量（无实现）
- 进行中：无
- 下一步：任务 13 — `platform-db` V1 sys 12 表（依赖 9、10）
- 代码实况：`server/` 十一模块在；kernel + contract 有可执行代码；无 `web/`、无 P1 域、无 `domain-points`；两应用仍未 compile 装配 domain
- Git：当前分支 `task/12-contract-ports`（未提交）。`task/11-kernel-basics` 已推到 origin @ `6b878f9`

## 关键技术决策（本轮新发生的）

- 端口签名只按 design §2.2.3 落接口与 record，**不**实现、不装配
- `TaskReadPort` 仅 `instanceCounts`，无写方法（D-13）
- D-05 封闭 13 个事件码；`audit.log` 标内部、不进附录 D
- `RetryableGrantException` / `PermanentGrantException` 带 R14.5 封闭原因枚举，**不**绑 HTTP ErrorCode（避免发明码；09 的 BusinessException 接线留给任务 33）
- RL-06：contract 生产 POM 只有 kernel；`ContractPurityArchTest` 扫 Spring/JDBC/Redis/MyBatis

## 改过的核心文件

- `server/platform-contract/`（端口、record、枚举、事件、测试）
- `server/admin-app/src/test/java/com/mkt/admin/ContractPurityArchTest.java` + RL-06 fixture
- `.kiro/specs/platform-v2/tasks.md`（仅任务 12 已勾）

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -DskipITs test` → 通过
- contract surefire：`PortSignatureTest` 4、`EventCodesTest` 2、`ContractRecordsTest` 6
- admin `ContractPurityArchTest` 3；0 失败 0 跳过
- 矩阵任务 12：contract 编译期签名锁定 + RL-06 已覆盖
- 本任务无 `*IT` / 无 Docker

## 已知问题（已核实）

- ArchUnit 仍只扫 admin-app 类路径，未纳入 portal-app 生产类
- D-04 全局 70% 仍非反应堆合计
- 发放异常尚未接 `BusinessException`/`ErrorCode`（等 reward 域登记 §4 码）
- 现网 Redis 6.0.8 / DB 2；本机无 Docker
- 对账 `CALLBACK_FAILED` / `MANUAL` 未进 §5.11（任务 34 前补设计）

## 尝试过但失败的方案

- contract 仅测端口签名时 JaCoCo bundle 52% < 70%；补齐枚举/record 构造断言后过门禁
- AssertJ `doesNotContainAnyOf` 在当前版本不存在，改用 `noneMatch`

## 明确禁止下一会话做的事

- 不要做任务 14+（V2–V4 业务表 / 域实现）
- 不要建 `web/`、P1 域、`domain-points`、`PointsPort`（属 reward 内部）
- 不要给应用加 **compile** 作用域的 domain 依赖
- 不要实现端口；不要把 `spike/` 拷进 `server/`
- 不要 commit / push，除非人类明确要求
- 不要发明错误码或新事件码

## 下一步开发顺序（最多 3 步）

1. 任务 13：`platform-db` V1 sys 12 表 + Flyway 仅 admin 执行（依赖 9、10）。开工前切 `task/13-<slug>`
2. 任务 14：V2–V4 业务表（依赖 13）
3. 任务 15：`platform-infra` 缓存 / 限流 / 降级
