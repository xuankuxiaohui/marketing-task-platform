# 全流程核验：2026-10-07

**当前核心业务链路可以运行，但未通过全部质量与部署门禁。** 后端各模块最新报告合计 956 项单元/属性/架构 + 123 项 IT，全部通过；来自分段执行与受影响用例重跑，并非一次未中断的完整 reactor 通过。两端 376 项前端测试通过。真实浏览器最终 15 项：14 通过、1 失败、0 跳过；失败项是完成反馈的奖品名称错误。后台仍有 9 处类型错误，原 Compose 构建仍被旧 Docker 宿主阻塞。

当前功能入口与五项明确实现缺口见 [功能清单](current-features.md)。本报告只陈述本轮证据；项目仍未上线，不把功能入口、历史勾选或测试编译当作实际通过。

## 1. 代码与运行环境

| 项目 | 本轮实际环境 |
|---|---|
| 代码 | `feat/portal-guest-home-ux`，HEAD `dd9feba` 加当前未提交工作区；保留已有变更，没有切分支、提交或推送 |
| 应用 | 当前两个可执行 JAR；后台 1 进程、门户 2 进程，本机 JDK 26.0.2、Spring Boot 4.1.0、`prod` profile |
| 虚拟机 | `192.168.88.149`，CentOS 7 / kernel `3.10.0-1160.83.1.el7.x86_64`，Docker 19.03.13 / API 1.40；独立 `docker-compose` 2.23.3 |
| 业务数据库 | 新建 Compose 项目 `mktverify20261007`；缓存 MySQL 镜像实际为 8.0.25；独立 schema、账号、卷和随机凭据 |
| 业务 Redis | 独立 Redis 7.0.5 容器，固定 DB 2；独立密码，仅 VM 回环端口，经 SSH 转发 |
| IT | 原 Testcontainers 2.0.5 容器生命周期和默认安全策略；显式替换为缓存 MySQL 8.0.25，标准 Redis `7-alpine` 实际 7.4.11；保留原断言、JaCoCo 和 Flyway |
| 浏览器代理 | 已安装 Chromium、真实 Vite 页面 → SSH → VM Nginx 1.27.5 → SSH → 后台及两个门户进程 → SSH → VM MySQL / Redis；不 mock 业务 API |

没有连接共享 `3308` / `6379` 做业务写入、清表或故障注入；现有 MySQL、Redis、若依容器不属于本轮资源。未安装或升级全局依赖、Docker 或 JDK，未修改真实部署环境文件。凭据只进入被忽略的临时环境文件和进程环境，不进入本报告。

Nginx 使用仓库原规则，临时副本仅将监听改为 VM 回环 `18090`，以及将第二门户上游端口改为 `8082` 适配三个 SSH 反向转发；路由、认证头转发和屏蔽规则保持原配置。该证据证明真实代理链路，但**不能替代原完整 Compose 容器部署成功**。

## 2. 实际执行结果

| 检查 | 结果 | 边界 |
|---|---|---|
| 后端全模块 `verify` 最新报告 | **956 单元/属性/架构 + 123 IT，失败/错误/跳过均 0** | 14 模块分段取证；67 个 IT 源文件均匹配本轮 XML；排除 2 份旧 OpenApiGroupsIT 报告 |
| Flyway | 通过 | 全部 9 个迁移到 v7、再次校验/迁移不重复执行；真实 MySQL IT 与业务应用启动均执行 |
| 门户 Vitest | 51 文件、245 项通过 | 当前门户组件、会话与请求、列表和纯逻辑 |
| 后台 Vitest | 48 文件、131 项通过 | 当前后台组件与逻辑；不意味着类型门禁通过 |
| 类型 | client 通过；admin 失败 | 后台 9 处错误，见下节 |
| ESLint | 0 错误 | client 0 警告；admin 124 警告，未消除或隐藏 |
| Vite 生产产物 | 两端通过 | 后台有 >500 kB chunk 警告；后台完整 build 含 vue-tsc，整体门禁仍失败 |
| Playwright 真实链路 | **14 通过、1 失败、0 跳过** | 门户 9 项行为与后台 5 项均通过；独立奖品反馈名称契约检查失败 |
| 补充 HTTP | 真实 Nginx 阶段 10 个业务/安全检查 + 1 个认证准备检查通过 | 实际双门户进程 / Redis；另有 5 条组摘要，不计作独立用例；初始临时 Node 网关报告保留 |
| Nginx | 配置检查与真实代理通过 | internal、actuator、Swagger UI 返回 404；prod OpenAPI JSON 匿名返回 200，属于上线检查缺口 |
| Prometheus | 配置及 8 条规则语法通过；三个应用指标接口可读 | 已看到 JVM / Hikari 指标；没有宣称抓取拓扑、告警触发或容量通过 |
| 原 Compose 构建 | **失败** | 默认 Docker 安全策略下 JDK 26 创建 GC 线程 EPERM；MySQL 8.0.46 初始化也创建线程失败 |

