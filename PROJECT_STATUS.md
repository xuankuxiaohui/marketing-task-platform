# PROJECT_STATUS
> 阶段：**编码（编组 D）** / 当前任务：**18（待人类评审）** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–18**
- 进行中：无
- 下一步：人类评审任务 18 后开任务 19
- 代码实况：`domain-risk` 已实现 `RiskCheckPort`（名单 + R-a–R-f）、`userSummary`、`risk:cnt` 消费/同步 ZADD、命中 REQUIRES_NEW 留痕、`risk.fallback-policy` 降级。admin-app 已装配 `domain-risk`。portal-app **未**装配（避免扫入 `/admin` 控制器）。未做规则管理端点（R26.6 / §4.6 PUT）
- Git：分支 `task/18-risk-check`（未提交，基于 `task/17-risk-list` @ `afa5963`）。任务 17 已提交 @ `afa5963`

## 关键技术决策（本轮新发生的）

- 判定链按 §5.9：名单 → REGISTER/LOGIN 结束；白名单 `SKIP_RULES` 跳规则不写 R-f；CLAIM/GRANT 再跑 R-a–R-f
- R-e 只认 `elapsedSeconds != null`（D-09：同请求新建 / 非 TASK_STEP / simulated 由调用方传 null）
- `RiskSubject` 无 `simulated` 字段：R-f 无法在判定点跳过模拟请求；R-a/R-b 在消费器读 payload.`simulated` 后跳过；R-c/R-d 模拟仍写入（观察不拦截留给调用方）
- `risk.fallback-policy` 默认 allow，测试可翻；不直读 `sys_config`（RL-11 / 任务 24）。降级留痕 = 计数 + warn 日志（D-05 无 fallback 事件码）
- 命中写入用 `TransactionTemplate` REQUIRES_NEW（§5.9）；独立失败只告警，不改 verdict

## 改过的核心文件

- `server/platform-infra/src/main/java/com/mkt/infra/redis/KeyValueStore.java`
- `server/platform-infra/src/main/java/com/mkt/infra/redis/MemoryKeyValueStore.java`
- `server/platform-infra/src/main/java/com/mkt/infra/redis/RedissonKeyValueStore.java`
- `server/domain-risk/src/main/java/com/mkt/risk/application/RiskCheckPortImpl.java`
- `server/domain-risk/src/main/java/com/mkt/risk/application/RiskCntConsumer.java`
- `server/domain-risk/src/main/java/com/mkt/risk/application/RiskHitRecorder.java`
- `server/domain-risk/src/main/java/com/mkt/risk/domain/RuleDecisionEngine.java`
- `server/domain-risk/src/main/java/com/mkt/risk/support/RiskCntWindow.java`

## 测试与验证

- `$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"; cd server; mvn -q -pl domain-risk,admin-app -am -DskipITs test` → 通过（`RuleDeterminismPropertyTest` 含 200 tries）
- 矩阵类：`RuleDeterminismPropertyTest`、`RuleRejectZeroSideEffectIT`、`RuleFallbackIT`、`ReElapsedScopeIT`
- 单测补了：黑优先、白名单跳规则、REGISTER/LOGIN 不跑规则、R-e 跳过/拦截、fallback allow/reject、`userSummary`、`RiskCntConsumer` 不写 R-e
- `RuleRejectZeroSideEffectIT` / `RuleFallbackIT` / `ReElapsedScopeIT`：**本机无 Docker，留给 CI**

## 已知问题（只写已证实）

- **本任务是风控路径，需人类评审，AI 不得宣称可合**
- portal-app 未装配 `domain-risk`；`RiskAdminAutoConfiguration` 已按 `AdminApplication` 条件扫描，任务 21 可只装配服务/消费器
- auth 事件附录 D 只要求 `userId`；R-c/R-d 消费器读可选 `ip`/`deviceId`，任务 21 必须带上
- `RiskSubject` 无 simulated，判定点 R-f 无法识别模拟器（R24.5 完整语义待调用方或契约扩字段）
- `risk.fallback-policy` 仍是进程内默认 allow，热切等任务 24
- 任务 21 未接线时管理端无会话会 403
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审

## 尝试过但失败的方案

- 无（本轮未回滚实现）

## 明确禁止下一会话做的事

- 不要做任务 19+，除非人类明确要求
- 不要做规则管理 PUT `/admin/risk/rules`（R26.6）
- 不要给 portal-app 装配 `domain-risk`（会扫入 admin 控制器），除非先拆 AutoConfiguration 扫描
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要建 `web/`、P1 域、`domain-points`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要 commit / push，除非人类明确要求
- 不要自审自合任务 18

## 下一步开发顺序（最多 3 步）

1. 人类评审任务 18（风控判定链）
2. 任务 19：domain-tracking 上报
3. 任务 20：元数据与调试
