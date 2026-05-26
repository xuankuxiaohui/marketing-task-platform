# v0.4.0 发布说明

最后更新：2026-05-26

## 概述

v0.4.0 三线并行交付：P1 条件分支（任务引擎能力深化）、P2 Sa-Token 鉴权迁移（生产前置）、P3 工程化补齐（快照增强 + HTTP 集成测试 + 前端类型安全）。

总测试数：171（153 unit + 18 integration），全部通过。

---

## P1：条件分支

### 动机

此前任务步骤严格线性推进（`seq + 1`），无法实现"VIP 走快速通道"、"不同选项进入不同分支"等业务场景。

### 变更

- 新建 `task_step_transition` 表 (Flyway V14)，支持步骤 → 多目标分支
- `StepAdvanceEngine.resolveNextSeq()` 按 priority 依次评估 QLExpress condition_expr
- NULL condition_expr = 默认分支，全不匹配时 fallback `seq + 1`（向后兼容）
- DAG 约束：目标 seq > 来源 seq，不可指向自身
- `TaskDefinitionCacheService` 新增 transitions 缓存，聚合保存/发布时失效
- 前端 StepsTab 新增分支配置 UI：优先级、目标步骤下拉（排除自身）、条件表达式 + 校验按钮、默认分支自动最低优先级
- 有分支的步骤卡片显示分支图标 + 数量 badge

### 新增文件

| 文件 | 说明 |
|---|---|
| `backend/.../db/migration/V14__step_branching.sql` | DDL |
| `backend/.../domain/entity/TaskStepTransition.java` | 实体 |
| `backend/.../domain/vo/TaskStepTransitionVO.java` | VO |
| `backend/.../mapper/TaskStepTransitionMapper.java` | Mapper |

### 修改文件

| 文件 | 变更 |
|---|---|
| `StepAdvanceEngine.java` | 新增 `resolveNextSeq()`，completeStep 使用分支路由 |
| `TaskDefinitionCacheService.java` | 新增 `getTransitions()` 缓存 |
| `TaskService.java` | publish() 写入 transitions 到快照 |
| `TaskSnapshotDTO.java` | 新增 `transitions` 字段 |
| `admin-web/.../StepsTab.vue` | 步骤编辑弹窗新增分支配置区域 |
| `admin-web/.../TaskEdit.vue` | 加载/保存 transitions |
| `admin-web/.../api/step.ts` | 新增 `StepTransition` 接口 |
| `admin-web/.../api/task.ts` | `TaskAggregateDTO` 新增 transitions |

### 测试

- 现有 `StepAdvanceEngineTest` 9 tests 全部通过
- `TaskLifecycleIntegrationTest` 9 tests 全部通过

---

## P2：Sa-Token 鉴权迁移

### 动机

原 JWT 鉴权基于自定义 AdminJwtProvider/ClientJwtProvider + 独立 Interceptor，缺乏 Token 续期、登出、并发控制等生产必需能力。Sa-Token 是成熟的开源鉴权框架，内置上述能力并提供 SSO/OAuth2 扩展点。

### 变更

- **依赖**：新增 `sa-token-spring-boot3-starter:1.44.0` + `sa-token-jwt:1.44.0`，移除 `jjwt-api`/`jjwt-impl`/`jjwt-jackson`
- **多账号模式**：`StpUtil` (type=admin) + `StpUserUtil` (type=client)，各自独立 JWT secret
- **配置**：`SaTokenConfig` @PostConstruct 注入 `StpLogicJwtForSimple`
- **路由**：`SaTokenRouteConfig` 注册 SaInterceptor (`/api/admin/**` + `/api/client/**`)，mock 模式跳过
- **桥接**：`SaTokenUserContextBridge` 将 Sa-Token 会话数据桥接到现有 `UserContext`/`UserContextHolder`，业务代码零改动
- **兼容**：保留 `admin_user`/`client_user` 表结构、BCrypt 密码校验、验证码、登录 Controller 签名

### 新增文件

