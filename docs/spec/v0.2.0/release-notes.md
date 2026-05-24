# v0.2.0 版本说明

发布日期：2026-05-18

## 目标

在 Sprint 1 全链路基础上，补齐 CALLBACK/PROGRESS 步骤类型、Internal API（业务系统回调）、Platform 适配器、Admin 聚合保存与子表 CRUD、admin-web 编辑表单、client-web C 端骨架。

## 新增内容

### 后端：CALLBACK/PROGRESS 步骤 + Internal API

- `StepAdvanceEngine.callback(instance, eventKey)` — 按 `currentStepSeq` 定位 CALLBACK 步骤，校验 `callbackEventKey` 匹配后推进并级联
- `StepAdvanceEngine.progress(instance, stepId, progressValue)` — 按 `stepId` 定位 PROGRESS 步骤，`progressValue >= targetValue` 时完成并级联，未达标只更新进度
- `CallbackStepHandler` / `ProgressStepHandler` — StepHandler 实现，`onStepEnter` 为空（等待外部事件）
- `InternalCallbackController` — `POST /api/internal/task/callback` 和 `/progress`，支持 instanceId 或 (userId+taskId+cycleKey) 定位实例
- `CallbackRequest` / `ProgressRequest` DTO — 内部回调请求体
- `WebConfig` 拦截器路径从 `/api/**` 改为 `/api/admin/**`, `/api/client/**`，排除 Internal API
- 9 个单元测试（`StepAdvanceEngineTest`）：覆盖 callback/progress 推进、幂等、类型不匹配、跨任务校验

### 后端：Admin 聚合保存 + 子表 CRUD

- `TaskAggregateDTO` — 聚合请求体：{ task, steps[], filters[], platforms[] }
- `TaskService.saveAggregate()` — 事务内 delete-all + re-insert 子表
- `AdminTaskController.save()` — 从接收 `Task` 改为接收 `TaskAggregateDTO`
- `AdminStepController` — `/api/admin/task/{id}/steps` CRUD，自动 MAX(seq)+1
- `AdminPlatformController` — `/api/admin/task/{id}/platforms` CRUD + upsert
- `AdminFilterCrudController` — `/api/admin/task/{id}/filters` CRUD
- `AdminStepPlatformController` — `/api/admin/task/{id}/steps/{sid}/platforms` CRUD

### 后端：Platform 适配器 + Seed 数据

- `IosPlatformAdapter` / `AndroidPlatformAdapter` / `MiniappPlatformAdapter` — @Component 自动注册
- Flyway V3 种子数据：3 个演示任务
  - `daily_checkin` (DAILY) — PASSIVE → CLICK → REWARD + BJ 过滤器
  - `monthly_survey` (MONTHLY) — CALLBACK(调查完成) → REWARD(优惠券)
  - `reading_challenge` (ONCE) — PROGRESS(3篇) → REWARD(读者勋章)

### Admin 前端

- `StepsTab.vue` — 从骨架替换为可编辑 el-table：添加/删除步骤，类型选择器动态显示配置字段（PROGRESS→targetValue, CALLBACK→callbackEventKey, REWARD→rewardConfigJson）
- `PlatformsTab.vue` — 平台复选框 + 端配置（buttonText, flowDesc, jumpUri）
- `FiltersTab.vue` — 过滤器列表：表达式 + 描述 + 启用开关 + 校验按钮
- `TaskEdit.vue` — 通过 Tab ref 调用 `defineExpose` 聚合提交 `TaskAggregateDTO`
- `TaskList.vue` — 操作列添加"编辑"按钮
- 新增 API 模块：`step.ts`, `platform.ts`, `filter.ts`（CRUD 函数）

### Client 前端

- Vue 3 + Vant 4 + Vite 6 工程，`unplugin-auto-import` + `VantResolver` 按需引入
- `TaskList.vue` — van-pull-refresh + CellGroup 任务列表，loading/empty/error 三态
- `TaskDetail.vue` — van-steps 进度展示 + sticky 操作栏（CLICK 步骤推进按钮）
- `MockLogin.vue` — van-form 用户上下文编辑（userId, province, role, tags, level, platform）
- `http.ts` — Axios 实例 + `X-User-*` 请求拦截器（从 Pinia store 读取）
- `user.ts` — Pinia store：mock UserContext + localStorage 持久化
- 后端配合：`ClientTaskController.detail()` 返回 `TaskInstanceDetailDTO`（含 instance + steps + stepPlatforms）

## 验证结果

### 后端编译 + 测试

```bash
mvn -f backend/pom.xml test -q
```

结果：14 tests passed（含 Sprint 1 FilterExpressionEngineTest 5 tests + Sprint 2 StepAdvanceEngineTest 9 tests）。

### Admin 前端构建

```bash
npm --prefix admin-web run build
```

结果：通过。

### Client 前端构建

```bash
npm --prefix client-web run build
```

结果：通过。

## v0.2.x — Auth 鉴权模块

发布日期：2026-05-24

