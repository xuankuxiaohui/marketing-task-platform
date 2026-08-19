# 设计文档 · API 契约（§4）

> 本文是 [design.md](design.md) **v2.13** 分册。§ 编号与总册索引一致，引用仍写 design §x.y。
> 需求：[requirements.md](requirements.md) v3.9　选型：[component-selection.md](component-selection.md)
> 总册索引（§ → 锚点）：[design.md](design.md) §0.2。本章跳转：搜索 `<!-- §x.y -->`，不要记行号。

---

<!-- §4 -->
## 4. API 契约

> 本章：§4.1 全局细则 + admin 六域 + internal + common 端。P0 契约范围如下各节。

<!-- §4.1 -->
### 4.1 全局契约细则（附录 C 的落地约定）

> **紧凑契约缺省规则**（§4.2–§4.8 单行端点适用，消除逐端点展开歧义）：① 列表端点出参统一 `records: [...]`——未逐点展开 records 字段处，字段清单 = 对应域 §3 表的业务列投影（按 DDL 列序，敏感列 password/secret/密文一律除外）；② 写端点出参统一 `{id}`（创建）/ `{ok: true}`（更新、删除、状态流转）；③ 未逐点列出错误码的端点按缺省三件套 `common.param-invalid`(400) / `common.not-found`(404) / `common.server-error`(500)。

| 项 | 约定 |
|----|------|
| 响应体 | 成功 `{"code":0,"message":"ok","data":<业务数据>,"traceId":"..."}`；失败 `{"code":"<域>.<场景>.<原因>","message":"<可展示文案>","traceId":"..."}`，HTTP 状态码按附录 C 映射（400 参数/业务拒绝、401 未认证、403 无权限、404 不存在、423 锁定、429 限流 + Retry-After、500 服务端异常） |
| 认证载体 | admin：登录成功经 `Set-Cookie`（HttpOnly+Secure+SameSite=Strict，R1.13）；CSRF 双重提交 = 非 HttpOnly CSRF Cookie（SameSite=Strict）与 data.`csrfToken` **同一值**（Cookie 为权威载体），写接口头 `X-CSRF-Token`；portal：`Authorization: Bearer client:<token>`（R1.13） |
| **401 原因码（决策点 D-02）** | 后台 401 统一 `auth.session.invalid`（含空闲过期、被踢、未携带，后台不区分文案）。门户封闭四码（R4.3 唯一权威）：`auth.session.missing`（未携带或无法解析）/ `auth.session.expired`（空闲过期）/ `auth.session.kicked-concurrent`（并发上限被踢，文案"账号已在其他设备登录"R32.5）/ `auth.session.kicked-admin`（强制下线，文案"账号已被下线，请联系客服"）。原因码即失败响应体的 `code` 字段。踢下线写 `session:kick-reason:{loginType}:{token}`（TTL 60s），过滤器读一次即删 |
| 分页 | 请求 `page`（≥1）+ `pageSize`（默认 20，>100 截断为 100）；响应 `data = {"total": <long>, "records": [...]}` |
| 时间字段 | 出入参一律 ISO-8601 带时区偏移（如 `2026-08-16T12:00:00+08:00`）；DB UTC 由服务端转换 |
| 校验失败 | Bean Validation 失败返回 400 + `common.param-invalid`，`message` 含字段级信息 |
| 管理端通用错误 | `common.permission-denied`(403)、`auth.session.invalid`(401)、`common.rate-limited`(429)、`common.not-found`(404)、`common.server-error`(500) 适用于本章全部端点，各端点错误码清单不再重复列出 |
| OpenAPI | springdoc 分组：`admin` / `portal` / `internal` 三组，随 CI 导出（NFR 可维护性 2） |

<!-- §4.2 -->
### 4.2 admin · 认证与会话（identity，R1/R2/R6）

**GET /admin/captcha**（公开）
- 出参 `data`: `{captchaId: string, imageBase64: string}`（easy-captcha PNG；Redis TTL `auth.captcha.ttl-seconds`，一次有效）

**POST /admin/auth/login**（公开，IP+账号双维限流）
- 入参：`{username, password, captchaId, captchaCode, deviceId?: string}`
- 出参 `data`：`{userId, nickname, roles: [string], permissions: [string], mustChangePassword: boolean, csrfToken: string}`；会话令牌经 Set-Cookie 下发；`csrfToken` 与非 HttpOnly CSRF Cookie 同值（R1.13）
- 错误：`auth.captcha.invalid` / `auth.captcha.expired`（400，单独提示并自动刷新 R32.2）、`auth.login.invalid-credential`（401，统一文案）、`auth.login.locked`（423，message 含剩余分钟数 R1.3）、`auth.login.rate-limited`（429）

**POST /admin/auth/logout**（登录态）
- 出参 `data`: `{}`；会话立即失效

**GET /admin/auth/menus**（登录态）
- 出参 `data`: 菜单树节点数组 `{id, name, route, component, icon, sort, children: [...]}`，仅含有权菜单及其子树（R2.4）

**GET /admin/auth/profile**（登录态）
- 出参 `data`: `{userId, username, nickname, roles: [...], permissions: [...]}`（R3.2 个人中心类接口，空角色用户可用）

**PUT /admin/auth/password**（登录态）
- 入参：`{oldPassword, newPassword}`（新密码满足 R1.5 复杂度）
- 出参 `data`: `{}`；当前会话保留、其他会话失效（R1.10）
- 错误：`auth.password.old-mismatch`(400)、`auth.password.policy-violated`(400)

**GET /admin/identity/users**（`identity:admin-user:query`）
- 查询参数：`username? nickname? status? roleId? page pageSize`
- 出参 `records`: `[{id, username, nickname, status, roles: [code], lastLoginAt, createdAt}]`（默认过滤 deleted，R3.4）

**POST /admin/identity/users**（`identity:admin-user:create`）
- 入参：`{username(4-30 [a-z0-9_], 不可改), nickname, password(复杂度 R1.5), roleIds: [long](允许空 R3.1)}`
- 错误：`auth.username.duplicate`(400)、`auth.password.policy-violated`(400)

**PUT /admin/identity/users/{id}**（`identity:admin-user:update`）· 入参 `{nickname, roleIds}`

