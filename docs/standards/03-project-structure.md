# 03 · 工程结构规范

> 领域规范。决定文件放哪里、模块谁依赖谁。  
> 权威：design §1.3、§2.2、§2.8（RL-01~12）。  
> 读者：全员与 AI。新建文件前先读本篇。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Maven POM 约定 / 多模块 | https://maven.apache.org/guides/mini/guide-multiple-modules.html | 父 POM 管理模块与依赖 |
| Maven Standard Directory Layout | https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html | `src/main/java`、`src/test/java`、`resources` |
| Alibaba Java Coding Guidelines · Project Specification | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ | GAV：`groupId` 稳定；`artifactId` = 产品-模块 |
| pnpm Workspaces | https://pnpm.io/workspaces | 前端 monorepo |
| Vue SFC | https://vuejs.org/api/sfc-spec.html | `*.vue` 单文件组件 |
| ArchUnit User Guide | https://www.archunit.org/userguide/html/000_Index.html | 分层规则落地 |
| Twelve-Factor I. Codebase | https://12factor.net/codebase | 一份代码库、多份部署 |

## 2. 仓库根布局

design §1.3 冻结。**MUST** 按此创建，禁止另起顶层业务目录。

```text
marketing-task-platform/
├── server/                          # 后端 Maven 多模块
│   ├── pom.xml                      # groupId=com.mkt，版本与依赖唯一来源
│   ├── platform-kernel/
│   ├── platform-contract/
│   ├── platform-db/
│   ├── platform-infra/
│   ├── domain-identity/
│   ├── domain-task/
│   ├── domain-reward/
│   ├── domain-risk/
│   ├── domain-tracking/
│   ├── admin-app/
│   └── portal-app/
│   # P1 再加 domain-signin / domain-activity / domain-ad
├── web/                             # pnpm workspace
│   ├── pnpm-workspace.yaml
│   ├── package.json
│   ├── apps/admin/                  # vue-pure-admin-thin
│   ├── apps/client/                 # Vant 4 H5
│   └── packages/shared/             # OpenAPI 生成类型与少量纯函数
├── docs/standards/                  # 本规范
├── .kiro/specs/platform-v2/         # 产品规格
├── spike/                           # 编组 A 冒烟，禁止业务代码，不进 server/
├── perf/                            # k6 脚本与报告（任务 43）
└── ci/                              # 部署冒烟脚本等
```

规则：

1. **MUST** `groupId = com.mkt`。`artifactId` 与目录名相同。Java 包根与 artifact 对应（下表）。
2. **MUST NOT** 在仓库根散落 Java / Vue 源码。
3. **MUST NOT** 把 spike 工程复制进 `server/` 当正式模块。
4. **MUST** 密钥、`.env`、本地 `application-local.yml` 真实值被 `.gitignore` 排除。提交 `.env.example` 与 `application-*.yml.example` 占位。

## 3. Maven 模块职责

| artifactId | 包根 | 允许出现 | 禁止出现 |
|------------|------|----------|----------|
| `platform-kernel` | `com.mkt.kernel` | `Result`、`ErrorCode`、`BusinessException`、`UserContext`、`JsonUtil`、分页、全局异常、traceId/MDC、`Clock` | 业务实体、Web 映射、Mapper、Spring Data |
| `platform-contract` | `com.mkt.contract` | 三端口接口与 record、事件常量 | Spring Web、JDBC、MyBatis、其它域实现（RL-06） |
| `platform-db` | `com.mkt.db` | Flyway 脚本、数据源/Flyway 配置 | 业务 Service |
| `platform-infra` | `com.mkt.infra` | Redis、两级缓存、锁、限流、Sa-Token 装配、Outbox Relay | 领域规则、Controller |
| `domain-*` | `com.mkt.<域>` | 该域实体、Mapper、应用服务、领域服务、三类 Controller | 其它域的 Mapper/Entity/Service |
| `admin-app` | `com.mkt.admin` | 启动类、装配、Namespace 守卫、调度、Flyway 执行 | 业务实现（应在域模块） |
| `portal-app` | `com.mkt.portal` | 启动类、装配、Namespace 守卫、Outbox Relay | 拆走 task/reward（RL-05） |

