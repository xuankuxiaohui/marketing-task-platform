# 10 · 日志与可观测性规范

> 适用范围：双应用日志、指标、健康检查；P1 链路追踪预留。  
> 权威：NFR 可观测性；design §6.6、§6.8、§2.3.3。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Twelve-Factor XI. Logs | https://12factor.net/logs | 日志是事件流；进程写 stdout，不自己管轮转集群 |
| OpenTelemetry Logs / Semantic Conventions | https://opentelemetry.io/docs/specs/semconv/general/logs/ · https://opentelemetry.io/docs/specs/semconv/http/ | 字段命名与 HTTP 语义，便于 P1 对接 OTel |
| Spring Boot Actuator | https://docs.spring.io/spring-boot/reference/actuator/endpoints.html | health / readiness / prometheus |
| Spring Boot Observability | https://docs.spring.io/spring-boot/reference/actuator/observability.html | Micrometer 仪表 |
| Micrometer | https://docs.micrometer.io/micrometer/reference/ | 计时、计数、gauge |
| logstash-logback-encoder | https://github.com/logfellow/logstash-logback-encoder | JSON 编码器 |
| SLF4J | https://www.slf4j.org/manual.html | 参数化日志，不拼接 |
| Alibaba Exception and Logs | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ | 级别、占位符、禁止输出敏感信息 |

## 2. 关联模型

```text
请求进入
  → 过滤器读取或生成 X-Trace-Id
  → MDC.put("traceId") + 可选 userId
  → 响应头与 Result.traceId
  → 日志 JSON 带同一 traceId
  → Micrometer HTTP / 业务指标
  → /actuator/prometheus 供抓取
```

P0 **不**引入 SkyWalking / Zipkin / 完整 OTel Agent（component-selection §3.5）。字段命名按 OTel 语义预留，P1 用 Micrometer Tracing Bridge。

## 3. traceId

design §6.6：

1. **MUST** 入口过滤器：请求头 `X-Trace-Id` 合法则沿用，否则生成短 UUID（去掉连字符或取 16 位 hex，全应用一致）。
2. **MUST** 写入 MDC `traceId`、响应头 `X-Trace-Id`、`Result.traceId`。
3. **MUST** 请求结束 `MDC.clear()`（含异步交接：包装任务先复制 MDC）。
4. internal HMAC 客户端 **MUST** 透传 `X-Trace-Id`；该头 **MUST NOT** 进入签名串（design §6.6）。
5. 调度线程无入口请求时：`traceId = sched:<taskName>:<epochMillis>`，避免空字段。

## 4. 日志格式

### 4.1 输出

1. **MUST** 使用 Logback + `net.logstash.logback.encoder.LogstashEncoder`（或等价 JSON encoder）。
2. **MUST** 写到 stdout（12-Factor XI）。文件轮转仅本地开发可选；生产由采集器（P1 Loki）收流。
3. **MUST NOT** 使用 `log4j` 1.x、`System.out`、`e.printStackTrace()`。

约定字段（design §6.6）：

| 字段 | 必填 | 说明 |
|------|------|------|
| `ts` | 是 | ISO-8601，UTC（design §6.6 字段名；不要再用 `@timestamp` 双轨） |
| `level` | 是 | TRACE/DEBUG/INFO/WARN/ERROR |
| `logger` | 是 | 类名 |
| `msg` | 是 | 短消息，英文或中文均可，无换行堆栈 |
| `traceId` | 是 | 见上 |
| `userId` | 否 | 已登录时 |
| `loginType` | 否 | `admin` / `client` |
| `thread` | 是 | 线程名 |
| `stack_trace` | ERROR 时 | encoder 标准字段 |

业务 JSON（审计摘要、事件属性、日志里的结构化字段）**MUST** 经 kernel `JsonUtil`（RL-11）。日志信封本身由 `LogstashEncoder` 输出，不要再包一层 `JsonUtil` 去「统一序列化日志」。

### 4.2 级别

| 级别 | 何时 |
|------|------|
| ERROR | 未预期失败、发放永久失败、迁移失败、会话因 Redis 宕机拒绝 |
| WARN | 可预期降级、限流触发、校验拒绝刷屏要节流、表达式包不存在 |
| INFO | 登录成功/失败摘要、发布任务、状态机终态、调度一轮结果 |
| DEBUG | 开发排障。生产默认关闭 |
| TRACE | 禁止在业务热路径使用 |

1. **MUST NOT** 在循环热路径打 INFO（步骤引擎每步、列表每条记录）。
2. 限流、风控拒绝 **SHOULD** 用计数指标 + 抽样日志，避免被打爆。
3. 参数化：**MUST** `log.info("claim rejected, userId={}, taskId={}, code={}", userId, taskId, code)`。禁止 `"..." + userId`（Alibaba；SLF4J 官方手册）。