**POST /admin/identity/users/{id}/disable** / **enable**（`identity:admin-user:disable`）
- 禁用即全部会话失效（R3.2）；自我保护：操作目标为自己或内置超管 → `auth.user.self-protected`(400)（R3.4）

**POST /admin/identity/users/{id}/reset-password**（`identity:admin-user:reset-password`）
- 入参 `{newPassword}`；全部会话失效；审计（R3.3）

**DELETE /admin/identity/users/{id}**（`identity:admin-user:delete`）· 逻辑删除不可恢复，同受自我保护约束

**GET /admin/identity/roles**（`identity:role:query`）· 分页 + `all=true` 不分页返回全量（下拉用，仅 ENABLED）；records: `[{id, code, name, status, userCount, createdAt}]`
**POST /admin/identity/roles**（`identity:role:create`）· `{code(3-30 [a-z0-9_-]), name, description?}`
**PUT /admin/identity/roles/{id}**（`identity:role:update`）· `{name, description, status}`；内置超管角色改权限集拒绝（R2.5）
**DELETE /admin/identity/roles/{id}**（`identity:role:delete`）· 内置拒绝 `auth.role.built-in`(400)
**PUT /admin/identity/roles/{id}/permissions**（`identity:role:assign-permission`）· `{permissionIds: [long]}`；生效即权限缓存失效（R2.6）

**GET /admin/identity/permissions/tree**（`identity:permission:query`）
**POST /admin/identity/permissions**（`identity:permission:create`）· `{parentId, type: MENU|OPERATION, code?, name, route?, component?, icon?, sort}`；OPERATION 必填 `域:资源:操作` 格式（R2.2）
**PUT /admin/identity/permissions/{id}**（`identity:permission:update`）· **DELETE**（`identity:permission:delete`）

**GET /admin/identity/portal-users**（`identity:portal-user:query`）
- 参数：`username? nickname? province? level? tag? status? registeredFrom? registeredTo? page pageSize`（R5.1）

**GET /admin/identity/portal-users/{id}**（`identity:portal-user:query`）
- 出参：基本信息 + `{inProgressInstanceCount, historyInstanceCount, pointsBalance, prizeSummary: {won, granted}, riskHits: n, listStatus: [BLACK/WHITE...]}`（R5.6 聚合）

**PUT /admin/identity/portal-users/{id}/profile**（`identity:portal-user:update-profile`）
- 入参：`{province?, userLevel?, userRole?, tags?: [string ≤20], orgId?}`；全量覆盖（R5.3）；下次请求即生效（属性 1）

**POST /admin/identity/portal-users/{id}/disable** / **enable**（`identity:portal-user:disable`）· 停用即无法登录+会话全失效（R5.2）
**POST /admin/identity/portal-users/{id}/reset-password**（`identity:portal-user:reset-password`）· 置 must_change_password=1（R5.4）
**DELETE /admin/identity/portal-users/{id}**（`identity:portal-user:delete`）· 逻辑删除（deleted=1，不可登录、历史实例与流水保留，R5.5）；级联会话失效（§6.1 logout(userId)）

**GET /admin/identity/sessions**（`identity:session:query`）· 参数 `accountType? account?`；`records: [{tokenLast4, account, accountType, loginAt, lastActiveAt, ip, deviceId?}]`（R6.1）
**POST /admin/identity/sessions/kick**（`identity:session:kick`）· 入参 `{accountType, account, tokenLast4?}` → 按账号 `StpLogic.logout(userId)` 全端下线（tokenLast4 仅界面确认展示，不作为定位键；1 秒全实例生效 R6.2）

**GET /admin/identity/internal-apps**（`identity:internal-app:query`）· 分页；records: `[{id, appId, appName, status, prevExpireAt?, createdAt}]`（secret 永不回显）
**POST /admin/identity/internal-apps**（`identity:internal-app:add`）· `{appName}` → 出参 `{id, appId, secret}`（secret = 43 位 [A-Za-z0-9] 加密随机，**仅此一次回显**，落库 AES-256-GCM 密文）；错误 `internal.app.duplicate`(400)
**POST /admin/identity/internal-apps/{id}/rotate-secret**（`identity:internal-app:edit`）· → `{secret(一次性), prevExpireAt}`（旧密钥 24h 双活窗口，§3.2.8）
**POST /admin/identity/internal-apps/{id}/disable / enable**（`identity:internal-app:edit`）· `{ok}`

<!-- §4.3 -->
### 4.3 admin · 系统管理（R7–R10）

**GET /admin/system/dict-types**（`system:dict-type:query`）· 分页；**POST**（create）`{code, name, remark?}`；**PUT /{id}**（update）`{name, status, remark}`；**DELETE /{id}**（delete，被引用校验提示）
**GET /admin/system/dict-types/{code}/entries**（登录态即可读，无操作权限码）· 返回该类型全部启用项 `[{label, value, sort}]` 升序（R7.3）
**POST /admin/system/dict-entries**（`system:dict-entry:create`）· `{typeCode, label(≤64), value(类型内唯一 ≤64 [A-Za-z0-9_\-.]), sort, remark?}`；**PUT /{id}** / **DELETE /{id}**（同域权限码）
- 错误：`dict.entry.duplicate-value`(400)

**GET /admin/system/configs**（`system:config:query`）· 分页 `configGroup? key?`；masked 项 value 返回固定掩码串（R8.2）
**POST /admin/system/configs**（`system:config:create`）· `{configKey, configGroup, configValue, valueType: STRING|NUMBER|BOOL|JSON, masked, remark?}`；错误 `config.value.type-mismatch`(400)
**PUT /admin/system/configs/{key}**（`system:config:update`）· 报文不含 `value` 字段=保持原值；携带 null/空串=`common.param-invalid`(400)（R8.2）；变更 1 秒内全实例生效（属性 1）

**GET /admin/system/cache/stats**（`system:cache:stats`）· `records: [{namespace, keyCount, hitRate}]`，命名空间封闭清单 R9.1
**POST /admin/system/cache/evict**（`system:cache:evict`）· 入参 `{level: KEY|PREFIX|NAMESPACE, namespace?, prefix?, key?}`；**level→必填字段：KEY→key、PREFIX→prefix、NAMESPACE→namespace**，缺失或多余组合 → `common.param-invalid`(400)；`namespace=identity:session` 或 key/prefix 落在该空间 → `system.cache.session-forbidden`(400)（R9.2，踢下线走 R6）；出参 `{evictedRedis: n, notifiedInstances: n}`（R9.2/9.3）；错误 `cache.namespace.unknown`(400)

