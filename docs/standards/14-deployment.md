# 14 · 部署与运行规范

> 适用范围：本地 Compose、预发、生产；配置、镜像、健康检查、迁移、备份。  
> 权威：R31；design §2.4、§2.6、§6.9；NFR 可用性 / 安全。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Twelve-Factor | https://12factor.net/ | I 代码库、II 依赖、III 配置、IV 后端服务、V 构建发布运行、VI 无状态进程、VII 端口绑定、IX 可抛弃、X 开发生产一致、XI 日志 |
| Twelve-Factor III Config | https://12factor.net/config | 配置进环境变量 |
| Spring Boot Deploying / Docker | https://docs.spring.io/spring-boot/how-to/deployment.html · https://docs.spring.io/spring-boot/how-to/deployment/docker.html | 可执行 jar、分层镜像 |
| Spring Boot Actuator | https://docs.spring.io/spring-boot/reference/actuator/endpoints.html | liveness / readiness |
| Flyway Production | https://documentation.red-gate.com/flyway/flyway-concepts/migrations | 启动时 migrate；已应用脚本只读 |
| Docker multi-stage | https://docs.docker.com/build/building/multi-stage/ | 构建与运行分离 |
| OWASP Secrets Management | https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html | 密钥不入库 |
| MySQL Backup | https://dev.mysql.com/doc/refman/8.0/en/backup-and-recovery.html | 全量 + binlog |

## 2. 运行形态

design §2.4 / §2.6：

| 进程 | 实例 | 职责 |
|------|------|------|
| admin-app | 1–2 | `/admin`、治理调度、默认执行 Flyway、Outbox Relay（producer=admin） |
| portal-app | ≥2 | `/api` `/internal`、无状态水平扩展、Outbox Relay（producer=portal） |
| MySQL 8 | 1（只读副本预留） | 共库 |
| Redis | 规格 Redis 7；**现网共享实例 6.0.8，逻辑库 DB 2** | 会话、缓存、锁、限流、风控计数。与若依共用 6379，禁止用 db0/db1。正式升 7 另排 |
| Nginx | 1+ | TLS 终止、静态资源、前缀路由 |
| Prometheus + Grafana | 1 套 | 指标 |

开发/测试：Compose 一键拉起以上组件（R31.1）。生产可用同一编排思想的 K8s Deployment，但路由语义不变。

**MUST NOT** 把两应用打成一个可执行文件「省事」。portal 拆走 task/reward 属重大架构变更（RL-05）。

现网共用中间件（开发机 / 虚拟机 `192.168.88.149`）**不是** R31.5 终态（哨兵或集群、≥4GB）。P0 协议（SET/ZSET/pub-sub/Lua）在 Redis 6.0.8 足够；升 7 与哨兵另排。

**MUST NOT** 让本项目 Flyway / Compose / 应用账号写到若依库 `marketing_task_platform`，也 **MUST NOT** 使用 Redis db0 / db1。只动 `mkt_platform` 与 DB 2。

## 3. 配置（12-Factor III）

1. **MUST** 随环境变化的值只来自环境变量。仓库只提交示例。
2. **MUST NOT** 在 `application-prod.yml` 写真实密码。
3. 业务可调参数走系统配置表（附录 A，52 键），经 `ConfigService`。**环境变量管连接与密钥；附录 A 管业务阈值。**
4. **MUST NOT** 存在关闭鉴权的环境变量（RL-10）。

### 3.1 环境变量清单（最低集）

名称以 `MKT_` 为前缀，避免与中间件通用名冲突。

| 变量 | 用途 |
|------|------|
| `MKT_DATASOURCE_URL` | JDBC |
| `MKT_DATASOURCE_USERNAME` | 应用账号 |
| `MKT_DATASOURCE_PASSWORD` | 应用密码 |
| `MKT_FLYWAY_USER` / `MKT_FLYWAY_PASSWORD` | 迁移账号（仅 admin-app） |
| `MKT_REDIS_URL` 或 host/port/password | Redis 连接 |
| `MKT_REDIS_DATABASE` | 逻辑库。**MUST 为 `2`**。禁止默认 0（db0/db1 是共享机上其它应用） |
| `MKT_SA_TOKEN_JWT_SECRET` 或 Sa-Token 会话密钥 | 按组件文档 |
| `MKT_INIT_ADMIN_PASSWORD` | 仅首次写入超管哈希，之后可撤 |
| `MKT_INTERNAL_APP_AES_KEY` | `sys_internal_app` secret 的 AES-256-GCM 主密钥（64 位 hex）；禁止明文落库 |
| `MKT_FLYWAY_ENABLED` | portal 默认真空/false |
| `SPRING_PROFILES_ACTIVE` | `prod` / `staging` / `local` |

`.env.example` **MUST** 列出全部键，值用占位符。R31.6。

编排文件 `deploy/.env` 可用短名 `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE`（见已提交的 `deploy/.env.example`）。注入进程时映射为 `MKT_REDIS_*`。`REDIS_DATABASE` **MUST** 为 `2`。

## 4. 构建与镜像

采纳 12-Factor V + Spring Boot Docker 指南 + 官方 multi-stage：

