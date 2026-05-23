# v0.2.0 API 文档

## 变更概述

v0.2.0 新增 Internal API 两个端点、Admin 子表 CRUD 四个 Controller、Client 任务详情返回结构变更。

## Internal API

### 推进 CALLBACK 步骤

```http
POST /api/internal/task/callback
Content-Type: application/json
```

#### 请求体

支持两种定位方式。`instanceId` 优先；无则用 `(userId, taskId, cycleKey)` 组合。

```json
{
  "instanceId": 100,
  "callbackEventKey": "survey_completed"
}
```

或：

```json
{
  "userId": "u_1001",
  "taskId": 2,
  "cycleKey": "202605",
  "callbackEventKey": "survey_completed"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `instanceId` | Long | 否 | 实例 ID（优先） |
| `userId` | String | 否 | 用户 ID |
| `taskId` | Long | 否 | 任务 ID |
| `cycleKey` | String | 否 | 周期键 |
| `callbackEventKey` | String | **是** | 业务事件 Key |

#### 行为

1. 定位实例
2. 按 `instance.currentStepSeq` 查找 CALLBACK 步骤
3. 校验 `step.callbackEventKey` == `request.callbackEventKey`
4. 完成步骤、级联推进（PASSIVE/REWARD 自动）
5. 幂等：已完成的步骤返回当前实例，不报错

#### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 100,
    "userId": "u_1001",
    "taskId": 2,
    "cycleKey": "202605",
    "status": "REWARDED",
    "currentStepSeq": 3
  }
}
```

#### 错误响应

事件 Key 不匹配：

```json
{
  "code": 400,
  "message": "回调事件Key不匹配",
  "data": null
}
```

### 推进 PROGRESS 步骤

```http
POST /api/internal/task/progress
Content-Type: application/json
```

#### 请求体

```json
{
  "instanceId": 100,
  "stepId": 15,
  "progressValue": 3
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `instanceId` | Long | 否 | 实例 ID（优先） |
| `userId` | String | 否 | 用户 ID |
| `taskId` | Long | 否 | 任务 ID |
| `cycleKey` | String | 否 | 周期键 |
| `stepId` | Long | **是** | 步骤 ID |
| `progressValue` | Integer | **是** | 当前进度值 |

#### 行为

1. 定位实例
2. 按 `stepId` 查找 PROGRESS 步骤，校验 `step.taskId` == `instance.taskId`
3. 插入或更新 `user_task_step_progress`
4. `progressValue >= step.targetValue` → 完成步骤，级联推进
5. `progressValue < step.targetValue` → 仅更新进度值，状态 IN_PROGRESS
6. 幂等：已完成的步骤返回当前实例

## Admin API 新增

### 步骤 CRUD

```http
GET    /api/admin/task/{taskId}/steps        # 列表
GET    /api/admin/task/{taskId}/steps/{id}   # 详情
POST   /api/admin/task/{taskId}/steps        # 创建（seq 自动 MAX+1）
PUT    /api/admin/task/{taskId}/steps/{id}   # 更新
DELETE /api/admin/task/{taskId}/steps/{id}   # 删除
```

### 端配置 CRUD

```http
GET    /api/admin/task/{taskId}/platforms        # 列表
GET    /api/admin/task/{taskId}/platforms/{id}   # 详情
POST   /api/admin/task/{taskId}/platforms        # 创建（同平台已存在则 upsert）
PUT    /api/admin/task/{taskId}/platforms/{id}   # 更新
DELETE /api/admin/task/{taskId}/platforms/{id}   # 删除
```

### 过滤器 CRUD

```http
GET    /api/admin/task/{taskId}/filters        # 列表
GET    /api/admin/task/{taskId}/filters/{id}   # 详情
POST   /api/admin/task/{taskId}/filters        # 创建
PUT    /api/admin/task/{taskId}/filters/{id}   # 更新
DELETE /api/admin/task/{taskId}/filters/{id}   # 删除
```

### 步骤端特化 CRUD

```http
GET    /api/admin/task/{taskId}/steps/{stepId}/platforms        # 列表
POST   /api/admin/task/{taskId}/steps/{stepId}/platforms        # 创建
DELETE /api/admin/task/{taskId}/steps/{stepId}/platforms/{id}   # 删除
```

## Admin 聚合保存

### 保存任务聚合

```http
POST /api/admin/task
Content-Type: application/json
```

#### 请求体（TaskAggregateDTO）

```json
{
  "task": {
    "id": 1,
    "code": "daily_checkin",
    "name": "日签到",
    "description": "每日签到领积分",
    "periodType": "DAILY",
    "status": "DRAFT"
  },
  "steps": [
    { "seq": 1, "code": "enter", "name": "进入任务", "type": "PASSIVE" },
    { "seq": 2, "code": "sign", "name": "点击签到", "type": "CLICK" },
    { "seq": 3, "code": "reward", "name": "领取积分", "type": "REWARD", "rewardConfigJson": "{\"type\":\"point\",\"amount\":10}" }
  ],
  "filters": [
    { "seq": 1, "expression": "inProvince(['BJ']) && levelGte(3)", "logicOp": "AND", "description": "仅BJ用户等级>=3", "enabled": true }
  ],
  "platforms": [
    { "platform": "WEB", "flowDesc": "在网页端签到", "buttonText": "去签到", "enabled": true }
  ]
}
```

#### 行为

`TaskService.saveAggregate()` 在事务内执行：
1. 保存/更新 `task` 主体
2. DELETE 该 taskId 的所有 `task_step` → INSERT 新列表
3. DELETE 该 taskId 的所有 `task_filter` → INSERT 新列表
4. DELETE 该 taskId 的所有 `task_platform` → INSERT 新列表

## Client API 变更

### 任务详情（返回结构变更）

```http
GET /api/client/task/{taskId}
X-User-Id: u_1001
X-Platform: WEB
```

#### v0.1.0 → v0.2.0

v0.1.0 返回 `UserTaskInstance` 对象。v0.2.0 改为返回 `TaskInstanceDetailDTO`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "instance": {
      "id": 100,
      "userId": "u_1001",
      "taskId": 1,
      "cycleKey": "20260518",
      "status": "IN_PROGRESS",
      "currentStepSeq": 2
    },
    "steps": [
      { "id": 10, "seq": 1, "code": "enter", "name": "进入任务", "type": "PASSIVE" },
      { "id": 11, "seq": 2, "code": "sign", "name": "点击签到", "type": "CLICK" },
      { "id": 12, "seq": 3, "code": "reward", "name": "领取积分", "type": "REWARD" }
    ],
    "stepPlatforms": [
      { "stepId": 11, "platform": "WEB", "buttonText": "去签到" }
    ]
  }
}
```

这让 C 端可以直接渲染步骤列表和端特化 UI，无需额外请求。

## 实现文件

| 端点 | 实现 |
|---|---|
| `POST /api/internal/task/callback` | `InternalCallbackController.java` |
| `POST /api/internal/task/progress` | `InternalCallbackController.java` |
| Admin Steps CRUD | `AdminStepController.java` |
| Admin Platforms CRUD | `AdminPlatformController.java` |
| Admin Filters CRUD | `AdminFilterCrudController.java` |
| Admin Step Platforms CRUD | `AdminStepPlatformController.java` |
| Admin 聚合保存 | `AdminTaskController.java` + `TaskService.saveAggregate()` |
| Client 详情变更 | `ClientTaskController.java` → `TaskInstanceDetailDTO` |
