# 编码规范总览

> 读者：人类开发者与 AI 编码代理。  
> 权威：本目录是**编码层**唯一规范集。产品语义以 `.kiro/specs/platform-v2/` 为准。  
> 版本：v1.4　日期：2026-08-18　技术栈：JDK 26 + Spring Boot 4 + MySQL 8 + Vue 3

## 1. 这套规范解决什么

本目录把「怎么写代码」从规格里拆出来，做成可被 AI 机械执行、可被开发者审查的规则。`server/` 已按任务 9 起骨架并持续编码；`web/` 在任务 36 落地。

规格回答 *what / why*。本目录回答 *how to write it so it stays consistent*。

## 2. 冲突裁决（MUST）

按下列顺序取高者，禁止自行调和：

| 优先级 | 来源 | 管什么 |
|--------|------|--------|
| 1 | [requirements.md](../../.kiro/specs/platform-v2/requirements.md) | 行为、验收、附录 C 契约 |
| 2 | [design.md](../../.kiro/specs/platform-v2/design.md) | 架构红线 RL-01~12、Schema、算法、测试策略（§0.2 索引 → 分册） |
| 3 | [tasks.md](../../.kiro/specs/platform-v2/tasks.md) | 交付边界、模块归属 |
| 4 | **本目录** | 命名、分层转换、风格、评审、工程化 |
| 5 | 所采用官方文档（见各篇「依据」） | 框架默认行为 |
| 6 | 行业指南（Alibaba / Google / Zalando 等） | 仅在本目录明确「采纳」的条款生效 |

行业指南与规格冲突时，**规格胜出**。各篇用「本项目覆盖」标明覆盖点，禁止 AI 把 Zalando / 阿里规约未采纳条款直接套进来。

## 3. 规范用语（RFC 2119）

