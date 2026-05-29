# 任务模块 API

最后更新：2026-05-29

## Admin 任务 CRUD

### 分页查询

```
GET /api/admin/task
```

返回：`Result<IPage<TaskVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| status | String | 否 | 任务状态筛选 |
| keyword | String | 否 | 搜索关键字 |
| periodType | String | 否 | 周期类型 |

### 聚合保存

```
POST /api/admin/task
```

返回：`Result<TaskVO>`

Body：`TaskAggregateDTO`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| task | TaskDTO | 是 | 任务基本信息 |
| steps | List\<StepDTO\> | 否 | 步骤列表 |
| filters | List\<FilterDTO\> | 否 | 过滤器列表 |
| platforms | List\<PlatformDTO\> | 否 | 平台列表 |
| transitions | List\<TransitionDTO\> | 否 | 条件分支列表 |

### 任务详情

```
GET /api/admin/task/{id}
```

返回：`Result<TaskVO>`

### 发布

```
POST /api/admin/task/{id}/publish
```

返回：`Result<Void>`

### 下线

```
POST /api/admin/task/{id}/offline
```

返回：`Result<Void>`

### 软删除

```
DELETE /api/admin/task/{id}
```

返回：`Result<Void>`

### 批量发布

```
POST /api/admin/task/batch-publish
```

返回：`Result<BatchTaskResult>`

Body：`BatchTaskRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| taskIds | List\<Long\> | 是 | 任务 ID 列表 |

### 批量下线

```
POST /api/admin/task/batch-offline
```

返回：`Result<BatchTaskResult>`

Body：`BatchTaskRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| taskIds | List\<Long\> | 是 | 任务 ID 列表 |

### 定时发布

```
POST /api/admin/task/{id}/schedule-publish
```

返回：`Result<Void>`

Body：`SchedulePublishRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| publishAt | LocalDateTime | 是 | 定时发布时间 |

### 取消定时发布

```
POST /api/admin/task/{id}/cancel-schedule
```

返回：`Result<Void>`

### 复制任务

```
POST /api/admin/task/{id}/copy
```

返回：`Result<TaskVO>`

Body：`TaskCopyRequest`

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String | 否 | 新任务名称，不填则自动追加"副本"后缀 |
| code | String | 否 | 新任务 code，不填则自动生成 |

### 版本历史

```
GET /api/admin/task/{id}/versions
```

返回：`Result<List<TaskVersionVO>>`

### 版本详情

```
GET /api/admin/task/{id}/versions/{versionId}
```

返回：`Result<TaskVersionDetailVO>`

## Admin 步骤管理

### 步骤列表

```
GET /api/admin/task/{taskId}/steps
```

返回：`Result<List<StepVO>>`

### 步骤详情

```
GET /api/admin/task/{taskId}/steps/{stepId}
```

返回：`Result<StepVO>`

### 创建步骤

```
POST /api/admin/task/{taskId}/steps
```

返回：`Result<StepVO>`

### 更新步骤

```
PUT /api/admin/task/{taskId}/steps/{stepId}
```

返回：`Result<StepVO>`

### 删除步骤

```
DELETE /api/admin/task/{taskId}/steps/{stepId}
```

返回：`Result<Void>`

### 校验 code 唯一性

```
GET /api/admin/task/{taskId}/steps/check-code
```

