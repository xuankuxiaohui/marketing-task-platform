# marketing-task-platform

营销自动化任务平台。运营在管理后台配置任务，用户在 C 端（Web / iOS / Android / 小程序 / 后台）完成任务并领取奖励。

## 当前状态

**Sprint 2 已完成 (2026-05-18)**。Sprint 1 全链路已跑通，Sprint 2 补齐了 CALLBACK/PROGRESS 步骤、Internal API、Platform 适配器、admin-web 聚合编辑表单、client-web (Vue3+Vant) 骨架。

完整设计方案、SQL、API 和版本说明见 `docs/v0.1.0/`。Sprint 2 新增内容见 `docs/v0.2.0/`。

## 技术栈

- **后端**：Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 + Flyway
- **表达式引擎**：QLExpress 3.3（用于任务过滤器，严格沙箱 + 函数白名单）
- **API 文档**：springdoc-openapi 2.x
- **前端**：Vue 3 + TS + Vite + Element-Plus（admin）/ Vant 4（client，Sprint 2 已实现）
- **Monorepo**：pnpm workspace

## 目录结构

```
backend/        Spring Boot 后端
admin-web/      管理后台前端（Vue3 + Element-Plus）
client-web/     C 端前端（Vue3 + Vant，Sprint 2+ 实现）
docs/           版本文档
```

## 核心领域模型

- `task` — 任务主体，含周期类型（ONCE/DAILY/MONTHLY/CRON/SPECIAL）、版本
- `task_step` — 任务步骤，类型为 CLICK/CALLBACK/PROGRESS/REWARD/PASSIVE
- `task_step_platform` — 步骤的端特化（按钮文字 + 跳转目标）
- `task_filter` — 任务过滤器（QLExpress 表达式 + 函数白名单）
- `task_platform` — 任务的端入口配置
- `user_task_instance` — 用户任务实例（user_id × task_id × cycle_key 唯一）
- `user_task_step_progress` — 用户每个步骤的进度

## 关键约定

### API 三层命名空间

- `/api/admin/**` 管理后台调
- `/api/client/**` C 端调（前端通过 Header 透传 UserContext）
- `/api/internal/**` 业务系统回调（推进 CALLBACK / PROGRESS 步骤）

### UserContext 注入（MVP）

通过 Header 透传：`X-User-Id` / `X-User-Province` / `X-User-Role` / `X-User-Tags` / `X-User-Org-Id` / `X-User-Level` / `X-Platform`。生产环境替换为 JWT 解析或 RPC 调用外部用户中心。本系统不存内部用户表。

### 周期 → cycle_key

`user_task_instance` 通过 `(user_id, task_id, cycle_key)` 唯一键防止同周期重复创建实例。

- `ONCE`    → 固定字符串 `"ONCE"`
- `DAILY`   → `yyyyMMdd`，例 `20260518`
- `MONTHLY` → `yyyyMM`，例 `202605`
- `CRON`    → 上次 fire time 的 `yyyyMMddHHmm`
- `SPECIAL` → `task.special_cycle_key`，例 `DOUBLE11_2026`

### 过滤器表达式安全规则（重要）

1. **必须**通过 `FilterExpressionEngine` 评估，不要直连 QLExpress。
2. 表达式只能调用 `FilterExpressionEngine` 注册的白名单函数：`inProvince` / `hasTag` / `hasAnyTag` / `roleEquals` / `roleIn` / `inAllowlist` / `notInDenylist` / `orgEquals` / `orgIn` / `levelGte` / `levelEq`。
3. **新增函数需在 `FilterExpressionEngine.registerFunctions()` 中显式注册并补充单元测试。**
4. 黑名单关键字（`System` / `Runtime` / `Process` / `Thread` / `Class.forName` / `import ` / `new ` / `exec` / `eval`）出现即拒绝。
5. 执行超时 100ms，表达式长度上限 1024 字符（DB 字段同步限制）。

### 步骤推进幂等

所有推进操作以 `(instance_id, step_id)` 为幂等键，重复调用返回当前状态而非报错。状态机只允许 `PENDING → IN_PROGRESS → COMPLETED`，不可回退。推进时对 `user_task_instance` 行加锁。

### 任务版本锁定

`user_task_instance.task_version` 在创建时快照。任务配置变更（发布时 `task.version + 1`）不影响已存在的 inflight 实例，避免运营调整任务结构导致用户进度异常。

### 级联推进

CLICK / CALLBACK / PROGRESS 步骤完成时，若下一步是 PASSIVE 或 REWARD，引擎自动级联推进，直到遇到需要用户/业务事件的步骤才停下。

## Sprint 2 关键新增

