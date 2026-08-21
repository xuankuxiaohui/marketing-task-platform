# PROJECT_STATUS
> 阶段：**编组 J 进行中** / 当前任务：**47 模拟器，已交付** / 更新：2026-08-21

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1、38.2、38.3、39、40、41、42、43、44、45、46、47**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38 至任务 47 PR #64 均未合 master）
- 进行中：无。**编组 J（44–49）进行中，任务 47 已交付。禁止在本分支继续写 48+**
- 下一步：下一会话从本分支 tip 开 `task/48-ads` 做编组 J 第五题。**禁止 merge / push / force-push master**
- Git：工作分支 `task/47-simulate`（基线 `origin/task/46-metrics` @ `61d1950` / 其上叠 46 → 45 → … → 29）。PR **#64** 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- 模拟器在 `admin-app`（`com.mkt.admin.simulate`），进程内调 `TaskClaimAppService` / `TaskStepAppService` / `TaskPortalAppService`，不跨应用 HTTP
- 端点 `/admin/simulate/task/{list,detail,start,click,callback,progress,flow,reverse}`；权限 `simulate:task` / `simulate:flow`；非 GET `@Audited` + CSRF
- 写路径一律 `simulated=true`：实例列、`GrantContext.simulated`、发放/积分流水、Outbox 事件、`evt_event_log`、`risk_hit_log`
- `RiskSubject.simulated`（默认 false）：R-e 直接 skip；R-a/R-b/R-f 不统计；R-c/R-d 观察记 MARK 不拦截
- 冲正：积分 `REVERSAL`（simulated=1）+ `restoreOne` + `rwd_stock_log.change_type=SIMULATE_REVERSE`；`SENDING` 只回补+标记；`channelRevoked=false`，不调履约适配器
- Flyway **V6_2** 只插权限/菜单（id 50–52）；**未**改 V1–V6_1；**未**用 V7（留给任务 48 ads）

## 改过的核心文件

- `server/platform-db/src/main/resources/db/migration/V6_2__simulate_permissions.sql`
- `server/admin-app/src/main/java/com/mkt/admin/simulate/**`、`controller/admin/SimulateAdminController.java`
- `server/platform-contract/.../RiskSubject.java`（增 `simulated`，四参构造默认 false）
- `server/domain-task/.../TaskClaimAppService.java`、`engine/StepEngine.java`（事件 payload 带 simulated）
- `server/domain-reward/.../GrantAppService.java`、`FulfillmentService.java`、`PointsAppService.java` / `PointsPort`
- `server/domain-risk/.../RiskCheckPortImpl.java`、`RiskHitRecorder.java`
- `web/apps/admin/src/views/simulate/**`、`api/simulate.ts`
- `.kiro/specs/platform-v2/tasks.md`（任务 47 勾选）、`design-api.md` §4.4.2 / §4.10、`design-architecture.md` §2.2.3 / §3.11.4
- `docs/verification-matrix.md`（R24.1 / 模拟器页已交付）

## 测试与验证

- 命令与结果：`cd server && mvn -q -DskipITs test` 绿；`cd web && pnpm test` 绿（admin 72 / client 90）；`pnpm lint` 绿
- 矩阵覆盖：verification-matrix 任务 47（R24.1 `SimulationIsolationIT`、`RiskCheckPortImplTest` simulated 规则、看板排除已在 46；页 `SimulatePage.spec.ts`）
- 未跑项及原因：`*IT` 本机 `-DskipITs` 留给 CI；未削弱断言，未用 H2 / Embedded Redis。本机未起 compose（禁止动 3308 / Redis / 8080 / 8081）

## 已知问题（只写已证实）

