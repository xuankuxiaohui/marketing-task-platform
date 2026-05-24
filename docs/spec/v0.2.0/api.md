# v0.2.0 API 文档

## 变更概述

v0.2.0 新增 Internal API 两个端点、Admin 子表 CRUD 四个 Controller、Client 任务详情返回结构变更。v0.2.x 补充 Auth 鉴权模块（JWT + 验证码 + 登录/注册）。

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

#### v0.2.1 快照读取

当实例 `taskVersion` 不为 null 时，`steps` 优先从 `task_definition_snapshot` 按版本号读取。无快照时 fallback 到实时 `task_step` 表（向后兼容旧实例）。

## Auth 鉴权模块

两种模式通过 `app.auth.mock-enabled` 配置切换。

### Mock 模式（开发/联调，`mock-enabled: true`）

通过 HTTP Header 透传用户上下文（与旧版 UserContextInterceptor 兼容）：

| Header | UserContext 字段 |
|---|---|
| `X-User-Id` | userId |
| `X-User-Province` | province |
| `X-User-Role` | role |
| `X-User-Tags` | tags (逗号分隔) |
| `X-User-Org-Id` | orgId |
| `X-User-Level` | level |
| `X-Platform` | platform |

### JWT 模式（生产，`mock-enabled: false`）

管理端和 C 端各自维护用户表（`admin_user` / `client_user`），通过 JWT 鉴权。

### 获取验证码

```http
GET /api/captcha
```

#### 响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "captchaKey": "uuid-string",
    "captchaImage": "data:image/png;base64,..."
  }
}
```

验证码有效期 60 秒，单次使用后清除。

### 管理端登录

```http
POST /api/admin/auth/login
Content-Type: application/json
```

#### 请求体

```json
{
  "username": "admin",
  "password": "admin123",
  "captchaKey": "uuid-string",
  "captchaCode": "abcd"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码 |
| `captchaKey` | String | 是 | 验证码 Key |
| `captchaCode` | String | 是 | 验证码 |

#### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": "1",
    "username": "admin",
    "nickname": "系统管理员",
    "expiresIn": 7200
  }
}
```

Admin JWT payload: `{ sub: userId, platform: "ADMIN", iat, exp }`

### C 端注册

```http
POST /api/client/auth/register
Content-Type: application/json
```

#### 请求体

```json
{
  "username": "newuser",
  "password": "password123",
  "captchaKey": "uuid-string",
  "captchaCode": "abcd",
  "nickname": "小明",
  "province": "BJ",
  "role": "vip",
  "tags": "vip,active",
  "orgId": "org_001",
  "level": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | String | 是 | 用户名 |
| `password` | String | 是 | 密码（≥6位） |
| `captchaKey` | String | 是 | 验证码 Key |
| `captchaCode` | String | 是 | 验证码 |
| `nickname` | String | 否 | 昵称 |
| `province` | String | 否 | 省份 |
| `role` | String | 否 | 角色 |
| `tags` | String | 否 | 逗号分隔标签 |
| `orgId` | String | 否 | 组织 ID |
| `level` | Integer | 否 | 等级 |

### C 端登录

```http
POST /api/client/auth/login
Content-Type: application/json
```

请求体与注册的必填字段相同（username, password, captchaKey, captchaCode）。响应中包含 `expiresIn`（秒）。

Client JWT payload: `{ sub: userId, province, role, tags, orgId, level, platform, iat, exp }`

### 错误响应

```json
{"code": 400, "message": "用户名或密码错误", "data": null}
{"code": 400, "message": "账号已停用", "data": null}
{"code": 400, "message": "用户名已存在", "data": null}
{"code": 400, "message": "验证码已过期，请刷新后重试", "data": null}
{"code": 401, "message": "Token无效或已过期", "data": null}
```

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
| `GET /api/captcha` | `CaptchaController.java` |
| `POST /api/admin/auth/login` | `AdminAuthController.java` |
| `POST /api/client/auth/*` | `ClientAuthController.java` |
| JWT 签发/验证 | `AdminJwtProvider.java`, `ClientJwtProvider.java` |
| 鉴权拦截 | `AdminAuthInterceptor.java`, `ClientAuthInterceptor.java` |
