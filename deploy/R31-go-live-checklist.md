# 首次上线验收清单

项目尚未上线。本清单保留首次发布前需要取得的证据，空勾不表示已经执行。旧规格已退役，部署以当前模板、[仓库边界](../AGENTS.md) 和已确认的环境、数据与性能决定为依据；见 [DEC-005 / DEC-006](../docs/decisions.md)。**R6 已开 / 进行中**（huihuang 2026-10-11 显式开启；**未通过** / 非 RC）。实际运行结果须引用对应批次报告，不能从本清单推断 Compose、恢复演练或压测已通过。

## 当前交付物与缺口

| 范围 | 仓库事实 | 尚需证明 |
|---|---|---|
| 测试拓扑 | [docker-compose.yml](docker-compose.yml) 声明 admin ×1、portal ×2、MySQL、Redis、Nginx、Prometheus | 成功启动、运行资源、服务就绪和真实业务链路 |
| 网关与静态资源 | [nginx.conf](nginx/nginx.conf) 已声明 portal/admin 静态根、SPA `try_files`、资产缓存；Compose 挂载 placeholders（发布时换 `web/*/dist`，见 [nginx/README.md](nginx/README.md)） | 正式域名、真实 dist 挂载、浏览器路由与缓存策略验证 |
| TLS | 模板已接线：`tls.conf.example` → `tls.conf`、`tls-servers.inc.example`、`redirect-https.inc.tls`、certs 卷与 `NGINX_HTTPS_PORT`（默认 stub 为空，HTTP-only 可启动）；**自签隔离取证**见 [nginx/drills/2026-10-11-f16-tls-static-browser.md](nginx/drills/2026-10-11-f16-tls-static-browser.md)（TLS 1.2+、跳转、HSTS、portal/admin 静态 Host） | 公网/正式 CA 证书、真实 dist、全栈 Secure Cookie 的浏览器验证 |
| 监控 | [alerts.yml](prometheus/alerts.yml) 已定义告警表达式 | 指标实际注册、名称/标签/单位匹配，告警可触发并恢复 |
| 备份恢复 | [backup.sh](backup/backup.sh) / [ship-binlog.sh](backup/ship-binlog.sh) / [restore.sh](backup/restore.sh) 全量与连续归档分离；恢复按 `BINLOG_START` 且要求显式确认隔离目标 | 在隔离环境完成[演练](backup/RESTORE-DRILL.md)并填写结果表 |
| 容量 | 存在 k6 与容量种子 | 先修正[测量缺口](../perf/README.md)，再形成可复现容量报告 |

## 安全与配置

- [ ] 镜像、仓库、日志和前端产物无真实密钥；配置来自运行环境或受控配置文件。
- [ ] 公网不可访问 `/internal/**`、`/actuator/**`、OpenAPI 和调试入口；分别验证网关与应用配置。应用侧：`application-prod.yml` 关闭 `springdoc.api-docs`；网关侧：`deploy/nginx/nginx.conf` 对 `/v3/api-docs`、`/swagger-ui` 返回 404。
- [ ] 两端账号隔离、后台 Cookie/CSRF、门户 Bearer 和 internal HMAC 均通过对应测试。
- [ ] 首次管理员密码已初始化并完成改密；确认后撤下初始化密码变量。
- [ ] Redis DB 2 与数据库隔离正确；生产 Redis 高可用及容量满足本次确认的部署目标，开发共享实例不冒充生产环境。

## 构建、迁移与发布

- [ ] 同一提交的后端镜像与前端产物有可追溯版本，构建及必要测试通过。
- [x] 从空库执行完整迁移并启动；存量测试库的升级路径按保留策略另外验证。
  - **空库迁移+启动（R6 首片，2026-10-11）**：bot box Compose `down -v` → `up -d --build`；admin 对空库应用 9 条迁移至 **v7**，51 业务表；admin + portal×2 readiness UP、nginx `/healthz` ok。证据：[drills/2026-10-11-r6-empty-flyway-dual-app.md](drills/2026-10-11-r6-empty-flyway-dual-app.md)。
  - **存量升级路径**：本片**未**覆盖（另验）。
- [x] admin 执行迁移，portal 关闭 Flyway；迁移校验成功后再接入流量。
  - **R6 首片（2026-10-11）**：compose/env 中 admin `SPRING_FLYWAY_ENABLED=true`，portal×2 `SPRING_FLYWAY_ENABLED=false` / `MKT_FLYWAY_ENABLED=false`（进程环境已核对）；admin 日志 `<< Empty Schema >>` → `Successfully applied 9 migrations … v7`；portal 日志 **0** 条 Flyway；两端 readiness UP 后再由 nginx 挂载。证据同上 [drills/2026-10-11-r6-empty-flyway-dual-app.md](drills/2026-10-11-r6-empty-flyway-dual-app.md)。
  - **说明**：本勾证明迁移归属与就绪，**不**等于全量业务旅程或双实例故障通过。
- [ ] 首次发布失败的恢复步骤与数据保留策略明确。将来采用滚动发布时，再验证新旧版本共存与回退兼容；不能把未上线理解成可以随意改已应用迁移。
- [x] portal 双实例及就绪检查有效，单节点退出时服务可继续使用。
  - **R6 双实例故障 Continuity（2026-10-11）**：bot box Compose `mkt`；baseline portal×2 readiness UP + nginx `/healthz` ok；`docker compose stop portal-app-1` → Exited (143)，portal-app-2 仍 UP；Continuity：`/healthz` 5/5 ok；经 nginx **写**（login `smoke_p0`、task start）+ **读**（points/balance=10）PASS（login+balance 5/5 failures=0）；可选恢复后双节点再 UP。证据：[drills/2026-10-11-r6-dual-instance-fault-continuity.md](drills/2026-10-11-r6-dual-instance-fault-continuity.md)。
  - **说明**：本勾覆盖单节点退出时 nginx 入口仍可服务门户流量；**不**等于容量(DEC-006)、恢复取证、真实浏览器或 RC 通过。
