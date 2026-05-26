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
- `TaskDefinitionCacheService` 新增 transitions 缓存
- 前端 StepsTab 新增分支配置 UI：优先级、目标步骤下拉、条件表达式 + 校验按钮、默认分支

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

---

## P2：Sa-Token 鉴权迁移

### 动机

原 JWT 鉴权基于自定义 AdminJwtProvider/ClientJwtProvider + 独立 Interceptor，缺乏 Token 续期、登出、并发控制等生产必需能力。

### 变更

- **依赖**：新增 `sa-token-spring-boot3-starter:1.44.0` + `sa-token-jwt:1.44.0`，移除 jjwt
- **多账号模式**：`StpUtil` (type=admin) + `StpUserUtil` (type=client)，各自独立 JWT secret
- **配置**：`SaTokenConfig` @PostConstruct 注入 `StpLogicJwtForSimple`
- **路由**：`SaTokenRouteConfig` 注册 SaInterceptor，mock 模式跳过
- **桥接**：`SaTokenUserContextBridge` 将 Sa-Token 会话桥接到现有 UserContext/UserContextHolder
- **兼容**：保留 admin_user/client_user 表结构、BCrypt 密码校验、验证码、登录 Controller 签名

### 新增文件

- `backend/.../config/SaTokenConfig.java`, `SaTokenRouteConfig.java`, `SaTokenType.java`
- `backend/.../context/SaTokenUserContextBridge.java`

### 删除文件

- `AdminJwtProvider.java`, `ClientJwtProvider.java`
- `AdminAuthInterceptor.java`, `ClientAuthInterceptor.java`

### 获得的新能力

- Token 自动续期 (`active-timeout: 1800`)
- 并发登录控制 (`is-concurrent`)
- 登出 / Token 失效
- SSO / OAuth2 / OIDC 扩展点预留
- 注解鉴权 (`@SaCheckLogin`)

---

## P3：工程化补齐

- **步骤平台配置纳入快照**：TaskSnapshotDTO 新增 `stepPlatforms`，发布时固化
- **HTTP 层集成测试**：AdminTaskControllerTest (4) + ClientTaskControllerTest (5) MockMvc
- **前端类型刷新**：admin-web 的 `generated/` 类型与后端 OpenAPI 对齐

---

## 升级说明

### 从 v0.3.x 升级

1. **数据库**：执行 Flyway V14 迁移
2. **依赖**：`mvn clean install` 更新 Sa-Token 依赖
3. **配置**：检查 `sa-token.*` 和 `app.auth.*` 配置
4. **JWT Secret**：生产环境配置独立的 `ADMIN_JWT_SECRET` 和 `CLIENT_JWT_SECRET`

### 不兼容变更

- JWT Token 格式变更（jjwt → Sa-Token JWT），现有 Token 失效
- 移除了自定义 JwtProvider 和 AuthInterceptor

---

## v0.3.x 历史

### v0.3.0 (2026-05-24)
- 事件埋点 + Micrometer 监控指标 + task_metrics 聚合
- Admin 模拟测试 (SimulateContextHolder + full-flow API)
- Admin Dashboard + TaskMetrics 前端

### v0.3.1 (2026-05-24)
- 任务灰度 (inGrayPercent / inABGroup / inCrowd)
- Admin Instance API

### v0.3.2 (2026-05-25)
- 任务列表优化 (筛选 + stepCount/instanceCount)
- 互斥组独立表 + 跨周期互斥

### v0.3.3 (2026-05-25)
- 奖品记录管理 + 补发
- 步骤拖拽 + 复制任务 + 版本对比
- 操作审计日志 + P3 CI/CD