积分代码在 `com.mkt.reward.points`，**MUST NOT** 建 `domain-points` 模块（design §2.2.1 / RL-02）。

## 4. 依赖方向（RL-01 / RL-02）

与 design §2.2.2 mermaid 同构：

```text
admin-app / portal-app
        ├─→ domain-*          （彼此无 Maven 依赖）
        ├─→ platform-infra
        └─→ platform-db
domain-*  → platform-contract
domain-*  → platform-infra
platform-infra → platform-kernel
platform-db    → platform-kernel
platform-contract → platform-kernel
```

1. **MUST** 依赖单向。禁止 kernel 依赖任何人；禁止 contract 依赖 infra/db/域。
2. **MUST** 域模块之间 POM 无依赖、Java 无 import（RL-02）。
3. **MUST NOT** 域模块依赖 `platform-db`（RL-09：域零 SQL 迁移；数据源只在应用 + `platform-db`）。
4. **MUST NOT** `platform-infra` / `platform-db` 依赖 `platform-contract`（二者直连 kernel）。
5. **MUST** 跨域同步只经 `platform-contract` 三写端口：`RewardPort`、`UserAttributePort`、`RiskCheckPort`。R5.6 只读门面见 design §2.2.3（D-13）。
6. **MUST** 跨域异步只经 Outbox 事件常量（RL-07）。
7. **MUST** `PointsPort` 留在 reward 内部，不算跨域端口。
8. ArchUnit 类名与 RL 编号对应：`ArchLayerRuleTest`、`ArchControllerRuleTest`…（design §7.6）。

## 5. 域模块内部包结构

每个 `domain-<x>` **MUST** 使用同一骨架。包名全小写单数（Alibaba Naming #9）。

```text
com.mkt.<domain>/
├── controller/
│   ├── admin/           # 仅 admin-app 扫描
│   ├── portal/          # 仅 portal-app 扫描
│   └── internal/        # 仅 portal-app 扫描（可空）
├── application/         # 应用服务：事务、编排、权限已在控制器声明
├── domain/              # 领域服务、状态机、纯函数（无 Spring Web）
├── entity/              # MyBatis-Plus 实体，对应一张表
├── mapper/              # Mapper 接口；XML 在 resources/mapper/
├── convert/             # Entity ↔ Command/Response，单向方法
├── command/             # 写入口 record
├── query/               # 查入口 record
├── response/            # HTTP 出参 record（不含 Result 外壳）
└── support/             # 本域小工具；能上沉 kernel 的不要放这里
```

补充：

| 域特有 | 位置 |
|--------|------|
| identity 配置实现 | `com.mkt.identity.config`（唯一可碰 `SysConfigMapper`，RL-11） |
| reward 积分 | `com.mkt.reward.points` |
| task 引擎 | `com.mkt.task.engine` |
| 端口实现 | 提供方域内 `com.mkt.<domain>.port`，例如 `RewardPortImpl` |

1. **MUST NOT** 把三类 Controller 揉在一个包。
2. **MUST NOT** 出现 `util` / `common` / `misc` 大杂烩包。跨域工具进 kernel。
3. **MUST NOT** 在 `entity` 包放非表映射对象。
4. XML 路径：`src/main/resources/mapper/<domain>/*.xml`，namespace 等于 Mapper 接口 FQCN。

## 6. 应用模块

### 6.1 组件扫描

```text
admin-app  扫描：com.mkt.** 中 controller.admin + 非 controller 的服务/Mapper
portal-app 扫描：com.mkt.** 中 controller.portal + controller.internal + 非 controller
```

启动时 **MUST** 断言：已注册映射全部落在本应用前缀（RL-08）。

允许前缀：

| 应用 | HTTP 前缀 |
|------|-----------|
| admin-app | `/admin`、`/actuator` |
| portal-app | `/api`、`/internal`、`/actuator` |

### 6.2 配置文件

```text
admin-app/src/main/resources/
  application.yml              # 非秘密默认
  application-prod.yml         # 生产 profile，仍无密钥明文
portal-app/src/main/resources/
  application.yml
  application-prod.yml
```