### 新增内容

- **JWT 鉴权**：`AdminJwtProvider` / `ClientJwtProvider` 基于 jjwt 0.12.6，HS256 签名
- **用户系统**：`admin_user` / `client_user` 表 + `AdminUserMapper` / `ClientUserMapper`
- **登录/注册**：`POST /api/admin/auth/login`, `POST /api/client/auth/login`, `POST /api/client/auth/register`
- **验证码**：`CaptchaService` 基于 easy-captcha，内存存储 60s TTL，daemon 线程定时清理
- **鉴权拦截器**：`AdminAuthInterceptor` / `ClientAuthInterceptor` 替换旧 `UserContextInterceptor`，支持 JWT / Mock 双模式
- **ErrorCode 枚举**：BAD_REQUEST(400), UNAUTHORIZED(401), FORBIDDEN(403), NOT_FOUND(404), INTERNAL_ERROR(500)
- **AuthProperties**：`@ConfigurationProperties(prefix="app.auth")` 配置 JWT 密钥、过期时间、mock 开关
- **BCrypt 密码编码**：`PasswordEncoderConfig` + Spring Security Crypto
- **MetaObjectHandler**：`MyMetaObjectHandler` 自动填充 `createdAt` / `updatedAt`
- **22 个单元测试**：AdminAuthService(5) + ClientAuthService(7) + CaptchaService(6) + AdminAuthInterceptor(4)

### 前端

- **admin-web**：Login.vue 渐变背景 + 品牌 Logo + 输入框图标 + 过渡动画
- **client-web**：Login.vue / Register.vue 品牌化 + 卡片式表单 + 自定义步骤指示器（TaskDetail）
- http.ts 请求拦截器：优先发送 `Authorization: Bearer <token>`，无 token 时 fallback mock headers
- user.ts Pinia store：`setAuth()` 支持 JWT payload 解码还原用户上下文

### 验证

```bash
mvn -f backend/pom.xml test  # 60 tests passed (22 new + 38 existing)
```

## v0.2.1 — 配置版本快照

发布日期：2026-05-24

- **`task_definition_snapshot` 表**：发布任务时将 task + steps + filters + platforms 序列化为 JSON 快照
- **版本化读取**：`ClientTaskController.detail()` 优先按实例 `taskVersion` 读取快照；无快照时 fallback 到实时表
- **Caffeine 缓存**：快照按 `taskId:version` 缓存，创建后不可变

## v0.2.2 — 真实奖励系统对接

发布日期：2026-05-24

- **`reward_record` 表 (Flyway V5)**：记录每笔发奖流水的 instance_id, step_id, reward_type, reward_config_json, status (PENDING/SUCCESS/FAILED), error_message
- **幂等发奖**：`(instance_id, step_id)` 唯一约束，已有 SUCCESS 记录则跳过，重复触发不重复发奖
- **失败重试**：handler 抛异常时记录 FAILED + error_message，不抛出异常阻塞实例，后续可重试
- **RewardStatus 枚举**：PENDING / SUCCESS / FAILED
- **10 个单元测试**：覆盖幂等跳过、FAILED 重试、point/coupon/badge 路由、handler 异常容错、记录字段完整性

### 验证

```bash
mvn -f backend/pom.xml test  # 70 tests passed (10 new + 60 existing)
```

## v0.2.3 — 独立奖品模块 (prize package)

发布日期：2026-05-24

- **4 张新表 (Flyway V6)**：`prize` — 奖品配置（库存/限制/有效期）、`prize_record` — 中奖记录（快照字段/状态流转）、`prize_claim_lock` — 防重领取锁、`prize_inventory_record` — 库存扣减流水
- **PrizeHandler 策略模式**：PointPrizeHandler（内部积分）/ CouponPrizeHandler（优惠券 API）/ BadgePrizeHandler（徽章 API）/ InternalPrizeHandler（内部通用），通过 handler_bean 字段分发
- **7 个 PrizeLimiter 校验链**：PrizeStatusLimiter（启用/时间窗口）、InventoryLimiter（总/月/日库存）、MutexLimiter（互斥奖品）、ProvinceLimiter（省份允许/拒绝）、LevelLimiter（等级限制）、TagLimiter（标签允许/拒绝）、UserRateLimiter（用户频率限制）
- **ClaimService 领奖**：prize_claim_lock 基于 UNIQUE(record_id) 防重 + DuplicateKeyException 检测并发 + 状态机 WON→CLAIMING→GRANTED/FAILED/FAILED_PERMANENTLY/EXPIRED
- **三机制过期处理**：用户进领奖专区主动标记 + PrizeExpiryScheduler 每小时扫描 + 领奖时过期校验
- **自动/手动领奖**：auto_grant 控制是否发奖后自动触发领奖
- **有效期类型**：DAYS（相对天数）、CALENDAR_MONTH（自然月月末）、FIXED_DATE（固定日期）
- **领奖专区/管理 API**：ClientPrizeController + AdminPrizeController
- **task_step 扩展**：新增 prize_id / prize_quantity 字段，RewardStepHandler 双路径兼容（prize_id 非空→PrizeService，空→legacy RewardService）
- **39 个单元测试**：PrizeServiceTest 13 + ClaimServiceTest 9 + PrizeLimiterTest 17

