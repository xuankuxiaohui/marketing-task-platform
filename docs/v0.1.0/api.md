# v0.1.0 API 文档

## 基础信息

| 项 | 值 |
|---|---|
| 默认服务地址 | `http://localhost:8080` |
| Swagger UI | `/swagger-ui.html` |
| OpenAPI JSON | `/v3/api-docs` |
| 响应包装 | `Result<T>` |

## 通用响应格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

错误示例：

```json
{
  "code": 400,
  "message": "过滤表达式包含禁用关键字",
  "data": null
}
```

## UserContext Headers

所有 `/api/**` 请求都会经过 `UserContextInterceptor`。v0.1.0 用 Header 注入用户上下文。

| Header | 必填 | 示例 | 说明 |
|---|---|---|---|
| `X-User-Id` | Client API 必填 | `u_1001` | 外部用户 ID |
| `X-User-Province` | 否 | `BJ` | 省份 |
| `X-User-Role` | 否 | `vip` | 用户角色 |
| `X-User-Tags` | 否 | `vip,active` | 逗号分隔标签 |
| `X-User-Org-Id` | 否 | `org_001` | 组织 ID |
| `X-User-Level` | 否 | `5` | 用户等级，整数 |
| `X-Platform` | 否 | `WEB` | 不传默认 WEB |

## Admin API

### 分页查询任务

```http
GET /api/admin/task?page=1&size=20
```

#### Query 参数

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `page` | long | `1` | 页码 |
| `size` | long | `20` | 每页数量 |

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "records": [
      {
        "id": 1,
        "code": "daily_checkin",
        "name": "日签到",
        "description": "每日签到领积分",
        "periodType": "DAILY",
        "status": "PUBLISHED",
        "version": 1
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1
  }
}
```

#### 实现

`backend/src/main/java/com/marketing/task/controller/admin/AdminTaskController.java`

### 创建或更新任务主体

```http
POST /api/admin/task
Content-Type: application/json
```

#### 请求体

当前 v0.1.0 保存的是 `Task` 主体，不包含步骤、过滤器和端配置的聚合保存。

```json
{
  "code": "daily_checkin",
  "name": "日签到",
  "description": "每日签到领积分",
  "periodType": "DAILY",
  "status": "DRAFT"
}
```

更新时传 `id`：

```json
{
  "id": 1,
  "code": "daily_checkin",
  "name": "日签到",
  "description": "每日签到领积分",
  "periodType": "DAILY",
  "status": "DRAFT",
  "version": 0
}
```

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 1,
    "code": "daily_checkin",
    "name": "日签到",
    "periodType": "DAILY",
    "status": "DRAFT"
  }
}
```

### 发布任务

```http
POST /api/admin/task/{id}/publish
```

#### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `id` | Long | 任务 ID |

#### 行为

- 将 `task.status` 设置为 `PUBLISHED`
- 将 `task.version` 设置为 `version + 1`，如果为空则设置为 `1`

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

### 下线任务

```http
POST /api/admin/task/{id}/offline
```

#### 行为

将 `task.status` 设置为 `OFFLINE`。

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

### 分页查询用户任务实例

```http
GET /api/admin/instance?page=1&size=20
```

#### Query 参数

| 参数 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `page` | long | `1` | 页码 |
| `size` | long | `20` | 每页数量 |

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "records": [
      {
        "id": 100,
        "userId": "u_1001",
        "taskId": 1,
        "taskVersion": 1,
        "cycleKey": "20260518",
        "status": "REWARDED",
        "currentStepSeq": 4,
        "rewardTime": "2026-05-18T15:30:00"
      }
    ],
    "total": 1,
    "size": 20,
    "current": 1
  }
}
```

#### 实现

`backend/src/main/java/com/marketing/task/controller/admin/AdminInstanceController.java`

### 校验过滤器表达式

```http
POST /api/admin/filter/validate
Content-Type: application/json
```

#### 请求体

```json
{
  "expression": "inProvince(['BJ']) && levelGte(3)"
}
```

#### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": null
}
```

#### 失败示例

```json
{
  "code": 400,
  "message": "过滤表达式包含禁用关键字",
  "data": null
}
```

#### 当前校验规则

| 规则 | 说明 |
|---|---|
| 非空 | 空表达式返回 400 |
| 长度 | 最大 1024 字符 |
| 禁用关键字 | `System`、`Runtime`、`Process`、`Thread`、`Class.forName`、`import`、`new`、`exec`、`eval` |

## Client API

