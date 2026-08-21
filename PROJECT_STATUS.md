# PROJECT_STATUS
> 阶段：**编组 J 进行中** / 当前任务：**44 签到域，已交付；PR #60 CI 续修** / 更新：2026-08-21

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1、38.2、38.3、39、40、41、42、43、44**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR #55、任务 40 PR #56、任务 41 PR #57、任务 42 PR #58、任务 43 PR #59、任务 44 PR #60 均未合 master）
- 进行中：无。**编组 J（44–49）进行中，任务 44 已交付；本会话只修 PR #60 CI（run 32433320742 e2e job 96629393266），未开任务 45**
- 下一步：下一会话从本分支 tip 开 `task/45-activity` 做编组 J 第二题。**禁止在本分支继续写 45+。禁止 merge / push / force-push master**
- Git：工作分支 `task/44-signin`（基线 `origin/task/43-e2e-k6` @ `9ac6168` / 其上叠 43 → 42 → 41 → 40 → 39 → 38.3 → … → 29）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- P1 第一域 `domain-signin` 入父 POM / ArchUnit RL-02；admin-app 与 portal-app 装配；**未**建 activity / ad
- V5 只加 `sgn_activity` / `sgn_activity_snapshot` / `sgn_record`（uk `activity_id+user_id+sign_date`）；不改 V1–V4
- 连签事实源 = `sgn_record`（含 CATCHUP），UTC+8 自然日；不建独立连签表
- 补签：同事务 `RewardPort.consume` + `INSERT sgn_record` + 可能的梯度发放；余额不足整笔回滚
- 梯度 `grantSource=SIGNIN_DAY`，`sourceId="{activityId}:{userId}:{day}"`，断链重攒不重复发放
- C-9：`SigninUniqueIT` 64 线程同用户同日；*IT 留 CI（`-DskipITs`）
- 附录 A 窗口/日限/消耗走 `SigninSettings` 默认值（7 / 1 / 100），与 V1 `sys_config` 种子同文
- C-7 失败者允许 `reward.claim.conflict` 或幂等 GRANTED；`PrizeClaimExactlyOnceIT` 以 MySQL `status='GRANTED'` 恰 1 行为准
- 领取过期兜底：`claim()` `noRollbackFor=BusinessException`，EXPIRED 翻转与 `CLAIM_EXPIRED` 同路径提交（R19.3）。IT 不把 persist-then-throw 包进 `TransactionTemplate`（同 `LoginLockCommitIT`）
- 名单写路径要 `UserContext` 操作者（`RiskOperator.requireUserId`）。`RiskRejectIdempotentIT` 与 `ListExpiryIT` / `ListConcurrentDecisionIT` / `CaseHandleAuditIT` 同模式；`RiskITSupport.start` 也落操作者，避免后续 IT 漏设
- Compose bake 按 **context** 解析 dockerfile。三处 app build context 改为仓库根，`dockerfile: deploy/docker/Dockerfile`
- 容器默认堆是 cgroup 的 25%。1G limit 下 admin-app 启动即死，compose 在 ~15s 报 unhealthy。镜像/编排设 `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`，app limit 1536M；JRE 装 fontconfig + fonts-dejavu-core（EasyCaptcha）；`HOME=/app`。smoke/e2e 失败 dump admin-app 日志
- JDBC `characterEncoding` 必须是 Java 字符集 `UTF-8`。Connector/J 不认 `utf8mb4`（Flyway `SQLException: Unsupported character encoding 'utf8mb4'`）。库/表/mysqld 仍用 utf8mb4。compose 注入 `MKT_DATASOURCE_URL`，admin/portal 默认 URL 与 `.env.example` 同步改
- V1 超管 `password_hash=''`，由 admin-app `InitAdminPasswordRunner` 读 `MKT_INIT_ADMIN_PASSWORD` 写入 BCrypt。该 runner **禁止** `@ConditionalOnBean(DataSource)`：扫描期 DataSource 尚未注册，bean 被跳过。**必须恰好一个构造器**：第二个构造器（即使包可见）让 Spring 回退无参构造，compose 报 `No default constructor found`，admin-app unhealthy。生产构造注入 `JdbcTemplate` + `@Value("${MKT_INIT_ADMIN_PASSWORD:}")`。`.env.example` 占位密码须满足 R1.5（含特殊字符）
- Vue Router 动态路由：`addRoute` 后必须 `replace` 原 path，否则刷新 / Playwright `goto` 仍走未匹配记录，画布列表不挂载，`task-create` 永远点不到。vue-pure-admin-thin 同模式
- Vue Router `stringifyQuery` 用 `encodeURI`，`redirect=/mine` 不会变成 `%2Fmine`。R32.1 e2e 用 `URLSearchParams.get("redirect")`，两种编码都认
- 首次 DRAFT 发布 `requiresConfirm=false`，不弹确认框；确认框只给已发布修订
- Playwright 填验证码必须先等到 `login-captcha-image` 可见（onMounted 的 GET 已结束），再 click refresh 并 waitForResponse。否则 waitForResponse 会吃到进行中的首次 GET，refresh 换了 `captchaId`，提交仍用旧码，注册停在 `/register`

