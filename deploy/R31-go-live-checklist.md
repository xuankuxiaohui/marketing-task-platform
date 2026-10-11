# 首次上线验收清单

项目尚未上线。本清单保留首次发布前需要取得的证据，空勾不表示已经执行。旧规格已退役，部署以当前模板、[仓库边界](../AGENTS.md) 和已确认的环境、数据与性能决定为依据；见 [DEC-005 / DEC-006](../docs/decisions.md)。**R6 已开 / 进行中**（huihuang 2026-10-11 显式开启；**未通过** / 非 RC）。实际运行结果须引用对应批次报告，不能从本清单推断 Compose、恢复演练或压测已通过。

## 当前交付物与缺口

| 范围 | 仓库事实 | 尚需证明 |
|---|---|---|
| 测试拓扑 | [docker-compose.yml](docker-compose.yml) 声明 admin ×1、portal ×2、MySQL、Redis、Nginx、Prometheus | 成功启动、运行资源、服务就绪和真实业务链路 |
| 网关与静态资源 | [nginx.conf](nginx/nginx.conf) 已声明 portal/admin 静态根、SPA `try_files`、资产缓存；Compose 挂载 placeholders（发布时换 `web/*/dist`，见 [nginx/README.md](nginx/README.md)） | 正式域名、真实 dist 挂载、浏览器路由与缓存策略验证 |
| TLS | 模板已接线：`tls.conf.example` → `tls.conf`、`tls-servers.inc.example`、`redirect-https.inc.tls`、certs 卷与 `NGINX_HTTPS_PORT`（默认 stub 为空，HTTP-only 可启动）；**自签隔离取证**见 [nginx/drills/2026-10-11-f16-tls-static-browser.md](nginx/drills/2026-10-11-f16-tls-static-browser.md)（TLS 1.2+、跳转、HSTS、portal/admin 静态 Host） | 公网/正式 CA 证书、真实 dist、全栈 Secure Cookie 的浏览器验证 |
| 监控 | [alerts.yml](prometheus/alerts.yml) 已定义告警表达式 | 指标实际注册、名称/标签/单位匹配，告警可触发并恢复 |
| 备份恢复 | [backup.sh](backup/backup.sh) / [ship-binlog.sh](backup/ship-binlog.sh) / [restore.sh](backup/restore.sh) 全量与连续归档分离；恢复按 `BINLOG_START` 且要求显式确认隔离目标 | **R6 隔离 PITR 已取证**（[backup/drills/2026-10-11-r6-isolated-restore.md](backup/drills/2026-10-11-r6-isolated-restore.md)；结果表见 [RESTORE-DRILL.md](backup/RESTORE-DRILL.md)）；生产 cron/sidecar 仍为样例 |
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
- [x] 首次发布失败的恢复步骤与数据保留策略明确。将来采用滚动发布时，再验证新旧版本共存与回退兼容；不能把未上线理解成可以随意改已应用迁移。
  - **R6 restore/deploy 片（2026-10-11）**：路径绑定的首次发布失败恢复步骤 + 数据保留策略（volumes / `down -v` / backup+ship+restore / 禁止改写已应用迁移）见 [drills/2026-10-11-r6-deploy-failure-recovery.md](drills/2026-10-11-r6-deploy-failure-recovery.md)。**证据类型：docs-only**（本片未实演一次真实失败发布）；步骤具体完整，达本清单既往 R6 文档勾选口径。
  - **说明**：本勾不覆盖滚动多版本共存；**不**等于容量 PASS / RC。
- [x] portal 双实例及就绪检查有效，单节点退出时服务可继续使用。
  - **R6 双实例故障 Continuity（2026-10-11）**：bot box Compose `mkt`；baseline portal×2 readiness UP + nginx `/healthz` ok；`docker compose stop portal-app-1` → Exited (143)，portal-app-2 仍 UP；Continuity：`/healthz` 5/5 ok；经 nginx **写**（login `smoke_p0`、task start）+ **读**（points/balance=10）PASS（login+balance 5/5 failures=0）；可选恢复后双节点再 UP。证据：[drills/2026-10-11-r6-dual-instance-fault-continuity.md](drills/2026-10-11-r6-dual-instance-fault-continuity.md)。
  - **说明**：本勾覆盖单节点退出时 nginx 入口仍可服务门户流量；**不**等于容量(DEC-006)、恢复取证、真实浏览器或 RC 通过。
