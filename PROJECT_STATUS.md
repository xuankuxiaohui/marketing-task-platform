# PROJECT_STATUS

> 阶段：**编码（编组 B）** / 当前任务：**11（已验收）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–11**
  - 1–8：编组 A 冒烟（`spike/`）
  - 9：`server/` 11 模块空壳 + 父 POM + 命名空间守卫
  - 10：ArchUnit 首批 + surefire/failsafe + JaCoCo + CI
  - 11：`platform-kernel`（Result / ErrorCode / JsonUtil / Clock / 异常处理 / traceId / GroupedOpenApi）
- 进行中：无
- 下一步：任务 12 — `platform-contract` 三端口 + D-13 只读 + 事件常量（依赖 11）
- 代码实况：`server/` 十一模块在；kernel 已有可执行代码；无 `web/`、无 P1 域、无 `domain-points`
- Git：当前分支 `task/11-kernel-basics`（工作区含任务 9–11 未提交内容）。`main` 仍是 `a3cfa87` Initial commit

## 关键技术决策（本轮新发生的）

- `Result.code`：成功数字 `0`，失败字符串；Jackson 3 经 `JsonUtil` 单例 `JsonMapper`（`tools.jackson`）
- 错误码：kernel 只落 §3.9 分段骨架 + 封闭 5 个 `common.*`；域枚举留给各域
- traceId：32 位 hex UUID 或合法入站 `X-Trace-Id`；写 MDC / 响应头 / `Result.traceId`
- OpenAPI 文档路径挂在本应用允许前缀下（RL-08）：admin = `/admin/v3/api-docs/{group}`，portal = `/api/v3/api-docs/{group}`；`swagger-ui.enabled=false`。任务 36 消费这两处导出，不要 spike/5 JSON
- `MutableClock` 在 kernel **test-jar**（classifier `tests`）；生产 `Clock` Bean = `Clock.systemUTC()`
- Sa-Token 异常映射（401 四码 / `NotPermissionException`）**未**进本任务：kernel 不依赖 Sa-Token

## 改过的核心文件

- `server/platform-kernel/`（Result、ErrorCode、BusinessException、JsonUtil、UserContext、分页、Clock、TraceIdFilter、GlobalExceptionHandler、OpenApiGroupsConfiguration、auto-config）
- `server/platform-kernel/src/test/`（含 `MutableClock`、`ResultJsonIT`、`TraceIdFilterIT`、`ErrorCodeFormatTest`）
- `server/pom.xml`（kernel test-jar 坐标）
- `server/admin-app/`、`server/portal-app/`（springdoc 依赖、api-docs 路径、NamespaceGuard 让位于 TraceIdFilter）
- `.kiro/specs/platform-v2/tasks.md`（仅任务 11 已勾）

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -DskipITs test` → 通过
- `mvn -q verify` → BUILD SUCCESS（enforcer + surefire + jacoco check + failsafe）
- kernel surefire 55（含 `ErrorCodeFormatTest` 32、`MutableClockTest` 2）；kernel failsafe：`ResultJsonIT` 4、`TraceIdFilterIT` 2；admin/portal `OpenApiGroupsIT` 各 1；0 失败 0 跳过
- 矩阵任务 11：kernel 单测 + `MutableClock` 已覆盖；`_测试：` 四类均交付
- JaCoCo：`platform-kernel` 门禁已开始跑（不再 skip）；根包 `com.mkt.kernel` 行覆盖远高于 85%；模块 bundle ≥70%
- 未跑：GitHub Actions 远端；无 Testcontainers `*IT`（本任务 ITs 不需要 Docker）

## 已知问题（已核实）

- ArchUnit 仍只扫 admin-app 类路径，**未**把 portal-app 生产类纳入（任务 10 遗留；本任务未做）
- D-04 全局 70% 仍非反应堆合计；无测试模块继续 skip
- RL-07 / RL-12 空规则弱于 §7.6 终态
- 任务 9–11 工作区尚未拆成独立已合入提交；合入前按 08 拆清 Refs
- 现网 Redis 6.0.8 / DB 2；本机无 Docker
- 对账 `CALLBACK_FAILED` / `MANUAL` 未进 §5.11（任务 34 前补设计）

## 尝试过但失败的方案

- Jackson 3 没有 `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS`，改用 `DateTimeFeature`（3.x 默认已是 ISO-8601）
- 单独 `mvn failsafe:integration-test` 不会 install kernel，后续模块解析失败；用 `mvn verify`

## 明确禁止下一会话做的事

- 不要做任务 13+（Flyway / 域实现）
- 不要建 `web/`、P1 域、`domain-points`
- 不要给应用加 **compile** 作用域的 domain 依赖（RL-05 终态仍未接线）
- 不要把 `spike/` 拷进 `server/`
- 不要 commit / push，除非人类明确要求
- 不要发明错误码；域码等对应域任务登记
- 不要把 OpenAPI 挪回根路径 `/v3/api-docs`（会撞 RL-08）
- 不要在 kernel 加 Sa-Token 依赖来「提前做完」401 映射

## 下一步开发顺序（最多 3 步）

1. 任务 12：`platform-contract` 三写端口 + D-13 只读 + 事件常量（依赖 11）。开工前切 `task/12-<slug>`
2. 任务 13：`platform-db` V1（依赖 9、10）
3. 任务 14：V2–V4 业务表（依赖 13）