### 查询当前用户可见任务列表

```http
GET /api/client/task/list
X-User-Id: u_1001
X-User-Province: BJ
X-User-Role: vip
X-User-Tags: vip,active
X-User-Level: 5
X-Platform: WEB
```

#### 行为

- 查询 `status = PUBLISHED` 的任务
- 按 `start_time` / `end_time` 过滤
- 使用 `FilterEvaluator` 执行任务过滤器
- 返回当前用户可见的 `Task` 列表

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "id": 1,
      "code": "daily_checkin",
      "name": "日签到",
      "description": "每日签到领积分",
      "periodType": "DAILY",
      "status": "PUBLISHED",
      "version": 1
    }
  ]
}
```

### 查询任务详情并创建实例

```http
GET /api/client/task/{taskId}
X-User-Id: u_1001
```

#### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `taskId` | Long | 任务 ID |

#### 行为

- 查询任务主体
- 根据任务周期计算 `cycle_key`
- 按 `(user_id, task_id, cycle_key)` 查询已有实例
- 不存在则创建 `user_task_instance`
- 调用 `StepAdvanceEngine.enter()` 自动推进 PASSIVE / REWARD 步骤

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 100,
    "userId": "u_1001",
    "taskId": 1,
    "taskVersion": 1,
    "cycleKey": "20260518",
    "status": "IN_PROGRESS",
    "currentStepSeq": 2
  }
}
```

### 显式开始任务

```http
POST /api/client/task/{taskId}/start
X-User-Id: u_1001
```

行为同 `GET /api/client/task/{taskId}`：获取或创建用户任务实例并触发自动步骤。

### 推进 CLICK 步骤

```http
POST /api/client/task/{taskId}/step/{stepId}/click
X-User-Id: u_1001
```

#### 路径参数

| 参数 | 类型 | 说明 |
|---|---|---|
| `taskId` | Long | 任务 ID |
| `stepId` | Long | CLICK 类型步骤 ID |

#### 行为

- 获取或创建用户任务实例
- 校验 `stepId` 存在且属于当前任务
- 校验步骤类型是 `CLICK`
- 标记当前步骤完成
- 继续级联 PASSIVE / REWARD
- 如果 REWARD 被执行，实例状态变为 `REWARDED`

#### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "id": 100,
    "userId": "u_1001",
    "taskId": 1,
    "taskVersion": 1,
    "cycleKey": "20260518",
    "status": "REWARDED",
    "currentStepSeq": 4,
    "rewardTime": "2026-05-18T15:35:00"
  }
}
```

#### 错误响应

步骤不存在：

```json
{
  "code": 400,
  "message": "步骤不存在",
  "data": null
}
```

步骤不是 CLICK：

```json
{
  "code": 400,
  "message": "当前步骤不是CLICK类型",
  "data": null
}
```

## Internal API

### 业务回调占位

```http
POST /api/internal/task/callback
```

#### 当前行为

v0.1.0 不实现 CALLBACK / PROGRESS。接口固定返回 501。

```json
{
  "code": 501,
  "message": "CALLBACK/PROGRESS将在Sprint 2实现",
  "data": null
}
```

## curl 示例

### 创建任务主体

```bash
curl -X POST http://localhost:8080/api/admin/task \
  -H 'Content-Type: application/json' \
  -d '{
    "code":"daily_checkin",
    "name":"日签到",
    "description":"每日签到领积分",
    "periodType":"DAILY",
    "status":"DRAFT"
  }'
```

### 发布任务

```bash
curl -X POST http://localhost:8080/api/admin/task/1/publish
```

### 查询 BJ 用户可见任务

```bash
curl http://localhost:8080/api/client/task/list \
  -H 'X-User-Id: u_1001' \
  -H 'X-User-Province: BJ' \
  -H 'X-User-Role: vip' \
  -H 'X-User-Tags: vip,active' \
  -H 'X-User-Level: 5' \
  -H 'X-Platform: WEB'
```

## 已知限制

| 限制 | 说明 |
|---|---|
| Admin 任务保存未覆盖子表 | 当前 `POST /api/admin/task` 只保存 `task` 主体 |
| 没有步骤 CRUD API | v0.1.0 生成了表和实体，但尚未暴露步骤配置接口 |
| 没有过滤器 CRUD API | 只有表达式校验接口 |
| 没有端配置 CRUD API | 表和适配器已预留 |
| Internal callback 未实现 | Sprint 2 实现 CALLBACK / PROGRESS |