- [x] [deploy-smoke.sh](../ci/deploy-smoke.sh) 的重复启动与业务幂等验证有运行结果；不能仅凭脚本存在认定通过。
  - **R6 旅程片（2026-10-11）**：bot box 运行 `bash ci/deploy-smoke.sh` → exit 0 / `deploy-smoke ok`；compose up×2 就绪；Flyway `1…7`；admin 登录 + prize/task；portal `smoke_p0` 注册/登录 → 任务 start → instance **COMPLETED**、grant **GRANTED**、balance **10**；flow1==flow2 幂等；SQL users/instances/grants=1。证据：[drills/2026-10-11-r6-full-journey-deploy-smoke.md](drills/2026-10-11-r6-full-journey-deploy-smoke.md)。
  - **说明**：本勾覆盖脚本定义的重复启动与业务幂等（登录 / 领取 / 完成 / 领奖 API 旅程）；**不**等于真实浏览器静态/刷新路由，也不等于双实例故障或 RC 通过。
- [x] 真实浏览器验证静态资源、登录、刷新路由、领取、完成任务和领奖。
  - **R6 真实浏览器 / 静态 SPA（2026-10-11）**：bot box Compose `mkt`；真实 `web/apps/{client,admin}/dist` 经 [`docker-compose.r6-browser-dist.yml`](docker-compose.r6-browser-dist.yml) 挂入 nginx（非 placeholders）；Playwright against `http://127.0.0.1:18080`：**静态 JS/CSS 200**、SPA `try_files`/硬刷新 `/mine/prizes`、注册/登录、领取/完成、领奖（已到账）+ 积分余额 10 **PASS**；admin Host `/console/` 静态烟测 PASS。证据：[drills/2026-10-11-r6-browser-static-spa.md](drills/2026-10-11-r6-browser-static-spa.md)。
  - **说明**：本勾覆盖 nginx 真实 dist + 真实浏览器门户旅程；**不**等于容量 PASS / RC。API `deploy-smoke` 旅程仍为独立已勾行。

## 数据与运行保障

- [ ] 全量备份与连续 binlog 归档已调度，备份校验与独立存储可用。
  - **部分取证（勾保持空）**：R6 隔离演练已证明 `backup.sh` 全量+`SHA256SUMS`、`ship-binlog.sh` 连续归档/`INVENTORY.tsv`/幂等/失败即停、独立归档目录可用（[backup/drills/2026-10-11-r6-isolated-restore.md](backup/drills/2026-10-11-r6-isolated-restore.md)）。生产 cron/sidecar **未**装机（仅 [backup/schedule/](backup/schedule/) 样例）→ 本勾不勾。
- [x] 在隔离环境完成指定时间点恢复，验证目标时点前后数据、资金余额和引用关系。
  - **R6 restore/deploy 片（2026-10-11）**：bot box throwaway Compose 项目 `mkt-r6-restore`（MySQL only；与仍 UP 的 `mkt` 隔离）；full → `ship-binlog.sh` → A/B → `STOP_DATETIME` PITR via `BINLOG_ARCHIVE_DIR`；A 在 / B 不在 / ledger=**150** → **PITR_OK**；refuse exit 2；size-mismatch fail-loud；fixture PASSED。证据：[backup/drills/2026-10-11-r6-isolated-restore.md](backup/drills/2026-10-11-r6-isolated-restore.md)。
  - **说明**：本勾覆盖隔离 PITR 标记/账本核对；**不**等于生产已调度归档、容量 PASS、真实浏览器或 RC。