### 验证

```bash
mvn -f backend/pom.xml test  # 109 tests passed (39 new + 70 existing)
```

## v0.2.4 — 端到端集成测试

发布日期：2026-05-24

- **H2 测试基础设施**：`application-test.yml` + `schema-h2.sql`（H2 MySQL 兼容模式），Flyway 在测试中禁用
- **TaskLifecycleIntegrationTest 9 个场景**：
  - 每日签到全链路（PASSIVE→CLICK 级联推进）
  - 问卷回调全链路（CALLBACK 事件匹配 + 级联）
  - 阅读进度全链路（PROGRESS 累计达标触发级联）
  - 互斥组校验（同组冲突拒绝 + 不同组放行）
  - 省份过滤器（SH-only 过滤 BJ 用户）
  - 发布→旧实例读旧快照→再发布版本递增
  - 奖品模块集成（REWARD 步骤发奖生成 prize_record）
  - 实例创建幂等（重复调用 getOrCreateInstance 返回同一实例）
- **cascade 双步进 bug 修复**：completeStep 已递增 currentStepSeq，cascade 循环末尾不再重复递增

### 验证

```bash
mvn -f backend/pom.xml test  # 118 tests passed (9 new + 109 existing)
```

## v0.2.5 — 异常码与错误文案标准化

发布日期：2026-05-24

- **ErrorCode 枚举扩展**：从 5 个通用码扩展为 28 个业务子码，覆盖 AUTH / CAPTCHA / TASK / STEP / INSTANCE / MUTEX / PRIZE / REWARD / FILTER 九个域
- **31 处全量替换**：所有 `throw new BusinessException` 从裸字符串或 `ErrorCode.NOT_FOUND` 替换为具体 ErrorCode（如 `ErrorCode.INSTANCE_NOT_FOUND`）
- **GlobalExceptionHandler 增强**：新增 BindException (400) / HttpMessageNotReadableException (400) / NoResourceFoundException (404) / AccessDeniedException (403) 处理器，覆盖 5+ 异常类型
- **安全加固**：Catch-all Exception handler 不再通过 `ex.getMessage()` 泄露内部异常细节
- **BusinessException 重构**：新增 ErrorCode 字段 + `BusinessException(ErrorCode)` / `BusinessException(ErrorCode, String)` 构造函数，保留 `BusinessException(int, String)` 向后兼容
- **Result 增强**：新增 subCode 字段 + `fail(ErrorCode)` 工厂方法，`@JsonInclude(NON_NULL)` 确保成功时不返回 subCode

### 验证

```bash
mvn -f backend/pom.xml test  # 118 tests passed
npm --prefix admin-web run build  # 通过
npm --prefix client-web run build  # 通过
```

## 已知限制

| 项 | 状态 | 后续版本 |
|---|---|---|
| 真实发奖 | ✅ 已实现 (v0.2.3)，独立 prize 模块 + 7 limiter + 领奖锁 | 外部奖励 API 对接后替换 handler 实现 |
| 配置版本快照 | ✅ 已实现 (v0.2.1)，发布时固化快照，C 端按版本读取 | — |
| MONTHLY/CRON 周期 | 数据模型和种子数据就绪 | v0.3.0 补充调度触发 |
| 任务互斥 | `mutex_group_key` 字段预留 | v0.3.0 |
| 真实鉴权 | JWT 自签（无网关/用户中心） | 接入外部鉴权中心 |
| 名单过滤 | allowlist/denylist 函数返回 true | 接入名单数据源 |
| 端到端测试 | ✅ 已实现 (v0.2.4)，9 个 @SpringBootTest 场景 (H2) | HTTP 层集成待补充 |

## 从 v0.1.0 的变更

| v0.1.0 | v0.2.0 |
|---|---|
| `POST /api/admin/task` 只保存 task 主体 | 改为接收 `TaskAggregateDTO` 聚合保存 |
| Internal API 返回 501 | CALLBACK / PROGRESS 端到端可用 |
| admin-web 编辑页骨架 | 可编辑表单（步骤/过滤器/端配置） |
| client-web 不存在 | Vue3+Vant 骨架工程 |
| 3 个 Platform 适配器占位 | iOS/Android/小程序适配器已注册 |
| 无种子数据 | V3 seed 3 个演示任务 |

## 建议的下一版本 v0.3.0

1. 任务互斥组：同 `mutex_group_key` 任务互斥展示
2. 真实发奖对接：替换 LogRewardService，对接奖励中心
3. Caffeine 缓存：任务定义本地缓存 + 发布时刷新
4. OpenAPI → 前端类型生成：消除 API 类型手工维护
5. MONTHLY/CRON 周期调度触发
