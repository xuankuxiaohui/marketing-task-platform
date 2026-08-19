# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**17（评审 blocking/should 已改，待人类评审）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–17**
- 进行中：无
- 下一步：人类评审任务 17 后开任务 18
- 代码实况：`domain-risk` 名单管理 / Redis 投影 / 命中查询 / 处置留痕。admin-app 已装配 `domain-risk`。未实现 `RiskCheckPort`、R-a–R-f、`risk:cnt`
- Git：分支 `task/17-risk-list`（未提交）。任务 16 已提交 @ `21a558c`

## 关键技术决策（本轮新发生的）

- 命中查询 / 处置与名单一样走编程式 `RiskPermissionGuard`（任务 21 的 `@SaCheckPermission` 拦截器未接线，注解单独不够）
- 名单值写入与点查同一规范化：USER 正整数无前导零；IPv4 拒 `+`/前导零；IPv6 经 `InetAddress` 小写，IPv4-mapped 收到 IPv4；DEVICE 小写。引擎对 IP/设备 `equalsIgnoreCase`
- 空 `UserContext` 映 403 `common.permission-denied`，不再 500
- `REMOVE_BLACK` + `toWhitelist` 仅在成功删黑之后加白；`expireAt` 必须晚于 now；处置 `userId` 必须 > 0

## 改过的核心文件

- `server/domain-risk/src/main/java/com/mkt/risk/controller/admin/RiskCaseAdminController.java`
- `server/domain-risk/src/main/java/com/mkt/risk/support/RiskListImportParser.java`
- `server/domain-risk/src/main/java/com/mkt/risk/support/ListLookup.java`
- `server/domain-risk/src/main/java/com/mkt/risk/support/RiskListProjection.java`
- `server/domain-risk/src/main/java/com/mkt/risk/support/RiskOperator.java`
- `server/domain-risk/src/main/java/com/mkt/risk/application/RiskListAppService.java`
- `server/domain-risk/src/main/java/com/mkt/risk/application/RiskCaseAppService.java`

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -pl domain-risk -am -DskipITs test` → 通过（含 jqwik 200 tries）
- `mvn -q -pl admin-app -am -DskipTests compile` → 通过
- 单测补了：命中查询 403、IPv6 大小写命中、`+123`/`0123` 拒绝、过期 expireAt、无黑名单加白 400、空上下文 403
- `ListExpiryIT` / `ListConcurrentDecisionIT` / `CaseHandleAuditIT`：**本机无 Docker，留给 CI**

## 已知问题（只写已证实）

- **本任务是风控路径，需人类评审，AI 不得宣称可合**
- 任务 21 未接线时管理端无会话会 403（名单 + 命中查询/处置）
- 服务内 `audit.log` 与任务 25 `@Audited` AOP 可能双记
- 投影命中仍回源 DB（C-12 幽灵键）；`denyLogin` 不在 Redis 值里，任务 18 P95 需另议
- 会话（15）与迁移（13/14）仍待人类评审

## 尝试过但失败的方案

- 给 C-12 加 `testcontainers-redis` 坐标会撞 enforcer；改为已有 `GenericContainer` + `redis:7-alpine`
- 权限守卫未登录跳过：读路径可拉全量名单；已改为默认拒绝
- `::ffff:10.0.0.1` 在 JDK 26 上 `getByName` 直接给出 `Inet4Address`，不能只认 `Inet6Address`

## 明确禁止下一会话做的事

- 不要做任务 18+，除非人类明确要求
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要建 `web/`、P1 域、`domain-points`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要 commit / push，除非人类明确要求
- 不要自审自合任务 17

## 下一步开发顺序（最多 3 步）

1. 人类评审任务 17（风控）
2. 任务 18：`RiskCheckPort` + R-a–R-f
3. 任务 19：domain-tracking 上报