- [ ] Prometheus 可查询实际业务指标；告警通过受控故障验证，埋点比例使用一致的事件单位。
- [ ] 性能报告记录有效业务吞吐、P95、错误/丢弃、积压恢复及资源条件，满足 DEC-006 确认的验收目标。
  - **已取证 / 整体 FAIL（勾保持空，待架构师审阅）**：bot box Compose `mkt` 跑 p0 seed + k6 P0（`DURATION=5m`）；**list / advance / complete / risk-on FAIL**；**track PASS**（≈6000 eps、P95≈11ms）；**admin-list PASS**（p0 数据量）；risk delta 数值≤20 但 risk-on 自身 HTTP/业务失败 → 不作干净 性能4 PASS。证据：[drills/2026-10-11-r6-capacity-dec006.md](drills/2026-10-11-r6-capacity-dec006.md)。**未**跑 p1/`run-full.sh`。**不**等于容量 PASS / RC；勾是否勾选待架构师审阅本报告。
  - **list 路径补强（#142；勾仍空）**：根因=每请求全量 `listPublished`+snapshots（~1000）且未读已有 `TASK_PUBLISHED_INDEX`；已接 catalog 缓存 + Hikari portal40/admin20。证据：[drills/2026-10-11-r6-capacity-list-path.md](drills/2026-10-11-r6-capacity-list-path.md)。**未**重跑 k6；**不**宣称 list/DEC-006 PASS。
  - **list 重测 after #142（#143；勾仍空）**：bot box Compose `mkt` 重建 portal 后 targeted list smoke 30s + 5m **FAIL**（~116 http/s vs 500；list P95~30s；checks~38%；http_req_failed~61%；dropped 仍高）。相对 #139：吞吐约翻倍，但出现大量 502/500/Hikari wait（#139 http_fail≈0）。证据：[drills/2026-10-11-r6-capacity-list-retest.md](drills/2026-10-11-r6-capacity-list-retest.md)。**fail-stop**；**未**开 advance；**不**改阈值 / 重开 DEC-006；**不**等于容量 PASS / RC。
  - **list hot-path 补强（#144；勾仍空）**：#142 后 general_log 显示每 list 仍打 risk_hit_log COUNT + BLACK/WHITE + ~1044 元 tuple IN；另 L1 catalog 命中仍全量反序列化 snapshot JSON。已改为 `onUserList`、`listByUser` 周期匹配、解析复用、INDEX TTL 5m。证据：[drills/2026-10-11-r6-capacity-list-hotpath.md](drills/2026-10-11-r6-capacity-list-hotpath.md)。**不**宣称 list/DEC-006 PASS；**无**改阈值。
  - **list 正式 5m after #144（本 PR；勾仍空）**：bot box Compose `mkt` 重建 portal 后 formal list smoke 30s（全绿）+ 5m **整体 FAIL**（http≈499.4/s；list P95≈4.13ms；checks 100%；http_req_failed 0%；**dropped_iterations ≈0.580/s (174) 越过 rate&lt;0.05**）。相对 #143：吞吐/延迟/错误全面改善，唯 dropped 仍挡门。证据：[drills/2026-10-11-r6-capacity-list-5m-after-144.md](drills/2026-10-11-r6-capacity-list-5m-after-144.md)。**fail-stop**；**未**开 advance；**不**改阈值 / 重开 DEC-006；**不**等于容量 PASS / RC。
  - **list residual dropped 补强（本 PR；勾仍空）**：#146 后 drops 集中在 ~4m16s VU 80→243（INDEX TTL 5m 对齐 5m 门 + 冷解析 stampede）。已：`list.js` warm→formal + `preAllocatedVUs` 250 + drop window；INDEX TTL 30m；Tomcat 300/accept 200；nginx keepalive(+Host 继承修复)；`fromCatalog` 单飞解析。**正式 5m list PASS**（~500/s；P95≈4.3ms；dropped **0**；checks 100%；http_fail 0%）。证据：[drills/2026-10-11-r6-capacity-list-dropped-residual.md](drills/2026-10-11-r6-capacity-list-dropped-residual.md)。**勾仍空**（架构师决定）；**未**开 advance；**不**改门禁数值；**不**宣称 RC / 全 P0 PASS。
- [ ] 容量场景与正常/异常业务回归均有证据；压测不属于例行 PR CI，入口与限制见 [perf/README.md](../perf/README.md)。

## 验收记录

每一勾均附提交、环境、日期、执行人和报告位置；失败项保留复现步骤及修复追踪。报告不得包含令牌或密钥。

