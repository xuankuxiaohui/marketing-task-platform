# marketing-task-platform

自动化营销任务系统。运营在管理后台配置任务（步骤、过滤器、端入口），C 端用户按步骤完成任务，系统在任务完成后触发奖励发放。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 + Flyway |
| 表达式引擎 | QLExpress 3.3（任务过滤器，严格沙箱和函数白名单） |
| API 文档 | springdoc-openapi 2.x (Swagger UI: `http://localhost:8080/swagger-ui.html`) |
| 管理端 | Vue 3 + TypeScript + Vite + Element Plus (port 5173) |
| C 端 | Vue 3 + TypeScript + Vite + Vant 4 (port 5174) |
| 包管理 | pnpm workspace |

## 目录结构

```
backend/        Spring Boot 后端 (port 8080)
admin-web/      管理后台前端 (port 5173)
client-web/     C 端前端 (port 5174)
docs/           系统文档、架构、路线图、编码规范
scripts/        工程脚本
```

## 快速开始

**环境要求**：JDK 17+、Maven 3.9+、Node.js 18+、pnpm 8+、MySQL 8.0+

```bash
# 1. 初始化数据库（创建 database，Flyway 自动迁移）
mysql -u root -e "CREATE DATABASE IF NOT EXISTS marketing_task DEFAULT CHARACTER SET utf8mb4"

# 2. 修改数据库连接配置
# 编辑 backend/src/main/resources/application-dev.yml

# 3. 启动后端 (port 8080)
cd backend && ./mvnw spring-boot:run

# 4. 启动 Admin 前端 (port 5173)
cd admin-web && npm install && npm dev

# 5. 启动 C 端前端 (port 5174)
cd client-web && npm install && npm dev
```

## 文档

| 文档 | 内容 |
|---|---|
| [项目总览](docs/conventions/00-overview.md) | 技术栈、目录结构、启动方式、领域模型 |
| [架构文档](docs/architecture.md) | 架构流程图、领域模型 ER 图、序列图 |
| [系统路线图](docs/current-system-roadmap.md) | 已完成能力、当前限制、后续计划 (P0-P3) |
| [当前版本规格](docs/spec/current/) | v0.2.0 API、SQL、发布说明 |
| [编码规范](docs/conventions/) | 后端 / 前端 / API / Git 规范 |

### 编码规范

| 文档 | 内容 |
|---|---|
| [API 约定](docs/conventions/api-rules.md) | RESTful 规范、响应格式、UserContext |
| [后端 Java 规范](docs/conventions/backend/java-style.md) | 命名、POJO 设计、异常、日志 |
| [后端分层架构](docs/conventions/backend/layering.md) | 包结构、Controller/Service/Mapper、缓存 |
| [数据库规范](docs/conventions/backend/database.md) | 表命名、实体映射、Flyway 迁移 |
| [过滤器安全](docs/conventions/backend/filter-safety.md) | QLExpress 白名单、沙箱、OWASP |
| [步骤推进](docs/conventions/backend/step-advancement.md) | 5 种步骤类型、状态机、并发控制 |
| [前端共享规范](docs/conventions/frontend/shared.md) | Vue 3 / TypeScript / Pinia |
| [Admin 前端](docs/conventions/frontend/admin.md) | Element Plus 规则 |
| [C 端前端](docs/conventions/frontend/web.md) | Vant 4 规则 |
| [Git 规范](docs/conventions/git.md) | 提交类型、分支命名 |