- [x] [deploy-smoke.sh](../ci/deploy-smoke.sh) 的重复启动与业务幂等验证有运行结果；不能仅凭脚本存在认定通过。
  - **R6 旅程片（2026-10-11）**：bot box 运行 `bash ci/deploy-smoke.sh` → exit 0 / `deploy-smoke ok`；compose up×2 就绪；Flyway `1…7`；admin 登录 + prize/task；portal `smoke_p0` 注册/登录 → 任务 start → instance **COMPLETED**、grant **GRANTED**、balance **10**；flow1==flow2 幂等；SQL users/instances/grants=1。证据：[drills/2026-10-11-r6-full-journey-deploy-smoke.md](drills/2026-10-11-r6-full-journey-deploy-smoke.md)。
  - **说明**：本勾覆盖脚本定义的重复启动与业务幂等（登录 / 领取 / 完成 / 领奖 API 旅程）；**不**等于真实浏览器静态/刷新路由，也不等于双实例故障或 RC 通过。
- [ ] 真实浏览器验证静态资源、登录、刷新路由、领取、完成任务和领奖。
  - **部分观察（非本勾通过）**：同一证据中 API 层已验证登录 / 领取 / 完成 / 领奖（见 [drills/2026-10-11-r6-full-journey-deploy-smoke.md](drills/2026-10-11-r6-full-journey-deploy-smoke.md)）；**真实浏览器 / 静态资源 / SPA 刷新路由未做**，本勾保持空。

## 数据与运行保障

- [ ] 全量备份与连续 binlog 归档已调度，备份校验与独立存储可用。
- [ ] 在隔离环境完成指定时间点恢复，验证目标时点前后数据、资金余额和引用关系。
- [ ] Prometheus 可查询实际业务指标；告警通过受控故障验证，埋点比例使用一致的事件单位。
- [ ] 性能报告记录有效业务吞吐、P95、错误/丢弃、积压恢复及资源条件，满足 DEC-006 确认的验收目标。
  - **已取证 / 整体 FAIL（勾保持空，待架构师审阅）**：bot box Compose `mkt` 跑 p0 seed + k6 P0（`DURATION=5m`）；**list / advance / complete / risk-on FAIL**；**track PASS**（≈6000 eps、P95≈11ms）；**admin-list PASS**（p0 数据量）；risk delta 数值≤20 但 risk-on 自身 HTTP/业务失败 → 不作干净 性能4 PASS。证据：[drills/2026-10-11-r6-capacity-dec006.md](drills/2026-10-11-r6-capacity-dec006.md)。**未**跑 p1/`run-full.sh`。**不**等于容量 PASS / RC；勾是否勾选待架构师审阅本报告。
- [ ] 容量场景与正常/异常业务回归均有证据；压测不属于例行 PR CI，入口与限制见 [perf/README.md](../perf/README.md)。

## 验收记录

每一勾均附提交、环境、日期、执行人和报告位置；失败项保留复现步骤及修复追踪。报告不得包含令牌或密钥。

| 提交/版本 | 环境 | 日期 | 责任人 | 证据位置 | 未解决项 / 发布结论 |
|---|---|---|---|---|---|
| `7f73856`（master tip；#135 文档/证据） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang 授权 R6 首片） | [drills/2026-10-11-r6-empty-flyway-dual-app.md](drills/2026-10-11-r6-empty-flyway-dual-app.md) | R6 **已开 / 进行中**；首片空库 Flyway + 双应用装配 **PASS**；旅程 / 双实例故障 / 容量 / 恢复 **当时未做**；**非 RC 通过** |
| `e281f60`（master tip；#136 文档/证据） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang 授权 R6 旅程片） | [drills/2026-10-11-r6-full-journey-deploy-smoke.md](drills/2026-10-11-r6-full-journey-deploy-smoke.md) | R6 **已开 / 进行中**；deploy-smoke 业务旅程（登录/领取/完成/领奖）**PASS**；双实例故障 / 容量 / 恢复 / 真实浏览器静态 **当时未做**；**非 RC 通过** |
| `e9b90ca`（master tip；本 PR 文档/证据） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang 授权 R6 双实例故障 Continuity） | [drills/2026-10-11-r6-dual-instance-fault-continuity.md](drills/2026-10-11-r6-dual-instance-fault-continuity.md) | R6 **已开 / 进行中**；portal 单节点退出 Continuity **PASS**；容量 / 恢复 / 真实浏览器静态 **未做**；**非 RC 通过** |
| `e795167`（master tip；本 PR 文档/证据 + seed/k6 可跑性小修） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang/架构师授权 R6 容量 seed+load） | [drills/2026-10-11-r6-capacity-dec006.md](drills/2026-10-11-r6-capacity-dec006.md) | R6 **已开 / 进行中**；容量 P0 **整体 FAIL**（track/admin-list PASS）；p1/full / 恢复 / 真实浏览器静态 **未做**；清单容量勾 **未勾**；**非 RC 通过** |
| 待后续切片 | | | | | |
