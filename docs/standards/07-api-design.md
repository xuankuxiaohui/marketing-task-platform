# 07 · 接口设计规范

> 适用范围：`/admin/**`、`/api/common/**`、`/internal/**` 及未来 `/api/<module>/**`。  
> 契约权威：requirements 附录 C + design §4。本篇约束*如何写新接口*，不重新定义已有端点。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| RFC 9110 HTTP Semantics | https://www.rfc-editor.org/rfc/rfc9110 | 方法安全/幂等语义、状态码 |
| RFC 3339 | https://www.rfc-editor.org/rfc/rfc3339 | 时间戳（ISO-8601 子集，带偏移） |
| RFC 2119 | https://www.rfc-editor.org/rfc/rfc2119 | MUST/SHOULD |
| Zalando RESTful API Guidelines | https://opensource.zalando.com/restful-api-guidelines/ | API First、OpenAPI、HTTP 方法、kebab-case 路径、无动词路径、429、禁止堆栈、官方状态码 |
| Microsoft REST API Guidelines | https://github.com/microsoft/api-guidelines | 集合与操作命名参考 |
| OpenAPI Specification | https://spec.openapis.org/oas/latest.html | 三分组导出 |
| springdoc-openapi | https://springdoc.org/ | Boot 4 分组、生产关 UI |
| RFC 9457 Problem Details | https://www.rfc-editor.org/rfc/rfc9457 | **仅作对照**；本项目不用 Problem JSON |

### 本项目覆盖（禁止按 Zalando / Microsoft 改现网契约）

| 外部条款 | 本项目 | 依据 |
|----------|--------|------|
| Zalando #135 不要用 `/api` 作基路径 | 门户公共 API 使用 `/api/common/<模块>` | 需求「应用与 API 命名空间规划」 |
| Zalando #118 JSON 属性 snake_case | JSON **camelCase** | design §4 示例：`userId`、`csrfToken`、`pageSize` |
| Zalando #130 查询参数 snake_case | 查询参数 **camelCase**（`pageSize`） | 附录 C |
| Zalando #176 Problem JSON | 统一 `Result` 外壳 | 附录 C |
| Zalando #160 优先游标分页 | `page` + `pageSize` 偏移分页 | 附录 C |
| Zalando #114/#115 媒体类型版本、禁止 URL 版本 | **不版本化路径**；破坏性变更走新资源或评审 | 双应用静态路由，P0 无公开多版本 |
| Zalando #134 资源名必须复数 | 遵循 design §4 已写路径（`/instances/{id}` 等），新资源用复数名词 | 与已冻结契约共存 |
| Microsoft 常用 `value` 作集合 | 集合字段名 `records` | 附录 C |

已存在于 design §4 的路径、字段、错误码是**冻结契约**。本篇管增量。

## 2. API First

采纳 Zalando #100 / #101：

1. **MUST** 先改 design §4（或任务中的契约表），再写 Controller。禁止代码先于契约。
2. **MUST** 用 springdoc 导出 OpenAPI 3，分组 `admin` / `portal` / `internal`（design §4.1）。
3. **MUST** 生产关闭 Swagger UI（`springdoc.swagger-ui.enabled=false`）。CI 导出 JSON 不受影响。
4. **MUST** 前端类型由导出 JSON 生成（[13-frontend-engineering.md](13-frontend-engineering.md)）。
5. 注解 **MUST** 写清 summary、错误码、权限码。用 `@Tag` 按域分组。

## 3. 命名空间

| 前缀 | 应用 | 调用方 | 鉴权 |
|------|------|--------|------|
| `/admin/**` | admin-app | 管理后台 | Cookie 会话 + CSRF（写）+ `@SaCheckPermission` |
| `/api/common/<模块>/**` | portal-app | 门户 H5 | Bearer `client:`；匿名清单见 design §4.9.0 |
| `/internal/**` | portal-app | 外部系统内网 | HMAC 四头；Nginx 不向公网暴露 |
| `/actuator/**` | 两应用 | 运维 | 生产仅 health/readiness/prometheus |

第二段模块枚举（门户）：`auth` `task` `prize` `points` `dict` `track` `captcha`；P1：`ad` `signin`。

1. **MUST NOT** 在 admin-app 注册 `/api` 或 `/internal`。
2. **MUST NOT** 在 portal-app 注册 `/admin`。
3. 越界请求 **MUST** 404，不进鉴权（RL-08）。
4. **MUST NOT** 把 internal 接口挂到 `/api` 下「方便联调」。

## 4. URL

