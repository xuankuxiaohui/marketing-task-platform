# 项目总览

自动化营销任务系统：运营在管理后台配置任务，C 端用户按步骤完成任务，系统在任务完成后触发奖励发放。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端框架 | Java 17 + Spring Boot 3.2 |
| ORM | MyBatis-Plus 3.5 |
| 数据库 | MySQL 8 + Flyway 迁移 |
| 表达式引擎 | QLExpress 3.3（任务过滤器，严格沙箱和函数白名单） |
| API 文档 | springdoc-openapi 2.x |
| 管理端前端 | Vue 3 + TypeScript + Vite + Element Plus |
| C 端前端 | Vue 3 + TypeScript + Vite + Vant 4 |
| 包管理 | pnpm workspace (monorepo) |

## 目录结构

```
marketing-task-platform/
├── backend/         Spring Boot 后端 (port 8080)
├── admin-web/       管理后台前端 (port 5173)
├── client-web/      C 端前端 (port 5174)
├── docs/            系统文档、架构、路线图、编码规范
└── scripts/         工程脚本
```

## 系统入口

| 入口 | 前端 | API 命名空间 | 鉴权方式 |
|---|---|---|---|
| 管理后台 | admin-web (Element Plus) | `/api/admin/**` | UserContext Header |
| C 端 | client-web (Vant 4) | `/api/client/**` | UserContext Header |
| 内部回调 | 外部业务系统 | `/api/internal/**` | 无拦截器 |

## 核心领域模型

| 表 | 说明 |
|---|---|
| `task` | 任务主体，含周期类型、状态、版本、互斥组 |
| `task_step` | 任务步骤 (CLICK / CALLBACK / PROGRESS / REWARD / PASSIVE) |
| `task_step_platform` | 步骤的端特化配置（按钮文案、跳转目标） |
| `task_filter` | 任务过滤器 (QLExpress 表达式 + 白名单函数) |
| `task_platform` | 任务在不同端的入口配置 |
| `user_task_instance` | 用户任务实例，按 (user_id, task_id, cycle_key) 唯一 |
| `user_task_step_progress` | 用户每个步骤的进度记录 |

## 启动命令

```bash
# 后端
cd backend && ./mvnw spring-boot:run
cd backend && ./mvnw test

# Admin 前端
cd admin-web && npm install && npm dev

# Client 前端
cd client-web && npm install && npm dev
```

数据库使用 MySQL 8.0+，连接配置见 `backend/src/main/resources/application-dev.yml`。

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/architecture.md` | 架构流程图、领域模型 ER 图、序列图 |
| `docs/current-system-roadmap.md` | 已完成能力、当前限制、后续计划 (P0-P3) |
| `docs/conventions/api-rules.md` | 前后端 API 约定 (RESTful 规范、错误格式、UserContext) |
| `docs/conventions/backend/java-style.md` | Java / Spring Boot 编码规范 |
| `docs/conventions/backend/layering.md` | 后端分层架构与包结构 |
| `docs/conventions/backend/database.md` | 数据库命名、实体映射、迁移策略 |
| `docs/conventions/backend/filter-safety.md` | 过滤器安全规则 (OWASP 上下文) |
| `docs/conventions/backend/step-advancement.md` | 步骤推进规则 (5 种类型、状态机、并发) |
| `docs/conventions/frontend/shared.md` | 两个 Vue 项目共享的规则 |
| `docs/conventions/frontend/admin.md` | 管理后台 (Element Plus) 特有规则 |
| `docs/conventions/frontend/web.md` | C 端 (Vant 4) 特有规则 |
| `docs/conventions/git.md` | Git 提交规范、分支命名 |
| `docs/spec/current/` | 当前版本规格 (API、SQL、发布说明) |

## 开发注意事项

- 修改任务定义写路径 → 检查缓存失效（见 `backend/layering.md` 缓存章节）
- 修改步骤推进 → 保持幂等和事务完整性（见 `backend/step-advancement.md`）
- 修改过滤表达式 → 保持白名单和沙箱限制（见 `backend/filter-safety.md`）
- 修改业务流程 → 先对照 `docs/current-system-roadmap.md` 和 `docs/architecture.md`
- 不要把 `tsconfig.tsbuildinfo` 等构建产物纳入版本管理

## 文档规范

### 版本化规格目录 (docs/spec/)

每个大版本（v0.3.0、v0.4.0 等）使用独立文件夹：

```
docs/spec/
├── v0.1.0/     # 初始版本
├── v0.2.0/     # v0.2.x 系列
├── v0.3.0/     # 当前版本
├── ...         # 后续大版本
└── current/    # 始终镜像最新版本内容
```

**规则：**

1. **大版本独立文件夹** — 每个新的主/次版本号（如 v0.3.0、v0.3.1、v0.4.0）在 `docs/spec/` 下创建独立目录，包含该版本的 `api.md`、`sql.md`、`release-notes.md`
2. **current/ 始终指向最新** — 每次创建新版本目录后，同步更新 `docs/spec/current/` 内容为最新版本
3. **release-notes 版本隔离** — 每个版本目录的 release-notes.md 仅包含该版本的变更，历史版本保留不动
4. **子版本号判断** — 如果是小迭代（如 v0.3.1 仅灰度能力），可归入当前大版本目录（v0.3.0/），在 release-notes 中追加子章节；如果是跨模块大版本则新建独立目录