- 任务 29 PR #38 至任务 47 PR #64 均未合 master；叠链 29 → … → 46 → 47
- PR #63 `61d1950` 全 CI 绿（本分支基线）；本任务 PR #64
- `GET/PUT /admin/risk/rules` 未在后端/OpenAPI 导出；规则页不发明读写契约（R26.6）；k6 性能 4 用 SQL 切换 `risk_rule_config.enabled`
- `GET /admin/reward/records` 未在后端/OpenAPI 导出；k6 后台列表用已有 `/admin/task/instances` `/admin/task/definitions` `/admin/points/transactions`
- portal `PrizeCardView.sourceTaskId` / `PointsPortalTxView.sourceTaskId` 后端现返回 null；有值才跳转
- 编组 F 评审必须项 #41 已在任务 31 分支修；待 CI 绿后关 #41
- 任务 28 已合 master（#36）；评审 #37、测试 #34、运维 #35 只记账不修
- 任务 27 PR #32 一轮评审 #33 已处理；测试 #30、运维 #31 只记账不修
- 任务 26 PR #28 一轮评审 #29 已处理；测试 #26、运维 #27 只记账不修
- 任务 25 PR #23 一轮评审 #24/#25 已处理；测试 #21、运维 #22 只记账不修
- 任务 24 PR #18 一轮评审无必须项；测试 #19、运维 #20 只记账不修
- 任务 23 PR #14 一轮评审 #17 已处理；测试 #15、运维 #16 只记账
- 任务 22 PR #9 评审 #10/#11 已处理，测试 #12 / 运维 #13 只记账
- 任务 18 风控、15 会话基建、13/14 迁移、17 名单仍待人类评审
- 本机 8080 / 8081 / MySQL 3308 / Redis 6379 旧进程未杀
- `GET /admin/system/dict-types/{code}/entries` 不回 id，字典项更新/删除端点存在但列表无法定位
- Grafana 看板后置；Redis 哨兵/≥4GB 是生产目标（R31.5），P0 compose 用单实例 Redis 7
- 备份演练脚本已交付；staging 真人 PITR 签字在上线清单
- 本任务未 `gen:api:fetch`（本机 8080/8081 是旧进程，禁止杀/重启）；前端模拟器走手写 `api/simulate.ts`
- 活动允许名单的人群包编码可配置，但跨域无 CrowdPort，参与判定只认用户 ID 列表

## 尝试过但失败的方案

- 用 Aviator 直接 compile AND/in 原文：Aviator 不认 `AND`，函数调用后的 `in` 也失败。白名单改由自研解析器执行。
- `FEATURE_SET` 空集 + 把 AND 替换成 && 再交给 Aviator：`in` 列表仍无法稳定编译。
- `SpringApplicationBuilder.properties(REDIS_HOST=…)` 不压过本机 env `REDIS_HOST`；改为 initializer 注册 Redis bean。
- 去掉 `@ConditionalOnBean(DataSource)` 但保留 `DataSource` 与包可见 `(JdbcTemplate, Function)` 双构造：单元测试绿，compose 仍因 `No default constructor found` 把 admin-app 判 unhealthy。

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要改 V1–V6_1
- 不要占用 V7（任务 48 ads）
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27、任务 27 的 #30 #31、任务 28 的 #34 #35
- 不要在 `task/47-simulate` 上继续写任务 48+
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET/PUT /admin/risk/rules` 或 `GET /admin/reward/records`
- 不要发明 portal `claimMode` 或改发放/积分后端只为补来源任务 ID
- 不要把 k6 5 分钟全量灌进例行 PR CI（§7.1 发布签署，不进例行 CI）
- 不要在 compose 里把 `/internal` 暴露到公网 Nginx
- 不要把真实密钥写进 `.env.example`
- 不要跑 P1 C-11（归 48）
- 不要建 P1 域 ad
- 不要把 `SIGNIN_DAY` sourceId 改成仅 recordId（断链重攒会重复发放）
- 不要把 JDBC `characterEncoding` 写成 `utf8mb4`（库/表字符集仍是 utf8mb4）
- 不要给 `InitAdminPasswordRunner` 加回 `@ConditionalOnBean(DataSource)`
- 不要给 `InitAdminPasswordRunner` 再加第二个构造器（即使包可见 / 测用）
- 不要把动态路由安装后的导航改回 `{ type: "next" }`（刷新会再次白屏）
- 不要在首次 captcha GET 仍在飞行时就 waitForResponse + click refresh
- 不要把 e2e click-complete 改回 timeline 可见后立刻点步骤（R-e 会 `risk.blocked.generic`）
- 不要为了 e2e 关掉 V4 R-e 或改阈值
- 不要给活动域加 CrowdPort 或直访 `task_crowd`
- 不要发明 `activity:schedule` 权限码（附录 B 无此项）
- 不要 C 端 `v-html` 未消毒字段
- 不要建 `domain-metrics`
- 不要给模拟冲正调渠道撤销 / 履约适配器
- 不要发明模拟器错误码；缺参走 `common.param-invalid`

## 下一步开发顺序（最多 3 步）

1. 等 PR #64 CI。下一会话从本分支 tip 开 `task/48-ads`（未合则叠在 47 上），做广告位域
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → … → 46 PR #63 → 47 PR #64
3. **停止本会话。不要在本分支写 48+。不要合 master。**
