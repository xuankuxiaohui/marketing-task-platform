# marketing-task-platform

自动化营销任务系统：运营在管理后台配置任务，C 端用户按步骤完成任务，系统在任务完成后触发奖励发放。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.5 + MyBatis-Plus 3.5 + MySQL 8 + Flyway |
| 鉴权 | Sa-Token 1.44（admin / client 多账号 JWT） |
| 表达式引擎 | QLExpress 3.3（任务过滤器，沙箱 + 白名单） |
| API 文档 | springdoc-openapi 2.x |
| 管理端 | Vue 3 + TypeScript + Vite + Element Plus |
| C 端 | Vue 3 + TypeScript + Vite + Vant 4 |
| 包管理 | pnpm workspace |

## 目录结构

```text
backend/     Spring Boot 后端 (port 8080)
admin-web/   管理后台前端 (port 5173)
client-web/  C 端前端 (port 5174)
docs/        架构、规范、版本化契约
scripts/     工程脚本
```

后端主包：`com.marketing.task`。奖品子域：`com.marketing.task.prize`。

## API 命名空间

| 命名空间 | 使用方 | 鉴权 |
|---|---|---|
| `/api/admin/**` | admin-web | Sa-Token `StpUtil` (type=admin) |
| `/api/client/**` | client-web | Sa-Token `StpUserUtil` (type=client) |
| `/api/internal/**` | 外部业务系统 | 无 |
| `/api/captcha` | 共享 | 无 |

Controller 统一返回 `Result<T>`，分页直接 `Result<IPage<T>>`。

## 项目红线（不可违反）

1. **Controller 禁止接收或返回 Entity**。入参用 `*Request`，返回用 `*Response` 或 `*VO`；详见 `docs/conventions/api-rules.md`。
2. **任务定义写路径必须调用 `TaskDefinitionCacheService` 缓存失效**（聚合保存、发布/下线、步骤/过滤器/平台 CRUD）。
3. **步骤推进必须幂等、事务、并发保护**。重复 click / callback / progress 对已完成步骤无副作用；`(user_id, task_id, cycle_key)` 唯一约束保护实例创建。
4. **过滤器表达式只能经 `FilterExpressionEngine`** —— 沙箱、白名单函数、禁用关键字（System / Runtime / Process / Thread / forName / import / new / exec / eval）、长度 ≤1024、超时 100ms。禁止直接调用 `ExpressRunner`。
5. **奖励发放必须可幂等、可审计**。`(instance_id, step_id)` 唯一约束；无匹配 handler 直接抛异常、事务回滚，不写"已奖励"。
6. **不在日志打印** token / JWT secret / 验证码 / 用户敏感信息 / 奖励配置原文 / SQL 完整堆栈。
7. **MyBatis-Plus 查询禁止字符串拼接 SQL**，优先 `LambdaQueryWrapper` / `LambdaUpdateWrapper`，必要时用参数绑定 `apply("... {0}", v)`。
8. **管理端 `/api/admin/**` 与 C 端 `/api/client/**` 的鉴权上下文不可混用**。

## 任务 → 必读文档

| 任务类型 | 必读 |
|---|---|
| 后端分层 / Service / Mapper | `docs/conventions/backend/layering.md` |
| Java 命名 / POJO / 异常 / 日志 / 测试 | `docs/conventions/backend/java-style.md` |
| 表 / 列 / 索引 / Flyway 迁移 | `docs/conventions/backend/database.md` |
| 过滤器表达式 / 白名单 / 沙箱 | `docs/conventions/backend/filter-safety.md` |
| 步骤推进 / 状态机 / 级联 | `docs/conventions/backend/step-advancement.md` |
| API 命名空间 / Result / 接口对象边界 | `docs/conventions/api-rules.md` |
| Vue / TypeScript / Pinia / API 层 | `docs/conventions/frontend/shared.md` |
| 管理端 Element Plus | `docs/conventions/frontend/admin.md` |
| C 端 Vant 4 | `docs/conventions/frontend/web.md` |
| 架构图 / ER 图 / 序列图 | `docs/architecture.md` |
| 已完成 / 当前限制 / 路线图 | `docs/current-system-roadmap.md` |
| 当前版本 API / SQL / release notes | `docs/spec/current/` |
| 历史版本快照 | `docs/spec/` 下 `v0.1.0` … `v0.4.0` |
| Git 提交格式 | `docs/conventions/git.md` |
| 本地开发 / 部署 / 上线检查 | `docs/deployment.md` |

## AI 行为规则

- 修改业务代码前先 `git status` + 读相关 Service / Controller / Mapper，不要默默按文档改代码；文档与代码冲突时以代码为准，并在结果中指出。
- 实现要求清晰时直接推进，不为低风险取舍反复提问。
- 小步修改，不顺手重构无关模块；删除或迁移旧逻辑前确认没有前端、测试、文档、配置仍在引用。
- 不新增 `any`；后端不写 `Map<String, Object>` 作为 HTTP 契约。
- 测试范围随风险决定：后端 `cd backend && ./mvnw test`，前端 `npm run type-check` 或 `npm run build`。若未运行，说明原因和残余风险。
- 提交格式见 `docs/conventions/git.md`。不提交构建产物、`tsconfig.tsbuildinfo`、IDE 私有配置、本地密钥。
