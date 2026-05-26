# v0.4.0 API 文档

最后更新：2026-05-26

## 变更概述

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

返回：`Result<Void>`

### 删除步骤的所有分支

```
DELETE /api/admin/step/{stepId}/transitions
```

返回：`Result<Void>`

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

transitions 使用 stepCode 引用而非 stepId，在聚合保存时解析为 ID。

### TaskAggregateDTO 新增字段

```java
private List<TaskStepTransitionVO> transitions;
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

### 登录 Controller（签名不变，内部切换）

```
POST /api/admin/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "******",
  "captchaKey": "uuid",
  "captchaCode": "1234"
}
```

内部调用 `StpUtil.login(adminUserId)`，返回 `SaTokenInfo` (tokenName, tokenValue, tokenTimeout)。

```
POST /api/client/auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "******"
}
```

内部调用 `StpUserUtil.login(clientUserId)`。

### 新增能力

| 能力 | 实现方式 |
|---|---|
| Token 自动续期 | `sa-token.active-timeout: 1800` (30min 无操作自动续) |
| 并发登录控制 | `sa-token.is-concurrent: true` |
| 登出/Token 失效 | `StpUtil.logout()` / `StpUserUtil.logout()` |
| SSO 扩展点 | `sa-token-sso` 依赖预留 |
| OAuth2/OIDC 扩展点 | `sa-token-oauth2` 依赖预留 |
| 注解鉴权 | `@SaCheckLogin(type="admin")` |

### 配置 (application.yml)

```yaml
sa-token:
  token-name: Authorization
  token-prefix: Bearer
  timeout: 7200
  active-timeout: 1800
  is-concurrent: true
  is-share: false
  is-log: true

app:
  auth:
    mock-enabled: ${MOCK_AUTH_ENABLED:false}
    admin-secret: ${ADMIN_JWT_SECRET:}
    client-secret: ${CLIENT_JWT_SECRET:}
```

### Mock 模式兼容

`mock-enabled: true` 时，UserContextInterceptor 从 `X-User-*` 请求头直接构造 UserContext，SaInterceptor 跳过鉴权检查。

### 请求头 (生产模式)

```
Authorization: Bearer <sa-token-jwt>
```

前端 axios 拦截器统一注入。

---

## 快照增强 (P3)

### TaskSnapshotDTO 结构

```java
public record TaskSnapshotDTO(
    Task task,
    List<TaskStep> steps,
    List<TaskFilter> filters,
    List<TaskPlatform> platforms,
    List<TaskStepPlatform> stepPlatforms,   // v0.4.0 新增
    List<TaskStepTransition> transitions     // v0.4.0 新增
)
```

C 端 `detail()` 读取快照时，步骤平台配置和分支配置均从快照中获得，不再查询实时表。

---

## HTTP 集成测试 (P3)

| 测试类 | 测试数 | 覆盖 |
|---|---|---|
| AdminTaskControllerTest | 4 | 聚合保存+发布、获取详情、列表查询 |
| ClientTaskControllerTest | 5 | 任务列表(含过滤)、详情、实例创建、CLICK 推进 |
| TaskLifecycleIntegrationTest | 9 | 全链路：签到/问卷/阅读进度、互斥、灰度、快照、奖品 |

全部使用 H2 内存库 + `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`。
