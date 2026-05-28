# v0.5.0 API 文档

最后更新：2026-05-28

## 变更概述

v0.5.0 新增批量发布/下线、定时发布、复制任务增强、任务软删除等 API。

---

## 批量发布

```
POST /api/admin/task/batch-publish
Content-Type: application/json

{
  "taskIds": [1, 2, 3]
}
```

返回：`Result<BatchTaskResult>`

```json
{
  "code": 200,
  "data": {
    "success": [1, 2],
    "failed": [
      { "id": 3, "reason": "任务状态不合法，当前状态: PUBLISHED" }
    ]
  }
}
```

校验规则：
- `taskIds` 不能为空
- 逐个执行 `publish()`，单个失败不阻塞其他
- 仅 DRAFT / SCHEDULED 状态可发布

---

## 批量下线

```
POST /api/admin/task/batch-offline
Content-Type: application/json

{
  "taskIds": [1, 2, 3]
}
```

返回：`Result<BatchTaskResult>`

校验规则：
- `taskIds` 不能为空
- 逐个执行 `offline()`，单个失败不阻塞其他
- 仅 PUBLISHED 状态可下线

---

## 定时发布

### 设置定时发布

```
POST /api/admin/task/{id}/schedule-publish
Content-Type: application/json

{
  "publishAt": "2026-06-01T10:00:00"
}
```

返回：`Result<Void>`

校验规则：
- `publishAt` 不能为空
- `publishAt` 必须在未来
- 任务状态必须为 DRAFT 或 SCHEDULED
- 设置后任务状态变为 SCHEDULED

### 取消定时发布

```
POST /api/admin/task/{id}/cancel-schedule
```

返回：`Result<Void>`

校验规则：
- 任务状态必须为 SCHEDULED
- 取消后任务状态回退为 DRAFT

### 定时发布调度

`TaskPublishScheduler` 每分钟扫描一次：
- 查询 `status = SCHEDULED` 且 `scheduled_publish_at <= now`
- 逐个调用 `taskService.publish(id)`
- 单个失败不影响其他任务

---

## 复制任务（增强）

```
POST /api/admin/task/{id}/copy
Content-Type: application/json

{
  "name": "自定义任务名称",
  "code": "custom_task_code"
}
```

返回：`Result<Long>` — 新任务 ID

请求体可选：
- `name` — 自定义名称，不传则为 `"{原名} (副本)"`
- `code` — 自定义 code，不传则为 `"{原code}_copy_{timestamp}"`

复制范围：
- 任务主体（description, periodType, cronExpr, startTime, endTime, mutexGroupId, grayType, grayConfig）
- 步骤（task_step）
- 过滤器（task_filter）
- 平台配置（task_platform）
- 步骤平台配置（task_step_platform）
- 分支配置（task_step_transition）

新任务状态为 DRAFT，version=0。

---

## 任务软删除

```
DELETE /api/admin/task/{id}
```

返回：`Result<Void>`

行为变更：从硬删除改为软删除（设置 `deleted=1`）。

### 查询已删除任务

```
GET /api/admin/task?page=1&size=20&status=DELETED
```

`status=DELETED` 时查询 `deleted=1` 的任务。其他 status 值默认过滤 `deleted=0`。

---

## DTO 结构

### BatchTaskRequest

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| taskIds | List\<Long\> | 是 | 任务 ID 列表，不能为空 |

### BatchTaskResult

| 字段 | 类型 | 说明 |
|---|---|---|
| success | List\<Long\> | 成功的任务 ID 列表 |
| failed | List\<FailedItem\> | 失败的任务及原因 |

### BatchTaskResult.FailedItem

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 失败的任务 ID |
| reason | String | 失败原因 |

### SchedulePublishRequest

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| publishAt | LocalDateTime | 是 | 定时发布时间 |

### TaskCopyRequest

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String | 否 | 自定义任务名称 |
| code | String | 否 | 自定义任务 code |
