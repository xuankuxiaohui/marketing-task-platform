# AGENTS.md

给编码代理的项目说明书（[agents.md](https://agents.md/)）。人读 [README.md](README.md) 和 [PROJECT_STATUS.md](PROJECT_STATUS.md)。阶段事实（目录是否已建、下一步任务号）只看 STATUS，不要把过期现状抄进本文件。

本文件**只写本仓库推不出来的约束**。风格、SQL、对象模型、错误码、测试细节在 [`docs/standards/`](docs/standards/README.md)，不要在这里复制。

## 权威（禁止自行调和）

```text
行为 / 终态 / Schema / RL：requirements.md > design.md（总册 §0.2 索引 → 分册）
本次做多远：当前任务在 tasks.md 的那一节（未勾选的不要做；37/38 用 37.1 等子号）
怎么写：docs/standards/（按层打开）
验收对照：docs/verification-matrix.md
本文件：常驻硬停止，覆盖不了上面三层；细则不在这里扩写
```

终态红线（如 RL-05 portal 必装 task+reward）与「本任务暂不装配」同时出现时：**按任务边界交付**，在 PR 写清未接线。禁止为了「一次做对」把后续任务做完。

规格在 [`.kiro/specs/platform-v2/`](.kiro/specs/platform-v2/)。条款互斥：**停下来写冲突**，不要静默选边。

## 这是什么

营销任务平台。运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。

- 两个进程：`admin-app`（`/admin/**` + `/actuator`）、`portal-app`（`/api/**` + `/internal/**` + `/actuator`）
- **终态**（RL-05）：`portal-app` 必须装配 `domain-task` + `domain-reward`（同事务发奖）。任务写「暂不装配」时不要提前加域依赖
- 域模块之间 **禁止** Maven 依赖和互相 import Mapper/Entity（RL-02/03）；跨域只走 `platform-contract` 三端口或 Outbox
- 编组 A 只在 `spike/`。**禁止**把 spike 工程拷进 `server/` 当正式模块
- P0 只有这 11 个 artifact（`groupId=com.mkt`，目录名 = artifactId）：
  `platform-kernel` / `platform-contract` / `platform-db` / `platform-infra` /
  `domain-identity` / `domain-task` / `domain-reward` / `domain-risk` / `domain-tracking` /
  `admin-app` / `portal-app`
- **禁止** `domain-points`（积分是 `com.mkt.reward.points`）。**禁止**现在建 P1 域（signin / activity / ad）和 `web/`（任务 36）

## 动手前读什么

1. [PROJECT_STATUS.md](PROJECT_STATUS.md)（一页现状）
2. 当前任务在 [tasks.md](.kiro/specs/platform-v2/tasks.md) 的那一节 + 它引用的 design 小节（先查 [design.md](.kiro/specs/platform-v2/design.md) §0.2，再在分册搜索 `<!-- §x.y -->`）
3. 按层打开规范，不要通读 14 篇：

| 改什么 | 读 |
|--------|----|
| 仓库布局 / POM / 模块 | 只读 03；模块名单以 design §2.2.1 为准 |
| Java 结构 / 命名 | 01 + 03 + 06 |
| 写路径 / 鉴权 / 审计 | **只读 05**（不要从本文件或 README 抄细则） |
| HTTP / 错误码 | 07 + 09 |
| SQL / Flyway | 02 |
| 前端 | 12 + 13 |
| 分支 / 提交 | 08 + 04 |
| 步骤引擎场景 | feasibility-step-engine.md |
| 对账 / 补发 | feasibility-recon.md + design §5.11 |
| 风控判定链 | feasibility-risk.md + design §5.9 |
| 任务验收 | [docs/verification-matrix.md](docs/verification-matrix.md) |

## 栈（不可自行替换）

- JDK **26**（本机 `D:\develop\jdk\jdk-26.0.2`，PATH 默认是 25）+ Spring Boot **4.1.x**
- Jackson 3 包名 `tools.jackson`，只经 `kernel JsonUtil`
- MyBatis-Plus、Flyway（只在 `platform-db`）、Sa-Token 双 `StpLogic`
- 前端：pnpm workspace；`web/apps/admin` = vue-pure-admin-thin；`web/apps/client` = Vant 4
- 版本号唯一来源：[dependency-matrix.md](.kiro/specs/platform-v2/dependency-matrix.md)
- 现网 Redis **6.0.8 / DB 2**（与若依共用 6379）。IT 镜像可用 Redis 7。**禁止**用 db0

## 硬停止（AI 高频翻车）

- 只做当前任务勾选项。不要提前建 `web/`、P1 模块、Flyway 业务表、ArchUnit 全套（任务 10）
- 不要发明表、错误码、缓存命名空间、权限码、匿名端点
- 不要引入反选型：RuoYi、Spring Cloud、XXL-Job、Drools、H2、完整 Spring Security（只许 `spring-security-crypto`）、Hutool JSON/HTTP/DB
- 不要 `new ObjectMapper()`；不要 evict `identity:session`（踢人走 R6）。**细则只在 05-security.md**，本条不扩写
- 后台非 GET **必须** `@SaCheckPermission` + CSRF + `@Audited`；门户 / internal **禁止** `@Audited`。审计 / 会话 / evict **只以 05 为准**
- P0 见到 `ad:position`：只允许命名空间占位。广告读写 = 任务 48
- 乐观锁：要么实体 `@Version`，要么 XML 里 `version+1` 一次。`rwd_prize` **没有** version 列，库存 SQL 听 design §5.7
- `Result.code`：成功是数字 `0`，失败是字符串。前端用 `isOk` / `isFail`
- 本机无 Docker：单元测试可跑；`*IT` 留 CI，禁止为了本地绿改 H2 或削弱断言
- 测试与实现同任务交付。开 PR 后做两轮分开的代码评审，有问题开 issue 并直接修。不要等人确认。主分支健康检查交给每 3 小时定时任务，不挡开发

## 命令

本机默认 PATH 是 JDK 25。工作区是 PowerShell：

```text
$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"
```

`server/` 落地后（任务 9 验收就是这句）：

```text
cd server; mvn -q -DskipTests compile
```

测试与前端命令等 `server/`、`web/` 都有了再用（surefire：`*Test` / `*PropertyTest` / `*ArchTest`；`*IT` 走 failsafe / CI）：

```text
cd server; mvn -q -DskipITs test
cd web; pnpm install --frozen-lockfile; pnpm lint; pnpm test
pnpm --filter @mkt/shared gen:api
```

密钥只在 gitignore 的 `deploy/.env`。模板：`deploy/.env.example`（`REDIS_DATABASE=2`）。

## Git

见 [08-git-workflow.md](docs/standards/08-git-workflow.md)。

- 分支只用 `task/<n>-<slug>` / `spike/<n>-<slug>` / `fix/<slug>` / `docs/<slug>`。**禁止** `feat/`、`develop/`
- 提交：`feat(task): …` + `Refs: task-28, R13.7`
- 不要 force-push `master`，不要提交 `spike/*/target`