**GET /admin/system/audits**（`system:audit:query`）· 参数 `operatorId? module? action? result? from? to?`（R10.4）

<!-- §4.4 -->
### 4.4 admin · 任务域（R11/R12/R14.8–14.9）

**POST /admin/task/definitions/save-aggregate**（新建 `task:definition:create` / 更新 `task:definition:update`）
- 入参（聚合原子保存 R11.4）：
```json
{
  "id": null,                    // null=新建
  "code": "daily_check",         // 4-64 [a-z0-9_-]
  "name": "每日签到任务", "description": "...", "category": "daily",
  "iconUrl": "https://...", "badgeText": "热门",
  "startTime": "...", "endTime": "...", "sortWeight": 0,
  "cycleType": "DAILY",          // NONE|DAILY|MONTHLY|CRON|SPECIAL
  "cronExpr": null, "specialStart": null, "specialEnd": null,
  "mutexGroupId": null,
  "gray": {"type": "RATIO", "ratio": 50, "abGroup": null, "crowdId": null, "excludeCrowdId": null},
  "filter": {"expr": "province() in ('GD')", "allowCrowdIds": [], "excludeCrowdIds": []},
  "steps": [
    {"code": "go_page", "name": "浏览页面", "seq": 1, "type": "PASSIVE",
     "progressTarget": null, "prizeId": null}
  ],
  "transitions": [
    {"fromStepCode": "go_page", "toStepCode": "get_reward", "conditionExpr": null, "priority": 0}
  ],
  "actions": [                   // 平台动作两层
    {"scope": "TASK", "stepCode": null, "platform": "WEB", "actionType": "ROUTE",
     "params": {"route": "home"}, "buttonText": "去完成"}
  ]
}
```
- 出参 `data`: `{id, code, version, status}`
- 错误（DAG/业务校验器，R11.2/11.3/11.6/11.9）：`task.definition.step-code-duplicate`、`task.definition.cycle-empty`、`task.definition.dag-cycle`(转移边回跳/成环)、`task.definition.reward-prize-invalid`(REWARD 未引用启用奖品)、`task.expression.invalid`(语法/白名单/超限)、`task.definition.mutex-cycle-mismatch`(同互斥组周期类型不一致)、`task.definition.step-count-exceeded`(>task.step.max-count)、`task.definition.time-window-invalid`
- 对 PUBLISHED/SCHEDULED 任务保存 = 修订草稿（置 pending_revision，R12.2 / D-01）

**GET /admin/task/definitions**（`task:definition:query`）· 参数 `code? name? status? category? page pageSize`
**GET /admin/task/definitions/{id}**（query）· 聚合视图（同 save-aggregate 结构 + `pendingRevision`、`currentVersion`）
**POST /admin/task/definitions/{id}/copy**（`task:definition:copy`）· `{code, name}`（R11.11）
**DELETE /admin/task/definitions/{id}**（`task:definition:delete`）· 已发布拒绝 `task.definition.published-not-deletable`(400)（R11.12）
**POST /admin/task/definitions/{id}/reset-revision**（`task:definition:update`）· 从当前版本快照重置编辑态并清 pending_revision（D-01 第 4 步）

**POST /admin/task/definitions/{id}/publish**（`task:definition:publish`）· 入参 `{confirm: true, early?: boolean}`（confirm 仅 pending_revision=1 时必填 true，其余场景忽略；修订发布须二次确认语义 R12.7）；校验失败返回 `task.publish.validate-failed` + `data.checkErrors: [{item, reason}]`（错误定位 R12.3：步骤可达性/REWARD 配奖品/表达式/时间窗/定时早于窗结束）。
- **DRAFT**：立即发布 → `PUBLISHED`，校验 + 固化 + version+1（`publishAt` 缺省）。
- **SCHEDULED 且 `early` 缺省/false**：只发布修订草稿（置 pending_revision=0、替换待发布内容，**不 +version、不生成快照、主状态保持 SCHEDULED**，R12.2）；到点由调度 1 校验+固化+version+1。
- **SCHEDULED 且 `early=true`**：手动提前发布（R12.1 `SCHEDULED→PUBLISHED`）。同到点路径：重新校验 → 固化快照 → version+1 → 主状态 `PUBLISHED`，清除 `schedule_publish_at`。取消定时再发布是两条边，不能替代本路径。
**POST /admin/task/definitions/{id}/schedule**（`task:definition:schedule`）· `{publishAt}`；**POST .../cancel-schedule**（schedule）；**POST .../offline**（`task:definition:offline`）· 记录 offline_at（R14.10）
**POST /admin/task/definitions/batch-publish** / **batch-offline**（publish/offline）· `{ids: [≤50]}`；响应逐条明细 `[{id, success, errorCode?}]`（R12.9）
**GET /admin/task/definitions/{id}/versions**（query）· 版本历史；**GET .../versions/diff?left=&right=**· 结构化差异 `{steps: [...], transitions: [...], filter: {...}, gray: {...}, actions: [...]}`（R12.8）

**POST /admin/task/expressions/validate**（`task:expression:validate`）· 入参 `{expression, type: FILTER|BRANCH}`；出参 `{valid, error?: {position, reason}, nullAttrSample?: [string]}`（空值语义模拟求值 R11.5）

**互斥组**：GET/POST/PUT/DELETE `/admin/task/mutex-groups`（`task:mutex-group:*`）· `{code, name, crossCycle}`；删除被引用拒绝 `task.mutex.in-use`
**人群包**：GET/POST/PUT/DELETE `/admin/task/crowds`（`task:crowd:*`）· `{code, name, status}`
**POST /admin/task/crowds/{id}/import**（`task:crowd:update`）· 入参 `{content: "10001\n10002\n..."}`（纯文本每行一个用户 ID）；出参 `{imported, deduplicated, invalid}`（R11.13 三类计数）；超上限 `task.crowd.size-exceeded`(400, crowd.max-size)