密钥 **MUST** 用环境变量占位：`${MKT_DATASOURCE_PASSWORD}`。清单见 [14-deployment.md](14-deployment.md)。

## 7. 前端结构

```text
web/
├── pnpm-workspace.yaml          # packages: ['apps/*', 'packages/*']
├── apps/admin/
│   ├── src/
│   │   ├── api/                 # 只放调用封装，类型从 shared 导入
│   │   ├── views/               # 按 §4.10 菜单：一级目录=模块
│   │   ├── router/              # 动态路由，权限码对齐附录 B
│   │   ├── store/               # Pinia
│   │   └── components/          # 本应用组件
│   └── package.json
├── apps/client/
│   ├── src/
│   │   ├── api/
│   │   ├── views/               # 列表/详情/奖品/积分/我的
│   │   ├── store/
│   │   └── components/
│   └── package.json
└── packages/shared/
    ├── src/openapi/             # openapi-typescript 生成物，禁止手改
    └── src/utils/               # 纯函数（时间展示、按钮状态映射）
```

1. **MUST** 管理端页面与 design §4.10 菜单种子一一对应，不得私自加顶级菜单。
2. **MUST** 共享类型来自 OpenAPI 生成物（NFR 可维护性 2）。
3. **MUST NOT** 在 `apps/admin` 引用门户业务页，反之亦然。共享只走 `packages/shared`。
4. 组件文件名 PascalCase 多词（Vue Style Guide Priority A）。

## 8. 测试代码位置

| 测试 | 位置 | 后缀 |
|------|------|------|
| 单元 / 属性 / 架构 | 各模块 `src/test/java` | `*Test` / `*PropertyTest` / `*ArchTest` |
| 集成 | 各模块或 app 模块 | `*IT` |
| 基类 `BaseIntegrationTest` | kernel test-jar | — |
| 前端单测 | `apps/*/src/**/*.spec.ts` | Vitest |
| E2E | `web/e2e` 或 `apps/*/e2e` | Playwright |
| 压测 | 仓库 `perf/` | k6 |

测试 **MUST NOT** 依赖开发者本机已手工安装的 MySQL/Redis；用 Testcontainers（NFR 可维护性 3）。本机无 Docker 时集成测试允许在 CI 跑，不得用 H2「近似 MySQL」替代（JSON / CHECK / 分区行为不同）。IT 镜像 Redis 7 与现网共享 6.0.8 的口径见 [11-testing.md](11-testing.md) / [14-deployment.md](14-deployment.md)。

## 9. 资源与禁止项

| 禁止 | 原因 |
|------|------|
| 域模块 `application.yml` 自配 DataSource | RL-09 |
| `src/main/resources` 放真实密钥 | NFR 安全 2 |
| 二进制大文件（录屏、node_modules）入库 | 仓库健康 |
| 多份 `ObjectMapper` 配置 | RL-11 |
| 在 `web/apps/*/src` 手写与 OpenAPI 重复的 interface | NFR 可维护性 2 |

## 10. 新建代码决策树（给 AI）

```text
要加一个 HTTP 接口？
  → 放对应域的 controller.<面>
  → 应用服务放 application/
  → 入参 command/ 或 query/，出参 response/
  → 禁止新建独立「API 模块」

要加一张表？
  → 只改 platform-db 新 V* 脚本
  → Entity + Mapper 放拥有该前缀的域
  → 禁止在别的域建该表的 Entity

要读另一个域的数据？
  → 已有端口能覆盖？用端口
  → 异步通知？Outbox 事件
  → 否则先改 contract（规格评审），禁止直接 import

要加工具方法？
  → 仅一域使用：该域 support/
  → 多域：kernel
  → 禁止每个域复制一份 Json/时间工具
```

## 11. AI 检查清单

- [ ] 新文件路径符合本节骨架
- [ ] POM 依赖未形成域间直依或反向依赖
- [ ] Controller 分包正确，映射前缀属于当前应用
- [ ] SQL 变更只在 platform-db
- [ ] 前端新页面对齐菜单种子或规格新增条款
- [ ] 未引入反选型组件（RuoYi、Spring Cloud、XXL-Job、Drools…）
