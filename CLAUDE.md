# marketing-task-platform

自动化营销任务系统。运营在管理后台配置任务，C 端用户按步骤完成任务，系统在任务完成后触发奖励发放。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 + Flyway |
| 表达式引擎 | QLExpress 3.3（任务过滤器，严格沙箱和函数白名单） |
| API 文档 | springdoc-openapi 2.x |
| 管理端 | Vue 3 + TypeScript + Vite + Element Plus |
| C 端 | Vue 3 + TypeScript + Vite + Vant 4 |
| 包管理 | pnpm workspace |

## 目录结构

```
backend/        Spring Boot 后端
admin-web/      管理后台前端
client-web/     C 端前端
docs/           系统文档、架构、路线图、编码规范
scripts/        工程脚本
```

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/conventions/00-overview.md` | 项目总览（技术栈、目录结构、启动方式、领域模型） |
| `docs/architecture.md` | 架构流程图、领域模型 ER 图、序列图 |
| `docs/current-system-roadmap.md` | 已完成能力、当前限制、后续计划 (P0-P3) |
| `docs/conventions/api-rules.md` | 前后端 API 约定（RESTful 规范、错误格式、UserContext） |
| `docs/conventions/backend/java-style.md` | Java / Spring Boot 编码规范 |
| `docs/conventions/backend/layering.md` | 后端分层架构与包结构约定 |
| `docs/conventions/backend/database.md` | 数据库命名、实体映射、迁移策略 |
| `docs/conventions/backend/filter-safety.md` | 过滤器安全规则（白名单函数、禁用关键字、超时保护） |
| `docs/conventions/backend/step-advancement.md` | 步骤推进规则（5 种类型、级联、幂等、事务、并发） |
| `docs/conventions/frontend/shared.md` | 两个 Vue 项目共享的规则 |
| `docs/conventions/frontend/admin.md` | 管理后台 (Element Plus) 特有规则 |
| `docs/conventions/frontend/web.md` | C 端 (Vant 4) 特有规则 |
| `docs/conventions/git.md` | Git 提交规范、分支命名 |
| `docs/spec/current/` | 当前版本规格 (API、SQL、发布说明) |

## 开发注意事项

- 所有详细文档在 `docs/` 目录下，本文件只做索引
- 修改业务流程 → 先对照 `docs/current-system-roadmap.md` 和 `docs/architecture.md`
- 修改任务定义写路径 → 检查缓存失效（见 `docs/conventions/backend/layering.md`）
- 修改步骤推进 → 保持幂等和事务完整性（见 `docs/conventions/backend/step-advancement.md`）
- 修改过滤表达式 → 保持白名单和沙箱限制（见 `docs/conventions/backend/filter-safety.md`）



## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
