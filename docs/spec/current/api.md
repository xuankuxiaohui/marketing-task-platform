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

### 获取测试用户列表

```
GET /api/admin/simulate/test-users
```

返回：`[{ userId: string }]`
从 `user_task_instance` 表中查询 distinct userId，按最近活跃时间排序，最多 50 条。

### 启动模拟流程

```
POST /api/admin/simulate/flow
Content-Type: application/json

{
  "userId": "sim_user_001",
  "taskId": 1,
  "province": "SH",
  "platform": "IOS"
}
```

自动设置模拟上下文 (SimulateContextHolder)，创建或查找已有的用户任务实例，返回实例详情和所有步骤状态。

返回结构：
```json
{
  "instance": { "id": 1, "userId": "...", "taskId": 1, "status": "IN_PROGRESS", "currentStepSeq": 2, ... },
  "steps": [
    { "stepId": 1, "seq": 1, "name": "浏览页面", "type": "CLICK", "isCurrentStep": false, "progressStatus": "COMPLETED", "progressValue": 0, ... },
    { "stepId": 2, "seq": 2, "name": "填写问卷", "type": "CALLBACK", "callbackEventKey": "survey_complete", "isCurrentStep": true, "progressStatus": null, "progressValue": null, ... }
  ]
}
```

### 获取实例详情（含步骤状态）

```
GET /api/admin/simulate/instance/{instanceId}/detail
```

返回格式与启动模拟流程相同，包含 `instance` 和 `steps` 两部分。

### 模拟点击 (CLICK 步骤)

```
POST /api/admin/simulate/click
Content-Type: application/json

{
  "instanceId": 123,
  "stepId": 5
}
```

### 获取实例事件日志

```
GET /api/admin/simulate/instance/{instanceId}/events
```

返回 event_log 表中该实例的所有事件，按时间正序（created_at ASC），时间相同时按 id 正序作为 tiebreaker，最多 100 条。
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

## v0.3.2 — 任务列表优化

### GET /api/admin/task 查询参数

```
GET /api/admin/task?page=1&size=20&status=DRAFT&keyword=签到&periodType=DAILY
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| page | long | 否 | 1 | 页码 |
| size | long | 否 | 20 | 每页条数 |
| status | String | 否 | -- | 按状态过滤：DRAFT / PUBLISHED / OFFLINE |
| keyword | String | 否 | -- | 模糊搜索任务编码(code)和名称(name) |
| periodType | String | 否 | -- | 按周期类型过滤：ONCE / DAILY / MONTHLY / CRON / SPECIAL |

返回：`Result<IPage<TaskAdminVO>>`

### TaskAdminVO 新增字段

| 字段 | 类型 | 说明 |
|---|---|---|
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |
| stepCount | Integer | 步骤数量（COUNT from task_step） |
| instanceCount | Integer | 用户实例数量（COUNT from user_task_instance） |

## v0.3.3 — 奖品记录管理

### 奖品记录列表（分页+筛选）

```
GET /api/admin/prize/records?page=1&size=20&userId=xxx&prizeId=1&status=FAILED&startDate=2026-05-01&endDate=2026-05-25
```

**查询参数**（全部可选）：

| 参数 | 类型 | 说明 |
|---|---|---|
| page | int | 页码，默认 1 |
| size | int | 每页条数，默认 20 |
| userId | String | 用户 ID（精确匹配） |
| prizeId | Long | 奖品 ID（精确匹配） |
| status | String | 记录状态：WON / CLAIMING / GRANTED / FAILED / FAILED_PERMANENTLY / EXPIRED |
| startDate | LocalDate | 获得时间起始 (yyyy-MM-dd) |
| endDate | LocalDate | 获得时间结束 (yyyy-MM-dd) |

返回：分页结果 `Result<Page<PrizeRecord>>`，按 wonAt 降序。

### 补发奖品

```
POST /api/admin/prize/records/{id}/reissue
```

将 FAILED / FAILED_PERMANENTLY / EXPIRED 状态的记录重置为 WON，延长过期时间 7 天，并触发领取。返回操作结果消息。GRANTED / CLAIMING 状态不可补发。

### 菜单优化

管理后台侧边栏菜单改为二级层级：
- **任务管理** → 任务列表
- **奖品管理** → 奖品配置 / 奖品记录