**GET /admin/task/instances**（`task:instance:query`）· 参数 `taskId? userId? status? simulated? from? to?`
**GET /admin/task/instances/{id}**（query）· `{..., steps: [{stepCode, type, status, progressCurrent, progressTarget?, activatedAt, completedAt}], events: [{code, time, payload摘要}]}`（步骤明细+事件时间线 R14.9）
**POST /admin/task/instances/{id}/abandon**（`task:instance:abandon`）· `{reason}`；abandonSource=ADMIN、释放互斥（R14.8）；错误 `task.instance.not-found`(404)；非 IN_PROGRESS（已终态）幂等返回当前状态

<!-- §4.5 -->
### 4.5 admin · 奖励与积分（R17/R18/R20/R37）

**奖品分类**：GET/POST/PUT `/admin/reward/prize-categories`（`reward:category:*`）· `{code, name, rewardTarget, fulfillmentMode, costMode, reconRequired, reconActionPolicy: REVIEW|AUTO, adapterCode?, paramSchema?}`；内置不可删；须对账三类种子 `reconActionPolicy=REVIEW`；`reconActionPolicy` 启用后可改；停用 `PUT .../{code}/disable`；错误 `reward.category.duplicate-code`、`reward.category.builtin-protected`

**奖品组**：GET/POST/PUT/DELETE `/admin/reward/prize-groups`（`reward:prize-group:*`）· `{code, name, remark}`

**GET /admin/reward/prizes**（`reward:prize:query`）· 参数 `code? name? categoryCode? status? groupId? page pageSize`
**POST /admin/reward/prizes**（`reward:prize:create`）· 入参 = §3.4.1 业务字段（`{code, name, imageUrl?, description?, categoryCode, typeParams, unitCostFen?, totalStock, dailyClaimLimit, totalClaimLimit, regionLimit, levelLimit, tagLimit, claimMode, reconActionPolicy?, expireHours?, groupId?, extConfig?}`）；`reconActionPolicy` 空 = 继承分类（R37.7）；服务端按分类写入 `rewardTarget`/`fulfillmentMode`；错误：`reward.prize.duplicate-code`、`reward.prize.points-amount-required`、`reward.prize.face-required`、`reward.prize.cost-required`、`reward.prize.limit-negative`、`reward.prize.category-disabled`、`reward.prize.adapter-required`
**PUT /admin/reward/prizes/{id}**（`reward:prize:update`）· 草稿/停用态可改启用态字段受限（状态机 R17.1）；`reconActionPolicy` 启用后仍可改（R37.7）；**DELETE**（`reward:prize:delete`）· 仅 DRAFT，被在线快照引用拒绝 `reward.prize.referenced-by-snapshot`
**POST /admin/reward/prizes/{id}/disable**（`reward:prize:disable`）· 入参 `{confirm: false|true}`；confirm=false 时出参返回影响面 `{affectedTaskCount, inFlightInstanceCount}`（实时查询口径 R17.7），confirm=true 才执行
**POST /admin/reward/prizes/{id}/enable**（`reward:prize:enable`）· 需重新执行影响确认（R17.1 状态流转）
**GET /admin/reward/prizes/{id}/stock-logs**（query）· 库存留痕分页（R17.4）
**POST /admin/reward/prizes/{id}/stock-replenish**（`reward:prize:stock-replenish`）· `{amount(>0), reason(必填)}` → 出参 `{remainingStock}`；change_type=REPLENISH 留痕 + 审计；补发（manual-grant）库存不足的前置回补入口（R17.5）

**GET /admin/reward/records**（`reward:record:query`）· 参数 `userId? prizeId? categoryCode? status? fulfillmentStatus? reconStatus? simulated? from? to?`；列表行含 `categoryCode, status, fulfillmentStatus, fulfillmentRef?, costFen, faceFen, reconStatus, fulfillFailReason?`
**POST /admin/reward/records/{id}/retry**（`reward:record:retry`）· 仅 RETRY_PENDING/PERMANENT_FAILED 前置校验可重试场景；出参 `{status, retryCount}`；错误 `reward.grant.not-retryable`(400)（R18.6）
**POST /admin/reward/records/{id}/fulfill-confirm**（`reward:record:fulfill`）· 仅 `status=GRANTED ∧ fulfillmentStatus=SENDING`（含 P0 第三方桩与 PLATFORM 实物）；出参 `{fulfillmentStatus: "ARRIVED"}`；错误 `reward.fulfill.not-sending`(400)
**POST /admin/reward/records/{id}/fulfill-retry**（`reward:record:fulfill`）· 仅 `FULFILL_FAILED`；重置为 `SENDING` 并调度适配器/待确认；错误 `reward.fulfill.not-retryable`(400)
**POST /admin/reward/records/manual-grant**（`reward:record:manual-grant`）
- 入参：`{userId, prizeId, reason(必填), bypassRules: [] 子集 ["REGION","LEVEL","TAG"]}`（仅三项运营规则可绕过；状态/限领/风控不可绕过 R17.5）
- 实现：生成虚拟补发申请单号（雪花）作为 sourceId（grantSource=MANUAL_GRANT）；扣减库存（不足拒绝 `reward.stock.insufficient`，需先回补）；申请上下文（原因/绕过项/操作人）落审计与 stock_log；补发不可绕过规则命中 → 对应业务错误码
- 错误：`reward.prize.disabled`、`reward.claim.limit-exceeded`、`risk.blocked.account-restricted`

**GET /admin/reward/spend**（`reward:record:query`）· 参数 `categoryCode? prizeId? from? to?`；出参 `{rows:[{categoryCode, arrivedCount, arrivedCostFen, sendingCount, sendingCostFen}]}`（R37.1，`simulated=0`）

