# 当前版本 API 文档

最后更新：2026-05-28

当前版本：v0.5.1

---

## v0.5.1 — 用户管理 API

### 后台用户管理

#### 分页查询后台用户

```
GET /api/admin/admin-users?page=1&size=20&keyword=admin
```

返回：`Result<IPage<AdminUser>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | long | 否 | 页码，默认 1 |
| size | long | 否 | 每页条数，默认 20 |
| keyword | String | 否 | 搜索关键字（匹配 username / nickname） |

#### 重置后台用户密码

```
PUT /api/admin/admin-users/{id}/reset-password
```

返回：`Result<String>` — 新密码明文（10 位随机字母数字）

#### 启用/停用后台用户

```
PUT /api/admin/admin-users/{id}/toggle-enabled
```

返回：`Result<Boolean>` — 新的启用状态

停用时自动踢下线（调用 `StpUtil.kickout()`）。

#### 踢后台用户下线

```
POST /api/admin/admin-users/{id}/kick
```

返回：`Result<Void>`

仅终止 session，不改变 enabled 状态。

---

### 客户端用户管理

#### 分页查询客户端用户

```
GET /api/admin/client-users?page=1&size=20&keyword=测试
```

返回：`Result<IPage<ClientUser>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | long | 否 | 页码，默认 1 |
| size | long | 否 | 每页条数，默认 20 |
| keyword | String | 否 | 搜索关键字（匹配 username / nickname / province / role） |

#### 重置客户端用户密码

```
PUT /api/admin/client-users/{id}/reset-password
```

返回：`Result<String>` — 新密码明文

#### 启用/停用客户端用户

```
PUT /api/admin/client-users/{id}/toggle-enabled
```

返回：`Result<Boolean>` — 新的启用状态

停用时自动踢下线（调用 `clientStpLogic.kickout()`）。

#### 踢客户端用户下线

```
POST /api/admin/client-users/{id}/kick
```

返回：`Result<Void>`

---

## v0.5.1 — 互斥组增强 API

### 移除任务关联

```
DELETE /api/admin/mutex-groups/{groupId}/tasks/{taskId}
```

返回：`Result<Void>`

将任务的 `mutex_group_id` 设为 NULL，清除任务定义缓存。

错误码：
- `NOT_FOUND` (1003) — 互斥组不存在
- `TASK_NOT_FOUND` (4000) — 任务不存在
- `BAD_REQUEST` (1000) — 任务不属于此互斥组

---

## v0.5.0 — 批量发布

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

## v0.5.0 — 批量下线

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

## v0.5.0 — 定时发布

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
- `publishAt` 不能为空，必须在未来
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

---

## v0.5.0 — 复制任务（增强）

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

复制范围：任务主体、步骤、过滤器、平台配置、步骤平台配置、分支配置。新任务状态为 DRAFT，version=0。

---

## v0.5.0 — 任务软删除

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

## v0.5.0 — DTO 结构

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

---

## v0.4.0 — 条件分支 API

### 获取步骤的分支列表

```
GET /api/admin/step/{stepId}/transitions
```

返回：`Result<List<TaskStepTransitionVO>>`

### 批量保存分支配置

```
POST /api/admin/step/{stepId}/transitions
Content-Type: application/json

[
  {
    "targetStepCode": "step_vip_fast",
    "conditionExpr": "isVip()",
    "priority": 1,
    "description": "VIP 快速通道"
  },
  {
    "targetStepCode": "step_normal",
    "conditionExpr": null,
    "priority": 99,
    "description": "默认分支"
  }
]
```

校验规则：
- conditionExpr 为 null 表示默认分支（自动最低优先级）
- targetStepCode 不可指向自身
- 目标步骤 seq > 来源步骤 seq（禁止回退）
- priority 在同一 step_id 下唯一
- 表达式通过 QLExpress 语法校验

### 删除步骤的所有分支

```
DELETE /api/admin/step/{stepId}/transitions
```

### 聚合保存含分支

```
POST /api/admin/task
Content-Type: application/json

{
  "task": { ... },
  "steps": [ ... ],
  "filters": [ ... ],
  "platforms": [ ... ],
  "transitions": [
    {
      "stepCode": "step_choice",
      "transitions": [
        { "targetStepCode": "step_vip", "conditionExpr": "isVip()", "priority": 1 },
        { "targetStepCode": "step_normal", "conditionExpr": null, "priority": 99 }
      ]
    }
  ]
}
```

### TaskStepTransitionVO 结构

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键 |
| stepId | Long | 来源步骤 ID |
| stepCode | String | 来源步骤 code（聚合保存时使用） |
| targetStepId | Long | 目标步骤 ID |
| targetStepCode | String | 目标步骤 code（聚合保存时使用） |
| conditionExpr | String | QLExpress 条件表达式，null = 默认分支 |
| priority | Integer | 优先级（越小越高），同 step 唯一 |
| description | String | 分支描述 |

---

## v0.4.0 — Sa-Token 鉴权

### 鉴权方式

| 层面 | 实现 |
|---|---|
| Admin 认证 | Sa-Token `StpUtil` (type=admin) + SaInterceptor |
| Client 认证 | Sa-Token `StpUserUtil` (type=client) + SaInterceptor |
| Token 格式 | Sa-Token JWT 插件 (`sa-token-jwt`) |
| 用户上下文 | SaTokenUserContextBridge → UserContext（对外接口不变） |

### 登录端点

```
POST /api/admin/auth/login
POST /api/client/auth/login
```

内部调用 `StpUtil.login()` / `StpUserUtil.login()`，返回 `SaTokenInfo`。

### Mock 模式

`mock-enabled: true` 时，从 `X-User-*` 请求头直接构造 UserContext，SaInterceptor 跳过鉴权。

---

## v0.3.x — 历史 API

### Admin Metrics API

```
GET /api/admin/metrics/dashboard
GET /api/admin/metrics/task/{taskId}/summary
GET /api/admin/metrics/task/{taskId}/daily?from=2026-05-01&to=2026-05-24
```

### Admin Simulation API

```
POST   /api/admin/simulate/impersonate
DELETE /api/admin/simulate/impersonate
POST   /api/admin/simulate/callback
POST   /api/admin/simulate/progress
POST   /api/admin/simulate/full-flow/{taskId}
GET    /api/admin/simulate/status
GET    /api/admin/simulate/test-users
POST   /api/admin/simulate/flow
GET    /api/admin/simulate/instance/{instanceId}/detail
POST   /api/admin/simulate/click
GET    /api/admin/simulate/instance/{instanceId}/events
```

### Admin Instance API

```
GET  /api/admin/instance?page=1&size=20&userId=xxx&taskId=123&status=IN_PROGRESS
GET  /api/admin/instance/{id}
```

### 过滤表达式灰度函数

`inGrayPercent(percent)`, `inABGroup(groupName)`, `inCrowd(crowdId)`

### 任务列表优化

```
GET /api/admin/task?page=1&size=20&status=DRAFT&keyword=签到&periodType=DAILY
```

### 奖品记录管理

```
GET  /api/admin/prize/records?page=1&size=20&userId=xxx&prizeId=1&status=FAILED
POST /api/admin/prize/records/{id}/reissue
```
