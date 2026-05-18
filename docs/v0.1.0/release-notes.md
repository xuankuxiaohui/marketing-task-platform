# v0.1.0 版本说明

发布日期：2026-05-18

## 目标

建立营销自动化任务平台的 Sprint 1 MVP 骨架，留下后续按版本演进的代码和文档基线。

## 新增内容

### Monorepo

- 新增根目录 `package.json`
- 新增 `pnpm-workspace.yaml`
- 新增 `.gitignore`
- 保留 `backend/`、`admin-web/`、`client-web/` 三个工作区结构

### 后端

- 新增 Spring Boot 3.2 后端工程
- 新增 MyBatis-Plus、Flyway、QLExpress、springdoc-openapi 依赖
- 新增 7 张核心表 Flyway 迁移
- 新增核心领域实体和 Mapper
- 新增 UserContext Header 注入
- 新增 QLExpress 过滤器安全边界
- 新增周期解析 `CycleKeyResolver`
- 新增步骤推进 `StepAdvanceEngine`
- 新增 Mock 发奖 `LogRewardService`
- 新增 Admin / Client / Internal Controller 骨架

### Admin 前端

- 新增 Vue 3 + TypeScript + Vite + Element-Plus 工程
- 新增任务列表页
- 新增任务编辑骨架页
- 新增过滤器表达式校验页签
- 新增用户任务实例查询页
- 新增 Mock 用户上下文页面

### 文档

- 新增版本化文档目录 `docs/v0.1.0/`
- 新增设计文档 `design.md`
- 新增 API 文档 `api.md`
- 新增 SQL 文档 `sql.md`
- 新增 SQL 快照 `sql/V1__init_task_core.sql` 和 `sql/V2__init_task_instance.sql`

## 验证结果

### 后端编译

```bash
mvn -f backend/pom.xml -DskipTests compile -q
```

结果：通过。

### Admin 前端构建

```bash
npm --prefix admin-web run build
```

结果：通过。

### pnpm 状态

```bash
pnpm --dir . install
```

当前机器失败：

```text
ERR_PNPM_META_FETCH_FAIL: Value of "this" must be of type URLSearchParams
```

已用 npm 兜底完成依赖安装和构建。后续可排查当前 Node/pnpm 组合或 registry 客户端问题。

## 已知限制

| 项 | 状态 | 后续版本 |
|---|---|---|
| Admin 保存任务聚合 | 只保存 task 主体 | v0.2.0 增加步骤、过滤器、端配置聚合保存 |
| 步骤配置页面 | 骨架提示 | v0.2.0 增加可编辑步骤表格 |
| 过滤器配置 | 只有校验接口 | v0.2.0 增加 CRUD 和任务关联 |
| 端配置 | 表和适配器预留 | v0.2.0 增加 CRUD |
| CALLBACK / PROGRESS | Internal API 返回 501 | Sprint 2 |
| 名单过滤 | allowlist / denylist 函数返回 true | 接入名单数据源 |
| 任务版本快照 | 只记录 `task_version` | 增加配置快照或子表 version |
| 真实鉴权 | Header Mock | 接入网关/JWT/用户中心 |

## 建议的下一版本 v0.2.0

1. 增加 Admin 任务聚合保存接口：一次提交 task、steps、filters、platforms。
2. 增加步骤、过滤器、端配置的管理 API。
3. 补 admin-web 任务编辑页真实表单和提交。
4. 增加最小种子数据或本地初始化脚本，便于一键演示 PASSIVE → CLICK → REWARD。
5. 增加 `FilterExpressionEngine` 单元测试。
