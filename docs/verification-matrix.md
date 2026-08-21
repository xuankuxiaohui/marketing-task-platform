# 验收矩阵

> 一表对照：任务号 → 需求 → 正确性属性 → 测试类。
> 权威：属性定义在 [requirements.md](../.kiro/specs/platform-v2/requirements.md)；测试类形态在 design §7.3 / §7.4 / §7.6；任务边界在 [tasks.md](../.kiro/specs/platform-v2/tasks.md) v2.9。
> 人类 reviewer 按任务号筛本表，再打开对应测试类与需求条款。不要用本表发明新属性或新测试类。

## 用法

1. 验收任务 N：筛「任务」列 = N（或 N.x）。
2. 对照需求条款 + 属性名。**P0 验收只看「P0 矩阵」**；P1 行是占位，P0 完成前不挡、不扩。
3. 测试类必须在该任务的 `_测试：_` 行或本表出现。绿灯 = 该类在对应模块跑绿。
4. Spike（1–8）无 §7.3 属性，冒烟即验收，不列入属性行。

## P0 矩阵

| 任务 | 需求 | 正确性属性 | 测试类 | 类型 |
|------|------|------------|--------|------|
| 10 | RL-01~04、AT-C01 | 架构红线（首批） | `ArchLayerRuleTest`、`ArchClockRuleTest` | 架构 |
| 11 | 附录 C / D-03 | Result / Clock 基建 | kernel 单测 + `MutableClock` | 单元 |
| 12 | §2.2.3 | 三端口契约 | contract 编译期 + RL-06（任务 10 扩） | 架构 |
| 13 | R7–R10 种子 | V1 迁移 | Flyway 启动 / IT clean+migrate | 集成 |
| 14 | R11–R20、R25–R29、R37 表 | V2–V4 迁移 | 同上 | 集成 |
| 15 | R7.4/R8.3/R9 / 可用性 4 | 缓存广播、限流、降级 | `CacheEvictBroadcastIT`、`RateLimitLuaIT`、`DegradeMatrixIT` | 集成 / 故障 |
| 16 | R10.3/R28.8/R28.9 | Outbox 回滚/重试/幂等 | `OutboxRollbackIT`、`OutboxRelayRetryIT`、`OutboxIdempotentInsertIT` | 集成 |
| 17 | R25.1 | 名单判定确定性与优先级 | `risk ListDecisionPropertyTest` | jqwik |
| 17 | R25.2 | 名单时效性 | `risk ListExpiryIT` | 集成 |
| 17 | 可维护性 4 | 名单并发无第三态 | `risk ListConcurrentDecisionIT`（C-12） | 并发 |
| 17 | R27.1 | 处置留痕完整 | `risk CaseHandleAuditIT` | 集成 |
| 18 | R26.1 | 规则判定确定性 | `risk RuleDeterminismPropertyTest` | jqwik |
| 18 | R26.2 | 拒绝零副作用 | `risk RuleRejectZeroSideEffectIT` | 集成 |
| 18 | R26.3 | 降级可观测 | `risk RuleFallbackIT` | 故障 |
| 18 | R26.4 | R-e 判定范围 | `risk ReElapsedScopeIT` | 集成 |
| 19 | R28.1 | 业务非阻塞 | `tracking TrackOutageNonBlockingIT` | 故障 |
| 19 | R28.2 | 事件不可变 | `tracking EventImmutabilityArchTest` | 架构 |
| 19 | R28.3 | 服务端事件与事务一致 | `tracking ServerEventTransactionalIT` | 集成 |
| 20 | R29.1 | 调试查询无副作用 | `tracking DebugQueryNoSideEffectIT` | 集成 |
| 21 | R1.1 | 锁定触发不变量 | `identity LoginLockPropertyTest` | jqwik |
| 21 | R1.2 | 审计完整性（登录） | `identity LoginAuditIT` | 集成 |
| 21 | R1.2 / R1.3 | 失败锁定与审计经代理提交 | `identity AdminAuthServiceTransactionalTest` / `PortalAuthServiceTransactionalTest` / `LoginLockCommitIT` | 单元+集成 |
| 21 | R1.3 | 改密会话失效 | `identity PasswordChangeSessionIT` | 集成 |
| 21 | R4.1 | 账号体系隔离 | `portal NamespaceIsolationIT` | 集成 |
| 22 | R2.1 | 权限判定确定性 | `identity PermissionUnionPropertyTest` | jqwik |
| 22 | R2.2 | 权限实时生效 | `identity PermissionImmediateEffectIT` | 集成 |
| 23 | R3.1 | 用户名唯一 | `identity AdminUsernameUniqueIT` | 集成 |
| 23 | R5.1 | 档案变更即时生效 | `identity ProfileEffectVisibilityIT` | 集成 |
| 24 | R7.1 | 字典缓存一致 | `identity DictCacheConsistencyIT` | 双实例 |
| 24 | R8.1 | 配置缓存一致 | `identity ConfigCacheConsistencyIT` | 双实例 |
| 24 | R9.1 | 清理全局一致 | `identity CacheEvictConsistencyIT` | 双实例 |
| 25 | R6.1 | 踢下线全局一致 | `portal KickoutConsistencyIT` | 双实例 |
| 25 | R10.1 | 审计完整性（后台写） | `identity AuditCompletenessIT` | 集成 |
| 26 | R11.1 | 聚合保存原子 | `task TaskAggregateAtomicIT` | 集成 |
| 26 | R11.2 | 流程图无环 | `task TaskGraphAcyclicPropertyTest` | jqwik |
| 26 | R11.9 | 表达式沙箱 | `task ExpressionSandboxMaliciousTest`（M-01~M-15） | 单元 |
| 27 | R12.1 | 快照不可变 | `task SnapshotImmutabilityIT` | 集成 |
| 27 | R12.2 | 修订草稿隔离 | `task RevisionIsolationIT` | 集成 |
| 27 | R12.3 | 批量任务级原子 | `task BatchPublishAtomicIT` | 集成 |
| 28 | R13.1 | 实例唯一 | `task InstanceUniquenessIT`（C-1） | 并发 |
| 28 | R13.2 | 灰度稳定 | `task GrayStabilityPropertyTest` | jqwik |
| 28 | R13.3 | 埋点非阻塞 | `task TrackingNonBlockingIT` | 故障 |
| 28 | R13.4 | 风控拒绝幂等 | `risk RiskRejectIdempotentIT` | 集成 |
| 29 | R14.1 | 推进恰一次 | `task StepAdvanceExactlyOnceIT`（C-2） | 并发 |
| 29 | R14.2 | 进度去重 | `task ProgressDedupIT`（C-3） | 并发 |
| 29 | R14.3 | 状态机合法 | `task StepStateMachinePropertyTest` | jqwik |
| 29 | R14.4 | 过期终局 | `task ExpiredFinalityIT` | 集成 |
| 30 | R15.1 | 防重放 | `internal ReplayAttackIT`（A-01~A-06） | 集成 |
| 31 | R16.1 | 动作合并确定 | `task ActionMergePropertyTest` | jqwik |
| 31 | R16.2 | 回退链完备 | `task ActionFallbackPropertyTest` | jqwik |
| 31 | R16.3 | 动作随快照固化 | `task ActionSnapshotFixityIT` | 集成 |
| 31 | R16.4 | 端标识白名单 | `common ClientPlatformResolverTest` | 单元 |
| 32 | R17.1 | 不超发 | `reward StockNoOversellIT`（C-4） | 并发 |
| 32 | R17.2 | 限领原子 | `reward ClaimLimitConcurrentIT`（C-5） | 并发 |
| 32 | R17.3 | 成本快照不可变 | `reward CostSnapshotIT` | 集成 |
| 33 | R18.1 | 发放恰一次 | `reward GrantExactlyOnceIT`（C-6） | 并发 |
| 33 | R18.2 | 风控拦截零副作用 | `reward GrantZeroSideEffectIT` | 集成 |
| 34 | R19.1 | 领取恰一次 | `reward PrizeClaimExactlyOnceIT`（C-7） | 并发 |
| 34 | R19.2 | 过期不可领取 | `reward PrizeExpireIT` | 集成 |
| 34 | R37.1 | 对账穷尽 | `reward ReconExhaustiveIT` | 集成 |
| 34 | R37.2 | 补发不重复到账 | `reward ReconReissueIT` | 集成 |
| 34 | R37.3 | 补发门禁 | `reward ReconReviewGateIT` | 集成 |
| 35 | R20.1 | 余额非负 | `points PointsNonNegativeIT`（C-8） | 并发 |
| 35 | R20.2 | 流水轧平 | `points PointsLedgerPropertyTest` | jqwik |
| 36 | R1 / R2 | 登录页与路由守卫 | admin 登录 Vitest | 前端 |
| 37.1 | R2–R10 | 系统管理页交互 | 各页关键 Vitest（§4.10 系统 9 页） | 前端 |
| 37.2 | R11–R12、R17–R20、R37 | 任务/奖励页交互 | 画布/发布确认/停用确认 Vitest | 前端 |
| 37.3 | R25–R29 | 风控/埋点页交互 | 名单/规则/调试 Vitest | 前端 |
| 38.1 | R32.1 | 匿名不可达 | `portal AnonymousBoundaryIT` | 集成 |
| 38.1 | R33.1 | 退出即时失效 | `portal LogoutImmediateIT` | 双实例 |
| 38.1 | R32 / R33 | 会话与档案组件 | 验证码 / 协议 / 空态 Vitest（§7.9） | 前端 |
| 38.2 | R34.1 | 列表状态一致 | `portal ListStateConsistencyIT` | 集成 |
| 38.2 | R34.2–R34.4 | 按钮/时间线/反馈 | 任务状态机 Vitest | 前端 |
| 38.3 | R35.1 | 按钮状态一致 | `web PrizeButtonStateTest` | 前端 |
| 38.3 | R28.10/13/14 | 埋点 SDK 聚合与补发 | SDK Vitest | 前端 |
| 39 | 可维护性 2 | OpenAPI 类型无 diff | openapi-typescript CI | CI |
| 40 | R14 / R18 | 步骤场景 01–23 | `task ScenarioMatrixIT` | 集成 |
| 41 | R14 场景 24 | 定时发布恰一 | `TwoAdminAppIT` scenario24 | 双实例 |
| 41 | RL-05 | portal 装配 task+reward | `portal PortalAssemblyIT` | 集成 |
| 41 | R4.1 / RL-08 | 命名空间互斥 | `NamespaceIsolationIT` | 集成 |
| 41 | §6.8 | Redis 降级逐行 | Redis pause × 降级矩阵 | 故障 |
| 41 | C-1~C-8、C-12 | 并发不变量（P0） | 见各域 C-* 行 | 并发 |
| 42 | R31.1 | 部署幂等 | `ci/deploy-smoke.sh` | CI |
| 43 | R32–R35 | 核心旅程 | `web/e2e/journey-core.spec.ts`、`journey-admin.spec.ts` | E2E |
| 43 | NFR 性能 1–5、7 | P0 k6 门槛 | `perf/*.js`（§7.8） | 压测 |