本目录关键词按 [RFC 2119](https://www.rfc-editor.org/rfc/rfc2119) 解释：

| 词 | 含义 | 违反后果 |
|----|------|----------|
| **MUST / MUST NOT** | 强制 | CI 或评审阻断 |
| **SHOULD / SHOULD NOT** | 强烈建议 | 评审需写明理由才能例外 |
| **MAY** | 允许 | 无强制 |

中文「必须 / 禁止 / 应当 / 可以」与上表一一对应。

## 4. 目录

| 编号 | 文件 | 覆盖 |
|------|------|------|
| 01 | [01-java-coding.md](01-java-coding.md) | Java / Spring Boot 4 / MyBatis-Plus / 命名与安全编码 |
| 02 | [02-sql.md](02-sql.md) | MySQL 8 DDL/DML、Flyway、索引、时区 |
| 03 | [03-project-structure.md](03-project-structure.md) | 仓库布局、Maven 模块、包结构、前端 workspace |
| 04 | [04-code-review.md](04-code-review.md) | 人类与 AI 的评审清单 |
| 05 | [05-security.md](05-security.md) | 鉴权、审计、会话、注入、密钥、IDOR 机械清单 |
| 06 | [06-object-model.md](06-object-model.md) | Entity / Command / Query / Response / Port 转换 |
| 07 | [07-api-design.md](07-api-design.md) | REST、OpenAPI、分页、时间、鉴权载体 |
| 08 | [08-git-workflow.md](08-git-workflow.md) | 分支命名、PR、main 保护、CODEOWNERS |
| 09 | [09-exception-errorcode.md](09-exception-errorcode.md) | 异常分层、错误码、HTTP 映射 |
| 10 | [10-logging-observability.md](10-logging-observability.md) | 结构化日志、traceId、指标、告警 |
| 11 | [11-testing.md](11-testing.md) | 单测 / 属性 / 集成 / 架构 / E2E / 压测 |
| 12 | [12-frontend-code.md](12-frontend-code.md) | Vue 3 + TS 组件与状态 |
| 13 | [13-frontend-engineering.md](13-frontend-engineering.md) | Vite / pnpm / OpenAPI 类型 / 质量门禁 |
| 14 | [14-deployment.md](14-deployment.md) | 12-Factor、Compose、健康检查、密钥、滚动发布 |

空号已用完。代理入口见仓库根 [AGENTS.md](../../AGENTS.md)。

### 4.1 高频约束的单一事实源

下列句子在 AGENTS / 本 README / 05 里容易抄散。**条款正文只维护一处**；别处只保留一行指针。

| 约束 | 编码层正文 | 需求 / 设计 |
|------|------------|-------------|
| 审计只走 Outbox，禁止业务线程 insert `sys_audit_log` | [05-security.md](05-security.md) §3 | R10.3 / design §6.5 |
| 禁止 evict `identity:session` | [05-security.md](05-security.md) §4 | R9.2 / design §6.2 |
| 后台写 = 权限 + CSRF + `@Audited`；门户/internal 禁止 `@Audited` | [05-security.md](05-security.md) §2–§3 | R10.1 / RL-10 |
| 缓存命名空间封闭清单 | [01-java-coding.md](01-java-coding.md) §7.6 | R9.1 / design §6.2 |

本 README **不**再复述上表正文。AGENTS 硬停止保持一行，改细则只改 05 / 01。

## 5. 给 AI 的使用方式

编码或改代码前：

1. 读本 README 的冲突裁决。
2. 按任务所属层打开对应规范（Java 改动读 01 + 03 + 06；写路径 / 鉴权 / 审计再读 05；接口读 07 + 09；SQL 读 02；前端读 12 + 13；分支读 08）。
3. 生成代码必须同时满足：规格条款编号 + 本目录 MUST。
4. 不要发明规格未定义的错误码、表、缓存命名空间、权限码。
5. 不要引入 `component-selection.md` 反选型清单中的组件。
6. 每条新公共 API / 新表 / 新错误码必须能指回 requirements 或 design 的编号。

输出代码时，优先给可编译、可测试的完整实现，而不是「示例片段 + 待补全」。

## 6. 技术栈锚点（不可自行替换）

来源：[component-selection.md](../../.kiro/specs/platform-v2/component-selection.md)、[dependency-matrix.md](../../.kiro/specs/platform-v2/dependency-matrix.md)、design §2.7。

| 层 | 锁定选择 |
|----|----------|
| 运行时 | JDK 26 + Spring Boot 4.1.x（[官方系统要求](https://docs.spring.io/spring-boot/system-requirements.html)：4.1.0 支持 Java 17–26） |
| JSON | `tools.jackson`（Jackson 3），经 `kernel JsonUtil` 单例。禁止 Hutool JSON |
| ORM | MyBatis-Plus SB4 starter |
| 迁移 | Flyway，仅 `platform-db` |
| 认证 | Sa-Token 双 `StpLogic`；密码只用 `spring-security-crypto` BCrypt |
| 缓存 | Spring Cache + Caffeine L1 + Redis L2。现网共享 Redis **6.0.8 / DB 2**；IT 镜像可用 Redis 7 |
| 锁 | Redisson `RLock` |
| 前端 | 管理端 vue-pure-admin-thin 骨架（Vue 3 + Ant Design Vue + Pinia + Vite）；门户 Vant 4 |
| 契约 | springdoc-openapi 三分组；前端 `openapi-typescript` 生成类型 |

版本号以 `dependency-matrix.md` 冒烟结论为唯一来源。本目录不重复钉小版本。

## 7. 本套规范采纳的外部标准

只列被本目录**实际引用**的来源。未列出的博客、二次转载、口头「最佳实践」一律不构成依据。

| 标准 | 官方入口 | 本目录用法 |
|------|----------|-----------|
| RFC 2119 | https://www.rfc-editor.org/rfc/rfc2119 | 用语 |
| Alibaba Java Coding Guidelines | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ · https://github.com/alibaba/p3c | Java 命名 / OOP / 集合 / 安全；MySQL 表设计参考 |
| Google Java Style Guide | https://google.github.io/styleguide/javaguide.html | import、`@Override`、禁止忽略异常、单顶级类 |
| Google Engineering Practices（Code Review） | https://google.github.io/eng-practices/review/ | 评审流程与标准 |
| Zalando RESTful API Guidelines | https://opensource.zalando.com/restful-api-guidelines/ | HTTP 方法、路径形态、429、禁止堆栈外泄 |
| Microsoft REST API Guidelines | https://github.com/microsoft/api-guidelines | 集合、过滤、幂等参考 |
| OpenAPI Specification 3.1 | https://spec.openapis.org/oas/latest.html | 契约导出 |
| RFC 9110 HTTP Semantics | https://www.rfc-editor.org/rfc/rfc9110 | 方法语义、状态码 |
| RFC 3339 / ISO-8601 | https://www.rfc-editor.org/rfc/rfc3339 | 对外时间格式 |
| MySQL 8.0 Reference | https://dev.mysql.com/doc/refman/8.0/en/ | 类型、字符集、CHECK、分区 |
| Flyway Migrations | https://documentation.red-gate.com/flyway/flyway-concepts/migrations | 命名、只应用一次、checksum |
| Spring Boot 4 文档 | https://docs.spring.io/spring-boot/ | 系统要求、Actuator、测试、JSON |
| Jakarta Bean Validation 3.0 | https://jakarta.ee/specifications/bean-validation/3.0/ | 入参校验 |
| JUnit 5 User Guide | https://docs.junit.org/ | 测试模型（规格锁定 JUnit 5 + jqwik 1.9.3，不用 JUnit 6） |
| Testcontainers | https://java.testcontainers.org/ | 集成测试容器 |
| ArchUnit | https://www.archunit.org/userguide/html/000_Index.html | 架构红线 |
| jqwik | https://jqwik.net/docs/current/user-guide.html | 属性测试 |
| Twelve-Factor App | https://12factor.net/ | 配置、日志、无状态进程 |
| OpenTelemetry Semantic Conventions | https://opentelemetry.io/docs/specs/semconv/ | 日志/指标字段名（P1 链路对齐） |
| Micrometer | https://docs.micrometer.io/micrometer/reference/ | 指标 |
| Vue.js Style Guide | https://vuejs.org/style-guide/ | 组件规则 Priority A/B |
| TypeScript Handbook | https://www.typescriptlang.org/docs/handbook/intro.html | 类型纪律 |
| OWASP Top 10 | https://owasp.org/www-project-top-ten/ | 安全基线 |
| Conventional Commits | https://www.conventionalcommits.org/ | 提交说明（评审引用） |

## 8. 维护

- 规格变更导致规范过时：先改规格，再改本目录，并在变更记录写清条款号。
- 新增 MUST 必须同时给出：依据链接、正反例、执行机制（ArchUnit / Spotless / 评审）。
- 禁止把个人偏好写成 MUST。

### 变更记录

| 版本 | 日期 | 内容 |
|------|------|------|
| v1.0 | 2026-08-18 | 首版落盘 |
| v1.1 | 2026-08-18 | 补 `@Audited`、乐观锁二选一、`BusinessException` cause、OpenAPI 门禁对齐、`Result.code` 异构建模、缓存/Redis 键封闭表、现网 Redis DB 2、actuator 实际键、Spotless/AssertJ/JUnit 5 钉死、启用 05 安全清单 |
| v1.2 | 2026-08-18 | 启用 08 Git 工作流；仓库根增加 AGENTS.md |
| v1.3 | 2026-08-18 | 库存 SQL 回到 §5.7；乐观锁与发布 version 分列；03 依赖图与 design mermaid 同构；05 补 Outbox/403 审计；11 纠正任务 43/49；01 Redis 键补 §3.10 |
| v1.4 | 2026-08-18 | 高频约束单一事实源（审计 Outbox / session 禁 evict → 05）；`ad:position` P0 占位；对齐 design v2.13 / tasks v2.9 |
