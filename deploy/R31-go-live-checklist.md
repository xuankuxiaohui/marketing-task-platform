# R31 上线检查清单（签署项）

上线前逐项打勾并签字。k6 P0 脚本：`perf/list.js` `advance.js` `complete.js` `risk-delta.js` `track.js` `admin-list.js`；执行 `perf/run-p0.sh`。P1 全量（含性能 6/8）：`perf/ad.js` + `perf/run-full.sh`（`--scale p1`，100 万用户 / 3000 eps）。**均不进例行 PR CI**（§7.8 发布签署项）。

## 安全（R31.2 / NFR 安全）

- [ ] 仓库与镜像无明文密钥；仅 `deploy/.env`（gitignore）与运行时环境变量
- [ ] `.env.example` 仍是占位符；`REDIS_DATABASE=2`
- [ ] TLS 1.2+（`deploy/nginx/tls.conf.example` 已纳入生产 Nginx）
- [ ] 公网 Nginx 无 `/internal`、无 `/actuator`、无 springdoc UI
- [ ] 无关闭鉴权的开关；生产 profile 无调试免登
- [ ] `MKT_INIT_ADMIN_PASSWORD` 写入哈希后已从编排移除
- [ ] Cookie HttpOnly + Secure + SameSite=Strict；CSRF 双重提交仍在

## 发布与回滚（R31.4）

- [ ] 迁移只由 **admin-app** 执行；portal `SPRING_FLYWAY_ENABLED=false`
- [ ] `flyway validate` 通过后再切流量
- [ ] 本次 schema 变更遵守先兼容后破坏（02 §2.3）；回滚代码能在新列存在时运行
- [ ] portal-app ≥ 2 实例滚动，始终保留 1 个就绪实例
- [ ] 同一 git commit 的 admin/portal 镜像打相同版本标签
- [ ] 回滚预案：切回 n-1 镜像；不回放已应用的 Flyway 版本；数据回退走备份

## 备份（R31.3）

- [ ] 每日全量 + binlog 已调度
- [ ] staging 已按 `deploy/backup/RESTORE-DRILL.md` 演练到指定时间点并留档

## Redis（R31.5）

- [ ] P0 compose / 现网开发机：单实例 Redis 7 镜像或现网 6.0.8 **DB 2**（不升级共享实例）
- [ ] 生产目标：哨兵或集群，容量 ≥ 4GB（100 万双账号会话 + 缓存冗余）——本清单签署时确认容量规划，P0 不在共享机上启哨兵

## 可观测（NFR 可观测性 3）

- [ ] Prometheus 仅内网抓取两应用 `/actuator/prometheus`（存活/就绪走 health probes）
- [ ] 告警项已加载 `deploy/prometheus/alerts.yml`（HTTP 5xx/P95、Outbox 积压、发放永久失败、库存 ≤10%、Redis 故障、降级事件、埋点丢弃 >0.1%）
- [ ] Grafana 看板可后置

## 性能（R31.2，任务 43 / 49 签署）

- [ ] k6 NFR 性能 1–5、7 在 staging compose（portal ×2）通过（`perf/run-p0.sh`，报告 `perf/reports/<日期>/`）
- [ ] k6 NFR 性能 1–8 全量 + 容量 100 万用户 / 3000 eps + 慢查询复盘（`perf/run-full.sh`，P1 任务 49；不进例行 PR CI）

## 部署幂等（R31.1）

- [ ] `ci/deploy-smoke.sh` 连续两次 `up -d`：两应用就绪绿、Flyway validate、登录冒烟、注册-领取-发奖-积分两次结果一致

## 签署

| 角色 | 姓名 | 日期 | 结果 |
|------|------|------|------|
| 研发 | | | |
| 运维 | | | |
| 安全 | | | |
