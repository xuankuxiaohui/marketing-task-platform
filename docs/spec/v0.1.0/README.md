# v0.1.0 文档索引

版本：`v0.1.0`
日期：2026-05-18
范围：Sprint 1 MVP 骨架。目标是跑通营销任务系统的最小闭环：配置任务、按用户上下文过滤展示、创建用户任务实例、推进 CLICK 步骤、级联 REWARD 发奖。

## 文档

| 文档 | 类型 | 内容 |
|---|---|---|
| [设计文档](design.md) | Explanation / Architecture | 系统边界、领域模型、核心引擎、MVP 范围和后续演进 |
| [API 文档](api.md) | Reference | 当前 Spring Controller 暴露的 Admin / Client / Internal API |
| [SQL 文档](sql.md) | Reference | v0.1.0 数据表、索引、约束和 Flyway 迁移索引 |
| [版本说明](release-notes.md) | Release notes | 本版本新增内容、验证结果、已知限制 |

## 代码入口

| 模块 | 路径 |
|---|---|
| 后端入口 | `backend/src/main/java/com/marketing/task/TaskPlatformApplication.java` |
| 数据库迁移 | `backend/src/main/resources/db/migration/` |
| Admin 前端入口 | `admin-web/src/main.ts` |
| Admin 路由 | `admin-web/src/router/index.ts` |

## 当前验证状态

```bash
mvn -f backend/pom.xml -DskipTests compile -q
npm --prefix admin-web run build
```

两项均已通过。`pnpm install` 在当前机器上因 registry fetch 层报 `ERR_INVALID_THIS`，已用 `npm` 完成依赖安装和前端构建。
