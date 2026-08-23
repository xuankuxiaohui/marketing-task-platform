# 组件选型分析：开源组件 vs 自建

> 依据：requirements.md v3.8。原则：通用能力用开源组件，业务语义自建。本文档是 design.md 的选型输入。

## 1. 选型原则

| 判断 | 标准 |
|------|------|
| 用开源 | 能力是技术通用语义（鉴权、缓存、锁、文档、校验、可观测），存在社区活跃（近 6 个月有 release/commit）、商用友好许可证、与我们技术栈兼容的组件 |
| 自建 | 能力是本平台业务语义（任务快照、步骤引擎、发放状态机、风控内置规则、埋点事件模型）——没有开源组件实现"我们的需求语义"，引入任何框架都只是借它的壳 |
| 反选型（明确不引入） | 组件成熟但为我们的规模/场景引入了不成比例的部署或耦合成本 |

## 2. 技术栈（全部开源）

| 层 | 组件 | 许可证 |
|----|------|--------|
| 运行时/框架 | JDK 26 + Spring Boot 4 | MIT / Apache-2.0 |
| ORM | MyBatis-Plus（SB4 专用 starter，[官方已声明支持 SB4](https://baomidou.com/getting-started/install/)） | Apache-2.0 |
| 迁移/连接池 | Flyway + HikariCP（SB 默认） | Apache-2.0 |
| 认证 | Sa-Token（SB4 兼容版，[有完整项目升级先例](https://szadmin.cn/md/Help/doc/other/change-log.html)） | Apache-2.0 |
| 存储/缓存底座 | MySQL 8 + Redis 7 + Caffeine | GPL(客户端例外) / BSD / Apache-2.0 |

## 3. 逐能力选型决策

### 3.1 身份与权限域

| 能力（需求） | 选型 | 说明 |
|--------------|------|----------------|
| 认证/会话/踢下线/并发会话控制（R1/R4/R6） | **Sa-Token** | 双 StpLogic 多账号体系、Redis 会话、`kickout`、`maxSessionCount` 并发登录控制（原生支持「超出踢最早」语义，对应 R1.9/R4.3）、登录锁定可结合其 `isLocked` 或自建计数。不引入完整 Spring Security（过滤器链过重，只用 crypto 包）；不自研会话过滤器 |
| 密码哈希（R1.5/R4.1） | **spring-security-crypto**（仅 BCrypt 工具类） | 只引 `spring-security-crypto` 单包，不引完整 Spring Security |
| 图形验证码（R1/R4/R32） | **easy-captcha**（首版） | 数字/算术验证码满足 R 需求；配合登录限流。P1 可评估 aj-captcha 点选行为验证 |
| RBAC 数据模型（R2/R3/R5） | **自建 5 张表 + Sa-Token 注解鉴权**（`@SaCheckPermission`） | 权限模型是简单关系表。不引入 RuoYi/JeecgBoot 类后端全家桶（技术栈绑定、无关模块多、与领域模块化冲突）；可借鉴其权限表设计 |
| 审计 AOP（R10） | 自建注解 + Spring AOP + Outbox | 业务语义（必记清单/脱敏规则都是我们的需求），无通用组件可套 |

### 3.2 系统管理域

| 能力 | 选型 | 说明 |
|------|------|------|
| 两级缓存 + 广播失效 + 命中率统计（R9） | **Spring Cache + Caffeine + Redis pub/sub** | Spring 原语，无第三方缓存框架。命中率走 Micrometer |
| 缓存底座 L1 | Caffeine | L1 本地缓存 |
| 字典/配置管理（R7/R8） | 自建 | 业务表语义，无组件可替代 |
| 脱敏工具（R10.6） | **Hutool `DesensitizedUtil`** | 成熟实现（手机号/银行卡/邮箱等规则） |
| 参数校验 | Hibernate Validator（SB 内置 Bean Validation） | 零成本 |

### 3.3 分布式基础设施

| 能力 | 选型 | 说明 |
|------|------|------|
| 分布式锁（R14/R19/调度治理） | **Redisson RLock** | 看门狗续期、可重入。任务 1 验证 starter；无 starter 则手动装配 `RedissonClient`。锁不可用时调度与领取走 Lua / CAS（design §6.8） |
| 限流（R1.11/R4/R19.5/R28.3） | **Redis Lua 滑动窗口** + `@RateLimit` 注解薄层 | 同一脚本按 key 分桶，覆盖 IP/用户/appId 等维度；登录须同时通过 IP 桶与账号桶。不引入 Sentinel（集群流控需独立 token server）；不引入 resilience4j（单实例语义） |
| 定时调度（R12.5/R14.10/R18.5/R20.4 等，P0 共 10 项见 design §6.7） | **Spring `@Scheduled` + Redisson `tryLock(0)`** | 锁恰一执行。P0 不引入 XXL-Job（独立调度中心 + 独立 DB）；不引入 Quartz 集群或 ShedLock |
| Outbox（R10.3/R28.9） | 自建（表 + Relay + 退避重试，约 300 行） | 无轻量标准实现；不引入 Eventuate Tram 等框架。P1 引入 MQ 时事件出口已接口化 |

### 3.4 任务引擎与规则

| 能力 | 选型 | 说明 |
|------|------|------|
| 表达式引擎（R11.9 DSL） | **AviatorScript**（AST 白名单） | 编译期可遍历 AST 做白名单校验（安全需求核心）；高性能。不引入 QLExpress（沙箱弱）、SpEL（面向 Spring bean，攻击面大）、Drools（重型，语义不匹配） |
| 步骤推进引擎（R14） | 自建 | 核心业务语义；可行性分析已论证（乐观锁 + 唯一约束，无组件依赖） |
| 风控规则执行（R26） | **自建（参数化内置规则）** | R26 是 6 条封闭枚举规则 + 阈值配置，不是通用规则引擎场景。P0 不引入 Drools / LiteFlow |
| 风控统计存储 | 自建（Redis ZSET 滑动窗口计数） | 与限流同模式 |
| 灰度分桶（R11.8） | 自建（md5 取模，10 行） | 无需组件 |

### 3.5 埋点与可观测性

| 能力 | 选型 | 说明 |
|------|------|------|
| 埋点上报端点与存储（R28/R29） | 自建 | 事件模型是定制契约（附录 D）；不引入商业 SaaS SDK 与通用 APM tracker |
| 前端埋点上报器（R28.10/13/14） | 自建（约 100 行：批量缓冲 + sendBeacon + 重试） | 同上 |
| 指标（NFR 可观测性） | **Micrometer**（SB4 原生）+ **Prometheus + Grafana** | 标准三件套，零自研指标代码 |
| 日志 JSON（traceId 贯穿） | **logstash-logback-encoder** + MDC | 成熟；P1 日志聚合用 Grafana Loki |
| 分布式链路（P1） | **Micrometer Tracing**（P1 评估，Bridge 到 OTel） | P0 不引入 SkyWalking / Zipkin |

### 3.6 工具与测试

| 能力 | 选型 | 说明 |
|------|------|------|
| 通用工具集 | **Hutool**（按模块引 `hutool-core`） | 脱敏/加解密（R15 HMAC 用 `SecureUtil.hmacSha256`，不手写）/日期等。**边界**：JSON 统一走 kernel JsonUtil（tools.jackson），不使用 Hutool JSON，避免两套序列化 |
| API 文档 | **springdoc-openapi**（SB4 兼容版） | OpenAPI 导出与前端类型生成 |
| HTTP 客户端（P1 模拟器/外部履约适配） | **Spring RestClient**（SB4 内置） | 不引额外组件 |
| 单元/集成/属性/架构测试 | JUnit 5 + **Testcontainers**（MySQL/Redis）+ **jqwik**（66 条正确性属性）+ **ArchUnit**（模块红线） | 全部成熟开源；正确性属性测试策略（requirements）直接以 jqwik 落地 |
| 压测 / E2E | **k6** + **Playwright** | 任务 43 / 49 |
| 备用分布式 ID | MyBatis-Plus 内置 `ASSIGN_ID`（雪花） | 仅事件表等高写入表按需启用，默认 DB 自增 |

### 3.7 前端

| 能力 | 选型 | 说明 |
|------|------|------|
| 管理后台框架 | **vue-pure-admin-thin** 骨架（Vue3 + **Ant Design Vue** + Pinia + Vite） | RBAC 动态路由 / 权限指令 / 多标签；组件库为 Ant Design Vue |
| 任务流程画布（R11 编辑器） | **vue-flow**（MIT） | 任务 37 画布 |
| 看板图表（P1 R23） | **ECharts**（Apache-2.0） | 事实标准。不引入 Metabase / Superset（独立部署 + 权限打通成本，需求只是趋势图/漏斗） |
| 活动富文本（P1 R22） | **wangEditor v5**（备选 TipTap） | 中文生态成熟、可控 toolbar（服务端白名单过滤配合 R22） |
| 门户 H5 | Vant 4 + unplugin-auto-import | 移动端浏览器 / WebView |
| API 类型生成 | **openapi-typescript** | 从后端 OpenAPI 生成类型 |

## 4. 反选型清单（成熟但明确不引入）

| 组件 | 不引入原因 |
|------|-----------|
| RuoYi / JeecgBoot 等后端全家桶 | 技术栈与包结构绑定，携带大量无关模块；仅借鉴权限表设计 |
| Spring Cloud 全家桶（Gateway/Nacos/Config） | 两应用静态路由，Nginx 足够；无服务发现需求 |
| Spring Security 完整框架 | 模型远超需求，只取 crypto 包 |
| Sentinel | 集群流控需 token server，运维不成比例 |
| Drools / LiteFlow（P0） | 6 条封闭规则不需要通用规则引擎 |
| XXL-Job（P0） | 需独立调度中心；P1 规模变化后再评估 |
| Flowable / Camunda | 无审批流场景 |
| Metabase / Superset | 看板需求用 ECharts 自建页面即满足 |
| SkyWalking / ELK（P0） | P0 用 Micrometer+Prometheus+Grafana+Loki 路线 |
| 商业埋点 SaaS | 数据出域、收费、模型不匹配 |

## 5. 必须自建清单（业务语义绑定，开源无法替代）

任务快照与步骤引擎（R11–R14）、发放状态机与限制链（R17–R19）、风控规则集与名单判定（R25–R27）、埋点事件模型/上报端点/前端上报器（R28–R29）、广告位定向频控（R30）、连签计算（R21）、字典/配置/人群包/互斥组（R7/R8/R11）、Outbox Relay、审计 AOP 与错误码体系。**共性：全部是"我们的产品语义"，任何框架都只能提供外壳，反而增加学习与适配成本。**

## 6. JDK 26 + Boot 4 冒烟（任务 1–8）

运行时固定为 JDK 26 + Spring Boot 4。无官方 starter 时手动装配该组件。结论写入 `dependency-matrix.md`。

| # | 组件 | 风险 | 验证方式 | 装配说明 |
|---|------|------|----------|----------|
| 1 | **Redisson** | [SB4 starter 兼容 issue 未关闭](https://github.com/redisson/redisson/issues/6863) | 空工程引 starter 启动 + RLock 冒烟 + Lua 滑窗限流冒烟 | 优先 starter；否则手动装配 `RedissonClient`；再否则 Lettuce + 自封装锁/限流 Lua |
| 2 | Sa-Token | 需确认 SB4 兼容的具体版本号 | 双 StpLogic + Redis 会话冒烟 | 有升级先例，风险低 |
| 3 | MyBatis-Plus | 低（官方声明支持） | SB4 starter + 代码生成器冒烟 | — |
| 4 | Spring Cache | 两级 + 广播需自证 | Caffeine L1 + Redis L2 + `PUBLISH cache:evict` 冒烟 | 实现见 design §6.2 |
| 5 | springdoc-openapi | 需确认 SB4 兼容版本 | 启动 + OpenAPI JSON 导出 | — |
| 6 | AviatorScript | JDK 26 运行时验证 | 编译/求值/中断冒烟 | 不可用时用自建极简解释器（design §2.7.1） |
| 7 | Hutool | 与 tools.jackson 共存 | 序列化互操作冒烟 | 只用非 JSON 模块 |
| 8 | easy-captcha / jqwik / Testcontainers / ArchUnit / logstash-logback-encoder | JDK 26 + Boot 4 常规验证 | 冒烟 | 均有等价替代 |

任一组件无法在 JDK 26 + Boot 4 上完成冒烟，则阻塞任务 9 起编码。

## 7. 与规格的关系

本文是 design.md 与 tasks.md 编组 A 的选型输入。需求不绑定具体组件实现。

## 8. 许可证速查（商用友好性）

确定项：Spring 系 / MyBatis-Plus / Sa-Token / Redisson / Caffeine / Flyway(OSS) / springdoc / AviatorScript / ArchUnit / ECharts = Apache-2.0；Vue / Ant Design Vue / Vant / pure-admin / vue-flow / Playwright / Testcontainers / logstash-logback-encoder = MIT；MySQL 客户端 = GPL-2.0（仅作为库依赖分发无传染风险，自用部署无问题）。待任务 1–8 复核项：easy-captcha、aj-captcha、jqwik、wangEditor（以各仓库 LICENSE 为准，均为常见宽松许可）。
