# 系统模块 API

最后更新：2026-05-29

## 鉴权

### Admin 登录

```
POST /api/admin/auth/login
```

返回：`Result<LoginVO>`

### Client 注册

```
POST /api/client/auth/register
```

返回：`Result<LoginVO>`

### Client 登录

```
POST /api/client/auth/login
```

返回：`Result<LoginVO>`

**Mock 模式**：配置 `mock-enabled: true` 时，从 `X-User-*` 请求头构造 UserContext，跳过实际登录校验。

## Admin 用户管理

### Admin 用户列表

```
GET /api/admin/admin-users
```

返回：`Result<IPage<AdminUserVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| keyword | String | 否 | 搜索关键字 |

### 重置密码

```
PUT /api/admin/admin-users/{id}/reset-password
```

返回：`Result<Void>`

### 启用/停用

```
PUT /api/admin/admin-users/{id}/toggle-enabled
```

返回：`Result<Void>`

### 踢下线

```
POST /api/admin/admin-users/{id}/kick
```

返回：`Result<Void>`

## Client 用户管理

### Client 用户列表

```
GET /api/admin/client-users
```

返回：`Result<IPage<ClientUserVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| keyword | String | 否 | 搜索关键字 |

### 重置密码

```
PUT /api/admin/client-users/{id}/reset-password
```

返回：`Result<Void>`

### 启用/停用

```
PUT /api/admin/client-users/{id}/toggle-enabled
```

返回：`Result<Void>`

### 踢下线

```
POST /api/admin/client-users/{id}/kick
```

返回：`Result<Void>`

## 指标

### 仪表盘概览

```
GET /api/admin/metrics/dashboard
```

返回：`Result<DashboardVO>`

### 任务指标汇总

```
GET /api/admin/metrics/task/{taskId}/summary
```

返回：`Result<TaskSummaryVO>`

### 任务按日趋势

```
GET /api/admin/metrics/task/{taskId}/daily
```

返回：`Result<List<DailyMetricVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| from | String | 否 | 起始日期 (yyyy-MM-dd) |
| to | String | 否 | 截止日期 (yyyy-MM-dd) |

### 活动概览

```
GET /api/admin/metrics/activities
```

返回：`Result<List<ActivityMetricVO>>`

### 活动指标汇总

```
GET /api/admin/metrics/activity/{activityCode}/summary
```

返回：`Result<ActivitySummaryVO>`

### 活动按日趋势

```
GET /api/admin/metrics/activity/{activityCode}/daily
```

返回：`Result<List<DailyMetricVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| from | String | 否 | 起始日期 (yyyy-MM-dd) |
| to | String | 否 | 截止日期 (yyyy-MM-dd) |

## 模拟测试

### 模拟用户身份

```
POST /api/admin/simulate/impersonate
```

返回：`Result<Void>`

### 取消模拟

```
DELETE /api/admin/simulate/impersonate
```

返回：`Result<Void>`

### 模拟回调

```
POST /api/admin/simulate/callback
```

返回：`Result<Void>`

### 模拟进度

```
POST /api/admin/simulate/progress
```

返回：`Result<Void>`

### 一键全流程

```
POST /api/admin/simulate/full-flow/{taskId}
```

返回：`Result<SimulateFlowResultVO>`

### 模拟状态

```
GET /api/admin/simulate/status
```

返回：`Result<SimulateStatusVO>`

### 测试用户列表

```
GET /api/admin/simulate/test-users
```

返回：`Result<List<TestUserVO>>`

### 模拟流程

```
POST /api/admin/simulate/flow
```

返回：`Result<SimulateFlowResultVO>`

### 实例详情

```
GET /api/admin/simulate/instance/{instanceId}/detail
```

返回：`Result<InstanceVO>`

### 模拟点击

```
POST /api/admin/simulate/click
```

返回：`Result<StepClickResultVO>`

### 实例事件

```
GET /api/admin/simulate/instance/{instanceId}/events
```

返回：`Result<List<InstanceEventVO>>`

## 审计日志

### 审计日志分页查询

```
GET /api/admin/operation-logs
```

返回：`Result<IPage<OperationLogVO>>`

## 验证码

### 获取验证码

```
GET /api/captcha
```

返回：`Result<CaptchaVO>`

## Sa-Token 配置

| 端 | 工具类 | 类型 | 拦截路径 |
|---|---|---|---|
| Admin | StpUtil | type=admin | /api/admin/** |
| Client | StpUserUtil | type=client | /api/client/** |

- Token 格式：JWT（sa-token-jwt）
- Mock 模式：`mock-enabled=true` 时，`X-User-*` 请求头可跳过鉴权
