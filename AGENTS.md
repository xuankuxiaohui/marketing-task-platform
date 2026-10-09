# 编码代理入口

本文件只保留工作方式与容易破坏的项目边界。项目介绍见 [README](README.md)，当前范围见 [PROJECT_STATUS](PROJECT_STATUS.md)，完整导航见 [docs/README](docs/README.md)。

## 开始工作

1. 先读用户本次要求和 `PROJECT_STATUS.md`，再检查 `git status`。保护已有未提交文件；不擅自回滚、清理或把它们纳入自己的提交。
2. 按问题读需求与设计章节；[design.md](.kiro/specs/platform-v2/design.md) §0.2 是分册索引。不要每次通读全部规格。
3. 只打开相关层的[编码规范](docs/standards/README.md)。测试与变更一起交付，执行与改动相称的检查，并说明未验证项。
4. 历史 `tasks.md` 和已勾选条目只用于追溯，不决定下一项工作，也不证明当前测试通过。

## 文档权责

| 问题 | 依据 |
|---|---|
| 本次做什么、做到哪里 | 用户本次授权；`PROJECT_STATUS.md` 记录范围 |
| 业务行为、验收、不变量 | [requirements.md](.kiro/specs/platform-v2/requirements.md) |
| 架构、Schema、接口、算法 | 需求之下的 `design.md` 及分册 |
| 怎么组织和检查实现 | `docs/standards/` |
| 哪些决定尚未落实 | [docs/decisions.md](docs/decisions.md) |

代码描述现状，规格描述约定；不以现有实现反向证明规格正确。发现互斥条款时记录两方依据、影响和建议，暂停受影响的实现，继续不依赖该决定的工作。重构计划不能覆盖业务规格。

## 必须保留的边界

- 两应用、两套账号与命名空间隔离；`/internal/**` 不上公网。
- `portal-app` 必须装配 task + reward，步骤推进与发奖同事务（RL-05）。积分归 `com.mkt.reward.points`，不建 `domain-points`。
- 领域模块之间不建立 Maven 依赖、不 import 对方实现、不直接操作对方表。同步通过 `platform-contract` 已定义端口或只读门面，异步通过 Outbox；扩展边界先同步设计。
- Flyway 迁移集中在 `platform-db`。库存条件更新、幂等唯一键、快照绑定及事务语义不得因“重构”被削弱。
- 后台写入的权限、CSRF、审计，会话踢出及门户资源归属检查，统一遵守 [05-security.md](docs/standards/05-security.md)。禁止以缓存 evict 踢会话。
- JDK 26 + Spring Boot 4.1.x；Jackson 3 通过 kernel `JsonUtil`；前端保留 pnpm workspace、Ant Design Vue / Vant 4。版本依据见[依赖矩阵](.kiro/specs/platform-v2/dependency-matrix.md)，不借重构擅自换栈。
- 不引入 RuoYi、Spring Cloud、XXL-Job、Drools、H2、完整 Spring Security 或 Hutool JSON/HTTP/DB。选型边界见[组件选型](.kiro/specs/platform-v2/component-selection.md)。
- 不临时发明表、错误码、权限码、缓存空间或匿名入口；需要新增时在同一变更内补齐规格与验证。
- 共享开发 Redis 用 DB 2。真实凭据只放被忽略的环境文件或部署注入，不进入文档、日志和提交。

## 未上线项目的重构方式

允许按明确方案替换旧实现，无需默认保留尚无消费者的兼容分支。先确认要保留的功能和环境数据，再改契约或数据结构；“未上线”不等于可以删除开发数据或遗漏当前未合入的功能。数据库重建与迁移压缩见决策记录。

一次交付一个可独立验证的用例或基础能力。完整替换后移除旧路径，避免长期双实现。纯文档工作不顺手改代码、配置、依赖或生成物。

## 环境与交付

本机默认 Java 不是 26，运行 Maven 前设置：

```powershell
$env:JAVA_HOME = 'D:\develop\jdk\jdk-26.0.2'
```

构建和测试命令见 [server/README](server/README.md)、[web/README](web/README.md)。本机无 Docker，真实 MySQL / Redis 的 `*IT` 在具备 Docker 的 CI 执行；不得改成 H2 或削弱断言换取通过。

分支、提交和评审见 [08-git-workflow](docs/standards/08-git-workflow.md) 与 [04-code-review](docs/standards/04-code-review.md)。未经用户明确要求不合并或直推 `master`。有脏工作区时先完成授权范围内可审阅的改动，不强行切分支。