| 文件 | 说明 |
|---|---|
| `backend/.../config/SaTokenConfig.java` | 多账号 JWT 注入 |
| `backend/.../config/SaTokenRouteConfig.java` | 路由拦截注册 |
| `backend/.../config/SaTokenType.java` | 常量 ADMIN/CLIENT |
| `backend/.../context/SaTokenUserContextBridge.java` | 会话→UserContext 桥接 |

### 删除文件

| 文件 | 说明 |
|---|---|
| `AdminJwtProvider.java` | 替换为 Sa-Token StpUtil |
| `ClientJwtProvider.java` | 替换为 Sa-Token StpUserUtil |
| `AdminAuthInterceptor.java` | 替换为 SaInterceptor |
| `ClientAuthInterceptor.java` | 替换为 SaInterceptor |

### 修改文件

| 文件 | 变更 |
|---|---|
| `backend/pom.xml` | jjwt → sa-token 依赖替换 |
| `application.yml` | JWT 配置 → sa-token 配置块 |
| `application-dev.yml` | 新增 `sa-token.jwt-secret-key` |
| `application-test.yml` | 新增 mock + sa-token 测试配置 |
| `UserContextInterceptor.java` | 整合 mock/real 两种模式 |

### 获得的新能力

- Token 自动续期 (`active-timeout: 1800`)
- 并发登录控制 (`is-concurrent`)
- 登出 / Token 失效 (`StpUtil.logout()`)
- SSO 扩展点预留 (`sa-token-sso`)
- OAuth2/OIDC 扩展点预留 (`sa-token-oauth2`)
- 注解鉴权 (`@SaCheckLogin(type="admin")`)

---

## P3：工程化补齐

### 步骤平台配置纳入快照

- `TaskSnapshotDTO` 新增 `List<TaskStepPlatform> stepPlatforms` 字段
- 发布时 `TaskService.publish()` 查询 stepPlatforms + transitions 并写入快照 JSON
- C 端 `detail()` 从快照读取步骤平台配置（无需回表查询）

### HTTP 层集成测试

新增 9 个 MockMvc 测试，覆盖 Admin/Client controller 核心端点：

| 测试类 | 覆盖 |
|---|---|
| `AdminTaskControllerTest` (4) | 聚合保存+发布、获取任务详情、任务列表 |
| `ClientTaskControllerTest` (5) | 可见任务列表、任务详情（含过滤+灰度）、实例创建、CLICK 推进 |

全部使用 H2 内存库 + `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`。

### 前端类型刷新

- admin-web 的 `generated/` 类型与后端 OpenAPI 对齐，消除手写差异。

---

## 升级说明

### 从 v0.3.x 升级

1. **数据库**：执行 Flyway V14 迁移（`V14__step_branching.sql`），自动创建 `task_step_transition` 表
2. **依赖**：`mvn clean install` 更新 Sa-Token 依赖
3. **配置**：检查 `application.yml` 中 `sa-token.*` 和 `app.auth.*` 配置（参考 dev/test profile）
4. **JWT Secret**：生产环境务必配置独立的 `ADMIN_JWT_SECRET` 和 `CLIENT_JWT_SECRET` 环境变量
5. **Mock 模式**：测试/开发环境设置 `MOCK_AUTH_ENABLED=true` 保留 mock 鉴权兼容

### 不兼容变更

- JWT Token 格式变更（jjwt → Sa-Token JWT），升级后现有 Token 失效，用户需重新登录
- 移除了 `AdminJwtProvider` / `ClientJwtProvider` / `AdminAuthInterceptor` / `ClientAuthInterceptor`（如外部代码依赖需适配）

---

## 已知限制

| 限制 | 说明 |
|---|---|
| 外部用户中心 | Sa-Token 已就绪，尚未对接具体 SSO/OAuth2 提供方 |
| DAG 可视化编辑 | 分支配置为表单方式，尚未实现可视化 DAG 编辑器 |
| 批量预创建实例 | CRON 任务尚未批量预创建用户任务实例 |