## P1 占位（P0 完成后填充）

> 任务 44–49。§7.3 已登记的属性 / 测试类名写在下表，**实现与扩场景都等任务 43 验收之后**。
> 未开 feasibility 的模块（签到 / 活动 / 广告）不要在 P0 期间补场景行。点位以 design §3.11 为准。

| 任务 | 需求 | 正确性属性 | 测试类 | 状态 |
|------|------|------------|--------|------|
| 44 | R21.1 | 签到唯一 | `signin SigninUniqueIT`（C-9） | 已交付 · 64 线程同用户同日 |
| 44 | R36.1 | 日历状态一致 | `web SigninCalendarStateTest` | 已交付 · `signin-calendar-state.spec.ts` |
| 44 | R21 / R36 | 签到 H5 页（补签 / 断链 / 跨月） | `SigninPage.spec.ts` | 已交付 · 日历格子走后端四态 |
| 45 | R22.1 | 活动限量 | `activity ActivityQuotaIT`（C-10） | 已交付 · 128 线程全局日限量 10 |
| 45 | R22 | 活动页 + 富文本消毒验收 | `ActivityHtmlSanitizerTest` / `ActivityPage.spec.ts` / `ActivityManagePage.spec.ts` | 已交付 · 服务端白名单消毒，C 端只渲染 richText |
| 46 | R23.1 | 聚合幂等 | `metrics AggregationIdempotentIT` | 已交付 · 同窗重放 3 次计数不变，排除 simulated |
| 46 | R23 | 看板页（漏斗 / 成本 / 风控 / 广告） | `MetricsPage.spec.ts` / `DashboardPage.spec.ts` | 已交付 · ECharts + 转化率 0 分母为 — |
| 47 | R24.1 | 模拟隔离 | `simulate SimulationIsolationIT` | 占位 · P0 完成后填充 |
| 47 | R24 | 模拟器页 list/detail/flow | （待补） | 占位 · P0 完成后填充 |
| 48 | R30.1 | 广告频控不超限 | `ad AdFrequencyIT`（C-11） | 占位 · P0 完成后填充 |
| 48 | R30.2 | 排期正确 | `ad AdScheduleIT` | 占位 · P0 完成后填充 |
| 48 | R30 | 接线 `ad:position` + 门户广告组件 | （待补；P0 只占位 ns） | 占位 · P0 完成后填充 |
| 49 | NFR 性能 1–8 | 容量复验 | k6 全量 + 慢查询复盘 | 占位 · P0 完成后填充 |