返回：`Result<Boolean>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| code | String | 是 | 待校验的步骤 code |
| excludeId | Long | 否 | 排除的步骤 ID（编辑时使用） |

### 拖拽排序

```
PUT /api/admin/task/{taskId}/steps/reorder
```

返回：`Result<Void>`

## Admin 过滤器

### 表达式校验

```
POST /api/admin/filter/validate
```

返回：`Result<FilterValidateResult>`

### 过滤器列表

```
GET /api/admin/task/{taskId}/filters
```

返回：`Result<List<FilterVO>>`

### 过滤器详情

```
GET /api/admin/task/{taskId}/filters/{filterId}
```

返回：`Result<FilterVO>`

### 创建过滤器

```
POST /api/admin/task/{taskId}/filters
```

返回：`Result<FilterVO>`

### 更新过滤器

```
PUT /api/admin/task/{taskId}/filters/{filterId}
```

返回：`Result<FilterVO>`

### 删除过滤器

```
DELETE /api/admin/task/{taskId}/filters/{filterId}
```

返回：`Result<Void>`

## Admin 平台

### 平台列表

```
GET /api/admin/task/{taskId}/platforms
```

返回：`Result<List<PlatformVO>>`

### 平台详情

```
GET /api/admin/task/{taskId}/platforms/{platformId}
```

返回：`Result<PlatformVO>`

### 创建平台

```
POST /api/admin/task/{taskId}/platforms
```

返回：`Result<PlatformVO>`

### 更新平台

```
PUT /api/admin/task/{taskId}/platforms/{platformId}
```

返回：`Result<PlatformVO>`

### 删除平台

```
DELETE /api/admin/task/{taskId}/platforms/{platformId}
```

返回：`Result<Void>`

## Admin 步骤平台

### 步骤平台列表

```
GET /api/admin/task/{taskId}/steps/{stepId}/platforms
```

返回：`Result<List<StepPlatformVO>>`

### 创建步骤平台

```
POST /api/admin/task/{taskId}/steps/{stepId}/platforms
```

返回：`Result<StepPlatformVO>`

### 删除步骤平台

```
DELETE /api/admin/task/{taskId}/steps/{stepId}/platforms/{stepPlatformId}
```

返回：`Result<Void>`

### 任务维度步骤平台列表

```
GET /api/admin/task/{taskId}/step-platforms
```

返回：`Result<List<StepPlatformVO>>`

### 批量更新步骤平台

```
PUT /api/admin/task/{taskId}/step-platforms
```

返回：`Result<Void>`

## Admin 条件分支

### 获取任务所有分支

```
GET /api/admin/task/{taskId}/transitions
```

返回：`Result<List<TaskStepTransitionVO>>`

按 stepId 分组返回。

### 获取步骤分支列表

```
GET /api/admin/step/{stepId}/transitions
```

返回：`Result<List<TaskStepTransitionVO>>`

### 批量保存分支

```
POST /api/admin/step/{stepId}/transitions
```

返回：`Result<List<TaskStepTransitionVO>>`

### 删除步骤所有分支

```
DELETE /api/admin/step/{stepId}/transitions
```

返回：`Result<Void>`

## Admin 互斥组

### 互斥组列表

```
GET /api/admin/mutex-groups
```

返回：`Result<IPage<MutexGroupVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |

### 互斥组详情

```
GET /api/admin/mutex-groups/{id}
```

返回：`Result<MutexGroupVO>`

### 互斥组关联任务

```
GET /api/admin/mutex-groups/{id}/tasks
```

返回：`Result<List<TaskVO>>`

### 创建互斥组

```
POST /api/admin/mutex-groups
```

返回：`Result<MutexGroupVO>`

### 更新互斥组

```
PUT /api/admin/mutex-groups/{id}
```

返回：`Result<MutexGroupVO>`

### 删除互斥组

```
DELETE /api/admin/mutex-groups/{id}
```

返回：`Result<Void>`

### 移除任务关联

```
DELETE /api/admin/mutex-groups/{groupId}/tasks/{taskId}
```

返回：`Result<Void>`

## Admin 实例管理

### 实例分页查询

```
GET /api/admin/instance
```

返回：`Result<IPage<InstanceVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| userId | Long | 否 | 用户 ID |
| taskId | Long | 否 | 任务 ID |
| status | String | 否 | 实例状态 |

### 实例详情

```
GET /api/admin/instance/{id}
```

返回：`Result<InstanceVO>`

### 实例事件日志

```
GET /api/admin/instance/{id}/events
```

返回：`Result<List<InstanceEventVO>>`

## C 端任务

### 可见任务列表

```
GET /api/client/task/list
```

返回：`Result<List<TaskVO>>`

### 任务详情

```
GET /api/client/task/{taskId}
```

返回：`Result<TaskVO>`

### 开始任务

```
POST /api/client/task/{taskId}/start
```

返回：`Result<InstanceVO>`

### 点击步骤

```
POST /api/client/task/{taskId}/step/{stepId}/click
```

返回：`Result<StepClickResultVO>`

## Internal 接口

### 步骤回调

```
POST /api/internal/task/callback
```

返回：`Result<Void>`

### 步骤进度上报

```
POST /api/internal/task/progress
```

返回：`Result<Void>`

## DTO 结构

### BatchTaskRequest

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| taskIds | List\<Long\> | 是 | 任务 ID 列表 |

### BatchTaskResult

| 字段 | 类型 | 说明 |
|---|---|---|
| success | List\<Long\> | 成功的任务 ID |
| failed | List\<FailedItem\> | 失败的任务 ID 及原因 |

### FailedItem

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 任务 ID |
| reason | String | 失败原因 |

### SchedulePublishRequest

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| publishAt | LocalDateTime | 是 | 定时发布时间 |

### TaskCopyRequest

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| name | String | 否 | 新任务名称，不填则自动追加"副本"后缀 |
| code | String | 否 | 新任务 code，不填则自动生成 |

### TaskStepTransitionVO

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 分支 ID |
| stepId | Long | 来源步骤 ID |
| stepCode | String | 来源步骤 code |
| targetStepId | Long | 目标步骤 ID |
| targetStepCode | String | 目标步骤 code |
| conditionExpr | String | 条件表达式 |
| priority | Integer | 优先级 |
| description | String | 描述 |
