# v0.4.0 API 文档

最后更新：2026-05-27

## v0.5.1 变更概述

v0.5.1 新增用户管理 API（后台用户 + 客户端用户）、互斥组移除任务关联 API。

---

## 用户管理 API

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

## 互斥组增强 API

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

## v0.4.0 变更概述

v0.4.0 新增条件分支路由 API、升级 Sa-Token 鉴权、步骤平台配置纳入快照。

---

## 条件分支 API (P1)

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

## Sa-Token 鉴权 (P2)

### 鉴权方式变更

| 层面 | 变更前 | 变更后 |
|---|---|---|
| Admin 认证 | AdminJwtProvider + AdminAuthInterceptor | Sa-Token `StpUtil` (type=admin) + SaInterceptor |
| Client 认证 | ClientJwtProvider + ClientAuthInterceptor | Sa-Token `StpUserUtil` (type=client) + SaInterceptor |
| Token 格式 | 自定义 JWT (jjwt) | Sa-Token JWT 插件 (`sa-token-jwt`) |
| 用户上下文 | UserContext + UserContextHolder | SaTokenUserContextBridge → UserContext（对外接口不变） |

### 登录 Controller（签名不变）

```
POST /api/admin/auth/login
POST /api/client/auth/login
```

内部切换为 `StpUtil.login()` / `StpUserUtil.login()`，返回 `SaTokenInfo`。

### 新增能力

| 能力 | 实现方式 |
|---|---|
| Token 自动续期 | `sa-token.active-timeout: 1800` |
| 并发登录控制 | `sa-token.is-concurrent: true` |
| 登出/Token 失效 | `StpUtil.logout()` / `StpUserUtil.logout()` |
| SSO 扩展点 | `sa-token-sso` 依赖预留 |
| OAuth2/OIDC 扩展点 | `sa-token-oauth2` 依赖预留 |

### Mock 模式兼容

`mock-enabled: true` 时，从 `X-User-*` 请求头直接构造 UserContext，SaInterceptor 跳过鉴权。

---

## 快照增强 (P3)

TaskSnapshotDTO 新增 `stepPlatforms` 和 `transitions` 字段，发布时固化到快照。C 端 `detail()` 从快照读取，无需回表。

---

## HTTP 集成测试 (P3)

| 测试类 | 测试数 | 覆盖 |
|---|---|---|
| AdminTaskControllerTest | 4 | 聚合保存+发布、获取详情、列表查询 |
| ClientTaskControllerTest | 5 | 任务列表(含过滤)、详情、实例创建、CLICK 推进 |
| TaskLifecycleIntegrationTest | 9 | 全链路：签到/问卷/阅读进度、互斥、灰度、快照、奖品 |

---

## v0.3.x API（历史）

### v0.3.0 — Admin Metrics API

```
GET /api/admin/metrics/dashboard
GET /api/admin/metrics/task/{taskId}/summary
GET /api/admin/metrics/task/{taskId}/daily?from=2026-05-01&to=2026-05-24
```

### v0.3.0 — Admin Simulation API

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

### v0.3.1 — Admin Instance API

```
GET  /api/admin/instance?page=1&size=20&userId=xxx&taskId=123&status=IN_PROGRESS
GET  /api/admin/instance/{id}
```

### v0.3.1 — 过滤表达式新增灰度函数

`inGrayPercent(percent)`, `inABGroup(groupName)`, `inCrowd(crowdId)`

### v0.3.2 — 任务列表优化

```
GET /api/admin/task?page=1&size=20&status=DRAFT&keyword=签到&periodType=DAILY
```

### v0.3.3 — 奖品记录管理

```
GET  /api/admin/prize/records?page=1&size=20&userId=xxx&prizeId=1&status=FAILED
POST /api/admin/prize/records/{id}/reissue
```
