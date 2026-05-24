# v0.3.0 版本说明

发布日期：2026-05-24

## 目标

建立事件埋点基础设施（event_log），接入 Micrometer 实时指标，构建 task_metrics 日聚合表，增加管理端模拟测试能力（用户模拟 + 全流程测试），在前端展示运营仪表盘。

## 新增内容

### 事件埋点 (Event Tracking)

- **event_log 表 (Flyway V10)**：id / event_type / task_id / instance_id / step_id / user_id / platform / event_data(JSON) / created_at，4 个索引
- **EventType 枚举**：TASK_VIEWED, INSTANCE_CREATED, STEP_COMPLETED, REWARD_TRIGGERED, REWARD_SUCCESS, REWARD_FAILURE, CLAIM_SUCCESS, FILTER_EVALUATED
- **EventTrackingService**：`track()` 同步写入 event_log，内部 try-catch 保证不传播异常
- **10 个调用点注入**：ClientTaskController.list() / TaskService.getOrCreateInstance() / StepAdvanceEngine.completeStep() / RewardStepHandler.onStepEnter() / LogRewardService.reward() ×2 / ClaimService.claim() / FilterExpressionEngine.evaluate()

### Micrometer 指标 (Metrics)

- **MetricsService**：封装 MeterRegistry，6 个 Counter（task.views / task.instances.created / task.steps.completed / task.rewards.success / task.rewards.failure / prize.claims.success）+ 1 个 Timer（task.filter.evaluation）
- **Prometheus 预留**：通过 `/actuator/prometheus` 暴露（spring-boot-starter-actuator）
- **task_metrics 聚合表 (Flyway V10)**：task_id / metric_date / views / participants / completions / reward_success / reward_failure / avg_filter_ms，UNIQUE(task_id, metric_date)
- **TaskMetricsScheduler**：`@Scheduled(cron = "31 */5 * * * ?")` 每 5 分钟从 event_log 聚合写入 task_metrics（upsert）

### Admin Metrics API

- `GET /api/admin/metrics/dashboard` — 今日全局指标 + Top 10 任务排行
- `GET /api/admin/metrics/task/{taskId}/summary` — 单任务累计指标
- `GET /api/admin/metrics/task/{taskId}/daily?from=&to=` — 单任务按日趋势

### 管理端模拟测试 (Admin Simulation)

- **SimulateContextHolder**：ThreadLocal<UserContext>，独立于真实用户上下文
- **AdminSimulateController**：
  - `POST /api/admin/simulate/impersonate` — 设置模拟用户
  - `DELETE /api/admin/simulate/impersonate` — 退出模拟
  - `POST /api/admin/simulate/callback` — 模拟 CALLBACK
  - `POST /api/admin/simulate/progress` — 模拟 PROGRESS
  - `POST /api/admin/simulate/full-flow/{taskId}` — 一键全流程（创建实例→CLICK→CALLBACK→PROGRESS→REWARD 自动级联）
  - `GET /api/admin/simulate/status` — 查看当前模拟状态
- **TaskService.requireInstance()**：按 ID 查询实例，不存在时抛 INSTANCE_NOT_FOUND

### Admin 前端

- **Dashboard.vue**：4 张概览卡片（今日曝光/参与/完成/发奖成功率）+ 任务参与排行 Top 10 表格
- **TaskMetrics.vue**：累计指标卡片 + 日粒度指标表格 + 日期范围选择器
- **SimulateTab.vue**：用户身份模拟面板 + CALLBACK/PROGRESS 手动触发表单 + 一键全流程测试按钮 + 步骤执行结果展示
- **路由**：`/dashboard`（运营仪表盘）、`/tasks/:id/metrics`（任务指标）
- **API 模块**：metrics.ts（getDashboard / getTaskSummary / getTaskDaily）、simulate.ts（6 个模拟 API 函数）

## 验证结果

### 后端编译 + 测试

```bash
mvn -f backend/pom.xml test
```

结果：146 tests passed（13 new + 133 existing）。

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

## 新增测试

- EventTrackingServiceTest 4 tests
- MetricsServiceTest 3 tests
- TaskMetricsSchedulerTest 3 tests
- AdminMetricsControllerTest 3 tests

## 文件清单

### 新增后端文件

| 文件 | 说明 |
|---|---|
| `backend/.../common/EventType.java` | 事件类型枚举 |
| `backend/.../domain/entity/EventLog.java` | event_log 实体 |
| `backend/.../domain/entity/TaskMetrics.java` | task_metrics 实体 |
| `backend/.../mapper/EventLogMapper.java` | event_log Mapper |
| `backend/.../mapper/TaskMetricsMapper.java` | task_metrics Mapper |
| `backend/.../service/EventTrackingService.java` | 事件追踪服务 |
| `backend/.../service/MetricsService.java` | Micrometer + DB 指标服务 |
| `backend/.../service/TaskMetricsScheduler.java` | 指标聚合调度器 |
| `backend/.../context/SimulateContextHolder.java` | 模拟用户上下文 |
| `backend/.../controller/admin/AdminSimulateController.java` | 模拟测试控制器 |
| `backend/.../controller/admin/AdminMetricsController.java` | 指标 API 控制器 |
| `backend/.../db/migration/V10__event_log_and_metrics.sql` | Flyway 迁移 |

### 新增前端文件

| 文件 | 说明 |
|---|---|
| `admin-web/src/views/Dashboard.vue` | 全局仪表盘 |
| `admin-web/src/views/TaskMetrics.vue` | 单任务指标 |
| `admin-web/src/views/task/SimulateTab.vue` | 模拟测试 Tab |
| `admin-web/src/api/metrics.ts` | 指标 API |
| `admin-web/src/api/simulate.ts` | 模拟 API |

### 修改现有文件

- 6 个后端文件注入 EventTrackingService + MetricsService
- `admin-web/src/router/index.ts` 新增 2 条路由
- `admin-web/src/views/task/TaskEdit.vue` 新增模拟测试 Tab

## 已知限制

| 项 | 状态 |
|---|---|
| 灰度与实验 (百分比/AB/人群包) | 延至 v0.3.1 |
| Admin Metrics 可视化趋势图 | 当前用 el-table，后续可加入 echarts |