后端数量按每个模块的最新 XML 汇总，不累计重跑次数；“IT”包含真实数据库/Redis、完整上下文及部分内存/过滤器场景，不把 123 项统称真实 HTTP 旅程。共核对 360 份本轮报告，67 个 IT 源文件无缺失或额外 suite。

| 模块 | 单元/属性/架构 | IT | 最新结果 |
|---|---:|---:|---|
| platform-kernel | 62 | 7 | 通过 |
| platform-contract | 12 | 0 | 通过 |
| platform-db | 17 | 2 | 通过 |
| platform-infra | 42 | 6 | 通过 |
| domain-identity | 169 | 11 | 通过 |
| domain-task | 242 | 25 | 通过；含生命周期 11 项 |
| domain-reward | 119 | 14 | 通过 |
| domain-risk | 92 | 7 | UTC fixture 修正后通过 |
| domain-tracking | 40 | 4 | 通过 |
| domain-signin | 37 | 2 | 通过 |
| domain-activity | 25 | 1 | 通过 |
| domain-ad | 9 | 2 | 通过 |
| admin-app | 76 | 4 | 标记与 UTC fixture 修正后通过 |
| portal-app | 14 | 38 | 通过 |
| **合计** | **956** | **123** | **最新报告全部通过** |

### 浏览器与真实 HTTP 覆盖

浏览器完整执行：游客首页与私有页登录弹层 → 注册自动登录 → 指定任务曝光埋点 → 任务详情与领取 → CLICK 推进及 REWARD → 完成奖品明细 → 手动领奖 `GRANTED / ARRIVED` → 余额精确 10 → 退出，旧令牌访问返回 401。后台执行真实验证码登录、画布保存、非法表达式拒绝、发布、实例查询。注册主旅程使用唯一新账号；其他登录与后台查询使用本轮独立栈内的测试 fixture。按 taskId / instanceId / recordId 定位，不靠列表第一项或模拟请求完成业务。

补充 HTTP 验证包含：同一令牌跨两门户读取；后台与门户令牌/路径隔离；有效签名也不能穿过公开网关进入 internal；私有回调非法签名、旧时间戳和跨节点 nonce 重放拒绝；进度新 nonce + 相同 reportId 不累加，独立报告完成实例；今日签到 +10 且跨节点重试不二次发奖；昨日补签扣 100 并获得 +20，重复请求不二次扣/奖，EARN 2 笔、CONSUME 1 笔；非法日期和额度拒绝不改变余额；一个门户登出后，两门户的档案、积分和我的任务均拒绝旧令牌。

### 已确认的失败和偏差

