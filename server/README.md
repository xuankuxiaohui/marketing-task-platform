# 后端开发入口

后端是 Maven 多模块工程，包含 4 个平台模块、8 个业务域和 2 个应用，共 14 个子模块。项目尚未上线；模块存在不代表功能或验收已经完成。现有入口见 [功能清单](../docs/current-features.md)，历史诊断与候选批次见 [重构蓝图](../docs/refactoring-blueprint.md)；本次工作以用户要求为准。

## 模块与入口

| 模块 | 职责与主要入口 |
|---|---|
| `platform-kernel` | 统一响应、错误、JSON、时间与请求上下文；不持有业务实体和持久化规则 |
| `platform-contract` | 跨域端口、不可变入出参和事件常量；包含 `RewardPort`、`UserAttributePort`、`RiskCheckPort`、`TaskReadPort` |
| `platform-db` | 数据源配置与 Flyway 迁移；迁移脚本集中在本模块 |
| `platform-infra` | Redis、缓存、锁、限流、会话基础设施和 Outbox；入口 `InfraAutoConfiguration` |
| `domain-identity` | 双账号体系、权限、用户画像、配置、字典与审计 |
| `domain-task` | 任务编排、发布快照、领取、步骤推进、人群与实例；入口 `TaskClaimAppService`、`TaskStepAppService`、`TaskPortalAppService` |
| `domain-reward` | 奖品、库存、发放、履约、对账与积分；入口 `GrantAppService`、`PointsAppService`；积分属于本域 |
| `domain-risk` | 名单、风控规则求值与命中记录；实现 `RiskCheckPort` |
| `domain-tracking` | 埋点元数据、事件接收与查询、服务端事件消费和分区维护 |
| `domain-signin` | 签到配置、签到与补签、奖励发放编排 |
| `domain-activity` | 活动配置、展示、参与规则与参与奖励 |
| `domain-ad` | 广告位、素材、投放、展示筛选与频控；已接入 `ad:position` 缓存 |
| `admin-app` | `AdminApplication`；管理面装配、命名空间守卫、调度及跨域应用编排；现有看板与模拟实现待按蓝图梳理 |
| `portal-app` | `PortalApplication`；门户和内部回调装配、命名空间守卫 |

实际模块与依赖见 [父 POM](pom.xml) 和各模块 POM。两应用目前均依赖全部 8 个业务域。`domain-signin`、`domain-activity`、`domain-ad` 已有实现，不是待创建的空目录；不存在独立的 `domain-points` 模块。

## 装配与边界

域组件通过各模块的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 及自动配置类装配，管理和门户控制器分别扫描。应用边界如下：

| 应用 | 允许的 HTTP 前缀 | 边界 |
|---|---|---|
| `admin-app` | `/admin/**`、`/actuator/**` | 管理端账号、权限和 CSRF；后台写操作审计 |
| `portal-app` | `/api/**`、`/internal/**`、`/actuator/**` | 门户账号与内部 HMAC 回调分离；`/internal/**` 不得暴露到公网 |

两应用均有请求命名空间守卫和启动映射检查。后台写入须校验权限、CSRF 并保留审计；门户私有资源须校验归属，踢会话走会话服务而非缓存 evict。仓库约束见 [AGENTS.md](../AGENTS.md)，失败审计与事务的待定语义见 [DEC-004](../docs/decisions.md#dec-004独立事务与失败审计语义)。

后续重构必须保留以下业务边界；这些是约束，不能据此宣称所有现有代码均符合：

- 依赖方向为应用 → 业务域 → 契约 / 基础设施 → kernel。域之间不建立 Maven 依赖，不直接访问他域 Mapper、Entity、Service 或表。跨域同步经已定义的 contract 端口，异步经 Outbox；新增契约同批记录职责、语义与验收场景。
- 领取和步骤推进由 application 层建立事务，步骤引擎经进程内 `RewardPort` 调用发奖和积分服务。`portal-app` 必须同时装配 task 与 reward，不能以远程服务拆分破坏原子性。
- `EventPublisher.append` 要求当前存在事务。Outbox 记录与业务提交或回滚，Relay 按应用 producer 隔离消费；消费者仍需幂等。失败留痕的独立事务范围需结合 DEC-004 明确，不能把任意写入移到独立事务。
- MyBatis Mapper 与实体归所属域，Flyway 迁移归 `platform-db`。发布快照绑定实例后不可变；库存、积分与幂等约束不能仅靠缓存或进程内锁保证。

边界检查入口包括 `admin-app` 的 `ArchLayerRuleTest` / `ArchTxRemoteCallTest`、`portal-app` 的 `PortalAssemblyIT`。按改动核对相关端口、自动配置、迁移与测试；待改问题见 [重构方案](../docs/refactoring-blueprint.md)，不要把现有实现或历史规格直接当作已验证结论。

## 环境与构建

使用 JDK 26。本机默认 Java 不是 26，PowerShell 中先设置 `JAVA_HOME`。实际版本和依赖限制以父 POM、各模块 POM、继承的 BOM 与 Maven Enforcer 配置为准；旧依赖矩阵已退役。

以下命令从仓库根目录执行。编译和单元测试无需启动业务应用：

```powershell
$env:JAVA_HOME = 'D:\develop\jdk\jdk-26.0.2'
mvn -f server/pom.xml -q -DskipTests compile
mvn -f server/pom.xml -q -DskipITs test
```

Surefire 执行 `*Test`、`*PropertyTest`、`*ArchTest`，排除 `*IT`。`test` 阶段还绑定 JaCoCo 检查，因此单测断言通过不等于整条构建命令通过。

有 Docker 的 CI 环境执行完整验证：

```powershell
mvn -f server/pom.xml -B verify
```

`verify` 经 Failsafe 执行 `*IT`。本机无 Docker，MySQL / Redis 集成测试留给 CI；不能用 H2、跳过断言或本机手工数据库替代。流水线定义见 [ci.yml](../.github/workflows/ci.yml)，验收映射见 [verification-matrix.md](../docs/verification-matrix.md)。

本机运行应用使用仓库 [启动脚本说明](../scripts/README.md)；部署检查见 [首次上线清单](../deploy/R31-go-live-checklist.md)。不要把密码写入应用 YAML 或文档。Redis 使用 DB 2；账号密码和端口等环境值按运行配置提供。

## 如何验证一次重构

先定位需求条款与当前调用链，再补能暴露问题的测试，按用例替换实现；不要同时重写所有域。查询优化需要查询次数与分页语义证据，事务重构需要真实数据库回滚与并发证据，缓存和 Outbox 需要故障及恢复证据。

已有 `ArchLayerRuleTest` 检查域间依赖，`PortalAssemblyIT` 检查 task / reward / points 装配，`ScenarioMatrixIT` 和各域 IT 覆盖业务场景。它们是验证入口，不能用文件存在、旧报告或内存替身单测代替当前基线执行结果。

本文件的模块和调用关系来自 2026-10-07 工作区静态核对。本轮只调整文档，未重新运行编译、测试、应用启动或压测；当前可运行性、CI 结果及容量仍需后续执行验证。