### 后端
- `StepAdvanceEngine.callback()` / `.progress()` — CALLBACK 按 currentStepSeq + eventKey 匹配推进，PROGRESS 按 stepId + targetValue 达标推进
- `InternalCallbackController` — `POST /api/internal/task/callback` 和 `/progress`，支持 instanceId 或 (userId+taskId+cycleKey) 定位实例
- `WebConfig` 拦截器路径改为 `/api/admin/**`, `/api/client/**`，Internal API 不经过 UserContextInterceptor
- `AdminStepController` / `AdminPlatformController` / `AdminFilterCrudController` / `AdminStepPlatformController` — 子表 CRUD
- `TaskService.saveAggregate(TaskAggregateDTO)` — 事务内 delete-all + re-insert 模式
- `TaskInstanceDetailDTO` — Client 端任务详情返回 instance + steps + stepPlatforms
- iOS/Android/Miniapp 平台适配器（@Component 自动注册）
- Flyway V3 seed 数据：3 个演示任务

### Admin 前端
- StepsTab / PlatformsTab / FiltersTab 从骨架替换为可编辑表格
- Tab 组件通过 `defineExpose` 暴露 get/set，父组件 TaskEdit 聚合提交
- 步骤类型选择器动态显示对应配置字段（PROGRESS→targetValue, CALLBACK→callbackEventKey, REWARD→rewardConfigJson）

### Client 前端
- Vue 3 + Vant 4 + Vite 6 工程，unplugin-auto-import + VantResolver 按需引入
- 任务列表（van-pull-refresh + loading/empty/error）、任务详情（van-steps + sticky 操作栏）、Mock 登录页
- Pinia store 管理 mock UserContext，Axios interceptor 注入 X-User-* headers

## 开发陷阱与模式（Sprint 2 踩坑记录）

- **MyBatis-Plus 3.5.7 测试初始化**：`TableInfoHelper.initTableInfo()` 签名为 `(MapperBuilderAssistant, Class)`，非旧版 `(GlobalConfig, Class)`。需用 `new MapperBuilderAssistant(new Configuration(), "")` 初始化。
- **Mockito ambiguous `any()`**：`BaseMapper.updateById()` 有重载 `updateById(T)` 和 `updateById(Collection<T>)`，stub 时必须写类型见证 `any(UserTaskInstance.class)`。
- **ArgumentCaptor 与级联**：`cascade()` 会多次 insert progress，`getValue()` 返回最后一次，取第一次用 `getAllValues().get(0)`。
- **Vue v-model 限制**：不能在 `v-for`/`v-slot` scope 变量上使用 `v-model`，改用单向绑定 + 事件。
- **Vant Tag type 类型**：`:type` 期望 `TagType` 联合类型，map 需显式标注 `Record<string, 'warning'|'primary'|...>`。

## 文档

当前版本文档入口：`docs/v0.1.0/`

- `docs/v0.1.0/design.md` — 系统设计、领域模型、核心引擎、MVP 边界
- `docs/v0.1.0/api.md` — Admin / Client / Internal API 参考
- `docs/v0.1.0/sql.md` — 表结构、索引、Flyway 迁移索引
- `docs/v0.1.0/release-notes.md` — v0.1.0 版本说明、验证结果、已知限制
- `docs/v0.2.0/` — Sprint 2 版本文档（待创建）

## 开发命令

```bash
# 后端
cd backend
./mvnw spring-boot:run            # 默认 8080；Flyway 启动时自动迁移

# Admin 前端
cd admin-web
pnpm install
pnpm dev                          # 默认 5173，已配代理 /api → :8080
```

数据库使用 MySQL 8.0+，连接配置见 `backend/src/main/resources/application-dev.yml`。

## Sprint 路线图

| Sprint | 内容 |
|---|---|
| **1 (MVP)** | 工程骨架 + 后端全链路（ONCE/DAILY + CLICK/PASSIVE/REWARD + Web/Admin 端）+ admin-web 任务配置/查询 | ✅ 完成 |
| **2** | 补 CALLBACK/PROGRESS 步骤；MONTHLY/CRON 周期；client-web 骨架；iOS/Android/小程序适配器 | ✅ 完成 |
| **3** | 任务互斥；真实发奖对接；任务定义 Caffeine 缓存；OpenAPI → 前端类型生成 | 待开始 |
| **4** | 实例归档/分表；监控埋点；运营数据看板；性能压测 | 待开始 |

## MVP 验收清单

1. admin 配置 + 发布日任务（DAILY、`inProvince(['BJ'])`、PASSIVE → CLICK → REWARD）
2. BJ 用户能在 list 接口看到，SH 用户看不到
3. 调详情接口自动创建实例，PASSIVE 自动完成
4. 调 click 接口推进，级联触发 REWARD，状态变 REWARDED，日志可见发奖
5. 重复 click 幂等无副作用
6. 跨日（新 cycle_key）任务回到 PENDING
7. 表达式包含 `System.exit(0)` 等关键字时校验接口返回 400
8. 任务 version 变更后已存在实例仍按旧 version 走


## Commit Convention

Use these commit types:

- `feat`: new feature
- `fix`: bug fix
- `refactor`: refactoring
- `docs`: documentation
- `test`: tests