## 改过的核心文件

- `server/domain-signin/**`（新模块）
- `server/platform-db/src/main/resources/db/migration/V5__sgn_signin.sql`
- `server/platform-contract/.../RewardPort.java`（增 `consume`）
- `server/domain-reward` `RewardPortImpl` / `PointsPort` 接线 CONSUME
- `server/domain-reward/.../ClaimAppService.java`（claim 过期翻转不回滚）
- `server/domain-reward` `PrizeClaimExactlyOnceIT` / `PrizeExpireIT` / `ClaimAppServiceTest`
- `server/domain-risk` `RiskRejectIdempotentIT` / `RiskITSupport`
- `deploy/docker-compose.yml`、`deploy/docker/Dockerfile`、`.dockerignore`
- `deploy/.env.example`（JDBC `characterEncoding=UTF-8`；`MKT_INIT_ADMIN_PASSWORD` 满足 R1.5）
- `ci/deploy-smoke.sh`、`ci/e2e-compose.sh`
- `server/admin-app` / `portal-app` `application.yml`（JDBC URL）
- `server/admin-app` `InitAdminPasswordRunner` / `InitAdminPasswordRunnerTest` / `DeployComposeTest`
- `web/apps/admin/src/views/signin/**`、`web/apps/client/src/views/signin/**`
- `web/apps/admin/src/router/guards.ts` / `index.ts` / `guards.spec.ts`（动态路由 replace 再解析）
- `web/e2e/helpers/ui.ts`、`web/e2e/journey-core.spec.ts`、`web/e2e/journey-admin.spec.ts`
- `.kiro/specs/platform-v2/tasks.md`（任务 44 勾选）
- `docs/verification-matrix.md`（R21.1 / R36.1 / SigninPage 已交付）

## 测试与验证