| 问题 | 证据与影响 |
|---|---|
| 完成反馈的奖品名称错误 | [StepEngine](../server/domain-task/src/main/java/com/mkt/task/engine/StepEngine.java) 用 REWARD 步骤的 `def.name()` 填 `RewardFeedbackView.prizeName`；真实反馈为 `reward`，实际奖品为 `e2e_core_pts points`。领奖和积分通过，反馈名称契约单独保留失败 |
| 后台类型门禁失败 | `FormDialog.spec.ts` 3 处、`ActivityPage.spec.ts` 1 处：`get(...).exists()` 类型不允许；`AdminLayout.vue` 的 `open` 隐式 any；`table.spec.ts` 调用参数不符；Dashboard / Config / Role 测试的 `Result<unknown>` 泛型不符，共 9 处 |
| prod 匿名 OpenAPI JSON | 经真实 Nginx，`/admin/v3/api-docs`、`/api/v3/api-docs` 和门户 internal 文档分组均 200；分别包含 127、31、3 个 path。UI 关闭不能满足 [上线检查单](../deploy/R31-go-live-checklist.md) 的 OpenAPI 公网不可访问要求；需要单独收口 JSON 与导出环境 |
| Docker 宿主兼容阻塞 | 标准 JDK 镜像默认 `java -version` 创建线程 EPERM；一次独立 `seccomp=unconfined` 对照成功，推断为旧默认 seccomp 与新用户态线程创建的兼容问题。该选项只用于诊断，不用于业务/IT或生产模板；MySQL 8.0.46 默认启动也失败 |
| 门户 UI 与当时规格不一致 | 当时的 R32.1 / design-api 写领取等跳登录、活动半屏；组件及既有组件测试使用登录弹层、活动卡进入全页任务详情。本轮按当时实现核验，未把源码反向改为已批准规格，见 [DEC-008](decisions.md#dec-008门户交互与规格的待对齐项)。旧规格已于 2026-10-09 退役，历史原文见 [追溯说明](README.md)；不改变本报告的执行结果 |

## 3. 执行入口与证据口径

后端使用 `mvn -B -o -gs <临时settings> -s <临时settings> -f server/pom.xml verify`，进程内设置 JDK 26、`DOCKER_HOST=tcp://127.0.0.1:<SSH转发端口>`、`TESTCONTAINERS_HOST_OVERRIDE=192.168.88.149`、远端 Docker socket 路径。本轮临时 image substitutor 仅把 MySQL 8.0 映射为明确记录的缓存镜像；通过 no-op Java agent 加测试 classpath，不改字节码、POM、`argLine`、覆盖率阈值或容器安全选项。不能把该结果写成标准 MySQL 8.0.46 默认宿主通过。

首段 `CaseHandleAuditIT` 的审计/处理记录 3 条断言已经通过，失败在时间窗口命中数 0 / 期望 1。本机 Asia/Shanghai 下，JDBC 测试连接未设置时区，`Timestamp.from(12:00Z)` 按本机时间写入 DATETIME，而查询使用 UTC 的 12:00 窗口。[RiskITSupport](../server/domain-risk/src/test/java/com/mkt/risk/it/RiskITSupport.java) 仅增加 `connectionTimeZone=UTC`，与正式应用 URL 一致，没有降低断言或改业务规则；原失败 XML 保留，整域共享 fixture 重跑。

后台 [AggregationIdempotentIT](../server/admin-app/src/test/java/com/mkt/admin/metrics/AggregationIdempotentIT.java) 与 [SimulationIsolationIT](../server/admin-app/src/test/java/com/mkt/admin/simulate/SimulationIsolationIT.java) 同样未声明测试连接时区，写入后再按中国时区分桶导致查询落到次日；聚合计数、幂等及模拟数据排除的前置断言已经通过。两者各自工厂增加同一 UTC 参数，原断言真实重跑均通过。另恢复了真实旅程标题中的 `R32.1` 追溯标记，解决 `PerfBaselineTest` 的静态存在性检查；没有改为跳过检查。三个阶段快照保留 4 个历史失败及最新恢复结果。

为继续未执行模块，单独 `-DskipTests=true install` 安装当前前置模块及测试 JAR，**只是构建准备，不计作测试通过**。之后对 risk、tracking、signin、activity、ad、admin-app、portal-app 执行 `-T 2 -fae verify`；该验证命令没有跳过测试。临时 settings 只补齐既有缓存的 repository ID，未更改全局配置、已有依赖元数据或版本。

前端在各 app 目录使用已安装 CLI：`node node_modules/vitest/vitest.mjs run`、`node node_modules/vue-tsc/bin/vue-tsc.js --noEmit`、`node node_modules/vite/bin/vite.js build`；web 目录使用 ESLint 和 `node node_modules/@playwright/test/cli.js test -c playwright.config.ts`。本机 pnpm 启动器存在下载问题，因此没有重新安装依赖。

真实旅程新增可配置的 `E2E_ENV_FILE` 与 loopback Redis 隧道参数，默认本地 Compose 用法保留；Redis 适配仅 AUTH → SELECT 2 → GET 验证码，不能访问共享 6379。具体用法见 [web README](../web/README.md)。修正了旧跳转/半屏假设、隐藏 Ant Select option 和不确定 first 定位；奖品反馈的真实错误没有改为通过。

运行日志、XML 汇总、前端 JSON 结果、HTTP 脱敏结果、镜像 digest 与 Docker 失败诊断在被忽略的 `.run/`。最终汇总为 `backend-final-verification-summary.json`，明确四段执行、各模块最新完成状态及历史失败；不依赖旧统计。按日志中的测试类完成记录与 XML 更新时间排除陈旧报告；精确进程起点未记录，时间窗使用首条本轮日志前 60 秒的保守下界。后台最后只重跑两项受影响 IT，已通过的 TwoAdminAppIT 两项使用前一段本轮新报告。

## 4. 尚未得到的证明与后续顺序

- 第三方支付宝、微信、话费等真实渠道仍为 stub；活动人群和广告人群定向缺口、后台逐笔发奖查询及权限节点编辑页见功能清单。
- 部分名为 IT 的身份、回放用例使用内存会话/MockFilter；`TwoAdminAppIT` 手工装配服务，不是两个后台 HTTP 进程。真实双门户 HTTP 补充验证单独记录，不能扩大为后台跨进程踢出全路径通过。
- 未执行容量、全量 RBAC/审计浏览器矩阵、全部奖励类型、真实渠道对账、TLS/静态资源部署、备份恢复或告警触发；没有上线结论。
- 下一批先修复反馈奖品名、后台类型和公开 JSON 边界，并对齐门户交互规格；再处理已登记的人群、事务、查询与缓存重构。原 Compose 需要先在兼容 JDK 26 / 标准镜像的宿主验证，不能用业务链路成功替代部署成功。

本轮只调整验证适配、旅程、三个 MySQL 测试工厂的 UTC 连接及说明，没有修改业务实现、迁移、依赖或生产部署模板。验证完成后，本机验证进程、VM 独立数据库/Redis/卷、Nginx、SSH 反向转发、Docker API 回环 relay 与临时目录均清理；临时凭据和带凭据的归档已移除，日志与非敏感元数据保留。VM 仍只运行原 `redis`、`mysql8`、`ruoyi-ui` 容器；下载的 Docker 镜像缓存保留，没有执行 prune 或清理用户服务。