采纳 Zalando #129 / #136 / #141 / #143，并与已有路径对齐：

1. **MUST** 路径段 kebab-case：`/schedule-failures`、`/stock-replenish`。
2. **MUST** 无尾斜杠、无空段。
3. **MUST** 资源用名词。动作能映射为资源状态时不要用动词。
4. **MAY** 当 HTTP 方法不足以表达领域动作时，使用**已经在 design §4 出现**的动词尾段（`/start`、`/click`、`/publish`）。新增此类尾段 **MUST** 先写进规格。
5. **MUST** 用路径段标识资源：`/api/common/task/instances/{id}/steps/{stepCode}/click`。
6. **MUST NOT** 在查询串放密钥或令牌。
7. 标识符 **MUST** 为数字主键或规格中的 code；**MUST** URL-safe。

```text
正例  GET  /admin/task/definitions
正例  POST /api/common/task/{taskId}/start
正例  POST /internal/task/callback
反例  GET  /api/common/getTaskList
反例  POST /admin/task/definition/deleteTask
反例  GET  /admin/task/definitions/   （尾斜杠）
```

## 5. HTTP 方法（RFC 9110 + Zalando #148）

| 方法 | 用于 | 安全 | 幂等 |
|------|------|------|------|
| GET | 读 | 是 | 是 |
| POST | 创建、领域动作（领取、推进、登录） | 否 | 业务层用唯一约束保证「再调用安全」 |
| PUT | 本项目少用；完整替换才用 | 否 | 是 |
| PATCH | 本项目默认不用；规格未列不要加 | 否 | 视实现 |
| DELETE | 逻辑删除或移除名单 | 否 | 是 |

1. **MUST NOT** 用 GET 写数据（含「GET 领取」）。
2. **MUST NOT** 用 PUT 表达领取/发奖。
3. 列表过滤用 GET + query。复杂查询体 **SHOULD** 仍用 GET query；确需 JSON 查询时用 POST 并在 OpenAPI 标明非创建。
4. 登录、领奖等 POST **MUST** 按规格做幂等或限流，不能假设客户端只发一次。

## 6. 统一响应体（附录 C，不可偏离）

成功：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "1a2b3c4d"
}
```

失败：

```json
{
  "code": "task.claim.mutex-blocked",
  "message": "当前有进行中的互斥任务",
  "traceId": "1a2b3c4d"
}
```

1. 成功 `code` **MUST** 为数字 `0`（不是字符串 `"0"`）。
2. 失败 `code` **MUST** 为字符串 `<域>.<场景>.<原因>`，公共码两段 `common.param-invalid`。
3. `data` 失败时 **MUST** 省略或 null，不要塞部分成功对象，除非规格写「部分接受」（埋点 batch）。
4. `traceId` **MUST** 始终出现，与响应头、MDC 同一值（design §6.6）。
5. **MUST NOT** 失败时返回堆栈、SQL、内部类名（Zalando #177；附录 C 500 统一文案）。

### 6.1 `code` 的 OpenAPI / TypeScript（异构，冻结）

附录 C 成功为 number、失败为 string。**MUST NOT** 为了类型好看改成全 string 或全 number。

OpenAPI 建模：

```yaml
code:
  oneOf:
    - type: integer
      enum: [0]
    - type: string
