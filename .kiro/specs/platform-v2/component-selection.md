# 组件选型与边界

本文记录项目已经采用的选择及理由。业务规则见 [requirements](requirements.md)，具体架构见 [design](design.md)，版本与历史验证见 [dependency-matrix](dependency-matrix.md)。不是下一轮预研任务，也不宣称所选组件为当前行业最优。

## 1. 原则

身份、缓存、锁、校验、文档、日志等通用能力使用已有组件；任务快照、步骤转换、奖励与积分、风控规则和事件语义由项目实现。引入新组件须对应具体缺口，不能以全面重构为由重新堆叠框架。

## 2. 技术栈

JDK 26 + Spring Boot 4.1.x、MySQL、MyBatis-Plus、Flyway、Sa-Token、Redis + Caffeine、Redisson。前端为 Vue 3 + pnpm workspace；管理端 Ant Design Vue，门户 Vant 4。精确版本及环境差异只维护在依赖矩阵与构建文件。

## 3. 能力归属

### 3.1 身份与权限

Sa-Token 管理两套会话与权限校验；密码使用 `spring-security-crypto` BCrypt；验证码使用 easy-captcha。RBAC 数据模型、画像、审计注解与 Outbox 由项目实现。不要另外引入完整 Spring Security 或脚手架后端。

### 3.2 系统管理

字典与配置属于业务数据。缓存使用项目 PlatformCache 封装 Spring Cache / Caffeine / Redis；广播失效与降级语义以设计 §6.2/§6.8 为准。Bean Validation 校验输入，Hutool 只用于已选工具能力；JSON 统一经 kernel JsonUtil。

### 3.3 基础设施

Redisson 提供锁，Redis Lua 实现已定义的限流。业务调度使用 Spring 调度与锁恰一执行；Outbox 使用表、Relay、退避与幂等消费者。不因为存在性能问题就先增加调度中心或消息中间件；当前消费容量缺口见 [DEC-002](../../../docs/decisions.md)。

### 3.4 任务与风控

任务快照、步骤引擎和灰度规则自建；AviatorScript 仅在设计 §5.10 的白名单边界内使用。风控是六条内置规则及配置，不扩展为任意代码或通用工作流引擎。

### 3.5 埋点与可观测性

事件模型、批量接收和前端上报器遵守附录 D 与 R28。Micrometer、Prometheus 和 JSON 日志提供基础工具，但业务指标仍需实现与验证；配置文件存在不等于指标已经注册、告警已经可用。

### 3.6 工具与测试

springdoc 导出 API，openapi-typescript 生成前端类型。测试使用 JUnit、jqwik、ArchUnit、Testcontainers、Vitest、Playwright 与 k6。JSON、时间与错误处理通过 kernel 统一，不复制多份配置。外部 HTTP 使用既定 Spring 客户端，事务边界遵守设计。

### 3.7 前端

管理端沿用 vue-pure-admin-thin 的项目骨架及 Ant Design Vue；任务画布使用 vue-flow，看板选择 ECharts，活动编辑器选择 wangEditor v5。门户使用 Vant 4。shared 放契约与纯工具，不混合两端账号状态或 UI 组件体系。

以上是选型，不证明每个页面已经使用或完成这些能力；替换时同步规格、构建与验收。

## 4. 不引入的组件

| 组件 | 本项目边界 |
|---|---|
| RuoYi / JeecgBoot 后端全家桶 | 不替代既有模块、账号与数据模型 |
| Spring Cloud / Sentinel | 当前双应用静态路由，不增加服务发现或独立流控体系 |
| 完整 Spring Security | 只使用 crypto 工具包 |
| Drools / LiteFlow / Flowable / Camunda | 不以通用规则/流程引擎替代封闭业务语义 |
| XXL-Job / Quartz 集群 / ShedLock | 不增加第二套调度治理 |
| H2 | 不能替代真实 MySQL / Redis 集成验证 |
| Hutool JSON / HTTP / DB / all | 不增加平行序列化、HTTP 或持久化体系 |
| Metabase / Superset / 商业埋点 SaaS | 不引入独立看板平台或替代事件模型 |
| SkyWalking / ELK | 沿用现有可观测技术选择，不在重构中另起体系 |

## 5. 业务实现边界

任务、奖励积分、签到、活动、广告、风控及事件的规则仍在各所属领域内实现。共性代码只有在真实复用、生命周期与失败语义一致时才抽取；不以“自建”作为扩写通用框架的理由。

## 6. 历史组件实验

最初任务 1–8 的独立实验保留在 `spike/`，报告由[依赖矩阵](dependency-matrix.md)索引。它们记录组件装配和兼容依据，不是当前业务开发的前置审批流程。只在相关组件发生变化时重验对应风险，不再从任务 9 前重新起步。

## 7. 变更方式

需要替换选型时，写明问题、候选、数据/契约影响与验证方式，再同步设计和构建。组件许可证、分发条件等应以具体发行物核实；本文件不保留无证据的许可证速查或法律结论。