| 提交/版本 | 环境 | 日期 | 责任人 | 证据位置 | 未解决项 / 发布结论 |
|---|---|---|---|---|---|
| `7f73856`（master tip；#135 文档/证据） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang 授权 R6 首片） | [drills/2026-10-11-r6-empty-flyway-dual-app.md](drills/2026-10-11-r6-empty-flyway-dual-app.md) | R6 **已开 / 进行中**；首片空库 Flyway + 双应用装配 **PASS**；旅程 / 双实例故障 / 容量 / 恢复 **当时未做**；**非 RC 通过** |
| `e281f60`（master tip；#136 文档/证据） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang 授权 R6 旅程片） | [drills/2026-10-11-r6-full-journey-deploy-smoke.md](drills/2026-10-11-r6-full-journey-deploy-smoke.md) | R6 **已开 / 进行中**；deploy-smoke 业务旅程（登录/领取/完成/领奖）**PASS**；双实例故障 / 容量 / 恢复 / 真实浏览器静态 **当时未做**；**非 RC 通过** |
| `e9b90ca`（master tip；本 PR 文档/证据） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang 授权 R6 双实例故障 Continuity） | [drills/2026-10-11-r6-dual-instance-fault-continuity.md](drills/2026-10-11-r6-dual-instance-fault-continuity.md) | R6 **已开 / 进行中**；portal 单节点退出 Continuity **PASS**；容量 / 恢复 / 真实浏览器静态 **未做**；**非 RC 通过** |
| `e795167`（master tip；本 PR 文档/证据 + seed/k6 可跑性小修） | bot box Compose `mkt`（throwaway volume） | 2026-10-11 CST | Grok Bot（huihuang/架构师授权 R6 容量 seed+load） | [drills/2026-10-11-r6-capacity-dec006.md](drills/2026-10-11-r6-capacity-dec006.md) | R6 **已开 / 进行中**；容量 P0 **整体 FAIL**（track/admin-list PASS）；p1/full / 恢复 / 真实浏览器静态 **未做**；清单容量勾 **未勾**；**非 RC 通过** |
| `04fa5fd`（master tip；#140 前 tip；restore/deploy 证据） | bot box Compose `mkt-r6-restore`（throwaway MySQL；`mkt` 未 down） | 2026-10-11 CST | Grok Bot（架构师指定 restore/deploy 下一片；huihuang 连续切片） | [backup/drills/2026-10-11-r6-isolated-restore.md](backup/drills/2026-10-11-r6-isolated-restore.md)；[drills/2026-10-11-r6-deploy-failure-recovery.md](drills/2026-10-11-r6-deploy-failure-recovery.md) | R6 **已开 / 进行中**；隔离 PITR **PASS**；发布失败恢复 **docs-only PASS**；容量勾仍空/FAIL；真实浏览器当时未做；**非 RC 通过** |
| `cfcd77d`（master tip；#141 前 tip；browser SPA 证据） | bot box Compose `mkt`（throwaway volume；nginx 真实 dist） | 2026-10-11 CST | Grok Bot（架构师指定真实浏览器静态/SPA 下一片；huihuang 连续切片） | [drills/2026-10-11-r6-browser-static-spa.md](drills/2026-10-11-r6-browser-static-spa.md) | R6 **已开 / 进行中**；真实浏览器静态/SPA/登录/领取/完成/领奖 **PASS**；容量勾仍空/FAIL；p1/full / 生产 CA 等仍待；**非 RC 通过** |
| `ff1864b`（master tip；Merge #141；#142 list 路径补强） | bot box 代码/单测（Compose `mkt` 仍 UP；未重跑 k6） | 2026-10-11 CST | Grok Bot（架构师：容量补强先 list；huihuang 连续切片） | [drills/2026-10-11-r6-capacity-list-path.md](drills/2026-10-11-r6-capacity-list-path.md) | R6 **已开 / 进行中**；list catalog 缓存 + Hikari 池补强；**未**重测 DEC-006；容量勾仍空/FAIL；**非 RC 通过** |
| `6ac4133`（master tip；Merge #142；#143 list 重测证据） | bot box Compose `mkt`（throwaway volume；portal rebuild） | 2026-10-11 CST | Grok Bot（架构师：#142 后 list 重测；fail-stop；huihuang 连续切片） | [drills/2026-10-11-r6-capacity-list-retest.md](drills/2026-10-11-r6-capacity-list-retest.md) | R6 **已开 / 进行中**；list 重测 **FAIL**（~116/s；http_fail~61%）；容量勾仍空；advance **未开**；**非 RC 通过** |
| `1e4bf4e`（master tip；Merge #143；#144 list hot-path） | bot box 诊断 + 单测（Compose `mkt` UP） | 2026-10-11 CST | Grok Bot（架构师：list 仍 FAIL → Tomcat/GC/MySQL；huihuang 连续切片） | [drills/2026-10-11-r6-capacity-list-hotpath.md](drills/2026-10-11-r6-capacity-list-hotpath.md) | R6 **已开 / 进行中**；onUserList + listByUser + 解析复用；容量勾仍空/FAIL；**非 RC 通过** |
| `80482e7`（master tip；Merge #144；#146 list 5m 重测） | bot box Compose `mkt`（throwaway；portal rebuild） | 2026-10-11 CST | Grok Bot（架构师：#144 后 formal 5m list；fail-stop；huihuang 连续切片） | [drills/2026-10-11-r6-capacity-list-5m-after-144.md](drills/2026-10-11-r6-capacity-list-5m-after-144.md) | R6 **已开 / 进行中**；list 5m **FAIL**（dropped≈0.58/s；其余门绿）；容量勾仍空；advance **未开**；**非 RC 通过** |
| `df190bd`（master tip；Merge #146；本 PR residual dropped） | bot box 代码/配置（Compose `mkt`；**待**合入后 5m） | 2026-10-11 CST | Grok Bot（架构师：残差 dropped；huihuang 连续切片） | [drills/2026-10-11-r6-capacity-list-dropped-residual.md](drills/2026-10-11-r6-capacity-list-dropped-residual.md) | R6 **已开 / 进行中**；warm→formal + INDEX TTL30m + preVUs250 + Tomcat300/accept200 + nginx keepalive；容量勾仍空；advance **未开**；**非 RC 通过** |
| 待后续切片 | | | | | |
