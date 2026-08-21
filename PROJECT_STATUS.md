# PROJECT_STATUS
> 阶段：**编组 J 待验收** / 当前任务：**J 评审必须项已交付** / 更新：2026-08-21

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1、38.2、38.3、39、40、41、42、43、44、45、46、47、48、49**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38 至任务 49 PR **#66** 均未合 master）
- 进行中：无。**编组 J（44–49）已交付。评审必须项在 `fix/j-review-mustfix`。禁止写新任务。**
- 下一步：人类验收编组 J + 本必须项 PR **#67**。合入前 squash 顺序 29 PR #38 → … → 48 PR #65 → 49 PR #66 → **#67**。**禁止 merge / push / force-push master**。不要开任务 50。
- Git：工作分支 `fix/j-review-mustfix`（基线 `origin/task/49-perf` @ `5c87a1d` / 其上叠 49 → 48 → … → 29）。PR **#67** 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- R3.6 / R5.4：`MustChangePasswordFilter` 拦截非改密写路径；登录/资料带 `mustChangePassword`；后台改密页 `/change-password`
- R26.6：落地 `GET/PUT /admin/risk/rules`（附录 A 范围、`risk:rule:query` / `risk:rule:config`、审计、规则页读写）
- R26.1：`GrantContext` / `RiskSubject` 传真实 IP / deviceId；空白或 `0.0.0.0` 视为缺失，不再伪造
- 领取互斥/周期键读已发布快照，不读草稿定义（R13.7 / design §5.5）
- 奖品元数据更新不覆盖 `remaining_stock`；发放可按含逻辑删除行判定 `PRIZE_DELETED`（design §5.7）
- 步骤引擎对 `PERMANENT_FAILED` 状态与永久失败异常走同一跳过路径

## 改过的核心文件

- `server/domain-identity/**`（改密过滤器、资料/登录响应、portal `mustChangePassword`）
- `server/domain-risk/**`（规则读写、范围校验、权限）
- `server/domain-task/**`（快照互斥/周期、claim/step 传 IP）
- `server/domain-reward/**`（库存、删除奖品、grant 风控主体）
- `server/platform-contract`（`GrantContext.ip/deviceId`、`RiskSubject.simulated`）
- `server/domain-signin` / `server/domain-activity`（门户写路径传 IP/device）
- `web/apps/admin`（改密页、规则页 PUT、守卫）
- `web/apps/client`（改密守卫、登录 `mustChangePassword`）
- `web/packages/shared`（portal/admin OpenAPI `mustChangePassword`）
- `web/e2e` / `ci/deploy-smoke.sh` / `perf/seed/seed.py`（init admin 先改密再写）

## 测试与验证

- 命令与结果：`cd server && mvn -q -DskipITs test` exit 0；`cd web && pnpm test` exit 0（admin 76 / client 93 / shared 6）
- 矩阵覆盖：R3.6 / R5.4 / R26.1 / R26.6 / R13.7 / design §5.5 / §5.7
- 未改 V1–V4；未用 H2 / Embedded Redis；未杀 3308 / Redis / 8080 / 8081；未提交 `perf/seed/__pycache__`

## 已知问题（只写已证实）

- 任务 29 PR #38 至任务 49 PR #66、本必须项 PR **#67** 均未合 master；叠链 29 → … → 48 → 49 → 本必须项
- PR #65 `3f38b4f` 全 CI 绿；PR #66 已开，5 分钟 k6 不进例行 CI；PR #67 跟进 portal OpenAPI + smoke 改密
- `GET /admin/reward/records` 仍未在后端/OpenAPI 导出；k6 后台列表用已有 `/admin/task/instances` `/admin/task/definitions` `/admin/points/transactions`
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
- 活动允许名单的人群包编码可配置，但跨域无 CrowdPort，参与判定只认用户 ID 列表
- 广告投放 `crowd_id` 可存；匿名不生效；登录态不直访 `task_crowd`（无 CrowdPort）
- 积分后台无筛选列表可能 filesort（`idx_user_time` 最左列是 `user_id`）；种子流水很少，破 P95 再单独立项补索引

## 尝试过但失败的方案

- 用 Aviator 直接 compile AND/in 原文：Aviator 不认 `AND`，函数调用后的 `in` 也失败。白名单改由自研解析器执行。
- `FEATURE_SET` 空集 + 把 AND 替换成 && 再交给 Aviator：`in` 列表仍无法稳定编译。
- `SpringApplicationBuilder.properties(REDIS_HOST=…)` 不压过本机 env `REDIS_HOST`；改为 initializer 注册 Redis bean。
- 去掉 `@ConditionalOnBean(DataSource)` 但保留 `DataSource` 与包可见 `(JdbcTemplate, Function)` 双构造：单元测试绿，compose 仍因 `No default constructor found` 把 admin-app 判 unhealthy。

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要改 V1–V4
- 不要 evict `identity:session`
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27、任务 27 的 #30 #31、任务 28 的 #34 #35
- 不要在 `task/49-perf` 或 `fix/j-review-mustfix` 上继续写新任务
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET /admin/reward/records`
- 不要发明 portal `claimMode` 或改发放/积分后端只为补来源任务 ID
- 不要把 k6 5 分钟全量灌进例行 PR CI（§7.8 发布签署，不进例行 CI）
- 不要在 compose 里把 `/internal` 暴露到公网 Nginx
- 不要把真实密钥写进 `.env.example`
- 不要给广告域加 CrowdPort 或直访 `task_crowd`
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
- 不要为慢查询复盘擅自加 V8 索引

## 下一步开发顺序（最多 3 步）

1. 人类验收编组 J（44–49）与本必须项 PR **#67**。合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → … → 49 PR #66 → 67
2. staging 签署：`SEED_SCALE=p1 DURATION=5m bash perf/run-full.sh`（portal ×2），归档 `perf/reports/<日期>/`
3. **停止本会话。不要写任务 50。不要合 master。**
