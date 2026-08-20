# PROJECT_STATUS
> 阶段：**编组 I 进行中** / 当前任务：**42 部署编排，已交付** / 更新：2026-08-20

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–36、37.1、37.2、37.3、38.1、38.2、38.3、39、40、41、42**（任务 22–28 已 squash 合 master，#36 → `d7a02eb`；任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR #55、任务 40 PR #56、任务 41 PR #57、任务 42 本 PR 均未合 master）
- 进行中：无。**编组 I（40–43）进行中，任务 42 已交付**
- 下一步：下一会话从本分支 tip 开 `task/43-e2e-k6` 做编组 I 第四题（Playwright 旅程 + k6）。**禁止在本分支继续写 43+。禁止 merge / push / force-push master**
- Git：工作分支 `task/42-deploy-compose`（基线 `origin/task/41-two-admin-topology` @ `1059adf` / 其上叠 41 → 40 → 39 → 38.3 → 38.2 → 38.1 → 37.3 → 37.2 → 37.1 → 36 → 35 → 34 → 33 → 32 → 31 → 30 → 29）。PR 目标 **master**。唯一长期分支是 **master**

## 关键技术决策（本轮新发生的）

- Compose 一键拉起 MySQL 8 + Redis 7 + admin-app + portal-app×2 + Nginx 前缀路由 + Prometheus；host 口 18080/19090，不占用本机 3308 / 6379 / 8080 / 8081
- Nginx 公网只反代 `/admin/` 与 `/api/`；`/internal` 与 `/actuator` 返回 404；Prometheus 在 docker 网内抓两应用 `/actuator/prometheus`
- Flyway 仅 admin-app（`MKT_FLYWAY_USER`）；portal `MKT_FLYWAY_ENABLED=false` 且 compose 里 `SPRING_FLYWAY_ENABLED=false`；portal 等 admin 就绪后再起
- `.env.example` 全量键占位、`REDIS_DATABASE=2`；密钥只进 gitignore 的 `deploy/.env`
- 备份：每日全量 + binlog（`deploy/backup/*.sh` + `RESTORE-DRILL.md`）；R31 上线清单在 `deploy/R31-go-live-checklist.md`；Grafana 看板后置，告警规则按 NFR 可观测性 3 落 `deploy/prometheus/alerts.yml`
- `ci/deploy-smoke.sh`：连续两次 `up -d`、健康检查、Flyway validate、后台登录冒烟、注册-领取-发奖-积分两次指纹一致
- Redis 健康检查走 `RedisHealthIndicator`（`SessionAvailability.ping`），纳入 readiness group `db,redis`

## 改过的核心文件

- `deploy/docker-compose.yml`、`deploy/docker/Dockerfile`、`deploy/nginx/nginx.conf`、`deploy/.env.example`
- `deploy/prometheus/prometheus.yml`、`deploy/prometheus/alerts.yml`
- `deploy/backup/backup.sh`、`deploy/backup/restore.sh`、`deploy/R31-go-live-checklist.md`
- `ci/deploy-smoke.sh`、`.github/workflows/ci.yml`（`deploy-smoke` job）
- `server/admin-app` / `portal-app` `application.yml`（probes + prometheus + Flyway 账号）
- `server/platform-infra` `RedisHealthIndicator`、`InfraAutoConfiguration`（`MKT_REDIS_*`）
- `.kiro/specs/platform-v2/tasks.md`（任务 42 勾选）

## 测试与验证

- 命令与结果：`cd server && mvn -q -DskipITs test` 本机 exit 0；`DeployComposeTest` + `RedisHealthIndicatorTest` 覆盖编排清单与 Redis 就绪
- 矩阵覆盖：verification-matrix 任务 42（R31.1 `ci/deploy-smoke.sh`）
- 未跑项及原因：完整 compose 拉起与登录冒烟留给 CI `deploy-smoke` job（本机 3308/6379/8080/8081 在跑，编排故意不绑这些口）；未削弱断言，未用 H2 / Embedded Redis