```

`packages/shared` **MUST** 提供类型守卫（实现落在前端骨架任务），页面禁止在未收窄的分支里混用 `code === 0` 与字符串码：

```ts
export function isOk(r: Result): r is Result & { code: 0; data: unknown };
export function isFail(r: Result): r is Result & { code: string; message: string };
```

生成物与门禁见 [13-frontend-engineering.md](13-frontend-engineering.md) §4。门户包 **MUST NOT** import `admin.json` 生成类型；管理端默认 **MUST NOT** 把 `internal.json` 打进生产包。

## 7. HTTP 状态码映射（附录 C）

| HTTP | 场景 | 典型 code |
|------|------|-----------|
| 200 | 成功，含业务「已结束」类 200+状态 | — |
| 400 | 参数非法、业务拒绝 | `common.param-invalid`、多数 `task.*` / `reward.*` |
| 401 | 未认证 / 会话失效 | 后台 `auth.session.invalid`；门户四码见下 |
| 403 | 无权限；C 端部分冻结展示 | `common.permission-denied` |
| 404 | 资源不存在；命名空间越界也是 404 | `common.not-found` |
| 423 | 账号锁定 | `auth.login.locked` |
| 429 | 限流 | `common.rate-limited` 或域内 `*.rate-limited`；**MUST** 带 `Retry-After`（Zalando #153） |
| 500 | 未分类服务端错误 | `common.server-error`，对外文案统一 |

说明：

- **MUST NOT** 用 201/204 替换 200（附录 C 成功只有 200）。
- **MUST NOT** 用 422 表达校验失败（本项目 400）。
- 门户 401 四码（R4.3 / D-02）：`auth.session.missing` / `expired` / `kicked-concurrent` / `kicked-admin`。后台 401 只有 `auth.session.invalid`。
- internal 业务拒绝仍用 400 + 业务码；调用方按业务码分支（design §4.8 与 C 端 403 的差异是有意的）。

## 8. 分页与过滤

附录 C：

- 请求：`page`（从 1）+ `pageSize`（默认 20，>100 截断为 100）。
- 响应：`data: { total, records }`。

1. **MUST NOT** 使用 `pageNum` / `size` / `limit` / `offset` / `rows` 作为对外名。
2. **MUST NOT** 返回未分页的「可能很大」的管理列表。
3. 过滤参数用资源字段的 camelCase：`status`、`userId`、`taskId`。
4. 排序 **MUST** 白名单列，默认按规格（任务列表权重）。禁止把用户输入直接拼 `ORDER BY`。

## 9. 时间

附录 C + RFC 3339：

1. JSON 时间 **MUST** 为 ISO-8601 带偏移，例如 `2026-08-16T12:00:00+08:00`。
2. **MUST NOT** 输出 epoch millis 或无时区本地串 `2026-08-16 12:00:00`。
3. 服务内部与 DB 存 UTC。
4. 业务自然日、cycleKey、每日上限窗口 = UTC+8 `[00:00, 24:00)`。
5. Jackson 配置只在 kernel 做一次。

## 10. 鉴权与安全头

| 面 | 令牌 | 其它 |
|----|------|------|
| admin | `Set-Cookie` HttpOnly + Secure + SameSite=Strict，前缀 `admin:` | 写操作头 `X-CSRF-Token`；CSRF Cookie 非 HttpOnly，与 `data.csrfToken` 同值 |
| portal | `Authorization: Bearer client:<token>` | 无 CSRF |
| internal | `X-App-Id` `X-Timestamp` `X-Nonce` `X-Sign` | 签名串见 R15.2；`X-Trace-Id` 透传但不进签名 |
| 门户设备 | `X-Client-Platform` `X-Device-Id` | 设备标识不作鉴权依据（NFR 安全 7） |

1. **MUST NOT** 在 JSON 里回传后台会话令牌（R1.13）。
2. 交叉令牌 **MUST** 401（R4 属性 1）。
3. 匿名端点封闭清单以 design §4.9 为准，新增必须改规格。
4. CORS 由 Nginx 管，应用默认不放开（design §2.3.3）。

## 11. 幂等与并发

1. 创建类 POST：用规格给出的唯一键；冲突 **MUST** 返回既有资源或规格指定错误码，禁止再插一行。
2. 推进 / 发奖：乐观锁失败按规格重试或返回 `state-mismatch`。
3. internal：nonce 窗口内重复 **MUST** 拒绝且不改业务状态（R15.1）。
4. **MUST NOT** 用客户端生成的随机 id 代替服务端幂等键，除非规格允许。

## 12. OpenAPI 与兼容

1. **MUST** 每个公开操作有 `operationId`（稳定、camelCase），供代码生成。
2. **MUST** 文档化全部非 200 的业务码（或声明走缺省三件套）。
3. 兼容：只增可选字段 / 只增错误原因值（同一分段）视为兼容。删字段、改类型、改 code 字面量 **MUST** 走规格评审。
4. **MUST NOT** 用 `/v2` 前缀逃避兼容问题。

## 13. 文档注释模板（Controller）

```java
@Operation(summary = "领取任务", description = "R13.6–R13.8 / design §5.5")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "成功或幂等命中"),
    @ApiResponse(responseCode = "400", description = "task.claim.mutex-blocked / task.claim.daily-limit / …")
})
```

## 14. AI 检查清单

- [ ] 路径落在正确命名空间与模块第二段
- [ ] 方法语义符合 RFC 9110
- [ ] 成功/失败外壳符合附录 C；`code` 成功为 number `0`、失败为 string
- [ ] 错误码未新造分段（见 09）
- [ ] 分页与时间字段合规
- [ ] 鉴权载体正确；后台写含 CSRF 与权限
- [ ] OpenAPI 分组与 operationId 已加
- [ ] 未把 Entity 或 Map 当作 data
- [ ] C 端 message 可展示且不泄露风控内部原因
