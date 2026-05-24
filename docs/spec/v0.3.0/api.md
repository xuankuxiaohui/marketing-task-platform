# v0.3.0 API 文档

最后更新：2026-05-24

## 变更概述

v0.3.0 新增事件埋点基础设施、Micrometer 监控指标、管理端 Metrics API 和模拟测试 API。

## Admin Metrics API

### 全局仪表盘

```
GET /api/admin/metrics/dashboard
```

返回：`{ today: TaskMetrics, topTasks: TaskMetrics[] }`

### 单任务累计指标

```
GET /api/admin/metrics/task/{taskId}/summary
```

返回：`{ totalViews, totalParticipants, totalCompletions, totalRewardSuccess, totalRewardFailure, avgFilterMs }`

### 单任务按日趋势

```
GET /api/admin/metrics/task/{taskId}/daily?from=2026-05-01&to=2026-05-24
```

返回：`TaskMetrics[]`

### TaskMetrics 结构

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| taskId | Long | 任务 ID |
| metricDate | String | 日期 (yyyy-MM-dd) |
| views | Long | 曝光数 |
| participants | Long | 参与数 |
| completions | Long | 完成数 |
| rewardSuccess | Long | 发奖成功数 |
| rewardFailure | Long | 发奖失败数 |
| avgFilterMs | Double | 过滤平均耗时 (ms) |

## Admin Simulation API

### 模拟用户登录

```
POST /api/admin/simulate/impersonate
Content-Type: application/json

{
  "userId": "sim_user_001",
  "province": "SH",
  "role": "user",
  "tags": ["vip"],
  "level": 3,
  "platform": "IOS"
}
```

写入 SimulateContextHolder（独立于真实 UserContext），后续 admin 模拟 API 自动注入。

### 退出模拟

```
DELETE /api/admin/simulate/impersonate
```

清理 SimulateContextHolder。

### 模拟 CALLBACK

```
POST /api/admin/simulate/callback
Content-Type: application/json

{
  "instanceId": 123,
  "eventKey": "survey_complete"
}
```

### 模拟 PROGRESS

```
POST /api/admin/simulate/progress
Content-Type: application/json

{
  "instanceId": 123,
  "stepId": 5,
  "progressValue": 100
}
```

### 一键全流程测试

```
POST /api/admin/simulate/full-flow/{taskId}
```

在当前模拟用户下依次执行：创建实例 → CLICK 步骤推进 → CALLBACK 步骤推进 → PROGRESS 步骤推进 → REWARD 自动级联。返回每步执行结果列表。

### 查看模拟状态

```
GET /api/admin/simulate/status
```
