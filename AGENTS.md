# AGENTS.md

## 项目与工作范围

营销任务平台：运营在管理端编排任务，用户在移动门户参与并获得奖品、积分。项目尚未上线。

- 以用户本次要求确定范围；开始修改前检查 `git status`，保留已有未提交改动，不擅自回滚、清理或混入提交。
- 按任务需要查资料：[功能清单](docs/current-features.md) 查现有入口与缺口，[决策记录](docs/decisions.md) 查已确认规则和未决问题，[文档导航](docs/README.md) 查工程说明和历史报告。无需每次通读全部文档。
- 旧 Kiro 规格已退役，历史编号、勾选和报告不构成当前需求或测试通过证明。实现与测试用于核实现状；行为变更以本次要求和已确认决定为准。冲突影响实现时列明依据，澄清该项并继续无关工作。
- 纯文档任务只改文档。未上线允许删除已完整替换的旧路径，但不能据此删除开发数据、改写已应用迁移或遗漏现有功能；数据去留见 DEC-005。

## 仓库入口

| 路径 | 用途 |
|---|---|
| `server/` | Maven 多模块：`platform-*` 基础能力、`domain-*` 业务域、`admin-app` / `portal-app` 两应用；详见 [后端说明](server/README.md) |
| `web/apps/admin/` | Vue 3 + TypeScript + Ant Design Vue 管理端 |
| `web/apps/client/` | Vue 3 + TypeScript + Vant 4 移动门户 |
| `web/packages/shared/` | 两端共享纯工具与 OpenAPI 契约；工程命令见 [前端说明](web/README.md) |
| `scripts/`、`ci/`、`deploy/`、`perf/` | [本地启动](scripts/README.md)、CI、部署和性能验证；操作前确认目标环境 |

依赖版本以 `server/pom.xml`、各模块 POM、`web/package.json` 和 `web/pnpm-lock.yaml` 为准。保留 JDK 26、Spring Boot 4.1.x 和 pnpm workspace，不借重构换栈或绕过 Maven Enforcer 的依赖限制。

## 实现边界

- **应用与身份隔离**：管理端 `/admin/**`，门户 `/api/**`，内部回调 `/internal/**`；两套账号、会话和命名空间独立，内部回调及监控端点不向公网开放。
- **领域隔离**：领域模块之间不建 Maven 依赖、不导入对方实现、不直接操作对方表。同步通过 `platform-contract` 端口，异步通过 Outbox；新增跨域契约同批记录职责、语义和验证，聚合查询的待定边界见 DEC-003。
- **任务与奖励原子性**：`portal-app` 同时装配 task 和 reward，步骤推进、正常发奖与积分变动保持同一数据库事务；积分归 `com.mkt.reward.points`，不建 `domain-points`。独立事务和失败留痕不得任意扩展，见 DEC-004。
- **持久化正确性**：Flyway 迁移集中在 `server/platform-db`。保留库存条件扣减、幂等唯一键、CAS、实例快照绑定及 Outbox 与业务同事务；不能用缓存或进程锁替代数据库约束。旧实例、下线与到期规则见 DEC-007。
- **鉴权与会话**：后台写入保留权限校验、CSRF 与审计；门户私有资源验证当前用户归属。踢会话使用会话服务，禁止用缓存 evict 代替。公开浏览不等于匿名写入授权。
- **代码组织**：Controller 处理协议和身份，application 编排用例及事务，domain/engine 放规则，store/mapper 负责本域持久化。复用 kernel 的 `JsonUtil`（Jackson 3）与注入的 `Clock`；沿用现有错误码、权限码和缓存命名，新增时同步调用方、说明和测试。
- **前端状态**：两端各自管理认证与 UI，shared 不放应用会话。异步读取覆盖 loading、空、错和重试；切换账号、筛选或路由后旧响应不得回写。界面工作按需查 [DESIGN.md](DESIGN.md)。
- **生成文件**：不手改 `web/packages/shared/src/openapi/`；契约变更从后端导出 JSON、生成 TS，再核对调用方。命令及当前门禁局限见 [前端 API 类型](web/README.md#api-类型)。
- **环境数据**：共享开发 Redis 使用 DB 2。真实凭据仅放被忽略的环境文件或部署注入，不写入源码、文档、日志和提交；性能种子、恢复和清库仅在明确授权的目标环境执行。

## 常用检查

后端从仓库根执行。本机默认 Java 不是 26，先设置：

```powershell
$env:JAVA_HOME = 'D:\develop\jdk\jdk-26.0.2'
mvn -f server/pom.xml -q -DskipITs test
```

修改单个领域可用 `mvn -f server/pom.xml -q -pl domain-task -am -DskipITs test`，替换为目标模块；共享契约、装配或基础设施改动需覆盖消费者。`test` 包含 JaCoCo 门禁。真实 MySQL / Redis 的 `*IT` 由有 Docker 的环境运行 `mvn -f server/pom.xml -B verify`；本机无 Docker，不以 H2 或削弱断言替代。

前端在 `web/` 执行：

```text
pnpm install --frozen-lockfile
pnpm --filter client test
pnpm --filter client build
pnpm lint
```

按修改范围将 `client` 换为 `admin` 或 `@mkt/shared`（shared 使用 `typecheck`，无 build）；跨包测试用 `pnpm test`。`pnpm lint` 包含类型检查，应用 build 包含 `vue-tsc`，单独 Vite 成功不能代替它们。浏览器回归与后端环境要求见 [前端测试边界](web/README.md#测试边界)。

## 交付

- 行为修复交付能暴露原问题的回归测试，执行与变更相称的检查；文档改动检查链接、引用和 `git diff --check`，无需重跑业务测试。
- 总结改了什么、实际检查结果和未验证项，区分既有失败与本次引入的问题；历史报告不作为本轮通过证据。功能、命令或决定变化时更新对应文档，不另维护一份全项目进度流水账。
- 提交只包含本次授权范围。未经明确要求不合并或直推 `master`；脏工作区不强行切分支。
