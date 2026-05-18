# v0.2.0 版本说明

发布日期：2026-05-18

## 目标

在 Sprint 1 全链路基础上，补齐 CALLBACK/PROGRESS 步骤类型、Internal API（业务系统回调）、Platform 适配器、Admin 聚合保存与子表 CRUD、admin-web 编辑表单、client-web C 端骨架。

## 新增内容

### 后端：CALLBACK/PROGRESS 步骤 + Internal API

- `StepAdvanceEngine.callback(instance, eventKey)` — 按 `currentStepSeq` 定位 CALLBACK 步骤，校验 `callbackEventKey` 匹配后推进并级联
- `StepAdvanceEngine.progress(instance, stepId, progressValue)` — 按 `stepId` 定位 PROGRESS 步骤，`progressValue >= targetValue` 时完成并级联，未达标只更新进度
- `CallbackStepHandler` / `ProgressStepHandler` — StepHandler 实现，`onStepEnter` 为空（等待外部事件）
- `InternalCallbackController` — `POST /api/internal/task/callback` 和 `/progress`，支持 instanceId 或 (userId+taskId+cycleKey) 定位实例
- `CallbackRequest` / `ProgressRequest` DTO — 内部回调请求体
- `WebConfig` 拦截器路径从 `/api/**` 改为 `/api/admin/**`, `/api/client/**`，排除 Internal API
- 9 个单元测试（`StepAdvanceEngineTest`）：覆盖 callback/progress 推进、幂等、类型不匹配、跨任务校验

### 后端：Admin 聚合保存 + 子表 CRUD

- `TaskAggregateDTO` — 聚合请求体：{ task, steps[], filters[], platforms[] }
- `TaskService.saveAggregate()` — 事务内 delete-all + re-insert 子表
- `AdminTaskController.save()` — 从接收 `Task` 改为接收 `TaskAggregateDTO`
- `AdminStepController` — `/api/admin/task/{id}/steps` CRUD，自动 MAX(seq)+1
- `AdminPlatformController` — `/api/admin/task/{id}/platforms` CRUD + upsert
- `AdminFilterCrudController` — `/api/admin/task/{id}/filters` CRUD
- `AdminStepPlatformController` — `/api/admin/task/{id}/steps/{sid}/platforms` CRUD

### 后端：Platform 适配器 + Seed 数据

- `IosPlatformAdapter` / `AndroidPlatformAdapter` / `MiniappPlatformAdapter` — @Component 自动注册
- Flyway V3 种子数据：3 个演示任务
  - `daily_checkin` (DAILY) — PASSIVE → CLICK → REWARD + BJ 过滤器
  - `monthly_survey` (MONTHLY) — CALLBACK(调查完成) → REWARD(优惠券)
  - `reading_challenge` (ONCE) — PROGRESS(3篇) → REWARD(读者勋章)

### Admin 前端

- `StepsTab.vue` — 从骨架替换为可编辑 el-table：添加/删除步骤，类型选择器动态显示配置字段（PROGRESS→targetValue, CALLBACK→callbackEventKey, REWARD→rewardConfigJson）
- `PlatformsTab.vue` — 平台复选框 + 端配置（buttonText, flowDesc, jumpUri）
- `FiltersTab.vue` — 过滤器列表：表达式 + 描述 + 启用开关 + 校验按钮
- `TaskEdit.vue` — 通过 Tab ref 调用 `defineExpose` 聚合提交 `TaskAggregateDTO`
- `TaskList.vue` — 操作列添加"编辑"按钮
- 新增 API 模块：`step.ts`, `platform.ts`, `filter.ts`（CRUD 函数）

### Client 前端

- Vue 3 + Vant 4 + Vite 6 工程，`unplugin-auto-import` + `VantResolver` 按需引入
- `TaskList.vue` — van-pull-refresh + CellGroup 任务列表，loading/empty/error 三态
- `TaskDetail.vue` — van-steps 进度展示 + sticky 操作栏（CLICK 步骤推进按钮）
- `MockLogin.vue` — van-form 用户上下文编辑（userId, province, role, tags, level, platform）
- `http.ts` — Axios 实例 + `X-User-*` 请求拦截器（从 Pinia store 读取）
- `user.ts` — Pinia store：mock UserContext + localStorage 持久化
- 后端配合：`ClientTaskController.detail()` 返回 `TaskInstanceDetailDTO`（含 instance + steps + stepPlatforms）

## 验证结果

### 后端编译 + 测试

```bash
mvn -f backend/pom.xml test -q
```

结果：14 tests passed（含 Sprint 1 FilterExpressionEngineTest 5 tests + Sprint 2 StepAdvanceEngineTest 9 tests）。

### Admin 前端构建

```bash
npm --prefix admin-web run build
```

结果：通过。

### Client 前端构建

```bash
npm --prefix client-web run build
```

结果：通过。

## 已知限制

| 项 | 状态 | 后续版本 |
|---|---|---|
| 真实发奖 | LogRewardService（仅日志） | v0.3.0 对接真实发奖系统 |
| MONTHLY/CRON 周期 | 数据模型和种子数据就绪 | v0.3.0 补充调度触发 |
| 任务互斥 | `mutex_group_key` 字段预留 | v0.3.0 |
| 任务缓存 | 每次查询 DB | v0.3.0 Caffeine 缓存 |
| 真实鉴权 | Header Mock | 接入网关/JWT/用户中心 |
| 名单过滤 | allowlist/denylist 函数返回 true | 接入名单数据源 |
| 端到端测试 | 手动验证 | v0.3.0 考虑集成测试 |

## 从 v0.1.0 的变更

| v0.1.0 | v0.2.0 |
|---|---|
| `POST /api/admin/task` 只保存 task 主体 | 改为接收 `TaskAggregateDTO` 聚合保存 |
| Internal API 返回 501 | CALLBACK / PROGRESS 端到端可用 |
| admin-web 编辑页骨架 | 可编辑表单（步骤/过滤器/端配置） |
| client-web 不存在 | Vue3+Vant 骨架工程 |
| 3 个 Platform 适配器占位 | iOS/Android/小程序适配器已注册 |
| 无种子数据 | V3 seed 3 个演示任务 |

## 建议的下一版本 v0.3.0

1. 任务互斥组：同 `mutex_group_key` 任务互斥展示
2. 真实发奖对接：替换 LogRewardService，对接奖励中心
3. Caffeine 缓存：任务定义本地缓存 + 发布时刷新
4. OpenAPI → 前端类型生成：消除 API 类型手工维护
5. MONTHLY/CRON 周期调度触发