**GET /admin/reward/recon/batches**（`reward:recon:query`）· `categoryCode? billDate? status?`
**POST /admin/reward/recon/batches**（`reward:recon:import`）· `{categoryCode, billDate}`；重复日 `reward.recon.duplicate-day`
**POST /admin/reward/recon/batches/{id}/import**（import）· `{lines:[{fulfillmentRef, amountFen, channelTime?}]}`
**POST /admin/reward/recon/batches/{id}/match**（`reward:recon:match`）· 按 §5.11 匹配（平台集含 `FULFILL_FAILED`）；出参计数四类；须核渠项 `reviewStatus=PENDING_REVIEW`；`AUTO` 且总闸开则 afterCommit 仅自动 `REFULFILL`
**GET /admin/reward/recon/batches/{id}/items**（query）· `result? reviewStatus?`；行含 `reviewStatus, fulfillFailReason, effectivePolicy`
**POST /admin/reward/recon/items/{id}/review**（`reward:recon:action`）· `{decision: CONFIRM|REJECT, remark(必填)}`；仅 `PENDING_REVIEW`；`CONFIRM` = 已核渠渠道未出款；`REJECT` = 渠道已出款/无需处理。错误 `reward.recon.not-pending-review`
**POST /admin/reward/recon/items/{id}/action**（`reward:recon:action`）· `{action: REFULFILL|MANUAL_GRANT|ABSORB|LEDGER_ONLY, reason}`；`MANUAL_GRANT` 须 `{userId, prizeId}` 且走 R17.5；门禁见 §5.11。错误 `reward.recon.action-done`、`reward.recon.review-required`（须核渠未 CONFIRMED）、`reward.recon.action-forbidden`（结果/履约不允许该动作）

**GET /admin/points/accounts**（`points:account:query`）· `userId?` 分页
**POST /admin/points/accounts/adjust**（`points:account:adjust`）· `{userId, amount(非零整数), reason(必填)}`；出参 `{balance}`；错误 `points.account.reason-required`；致负 `points.account.insufficient-balance`(400)；无账户 `points.account.not-found`(400)（R20.3/20.6）
**GET /admin/points/transactions**（`points:transaction:query`）· `userId? type? from? to?` 分页

<!-- §4.6 -->
### 4.6 admin · 风控（R25–R27）

**GET /admin/risk/list-items**（`risk:blacklist:query` / `risk:whitelist:query` 按 listType）· 参数 `dimension? listType? value? from? to?`
**POST /admin/risk/list-items**（黑 `risk:blacklist:add` / 白 `risk:whitelist:add`）· `{dimension: USER|IP|DEVICE, listType: BLACK|WHITE, listValue, reason, expireAt?, denyLogin?}`；重复幂等返回既有条目并提示（R25.2）`risk.list.duplicate-returned`
**POST /admin/risk/list-items/import**（`risk:blacklist:import`）· `{dimension, listType, content, reason}`；出参 `{imported, invalid}` 结果报告（R25.3）
**DELETE /admin/risk/list-items/{id}**（`risk:blacklist:remove` / `risk:whitelist:remove`）· `{reason(必填)}` 作为请求体；留痕（R25.3）

**GET /admin/risk/rules**（`risk:rule:query`）· 全量 6 条（R-a–R-f）
**PUT /admin/risk/rules/{ruleCode}**（`risk:rule:config`）· `{enabled, threshold, windowSeconds?, action}`；实时生效+审计（R26.6）；错误 `risk.rule.range-violated`(附录 A 合法范围)

**GET /admin/risk/hits**（`risk:case:query`）· `ruleCode? hitType? dimensionValue? userId? actionResult? from? to?`（R27.1）
**POST /admin/risk/cases/handle**（`risk:case:handle`）· `{hitLogId?, userId?, action: ADD_BLACK|REMOVE_BLACK|MARK_FALSE_POSITIVE, toWhitelist, reason(必填), expireAt?}`；处置动作落名单表+处置留痕表+审计（R27.2）

<!-- §4.7 -->
### 4.7 admin · 埋点元数据与调试查询（R29）

**GET/POST/PUT/DELETE /admin/track/metadata**（`track:metadata:query/create/update/delete`）· `{eventCode, name, propSchema: [{name, type, required, remark}], status: ENABLED|DISABLED, owner, remark}`（R29.1；持久化 = `evt_event_metadata`）；错误 `track.metadata.duplicate-code`
**GET /admin/track/events/debug**（`track:event:query`）· 参数 `eventCode? userId? source? deviceId? from? to?`；按 `track.query.sample-ratio-percent` 抽样 + 独立限流（R29.3）；查询无副作用（属性 1）

<!-- §4.8 -->
### 4.8 internal · 外部回调与进度上报（R15）

**认证（本节端点共用）**：请求头 `X-App-Id` / `X-Timestamp`(毫秒) / `X-Nonce`(≤64) / `X-Sign`。secret 读取 `sys_internal_app`（§3.2.8：AES-256-GCM 解密，轮换双密钥 24h 窗口内新旧同验签；内存缓存 5min）。
签名规范（R15.2 全文落地）：

```text
stringToSign = METHOD(大写) + "\n" + PATH(含 query string) + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + HEX(SHA-256(body 原始字节))
             // 空 body 按 SHA-256(空串)
X-Sign = lowerHex( HMAC-SHA256( secret, stringToSign ) )
校验：时间戳容差 ±300s（internal.timestamp.tolerance-seconds）；nonce 去重窗口 600s（SETNX）；常量时间比较（SecureUtil）
限流：按 appId 维度 ratelimit.internal.accesskey.per-second
```

**POST /internal/task/callback**
- 入参：`{instanceId: long | (userId: long, taskCode: string, cycleKey: string), stepCode: string, bizNo?: string(≤64)}`（bizNo 落 `task_instance_step.last_biz_no`，多次回调覆盖为最新，§3.3.7）
- 出参 `data`: `{instanceId, stepCode, stepStatus, instanceStatus}`（幂等重投返回当前终态快照）
- 错误：`internal.sign.invalid-signature`(401)、`internal.app.not-found` / `internal.app.disabled`(401)、`internal.nonce.replayed`(400)、`internal.timestamp.skew-exceeded`(400)、`task.instance.not-found`(404)、`task.step.state-mismatch`(400)、`task.step.not-found`(404)、`common.param-invalid`(400)
- 冻结语义：用户黑名单冻结推进期间 callback 返回 `risk.blocked.account-restricted`(400，业务码可重试语义，R25.4；internal 域统一按附录 C 业务拒绝=400——调用方按业务码分支而非 HTTP 状态分支，与 C 端 click 的 403 展示口径为有意差异)