- 命令与结果：本轮只改 e2e helper / R32.4 断言 / compose 失败 dump。`cd web/apps/admin && pnpm exec vitest run` 与 `cd server && mvn -q -DskipITs test` 未重跑（未改 Vue 实现 / Java）
- 矩阵覆盖：verification-matrix 任务 44（R21.1 C-9 `SigninUniqueIT`、R36.1 `signin-calendar-state.spec.ts`、H5 `SigninPage.spec.ts`）；本轮补 R32.4 注册自动登录 e2e
- 未跑项及原因：`*IT` 本机 `-DskipITs` 留给 CI；未削弱断言，未用 H2 / Embedded Redis。本机未起 compose（禁止动 3308 / Redis / 8080 / 8081）；e2e / deploy-smoke 等 PR #60 CI。run 32433320742：web 绿、deploy-smoke 绿、e2e job 96629393266 红（R32.4）

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR #55、任务 40 PR #56、任务 41 PR #57、任务 42 PR #58、任务 43 PR #59、任务 44 PR #60 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37.1 → 37.2 → 37.3 → 38.1 → 38.2 → 38.3 → 39 → 40 → 41 → 42 → 43 → 44
- PR #60 `c444d44`（run 32433320742）e2e job 96629393266：compose 已健康，admin 5 绿，R32.1 绿。R32.4 提交后 URL 仍是 `/register`（5s）。根因：`fillPortalCaptcha` 的 waitForResponse 吃到 onMounted 进行中的 GET，refresh 换了 `captchaId`，提交验证码与 id 不一致
- 此前 `4cb77bc`（run 32432266368）e2e job 96626195604：门户 R32.1 实际 URL 是 `login?redirect=/mine`，断言写了 `%2Fmine`。后台登录到 dashboard 后 `goto /task/definitions` 全页刷新，动态路由 `addRoute` 后未 replace，列表页不挂载，60s 等不到 `task-create`
- 此前 `e9a18ff`（run 32431360120）e2e / deploy-smoke：compose `mkt-admin-app-1 is unhealthy`。根因：去掉 `@ConditionalOnBean` 后 runner 被扫描，但 `DataSource` 与包可见测试构造并存，Spring 找无参构造失败（`NoSuchMethodException: InitAdminPasswordRunner.<init>()`）。已收成单构造器
- 此前 `18d9a96` 后 compose 已健康，但登录 `auth.login.invalid-credential`：`@ConditionalOnBean(DataSource)` 扫描期跳过 runner，V1 空哈希未写入
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
- 本任务未 `gen:api:fetch`（本机 8080/8081 是旧进程，禁止杀/重启）；前端签到走手写 `api/signin.ts`

## 尝试过但失败的方案

- 用 Aviator 直接 compile AND/in 原文：Aviator 不认 `AND`，函数调用后的 `in` 也失败。白名单改由自研解析器执行。
- `FEATURE_SET` 空集 + 把 AND 替换成 && 再交给 Aviator：`in` 列表仍无法稳定编译。
- `SpringApplicationBuilder.properties(REDIS_HOST=…)` 不压过本机 env `REDIS_HOST`；改为 initializer 注册 Redis bean。
- 去掉 `@ConditionalOnBean(DataSource)` 但保留 `DataSource` 与包可见 `(JdbcTemplate, Function)` 双构造：单元测试绿，compose 仍因 `No default constructor found` 把 admin-app 判 unhealthy。

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27、任务 27 的 #30 #31、任务 28 的 #34 #35
- 不要在 `task/44-signin` 上继续写任务 45+
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET/PUT /admin/risk/rules` 或 `GET /admin/reward/records`
- 不要发明 portal `claimMode` 或改发放/积分后端只为补来源任务 ID
- 不要把 k6 5 分钟全量灌进例行 PR CI（§7.1 发布签署，不进例行 CI）
- 不要在 compose 里把 `/internal` 暴露到公网 Nginx
- 不要把真实密钥写进 `.env.example`
- 不要跑 P1 C-10 / C-11（归 45 / 48）
- 不要建 P1 域 activity / ad
- 不要把 `SIGNIN_DAY` sourceId 改成仅 recordId（断链重攒会重复发放）
- 不要把 JDBC `characterEncoding` 写成 `utf8mb4`（库/表字符集仍是 utf8mb4）
- 不要给 `InitAdminPasswordRunner` 加回 `@ConditionalOnBean(DataSource)`
- 不要给 `InitAdminPasswordRunner` 再加第二个构造器（即使包可见 / 测用）
- 不要把动态路由安装后的导航改回 `{ type: "next" }`（刷新会再次白屏）
- 不要在首次 captcha GET 仍在飞行时就 waitForResponse + click refresh

## 下一步开发顺序（最多 3 步）

1. 等 PR #60 CI（server / e2e / deploy-smoke）。下一会话从本分支 tip 开 `task/45-activity`（未合则叠在 44 上），做活动域 V6 / C-10
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 PR #48 → 37.1 PR #49 → 37.2 PR #50 → 37.3 PR #51 → 38.1 PR #52 → 38.2 PR #53 → 38.3 PR #54 → 39 PR #55 → 40 PR #56 → 41 PR #57 → 42 PR #58 → 43 PR #59 → 44 PR #60
3. **停止本会话。不要在本分支写 45+。不要合 master。**