1. **MUST** 构建阶段与运行阶段分离。运行镜像不含 JDK 源码、不含测试、不含 `.git`。
2. **SHOULD** 使用 Spring Boot 分层 jar（`layertools`）以利用 Docker 缓存。
3. 运行用户 **MUST NOT** 为 root。
4. 时区：容器 `TZ=UTC`。业务日换算在应用层 UTC+8，不靠容器时区。
5. 同一 git commit 产出的 admin/portal 镜像打相同版本标签。
6. **MUST** 生产 profile 编译期排除调试免登与 mock（RL-10 / P10）。

## 5. 进程与端口（12-Factor VI / VII / IX）

1. 应用 **MUST** 无本地会话状态；会话在 Redis。
2. **MUST** 监听端口由环境绑定（`SERVER_PORT`），Nginx 反代。
3. 收到 `SIGTERM` **MUST** 优雅停：停止接新请求、完成进行中请求、释放调度锁。Spring Boot 默认 shutdown 开启。
4. 启动要快；就绪探针失败时不要强行杀循环，先查依赖。

## 6. 网关路由

design §2.3.2 最小语义：

```nginx
location /admin/ { proxy_pass http://admin_app; }
location /api/   { proxy_pass http://portal_app; }
# 无 /internal —— 不向公网暴露
```

1. **MUST** TLS 1.2+（NFR 安全 1）。
2. **MUST** 转发 `X-Trace-Id`、`Authorization`、`Cookie`、`X-CSRF-Token`、HMAC 头。
3. CORS 白名单只在 Nginx，版本化配置。同源部署时默认不开跨域。
4. `actuator` 与 `prometheus` **MUST** 不对公网开放（内网 location 或单独 listen）。

## 7. 数据库变更发布

1. 迁移只由 **admin-app** 默认执行（design §6.9）。多 admin 实例时 Flyway 用库锁，仍应避免同时改脚本。
2. **MUST** 先兼容后破坏（02 §2.3，R31.4）。滚动发布期间新旧代码共存。
3. 发布检查：`flyway validate` 通过才能切流量。
4. 回滚代码 **MUST** 仍能在新列存在时运行（因此禁止「一步删列」）。

## 8. 健康、依赖顺序、幂等部署

R31.1 / R31 属性 1：

1. Compose / 编排 **MUST** 声明依赖：MySQL、Redis healthy 后再启应用。
2. 存活与就绪分离（10 §6）。生产键：`management.endpoint.health.probes.enabled=true`，`management.endpoints.web.exposure.include=health,prometheus`。不要把 `readiness` 写成独立 exposure id。
3. `ci/deploy-smoke.sh`：连续两次 `up -d` → 两应用健康绿 → Flyway validate → 冒烟数据集（注册-领取-发奖-积分）两次结果一致。

## 9. 备份与恢复（R31.3）

1. **MUST** 每日全量 + 连续 binlog（或等价增量）。
2. **MUST** 能恢复到任意指定时间点。演练记录留档。
3. 备份介质与生产账号分离。备份含个人信息，按合规控制访问（NFR 数据与合规）。
4. Redis 会话可不备份；故障后用户重新登录。缓存全部可重建。

## 10. 调度与双活

1. 治理调度只在 admin-app。锁 `tryLock(0)`，失败跳过（design §6.7）。
2. Outbox 锁键按应用：`outbox:relay:admin` / `outbox:relay:portal`，SQL 带 `producer`（D-11）。
3. portal 多实例滚动：**MUST** 至少保持 1 个就绪实例。500 QPS 验收按 2 实例（NFR 性能 1）。

## 11. 密钥与安全

1. 密钥轮换：internal app secret 支持登记与失效（R15.2）。轮换期间双密钥窗口要在实现里明确，禁止「先删后加」导致回调全失败。
2. 生产关闭 springdoc UI。
3. 生产日志与镜像 **MUST NOT** 打印环境变量全集（防止 secret 进采集器）。
4. 超管初始密码：设置 `MKT_INIT_ADMIN_PASSWORD` 启动一次，确认哈希写入后从编排中移除该变量。

## 12. 开发生产一致（12-Factor X）

1. 本地 Compose（有 Docker 时）使用与规格同主版本的 MySQL 8、Redis 7 镜像，便于 IT。现网共享中间件是 MySQL 8.0.25 + Redis 6.0.8 / DB 2，P0 不升级共享实例。
2. **MUST NOT** 用 SQLite「先跑起来」。
3. 本机无 Docker 时：可以只跑单元测试，应用连 LAN 中间件（`192.168.88.149`，库 `mkt_platform`，Redis DB 2）；集成与部署冒烟留 CI。不得因此改生产拓扑或改用 db0。

## 13. AI 检查清单

- [ ] 新配置有环境变量或附录 A 键，且写入 `.env.example` / 附录 A
- [ ] Redis 逻辑库为 2，未默认落到 db0
- [ ] 无密钥进仓库
- [ ] 迁移兼容滚动
- [ ] 健康检查覆盖新的强依赖；actuator 暴露键与 10 §6 一致
- [ ] Nginx 未把 `/internal` 暴露到公网
- [ ] 调度/Relay 锁键未冲突
- [ ] 镜像非 root、UTC、分层构建