**POST /internal/task/progress**
- 入参：`{instanceId | (userId, taskCode, cycleKey), stepCode, value: int(1–1000), reportId: string(必填 ≤64)}`
- 出参 `data`: `{instanceId, stepCode, progressCurrent, progressTarget, stepStatus}`；累加冲突重试耗尽返回 `task.progress.processing`（**HTTP 400** + 业务码，附录 C 业务拒绝；调用方按 at-least-once 原样重投 R14.3/R15.4，勿把 200 当成功）
- 其余错误同 callback；`reportId` 缺失 = `common.param-invalid`

**POST /internal/reward/fulfillment/callback**（第三方履约回执，认证同本节）
- 入参：`{fulfillmentRef: string(必填 ≤64), result: SUCCESS|FAILED, failReason?: string(≤64)}`
- 行为：按 `fulfillment_ref` 定位 `GRANTED + SENDING` 记录；`SUCCESS` → `ARRIVED` + `fulfilled_at` + `reward.fulfill.arrived`；`FAILED` → 计次后 `SENDING`（未超限）或 `FULFILL_FAILED`（超 `reward.fulfill.retry-max`）+ `reward.fulfill.failed`
- 幂等：已 `ARRIVED` 的相同 `fulfillmentRef` 原样返回；未知 ref → `reward.fulfill.not-found`(404)

<!-- §4.9 -->
### 4.9 common 端 · 门户 C 端接口

#### 4.9.0 匿名边界与通用约定（R32.1）

匿名可访问端点封闭清单：`GET /api/common/captcha`、`GET /api/common/auth/username-available`、`POST /api/common/auth/register`、`POST /api/common/auth/login`、`POST /api/common/track/batch`（匿名按 X-Device-Id 身份，R28.3）、P1 的 `GET /api/common/ad/positions/{code}`（R30.6）。**其余门户端点一律登录态**，未携带或无法解析令牌 → 401 `auth.session.missing` + 登录引导（R32.1 属性 1）；已携带但过期/被踢按 R4.3 其余三码。C 端错误码/message 面向用户可读；不暴露灰度/过滤/风控内部原因（R34.6）。

#### 4.9.1 auth（R4/R32）

**GET /api/common/captcha**（匿名）· 出参同 §4.2 后台结构（独立 captchaId 空间，Redis 键按账号域隔离）

**GET /api/common/auth/username-available?username=**（匿名，IP 限流防枚举——复用登录 IP 限流桶 `ratelimit.login.ip.per-minute`，附录 A）
- 出参 `data`: `{available: boolean, reason?: "format" | "duplicate"}`（R32.4 注册失焦实时校验）

**POST /api/common/auth/register**（匿名，IP+维度限流）
- 入参：`{username(4-30 [a-z0-9_]), password(≥8 且含字母与数字 R4.1), captchaId, captchaCode}`；请求头 `X-Device-Id`（UUID v4，缺失/非法按未携带计指标不拒绝，R4.7）
- 行为：IP/设备黑名单同步校验（R25.4）→ 注册 → 生成默认昵称"用户"+ID 后 6 位（R4.10）→ **自动登录**（R32.4）
- 出参 `data`: `{token: "client:...", userId, nickname}`
- 错误：`auth.username.invalid-format` / `auth.username.duplicate`(400)、`auth.password.policy-violated`(400)、`auth.captcha.invalid` / `auth.captcha.expired`(400)、`risk.blocked.register`(403，文案通用"暂时无法完成注册")、`auth.register.rate-limited`(429)

**POST /api/common/auth/login**（匿名，IP+账号限流）
- 入参：`{username, password, captchaId, captchaCode}`
- 出参 `data`: `{token, userId, nickname}`；锁定返回 423 `auth.login.locked`（message 含剩余分钟 R32.3）
- 错误：`auth.login.invalid-credential`(401 统一文案)、`auth.login.locked`(423)、`auth.login.rate-limited`(429)、`risk.blocked.login`(403，IP/设备/用户黑名单 denyLogin 条目，文案通用)、`auth.account.disabled`(403，停用账号 R5.2)

**POST /api/common/auth/logout**（登录态）· 令牌立即失效（R33 属性 1）

**GET /api/common/auth/profile**（登录态）
- 出参 `data`: `{userId, username, nickname, province?, userLevel?, userRole?, tags?: [string], pointsBalance}`（R4.5；运营属性只读展示 R33.3）

**PUT /api/common/auth/profile**（登录态）· 入参 `{nickname(1-30，中英文/数字/下划线)}`；即时生效不审计（R4.10）；错误 `auth.profile.nickname-invalid`(400)

**PUT /api/common/auth/password**（登录态）· `{oldPassword, newPassword}`；当前会话保留其他失效（R4.9）；错误 `auth.password.old-mismatch` / `auth.password.policy-violated`；must_change_password 状态下首次登录后引导跳转本接口（R5.4）

#### 4.9.2 task（R13/R34）

**GET /api/common/task/list**（登录态）
- 参数：`category? page pageSize`；头 `X-Client-Platform`（端解析 R16.4）
- 判定链（R13.1）：主状态 PUBLISHED ∧ 时间窗 ∧ 灰度 ∧ 过滤；**已有 IN_PROGRESS 实例则仍入列表（即使已不命中灰度/过滤）**；用户黑名单 → 投放列表空（进行中走 /mine）
- 出参 `records`: `[{taskId, taskCode, name, category, iconUrl, badgeText, rewardPreview: {firstName, totalCount}（快照顺序首个 REWARD 奖品名 + "等 N 项"，R34.1）, userStatus: NOT_STARTED|IN_PROGRESS|COMPLETED|ABANDONED|EXPIRED, sortWeight}]`（按钮状态机映射 R34.2）；**排序 = sort_weight ASC, id ASC**（admin 各分页列表缺省排序 = id DESC，未另行声明处按此缺省）
- 曝光埋点：客户端按 R28.13 口径上报 `task.card.exposure`（附录 D），服务端不在本接口产生埋点写入（R13 属性 3 响应与埋点无关）