无属性行、但仍须交测试的任务：1–8（Spike 冒烟）、9（`mvn compile`）、12/13/14 的编译与迁移失败即失败。

## 与场景矩阵的关系

| 矩阵 | 文件 | 落地任务 | 测试类 |
|------|------|----------|--------|
| 步骤引擎 24 场景 | [feasibility-step-engine.md](../.kiro/specs/platform-v2/feasibility-step-engine.md) | 40（01–23）、41（24） | `ScenarioMatrixIT` / `TwoAdminAppIT` |
| 对账 28 场景 | [feasibility-recon.md](../.kiro/specs/platform-v2/feasibility-recon.md) | 34 | `ReconExhaustiveIT` / `ReconReissueIT` / `ReconReviewGateIT` |
| 风控 30 场景 | [feasibility-risk.md](../.kiro/specs/platform-v2/feasibility-risk.md) | 17、18 | 名单/规则 §7.3 行 + C-12 |

## 属性计数

§7.3 声明 66 条。P0 矩阵覆盖 P0 属性 + RL / Outbox / Spike 基建行。P1 六条属性（R21.1 / R22.1 / R23.1 / R24.1 / R30.1–2 / R36.1）在「P1 占位」表，P0 完成后按 §3.11 补前端点位与场景矩阵，再改本表。新增属性必须先改 requirements，再改 §7.3，再改本表。