### 4.3 敏感字段

NFR 安全 7、R10.6、R28.3：

**MUST NOT** 出现在日志、审计摘要、埋点属性：

- 密码、验证码答案、BCrypt 哈希
- 会话令牌、CSRF 明文（可记「已校验」）
- HMAC secret、internal 签名原文
- 银行卡、证件号；手机号/邮箱按 `DesensitizedUtil` 规则

审计摘要：JSON → 脱敏 → 截断 2000 字符 + `...(truncated)`（R10.1）。

## 5. 指标（Micrometer）

NFR 可观测性 1。命名用点分小写，标签低基数。

### 5.1 技术指标

Spring Boot 自动：HTTP 服务端（通过 Micrometer）、HikariCP、JVM、Redis 命令（若装配）。

**MUST** 暴露：

- HTTP：QPS、错误率、P95（按 `uri` 模板，**禁止**用原始 path 以免 id 爆炸）
- 连接池活跃/空闲
- Redis 命令延迟

URI 标签 **MUST** 使用 Spring 模板 `/api/common/task/{taskId}/start`，不要 `/api/common/task/123/start`。

### 5.2 业务指标（P0 必做）

| 指标 | 类型 | 标签 |
|------|------|------|
| `mkt.grant.count` | counter | `status`、`source` |
| `mkt.grant.fail` | counter | `reason` |
| `mkt.outbox.backlog` | gauge | `producer`、`status` |
| `mkt.ratelimit.hit` | counter | `dim` |
| `mkt.track.drop` | counter | `reason` |
| `mkt.prize.stock.ratio` | gauge | `prizeId`（奖品数量可控；不要给每个用户打 gauge） |
| `mkt.risk.block` | counter | `scene`（不要打 rule 高基数到无限标签） |
| `mkt.degrade` | counter | `component` |
| `mkt.audit.write.delay` | timer | — |
| `mkt.cache.hit` | 复用 Spring Cache / Caffeine 统计 | `ns`（R9.1 封闭命名空间） |
| `mkt.sched.run` | timer + counter | `task`、`result` |

1. **MUST NOT** 用无界标签：`userId`、`ip`、`token`、完整 URL。
2. 缓存命中率经 Micrometer，管理端 `/admin/system/cache/stats` 读取（R9）。
3. `identity:session` 不纳入本组件计数（design §6.2）。

## 6. 健康检查

两应用 **MUST** 提供（NFR 可观测性 4）：

| 端点 | 语义 |
|------|------|
| `/actuator/health/liveness` | 进程活着 |
| `/actuator/health/readiness` | 能接流量：数据源、Redis（会话依赖）可达 |
| `/actuator/prometheus` | 抓取；仅内网 |

生产实际键（Boot 3/4，本目录按此执行）：

```text
management.endpoint.health.probes.enabled=true
management.endpoints.web.exposure.include=health,prometheus
```

就绪是 health group，**不是**独立 endpoint。与 design §2.3.3 同一套键。不要把 `readiness` 当成 `management.endpoints.web.exposure` 的合法 id。

就绪失败时编排 **MUST** 从负载均衡摘除，而不是继续 500。

Redis 故障：会话路径拒绝（健康检查应反映 Redis 不可用，避免无会话集群继续接登录态流量）。见 design §6.8。

## 7. 告警基线（NFR 可观测性 3）

这些是运营告警，不是单元测试：

- HTTP 5xx 率 / P95 超阈
- Outbox 积压超阈
- 发放永久失败发生
- 任一奖品 `remaining/total ≤ 10%`
- Redis 故障或切换
- 降级事件 `mkt.degrade` 发生
- 埋点丢弃率 > 0.1%

告警通知通道 P0 可先打 ERROR 日志 + 指标；对接即时通讯在部署文档实现，但指标必须先有。

## 8. 降级可观测

design §6.8 每条降级 **MUST**：

1. 计数 `mkt.degrade{component=...}`
2. WARN/ERROR 日志含 component 与策略（放行/拒绝）
3. 风控降级另写降级事件（R26.3）

禁止静默降级。

## 9. 前端可观测

1. 门户上报器：批量 + `sendBeacon` + 失败本地补发（R28.10/14）。**MUST NOT** 阻塞业务点击。
2. 前端 **MUST NOT** 把令牌打进 `console.log`。
3. 管理端错误提示展示 `message` + 可复制 `traceId`，便于对后端日志。

## 10. AI 检查清单

- [ ] 新路径日志带 traceId，无密钥
- [ ] 使用 SLF4J 占位符
- [ ] 热路径无 INFO 刷屏
- [ ] 新业务计数器标签基数有界
- [ ] 降级有指标 + 日志
- [ ] 未新增未授权 actuator 端点