**GET /api/common/task/{taskId}/detail**（登录态）
- 出参按用户状态三分（R13.5）：
  - 未开始：`{status: "NOT_STARTED", task: {name, iconUrl, description, category}, stepsPreview: [{seq, name, type, progressTarget?}], rewardPreview}`
  - 进行中：`{status: "IN_PROGRESS", instanceId, steps: [{stepCode, name, type, status, progressCurrent, progressTarget?}], currentStep: {stepCode, name, type, progressCurrent?, progressTarget?, action}}`——`action` 为平台动作合并结果（R16.2 回退链）：`{actionType, params, buttonText} | null`。**回退链 = 步骤级(端)→任务级(端)→步骤级(WEB)→任务级(WEB)→null；actionType=NONE 视为命中并输出无动作占位（不再回退）**
  - 终态：`{status: "COMPLETED"|"ABANDONED"|"EXPIRED", instanceId?}`
  - 有 IN_PROGRESS 实例：忽略任务 OFFLINE，按进行中三分渲染（R13.5）
  - 无实例且任务已下线/不可见：`{status: "OFFLINE"}`（HTTP 200，R34.6）
- 进度刷新时机：客户端进入详情页拉取 + 手动刷新入口，停留期间不轮询（R34.4）

**POST /api/common/task/{taskId}/start**（登录态，用户写限流）
- 行为（与 §5.5 同序）：账号状态 → **幂等短路（已有实例无论终态直接 200）** → 可见性 → 风控 → 互斥 → 每日上限 → 创建 → enter
- 出参 `data`: `{instanceId, instanceStatus, currentStep: {stepCode, name, type, action}}`
- 幂等响应语义：重复领取返回既有实例（一次性任务已放弃 → 返回该终态实例，`instanceStatus: "ABANDONED"`，前端置灰，R13.9）
- 错误：`task.claim.not-visible`(400，灰度/过滤/时间窗外直接请求的统一提示"不符合参与条件")、`task.claim.mutex-blocked`(400)、`task.claim.daily-limit`(400)、`risk.blocked.generic`(403，黑名单/规则命中统一文案 R34.6)、`auth.login.rate-limited` 类推 `task.claim.rate-limited`(429)

**POST /api/common/task/instances/{instanceId}/steps/{stepCode}/click**（登录态）
- 出参 `data`: `{instanceId, stepStatus, instanceStatus, nextStep?: {stepCode, name, type, action}, rewardFeedback?: [{prizeName, count}]（实例完成时 R34.4）}`
- 错误：`task.instance.not-found`(404)、`task.step.state-mismatch`(400 **仅**乱序/INACTIVE/SKIPPED)、已完成步骤重复 click → **200 幂等**返回当前快照（R14.6 / feasibility §2，不得二次推进）、`task.instance.frozen`(403，黑名单冻结"账号受限" R25.4)、`task.instance.expired`(400，终局性 R14 属性 4)

**POST /api/common/task/instances/{instanceId}/abandon**（登录态）
- 出参 `data`: `{instanceStatus: "ABANDONED"}`；abandonSource=USER、释放互斥（R13.9）；错误同 click 前置

**GET /api/common/task/mine**（登录态）· 参数 `status?: IN_PROGRESS|COMPLETED|ABANDONED|EXPIRED, category? page`；`records` 含进度概要 `{instanceId, taskId, taskName, iconUrl, category, status, currentStepName?, startedAt}`（R13.3）

#### 4.9.3 prize / points / dict / track（R19/R20/R7.3/R28/R35）

**GET /api/common/prize/list**（登录态）· 参数 `tab: PENDING|ALL`（默认 PENDING，R35.1）；**PENDING tab = status IN ('WON','CLAIMING','RETRY_PENDING') 或 (status='GRANTED' ∧ fulfillmentStatus IN ('SENDING','FULFILL_FAILED'))；PERMANENT_FAILED / EXPIRED / (GRANTED∧ARRIVED) 仅 ALL tab 可见**
- `records`: `[{recordId, prizeName, prizeImage, categoryCode, rewardTarget, fulfillmentMode, status, fulfillmentStatus, expireAt?, sourceTaskId?, sourceTaskName?, failReason?, fulfillFailReason?}]`
- 展示映射（R35.2）：WON=可领取；CLAIMING=loading；RETRY_PENDING=可手动重试附错误文案（AUTO 型置灰「发放重试中」）；PENDING=置灰「发放创建中」（仅 ALL）；PERMANENT_FAILED=置灰仅原因；EXPIRED=置灰；GRANTED+ARRIVED=置灰「已到账」；GRANTED+SENDING=置灰「发送中」；GRANTED+FULFILL_FAILED=置灰「发送失败」+原因

**POST /api/common/prize/records/{id}/claim**（登录态，用户限流 R19.5）
- 出参 `data`: `{status: "GRANTED", fulfillmentStatus: "ARRIVED"|"SENDING"}`（INSTANT→ARRIVED，ASYNC→SENDING）；并发恰一次（锁+CAS，R19 属性 1）；**前置 = status IN ('WON','RETRY_PENDING')**（R19.4 用户手动重试；AUTO 型记录的 RETRY_PENDING 由前端置灰「发放重试中」，后端不区分拒绝）
- 错误：`reward.claim.not-won`(400 状态不符，含 PENDING/PERMANENT_FAILED/EXPIRED 等全部非可领取态)、`reward.claim.expired`(400 兜底校验，R19 属性 2)、`reward.claim.conflict`(400 并发竞争提示稍后再试)

**GET /api/common/points/balance**（登录态）· `{balance}`（获得积分后即时刷新，R35.4）
**GET /api/common/points/transactions**（登录态）· `type? page`；`records: [{type, amount, balanceAfter, bizSource?, sourceTaskId?(收入来源任务可跳转 R35.3), remark?, createdAt}]`

**GET /api/common/dict/{typeCode}**（登录态，R32.1 边界内）· `[...,{label, value}]` 按排序升序；类型停用返回空数组（R7.3）

