# 前后端 API 约定

前后端交互的通用规则：命名空间、RESTful 风格、响应格式、错误码、UserContext 透传。

## API 命名空间

| 命名空间 | 使用方 | 鉴权 |
|---|---|---|
| `/api/admin/**` | admin-web 管理后台 | AdminAuthInterceptor（JWT / mock header） |
| `/api/client/**` | client-web C 端 | ClientAuthInterceptor（JWT / mock header） |
| `/api/internal/**` | 外部业务系统 | 无拦截器 |
| `/api/captcha` | 共享 | 无拦截器 |

鉴权排除路径：`/api/admin/auth/login`、`/api/client/auth/login`、`/api/client/auth/register`。

## RESTful 约定

- URL 使用复数名词：`/api/admin/tasks`、`/api/admin/instances`
- 嵌套资源：`/api/admin/task/{taskId}/steps`、`/api/admin/task/{taskId}/steps/{stepId}/platforms`
- HTTP 方法：`GET` 查询、`POST` 创建、`PUT` 全量更新、`DELETE` 删除
- 状态变更用 `POST`：`/api/admin/task/{id}/publish`、`/api/admin/task/{id}/offline`

## 响应格式

`Result<T>` 统一包装：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

- 成功：`code = 0`, `message = "ok"`
- 业务错误：`code = 400/404/500`，`message` 为具体错误描述
- 分页：`IPage<T>` 直接放入 `data`，不做二次包装

错误码当前为硬编码整数，后续计划迁移为 `ErrorCode` 枚举（4xx 客户端错误 / 5xx 服务端错误）。

## 鉴权模式

通过 `app.auth.mock-enabled` 配置切换两种鉴权模式：

### JWT 模式（`mock-enabled: false`，默认生产模式）

管理端和 C 端各自维护用户表（`admin_user` / `client_user`），通过 JWT 鉴权。

**流程**：登录 → 返回 JWT token → 前端存储 → 后续请求带 `Authorization: Bearer <token>` → `AuthInterceptor` 验证 JWT → 还原 UserContext → 业务代码通过 `UserContextHolder.get()` 获取

**JWT Payload**：
- Admin JWT：`{ sub: userId, platform: "ADMIN", iat, exp }`
- Client JWT：`{ sub: userId, province, role, tags, orgId, level, platform, iat, exp }`

配置项：`app.auth.admin.secret` / `app.auth.client.secret`（JWT 签名密钥）、`expiry-minutes`（过期时间，默认 120 分钟）。

### Mock 模式（`mock-enabled: true`，开发/联调）

通过 HTTP Header 透传用户上下文，兼容旧版 UserContextInterceptor。

**Header 字段**：

| Header | 类型 | UserContext 字段 |
|---|---|---|
| `X-User-Id` | String | userId |
| `X-User-Province` | String | province |
| `X-User-Role` | String | role |
| `X-User-Tags` | 逗号分隔 → Set\<String\> | tags |
| `X-User-Org-Id` | String | orgId |
| `X-User-Level` | Integer (可空) | level |
| `X-Platform` | String → Platform 枚举 (默认 WEB) | platform |

### 鉴权 API

| 端点 | 说明 |
|---|---|
| `POST /api/admin/auth/login` | 管理端登录（AdminLoginRequest → LoginResponse） |
| `POST /api/client/auth/register` | C 端注册（RegisterRequest → LoginResponse） |
| `POST /api/client/auth/login` | C 端登录（LoginRequest → LoginResponse） |
| `GET /api/captcha` | 获取验证码（CaptchaResponse） |

所有登录/注册接口需携带验证码（captchaKey + captchaCode）。

## 前端 API 层

**http.ts（Axios 实例）**：
- `baseURL: '/api'`, `timeout: 10000`
- 请求拦截器：从 Pinia `useUserStore()` 读取用户上下文，注入 `X-User-*` 和 `X-Platform` Header
- 响应拦截器：统一处理 401 跳转登录、500 全局提示

API 文件按域拆分（`api/task.ts`、`api/step.ts` 等），TypeScript 接口与 API 函数同文件定义。

响应需解包两层：`response.data`（Axios）→ `data.data`（Result 包装）。建议封装 `unwrapResponse<T>(response): T` 工具函数。

## 错误处理拦截器

```typescript
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      router.push('/login')
    } else if (error.response?.status >= 500) {
      ElMessage.error('服务器异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)
```

每个 API 调用必须用 try/catch 包裹。建议封装 `useRequest()` composable 统一管理 loading/error/data 状态。

## API 版本化

API 变更按 `docs/spec/` 下的版本目录记录。当前活跃版本 `v0.2.0`（`docs/spec/current/`），冻结版本 `v0.1.0`。