## 已知问题（只写已证实）

- 任务 29 PR #38、任务 30 PR #39、任务 31 PR #40、任务 32 PR #42、任务 33 PR #43、任务 34 PR #46、任务 35 PR #47、任务 36 PR #48、任务 37.1 PR #49、任务 37.2 PR #50、任务 37.3 PR #51、任务 38.1 PR #52、任务 38.2 PR #53、任务 38.3 PR #54、任务 39 PR #55、任务 40 PR #56、任务 41 PR #57、任务 42 本 PR 均未合 master；叠链 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36 → 37.1 → 37.2 → 37.3 → 38.1 → 38.2 → 38.3 → 39 → 40 → 41 → 42
- `GET/PUT /admin/risk/rules` 未在后端/OpenAPI 导出；规则页不发明读写契约（R26.6）
- `GET /admin/reward/records` 未在后端/OpenAPI 导出；发放页不发明列表契约
- portal `PrizeCardView.sourceTaskId` / `PointsPortalTxView.sourceTaskId` 后端现返回 null；有值才跳转，本任务不改发放/积分后端
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
- `GET /admin/system/dict-types/{code}/entries` 不回 id，字典项更新/删除端点存在但列表无法定位；本任务不发明列表接口
- Grafana 看板后置；Redis 哨兵/≥4GB 是生产目标（R31.5），P0 compose 用单实例 Redis 7
- 备份演练脚本已交付；staging 真人 PITR 签字在上线清单

## 尝试过但失败的方案

- 用 Aviator 直接 compile AND/in 原文：Aviator 不认 `AND`，函数调用后的 `in` 也失败。白名单改由自研解析器执行。
- `FEATURE_SET` 空集 + 把 AND 替换成 && 再交给 Aviator：`in` 列表仍无法稳定编译。
- `SpringApplicationBuilder.properties(REDIS_HOST=…)` 不压过本机 env `REDIS_HOST`；改为 initializer 注册 Redis bean。

## 明确禁止下一会话做的事

- 不要合 master，不要 push 到 master
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要用 H2 / Embedded Redis 让 IT 本地变绿
- 不要再创建或推送 `main`
- 不要削弱 CSRF 双重提交断言
- 不要用 REQUIRES_NEW 修登录失败落库（05-security §3.4）
- 不要修任务 22 的 #12 #13、任务 23 的 #15 #16、任务 24 的 #19 #20、任务 25 的 #21 #22、任务 26 的 #26 #27、任务 27 的 #30 #31、任务 28 的 #34 #35
- 不要在 `task/42-deploy-compose` 上继续写任务 43+
- 不要做多轮代码评审
- 不要杀本机 MySQL 3308 / Redis / admin 8080 / portal 8081
- 不要发明 `GET/PUT /admin/risk/rules` 或 `GET /admin/reward/records`
- 不要发明 portal `claimMode` 或改发放/积分后端只为补来源任务 ID
- 不要把 Playwright 骨架改成任务 43 的全链路 CI 阻断（任务 43 另开分支）
- 不要跑 P1 C-9 / C-10 / C-11
- 不要在 compose 里把 `/internal` 暴露到公网 Nginx
- 不要把真实密钥写进 `.env.example`

## 下一步开发顺序（最多 3 步）

1. 下一会话从本分支 tip 开 `task/43-e2e-k6`（未合则叠在 42 上），做任务 43 Playwright E2E + k6
2. 合入前不要从过期 master 另开分支；squash 顺序 29 PR #38 → 30 PR #39 → 31 PR #40 → 32 PR #42 → 33 PR #43 → 34 PR #46 → 35 PR #47 → 36 PR #48 → 37.1 PR #49 → 37.2 PR #50 → 37.3 PR #51 → 38.1 PR #52 → 38.2 PR #53 → 38.3 PR #54 → 39 PR #55 → 40 PR #56 → 41 PR #57 → 42 本 PR
3. **停止本会话。不要在本分支写 43+。不要合 master。**