**POST /api/common/track/batch**（匿名可访问，独立限流按 userId/deviceId，R28.3）
- 头：`X-Device-Id`；入参：
```json
{
  "events": [ {"code": "task.card.exposure", "props": {"taskId": 1}, "clientTime": "..."} ],
  "platform": "WEB", "appVersion": "1.0.0"
}
```
- 约束：单批 ≤ `track.batch.max-size`(50)、单条 props 序列化 ≤ `track.event.max-payload-kb`(8KB)；畸形事件（超限/非法 JSON/超长）**丢弃并计数，不影响同批合法事件**（R28.5）；未登记/停用事件按 `track.unregistered-policy` / `track.disabled-event-policy`（R28.6/R29.2，附录 A，默认 drop-count）
- 出参 `data`: `{accepted: n, dropped: n}`（接收条数 R28.5）
- 错误：`track.batch.overflow`(400 整批 >50)、`track.batch.rate-limited`(429)、`common.param-invalid`(400 整批非法 JSON)
- 客户端上报行为约束（R28.10/13/14）：本地聚合满 20 条或 5 秒触发；失败重试 ≤2；页面卸载用 sendBeacon，失败本地暂存下次访问补发（分析口径以服务端接收时间为准）

#### 4.9.4 C 端交互契约注记（模块 I，约束门户前端实现）

| 约束 | 内容 | 需求 |
|------|------|------|
| 列表按钮状态机 | NOT_STARTED=领取 / IN_PROGRESS=继续（直达当前步骤）/ 终态置灰文案（已完成/已放弃/已过期），与实例状态严格一致 | R34.2、R34 属性 1 |
| 详情步骤时间线 | 已完成打勾、当前高亮、未激活置灰；当前步骤按 action 合并渲染；进度步骤展示 x/N 与进度条 | R34.3 |
| 会话体验 | 401 统一拦截跳登录页含回跳；kicked-concurrent 与 kicked-admin 文案区分（§4.1 D-02 / R4.3） | R32.5 |
| 领取按钮状态 | 按 §4.9.3 状态映射一一对应 | R35 属性 1 |
| 待领取倒计时 | 待领取奖品显示剩余时间倒计时 | R35.1 |
| 图片兜底 | 外链图片加载失败统一占位图，不阻塞页面 | 模块 I 基线 |
| 冻结展示 | 冻结不预置展示：列表/详情按钮照常渲染"继续"，点击时返回 `task.instance.frozen`(403) 并提示"账号受限"（有意设计：冻结状态不向未触发场景暴露，与 R34.6 同源） | R25.4 |
| 字典值失效回显 | 引用停用字典项的存量数据按原值回显（label 取不到时显示 value 原文），不报错 | R7.5 |
| 空态引导 | 各列表空态文案与引导（"暂无进行中的任务，去看看任务列表"） | R33.4 |

> P1 端点：`/api/common/ad/**`（R30）、`/api/common/signin/**`（R21/R36）。契约要点由对应需求条款封闭（匿名访问、频控主键、素材输出、签到唯一约束）；端点明细随 P1 任务写入，不在本章 P0 范围。

---

<!-- §4.10 -->
### 4.10 admin 前端页面清单（pure-admin-thin 对齐，V1 菜单种子唯一依据）

> 页面 ↔ 路由 ↔ component（views/ 下路径）↔ 数据来源端点。**V1 权限树种子 = 本表 route/component 全量**；权限码 = 各页数据来源端点在 §4.2–§4.7 逐端点声明的权限码（不另行发明）；路由注册由 pure-admin-thin 动态路由按菜单数据生成。P1 页面（看板/模拟器/签到/活动/广告位）随对应域交付追加登记本表。

| 页面 | 路由（菜单 route） | component | 数据来源 |
|------|-------------------|-----------|---------|
| 登录 | `/login` | `login/index` | §4.2 登录/验证码（匿名） |
| 工作台 | `/dashboard` | `dashboard/index` | P0 静态欢迎页（无聚合指标，P1 看板替换） |
| 后台用户 | `/system/users` | `system/user/index` | §4.2 users |
| 角色权限 | `/system/roles` | `system/role/index` | §4.2 roles/permissions/tree |
| 会话管理 | `/system/sessions` | `system/session/index` | §4.2 sessions/kick |
| 门户用户 | `/system/portal-users` | `system/portal-user/index` | §4.2 portal-users |
| internal 调用方 | `/system/internal-apps` | `system/internal-app/index` | §4.2 internal-apps |
| 字典管理 | `/system/dicts` | `system/dict/index` | §4.3 dict-types/entries |
| 参数配置 | `/system/configs` | `system/config/index` | §4.3 configs |
| 缓存管理 | `/system/cache` | `system/cache/index` | §4.3 cache 概况/清理 |
| 操作审计 | `/system/audits` | `system/audit/index` | §4.3 审计查询 |
| 任务列表 | `/task/definitions` | `task/definition/index` | §4.4 definitions + schedule-failures |
| 任务编辑（画布） | `/task/definitions/edit/:id?` | `task/definition/edit`（vue-flow） | §4.4 save-aggregate/expressions/validate/copy |
| 任务版本 | `/task/definitions/:id/versions` | `task/definition/version` | §4.4 versions/对比 |
| 互斥组 | `/task/mutex-groups` | `task/mutex-group/index` | §4.4 mutex-groups |
| 人群包 | `/task/crowds` | `task/crowd/index` | §4.4 crowds/导入 |
| 实例管理 | `/task/instances` | `task/instance/index` | §4.4 instances/详情/abandon |
| 奖品分类 | `/reward/categories` | `reward/category/index` | §4.5 prize-categories |
| 奖品管理 | `/reward/prizes` | `reward/prize/index` | §4.5 prizes/stock-replenish/stock-logs |
| 发放记录 | `/reward/records` | `reward/record/index` | §4.5 records/retry/manual-grant/fulfill/spend |
| 渠道对账 | `/reward/recon` | `reward/recon/index` | §4.5 recon batches/import/match/action |
| 积分账户 | `/points/accounts` | `points/account/index` | §4.5 points accounts/adjust |
| 积分流水 | `/points/transactions` | `points/transaction/index` | §4.5 points transactions |
| 风控名单 | `/risk/list-items` | `risk/list-item/index` | §4.6 list-items/导入 |
| 风控规则 | `/risk/rules` | `risk/rule/index` | §4.6 rules |
| 命中与处置 | `/risk/cases` | `risk/case/index` | §4.6 hits/cases |
| 埋点元数据 | `/track/metadata` | `track/metadata/index` | §4.7 metadata |
| 事件调试 | `/track/events` | `track/event/index` | §4.7 调试查询 |
