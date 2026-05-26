# 前后端 API 约定

前后端交互的稳定规则：命名空间、REST 风格、`Result<T>`、鉴权、接口对象边界。

## API 命名空间

| 命名空间 | 使用方 | 鉴权 |
|---|---|---|
| `/api/admin/**` | admin-web 管理后台 | AdminAuthInterceptor |
| `/api/client/**` | client-web C 端 | ClientAuthInterceptor |
| `/api/internal/**` | 外部业务系统 | 无拦截器 |
| `/api/captcha` | 共享验证码 | 无拦截器 |

鉴权排除路径：`/api/admin/auth/login`、`/api/client/auth/login`、`/api/client/auth/register`。

## REST 约定

- URL 使用资源名，优先复数名词：`/api/admin/tasks`、`/api/admin/instances`。
- 既有接口存在单数路径时，不为文档一致性单独改 URL；新增接口按复数资源命名。
- 嵌套资源表达归属关系：`/api/admin/tasks/{taskId}/steps`。
- HTTP 方法：`GET` 查询、`POST` 创建或动作、`PUT` 全量更新、`DELETE` 删除。
- 状态变更使用动作端点：`POST /api/admin/tasks/{id}/publish`。

## 响应格式

Controller 统一返回 `Result<T>`：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

- 成功：`code = 0`，`message = "ok"`。
- 业务错误：使用明确错误码和可给前端展示的 `message`。
- 分页：`IPage<T>` 直接放入 `data`，不做二次包装。
- 不在 Controller 返回裸对象、裸集合或未包装错误。

## 接口对象边界

| 类型 | 职责 | 所在范围 | 示例 |
|---|---|---|---|
| Entity | 数据库映射 | Mapper / Service 内部 | `Task`, `Prize` |
| Request | HTTP 请求体 | Controller 入参 | `LoginRequest`, `PrizeClaimRequest` |
| Response | 稳定 HTTP 响应契约 | Controller 返回 | `LoginResponse`, `CaptchaResponse` |
| VO | 页面视图聚合或展示模型 | Controller 返回 / 前端展示 | `TaskAdminVO`, `TaskClientVO` |
| DTO | 内部跨层传输、快照、批处理 | Service 内部或非 HTTP 专用 | `TaskSnapshotDTO` |

规则：

- Controller 禁止接收或返回 Entity。
- 新增请求体必须使用 `*Request`，不要用 VO/DTO 兼任。
- 新增稳定响应优先使用 `*Response`；如果对象明显服务于页面聚合展示，可使用 `*VO`。
- `DTO` 不作为新增 HTTP 契约的默认命名；已有 DTO 可逐步迁移，不做无关批量重命名。
- `Map<String, Object>` 只允许用于临时调试或极窄内部场景；对外 API 应收敛为明确 Response/VO。

## 鉴权模式

通过 `app.auth.mock-enabled` 切换两种模式。

### JWT 模式

生产默认使用 JWT：

- 登录返回 JWT token。
- 前端请求携带 `Authorization: Bearer <token>`。
- 拦截器验证 JWT，构建 `UserContext` 并写入 `UserContextHolder`。
- 请求结束后清理 ThreadLocal。

### Mock 模式

开发和联调用 Header 透传用户上下文：

| Header | 类型 | UserContext 字段 |
|---|---|---|
| `X-User-Id` | String | userId |
| `X-User-Province` | String | province |
| `X-User-Role` | String | role |
| `X-User-Tags` | 逗号分隔 | tags |
| `X-User-Org-Id` | String | orgId |
| `X-User-Level` | Integer | level |
| `X-Platform` | Platform 枚举名 | platform |

## 前端 API 层

- Axios 实例使用 `baseURL: '/api'` 和统一超时。
- 请求拦截器注入 JWT 或 Mock Header。
- 响应拦截器统一处理 401、5xx 和 `Result<T>` 业务错误。
- API 文件按域拆分，例如 `api/task.ts`、`api/step.ts`。
- 多处复用类型提取到 `src/types/`。
- 响应解包逻辑集中封装，组件不重复手写深层访问。

## API 版本化

API 变更同步更新：

- 后端 Controller、Request/Response/VO、校验规则。
- 前端 `src/api/` 方法和类型。
- `docs/spec/current/api.md`。
- 如果属于版本发布，更新对应 `docs/spec/v*/api.md` 和 release notes。
