# marketing-task-platform

自动化营销任务系统。运营在管理后台配置任务（步骤、过滤器、端入口），C 端用户按步骤完成任务，系统在任务完成后触发奖励发放。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.5 + MyBatis-Plus 3.5 + MySQL 8 + Flyway |
| 鉴权 | Sa-Token 1.44（admin / client 多账号 JWT） |
| 表达式引擎 | QLExpress 3.3（任务过滤器，沙箱 + 白名单） |
| API 文档 | springdoc-openapi 2.x（`/swagger-ui.html`） |
| 管理端 | Vue 3 + TypeScript + Vite + Element Plus（port 5173） |
| C 端 | Vue 3 + TypeScript + Vite + Vant 4（port 5174） |
| 包管理 | pnpm workspace |

## 目录结构

```text
backend/     Spring Boot 后端 (port 8080)
admin-web/   管理后台前端
client-web/  C 端前端
docs/        架构、规范、版本化契约
scripts/     工程脚本
```

## 快速开始

环境：JDK 17+、Maven 3.9+（`./mvnw` 自带）、Node.js 20+、pnpm 9+、MySQL 8.0+。

```bash
# 1. 创建数据库
mysql -u root -e "CREATE DATABASE IF NOT EXISTS marketing_task_platform DEFAULT CHARACTER SET utf8mb4"

# 2. 配置后端数据库连接：编辑 backend/src/main/resources/application-dev.yml

# 3. 启动后端 (port 8080)
cd backend && ./mvnw spring-boot:run

# 4. 启动管理后台 (port 5173)
cd admin-web && pnpm install && npm run dev

# 5. 启动 C 端 (port 5174)
cd client-web && pnpm install && npm run dev
```

详细部署与上线检查见 `docs/deployment.md`。

## 文档入口

| 文档 | 内容 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | AI 协作入口：项目红线、任务→文档索引、行为规则 |
| [docs/architecture.md](docs/architecture.md) | 架构流程图、领域模型 ER 图、序列图 |
| [docs/current-system-roadmap.md](docs/current-system-roadmap.md) | 已完成能力、当前限制、后续计划 |
| [docs/deployment.md](docs/deployment.md) | 本地开发、测试环境、生产部署、上线检查 |
| [docs/conventions/](docs/conventions/) | 后端 / 前端 / API / Git 编码规范 |
| [docs/spec/current/](docs/spec/current/) | 当前版本 API、SQL、发布说明 |
