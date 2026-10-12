# 模块边界（P1）

**状态**：活规则。架构师拥有本文；开发只在本文「第一刀」列出具体违规时做小修复。未列出的热点、密度和缓存形态保持现状。

**本片**：勾空；非 RC。文档冻结非法跨模块依赖，不改产品代码，不做大爆炸重构，不降低 [DEC-006](../decisions.md#dec-006性能目标及证据口径已关闭) 门禁，不重开容量刀。

**对照基线**：`master` `e5c3f4f`（#195）。工程是 `server/` 下的 Maven 模块化单体（父 POM `platform-parent`），没有 Gradle。入口说明见 [后端说明](../../server/README.md) 与 [AGENTS.md](../../AGENTS.md)。

## 目的

把「谁可以调用谁」写成可执行规则，避免后续功能在模块之间顺手打洞。已有门禁是 `admin-app` 的 [`ArchLayerRuleTest`](../../server/admin-app/src/test/java/com/mkt/admin/ArchLayerRuleTest.java)（RL-01 层方向、RL-02 域间不互相 import / POM 不依赖兄弟域、RL-03 不碰他域 Mapper / Entity / application / domain）。本文与该测试、[DEC-003](../decisions.md#dec-003跨域只读契约与人群能力已关闭) 一致；冲突时以已关闭决定和测试为准，并回写本文。

## 模块清单

`server/` 共 14 个 Maven 子模块。包根均在 `com.mkt.*`。

| 模块 | 包根 | 角色 |
|---|---|---|
| `platform-kernel` | `com.mkt.kernel` | 统一响应、错误码、JSON、时钟、追踪与请求上下文；不持有业务实体和持久化规则 |
| `platform-contract` | `com.mkt.contract` | 跨域同步端口与不可变入出参、事件常量。现有端口：`RewardPort`、`UserAttributePort`、`RiskCheckPort`、`TaskReadPort`、`CrowdPort` |
| `platform-infra` | `com.mkt.infra` | Redis、`PlatformCache`（`TwoLevelPlatformCache`）、锁、限流、会话与 Outbox |
| `platform-db` | `com.mkt.db` | 数据源与集中式 Flyway；业务表所有权仍在各域 |
| `domain-identity` | `com.mkt.identity` | 双账号、权限、用户画像、配置、字典与审计；实现 `UserAttributePort` |
| `domain-task` | `com.mkt.task` | 任务编排、发布快照、领取、步骤推进、人群与实例；实现 `CrowdPort`、`TaskReadPort` |
| `domain-reward` | `com.mkt.reward` | 奖品、库存、发放、履约、对账与积分；实现 `RewardPort`。积分在本域，不另建 `domain-points` |
| `domain-risk` | `com.mkt.risk` | 名单、规则求值与命中记录；实现 `RiskCheckPort` |
| `domain-tracking` | `com.mkt.tracking` | 埋点元数据、事件接收与查询、服务端事件消费 |
| `domain-signin` | `com.mkt.signin` | 签到配置、签到与补签、奖励发放编排 |
| `domain-activity` | `com.mkt.activity` | 活动配置、展示、参与规则与参与奖励 |
| `domain-ad` | `com.mkt.ad` | 广告位、素材、投放、展示筛选与频控 |
| `admin-app` | `com.mkt.admin` | 管理面装配、`/admin/**` 命名空间、调度与已登记的只读聚合 |
| `portal-app` | `com.mkt.portal` | 门户与内部回调装配、`/api/**` 与 `/internal/**` 命名空间。步骤推进、正常发奖与积分变动在此同时装配 task 与 reward，保持同一数据库事务 |

编译期依赖（对照各模块 `pom.xml`）：

- `platform-contract`、`platform-infra`、`platform-db` 编译依赖 `platform-kernel`。
- 每个 `domain-*` 编译依赖 `platform-contract` 与 `platform-infra`（kernel 经二者传递）。`platform-kernel`（`tests` classifier）与 `platform-db` 仅为测试依赖。
- `admin-app` 与 `portal-app` 编译依赖 `platform-infra`、`platform-db` 以及全部 8 个 `domain-*`。
- 没有任何 `domain-*` 的 POM 依赖另一个 `domain-*`，也没有域依赖 `admin-app` / `portal-app`。

`web/` 不在 Maven 图内：`web/apps/admin`、`web/apps/client` 经 HTTP 调用两套应用；`web/packages/shared` 只放纯工具与生成的 OpenAPI 类型。服务端模块不依赖前端包。`perf/` 与 `scripts/` 是测量和本地启动，见下文允许规则。

## 允许的依赖

方向只有一条：应用 → 域 → 契约 / 基础设施 / kernel。`platform-db` 由两个应用在编译期装配；域在集成测试中使用它，生产代码不把它当作跨域读表入口。

| 调用方 | 可以依赖 |
|---|---|
| `admin-app`、`portal-app` | 各域的公开应用服务与本域控制器装配、`platform-infra`、`platform-db`、传递而来的 contract / kernel |
| 任一 `domain-*` | `platform-contract` 的端口与类型、`platform-infra`、传递的 `platform-kernel`；本域自己的 application / domain / mapper / entity |
| 域与域之间 | 仅同步调用 `platform-contract` 端口；异步经 Outbox。新增端口同批写明职责、语义和验证 |
| `platform-contract` | `platform-kernel` |
| `platform-infra`、`platform-db` | `platform-kernel` |
| `platform-kernel` | 无业务域、无应用 |
| `web/**` | 只消费 HTTP 与生成契约，不进入服务端编译图 |
| `perf/`、`scripts/`、测量用 SQL / k6 | 只观察或加压。脚本不得改变产品语义、门禁数值或业务代码 |

已登记例外（DEC-003，F06）：管理端只读聚合 `MetricsAggregateService` / `MetricsQueryService` / `JdbcSimulateGrantLookup` 可以按表白名单读源表，写仅限 `mtr_*`。表所有权留在数据域。未登记的跨域 SQL 仍由 `ArchAdminAggregateSqlTest` 失败。这条例外不推广到域模块。

`domain-identity` 在缺少真实 Bean 时用 `@ConditionalOnMissingBean` 注册 `RewardPortStub`、`TaskReadPortStub`。二者只实现契约类型，不 import `domain-reward` / `domain-task` 内部包。

## 禁止

下列依赖视为非法。发现一处，单独立项，只修这一处：

1. **域 → 应用**：任何 `domain-*` 不得依赖 `portal-app` / `admin-app`，不得 import `com.mkt.portal` / `com.mkt.admin`。
2. **域 → 另一域内部包**：不得绕过 `platform-contract` 引用他域的 mapper、entity、application、domain 或表。禁止在域 POM 中加入兄弟域 `artifactId`。
3. **kernel 堆积领域类型**：`platform-kernel` 不新增任务、奖品、风控、人群等领域模型；跨域类型放 `platform-contract`。
4. **无 DEC 的顺手功能**：改一个模块时不得在无关模块加行为、缓存或查询。跨域新能力先有决定（或本文列出的违规修复），再改代码。
5. **测量改语义**：`perf/` 与容量脚本不得借跑数修改产品行为，不得下调 DEC-006 门禁，不得把已收口的容量刀重新打开。

控制器仍只做协议和身份，用例与事务在 application，规则在 domain / engine，本域持久化在 store / mapper。

## 已知热点

下列是对照 `e5c3f4f` 源码后的记录，供架构师判断后续是否单独立项。它们不是本片的违规，开发不要据此拆类、换缓存或改 TTL。

### TaskPortalAppService / TaskStepAppService 密度

`domain-task` 的门户与步骤用例集中在少数应用服务里：

| 类 | 行数（`wc -l`） | 跨域端口 |
|---|---|---|
| `server/domain-task/src/main/java/com/mkt/task/application/TaskPortalAppService.java` | 830 | `UserAttributePort`、`RiskCheckPort`；另有 `PlatformCache` |
| `server/domain-task/src/main/java/com/mkt/task/application/TaskStepAppService.java` | 372 | `UserAttributePort`、`RiskCheckPort` |

同域还有更大的 `TaskPublishAppService`（848 行）和 `TaskDefinitionAppService`（563 行）。密度说明职责堆在 application 层，Maven 与 import 仍然停在本域加契约。拆分需要架构师单独授权，不能当作边界违规的附带重构。

### RiskCheckPort：domain-task 使用的是契约

`RiskCheckPort` 定义在 `server/platform-contract/src/main/java/com/mkt/contract/RiskCheckPort.java`，实现是 `domain-risk` 的 `RiskCheckPortImpl`。

`domain-task` 生产代码只 import `com.mkt.contract.RiskCheckPort`，注入点在 `TaskPortalAppService`、`TaskStepAppService`、`TaskClaimAppService`、`TaskInstanceAppService`。同一端口还被 `domain-reward` 的 `GrantAppService` 和 `domain-identity` 的 `PortalAuthService`、`PortalUserAppService` 使用。没有 `import com.mkt.risk.*`。这是允许的端口调用。

### 域内 Caffeine 与 TwoLevelPlatformCache

共享门面是 `platform-infra` 的 `PlatformCache` / `TwoLevelPlatformCache`：Caffeine L1 + Redis L2，失效走 `cache:evict`。`domain-ad`、`domain-identity` 以及 task 的发布路径走这个门面。

另外有进程内 `Caffeine.newBuilder()`，不经过 `PlatformCache`：

| 位置 | 形态 |
|---|---|
| `TaskPortalAppService` | 门户当前实例，TTL 5s，`maximumSize` 8192 |
| `TaskStepAppService` | 推进前快照与用户属性，TTL 3s |
| `TaskInstanceAppService` | 管理端实例 COUNT，TTL 3s，`maximumSize` 1024 |
| `MybatisRiskRuleConfigStore` | 见下节 |

这些是已合入的容量实现。本文只登记差异：本地 Caffeine 没有 L2，也不参加 `cache:evict`。不在本片迁回 `TwoLevelPlatformCache`。

### MybatisRiskRuleConfigStore.listAll

`server/domain-risk/src/main/java/com/mkt/risk/application/MybatisRiskRuleConfigStore.java` 对 `listAll()` 使用 Caffeine：TTL 3s、`maximumSize` 1、`get(key, loader)` 合并并发加载；`update` 时 `invalidate`。调用方是本域 `RiskCheckPortImpl`。这是 #195 的风险规则缓存，属于容量收口后的既有行为，不是待删的违规缓存。

## 开发第一刀

对照范围：14 个模块的 `pom.xml`；`domain-*` 与 `platform-*` 的 Java import（生产与测试）是否引用其他域包或 `com.mkt.admin` / `com.mkt.portal`。

**no violation found — stop at doc**

未发现域 POM 依赖兄弟域或应用，也未发现域代码 import 他域内部包或应用包。`ArchLayerRuleTest` 已覆盖生产类的层方向和域间依赖。本片到文档为止，不开 Java 修复。

核对时见到的字符串排序不构成 import 或依赖环：`domain-task`、`domain-reward`、`domain-signin`、`domain-activity`、`domain-ad` 的自动配置类用 `@AutoConfigureBefore(name = "com.mkt.identity.IdentityAutoConfiguration")` 保证真实端口 Bean 先于 identity 的 `ConditionalOnMissingBean` 占位。类名是字符串，没有编译期依赖。本片不把它列为待修违规。

## 指针

`docs/replan/2026-10-13-project-health-checklist-draft.md` 与 `docs/replan/2026-10-13-replan-backlog.md` 在基线 `e5c3f4f` 的 `master` 上不存在，因此本 PR 没有往这两份文件插入指针，也没有新建它们。

它们落地后，各加一行即可：

```text
模块边界（P1，架构师）：[module-boundaries.md](module-boundaries.md)。
```
