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

## v0.3.1 — Admin Instance API (实例查询优化)

### 实例列表（带筛选）

```
GET /api/admin/instance?page=1&size=20&userId=xxx&taskId=123&status=IN_PROGRESS&startDate=2026-05-01&endDate=2026-05-24
```

**查询参数**（全部可选）：

| 参数 | 类型 | 说明 |
|---|---|---|
| page | long | 页码，默认 1 |
| size | long | 每页条数，默认 20 |
| userId | String | 用户 ID（模糊匹配） |
| taskId | Long | 任务 ID（精确匹配） |
| status | String | 实例状态：PENDING / IN_PROGRESS / COMPLETED / REWARDED / EXPIRED |
| startDate | LocalDate | 创建时间起始 (yyyy-MM-dd) |
| endDate | LocalDate | 创建时间结束 (yyyy-MM-dd) |

**返回**：分页结果 `IPage<UserTaskInstanceVO>`，每条记录额外包含 `taskName`（任务名称）和 `createdAt`（创建时间）字段。

### 实例详情

```
GET /api/admin/instance/{id}
```

**返回**：

```json
{
  "instance": { ... UserTaskInstanceVO },
  "steps": [
    {
      "stepId": 1,
      "stepSeq": 1,
      "stepName": "分享任务",
      "stepType": "CLICK",
      "stepDescription": "分享到社交媒体",
      "targetValue": null,
      "status": "COMPLETED",
      "progressValue": null,
      "completeTime": "2026-05-24T10:30:00"
    }
  ],
  "totalSteps": 5
}
```

步骤列表将任务定义（task_step）与步骤进度（user_task_step_progress）合并，即使某步骤尚未产生进度记录，也会显示为 PENDING 状态。PROGRESS 类型步骤包含 `targetValue`（任务定义的目标值）和 `progressValue`（当前进度值）。

## v0.3.1 — 过滤表达式新增灰度函数

在 FilterExpressionEngine 中新增 3 个过滤函数：

| 函数 | 参数 | 示例 | 行为 |
|---|---|---|---|
| `inGrayPercent(percent)` | 0-100 整数 | `inGrayPercent(10)` | hash(userId+taskId) % 100 < percent |
| `inABGroup(groupName)` | 字符串 | `inABGroup('A')` | 基于 hash + grayConfig 分组分配判断 |
| `inCrowd(crowdId)` | 整数 | `inCrowd(1)` | 查 list_data 表 (listType=CROWD) |

### 表达式示例

```
inGrayPercent(30)                                   // 30% 灰度
inABGroup('A')                                      // AB 实验 A 组
inCrowd(1) || inCrowd(2)                            // 人群包 1 或 2
inGrayPercent(50) && inABGroup('A')                 // 50% 灰度中的 A 组
inProvince(['BJ','SH']) && inGrayPercent(10)         // 仅京沪 10% 灰度
```
