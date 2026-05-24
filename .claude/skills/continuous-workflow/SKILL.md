---
name: continuous-workflow
description: "Use when the user asks to continue development, push the project forward, or work autonomously across multiple sessions/days. This skill defines the prioritized task queue, quality gates, and session startup checklist for multi-day continuous progress."
---

# Continuous Workflow — marketing-task-platform 持续推进工作流

当用户要求持续开发（多天/多任务/自主推进），按本文档执行。

## 1. 会话启动自检

每个新会话开始时，先执行以下检查（不依赖记忆）：

```bash
# 1. 确认分支和目录
git branch --show-current

# 2. 读取最新提交
git log --oneline -3

# 3. 后端测试基线
mvn -f backend/pom.xml test 2>&1 | grep -E "Tests run:|BUILD"

# 4. 检查未提交变更
git status --short
```

如果有未提交变更，先处理（提交或询问用户）再继续新任务。

## 2. 当前基线

| 维度 | 状态 |
|---|---|
| 后端测试 | 60 tests passed |
| Admin 前端构建 | `npm --prefix admin-web run build` 通过 |
| Client 前端构建 | `npm --prefix client-web run build` 通过 |
| 数据库迁移 | V1 (core) → V2 (seed) → V3 (auth) → V4 (snapshot) |
| Auth 鉴权 | JWT + Mock 双模式, admin_user / client_user, 22 单测 |
| 配置快照 | publish 时序列化 task+steps+filters+platforms, C 端按版本读取 |

## 3. 任务队列（严格顺序执行）

### P0-2：真实奖励系统对接

**文件范围**：`backend/src/main/java/com/marketing/task/service/reward/`

**步骤**：

1. **新建 `reward_record` 表 (Flyway V5)**
   - 字段：id, instance_id, step_id, reward_type, reward_config_json, status (PENDING/SUCCESS/FAILED), idempotent_key, error_message, created_at
   - 唯一约束：`(instance_id, step_id)` 作为幂等键

2. **完善 `RewardService` — 幂等发奖**
   - 发奖前查 `reward_record`，已有 SUCCESS 则跳过
   - 失败时记录 FAILED + error_message，不阻塞任务实例（可重试）
   - 替换 `PointRewardHandler` / `CouponRewardHandler` / `BadgeRewardHandler` 中的占位逻辑

3. **单元测试 `RewardServiceTest`**
   - 幂等：重复发奖只执行一次
   - 失败重试
   - 多奖励类型路由

**完成标准**：`mvn test` 全过，重复触发 REWARD 不重复发奖

---

### P0-4：端到端集成测试

**文件范围**：`backend/src/test/java/com/marketing/task/`

**步骤**：

1. 创建 `TaskLifecycleIntegrationTest`，使用 `@SpringBootTest` + H2 内存库
2. 覆盖场景：
   - 每日签到全链路（PASSIVE → CLICK → REWARD）
   - 问卷回调全链路（CALLBACK → REWARD）
   - 阅读进度全链路（PROGRESS → REWARD）
   - 互斥组校验
   - 过滤器：省份/等级过滤
   - 快照：发布后重新编辑，旧实例读旧快照

**完成标准**：至少 3 个集成测试场景通过，`mvn verify` 全过

---

### P0-5：异常码与错误文案标准化

**文件范围**：`backend/src/main/java/com/marketing/task/common/`

**步骤**：

1. 扩展 `ErrorCode` 枚举，增加业务子码（如 `TASK_NOT_FOUND(404, "TASK_001", "任务不存在")`）
2. 遍历所有 `throw new BusinessException("...")`，替换为对应 ErrorCode
3. 增强 `GlobalExceptionHandler`：覆盖 `MethodArgumentNotValidException`, `BindException`, `NoResourceFoundException`, `AccessDeniedException`

**完成标准**：所有异常使用 ErrorCode，Handler 覆盖 5+ 异常类型

---

### P1-1：CRON/MONTHLY 调度

**步骤**：

1. 新建 `TaskScheduler` 组件，`@Scheduled(cron="0 */5 * * * ?")` 每 5 分钟扫描
2. 为 PUBLISHED 且 period_type IN (CRON, MONTHLY) 的任务预创建实例
3. 不重复创建已有实例

---

### P1-2：名单过滤

**步骤**：

1. 新建 `list_data` 表（list_type, list_key, user_id）
2. 修改 `FilterExpressionEngine` 中 `inAllowlist()` / `inDenylist()` 实现，查表

---

### P1-3：平台适配器完善

**步骤**：在 `ClientTaskController.detail()` 中调用 `PlatformAdapterRegistry`，合并 step + stepPlatform → 前端渲染模型

---

### P2 ~ P3

| 任务 | 关键产出 |
|---|---|
| Admin 任务预览 | `POST /api/admin/task/{id}/preview` |
| 实例详情页 | `GET /api/admin/instance/{id}` + admin-web 详情页 |
| Admin 拖拽排序 | 步骤拖拽排序、复制任务、版本对比 |
| 监控指标 | 曝光/参与/完成/发奖指标 |
| 审计日志 | 配置变更 diff |
| OpenAPI 类型生成 | 脚本生成前端 TS 类型 |
| CI 配置 | `.github/workflows/ci.yml` |

## 4. 执行规则

### 4.1 质量门禁（每项任务必须全部通过）

- [ ] `mvn -f backend/pom.xml compile` 通过
- [ ] `mvn -f backend/pom.xml test` 全部通过（60+ tests）
- [ ] 新增代码有对应单元测试
- [ ] `npm --prefix admin-web run build` 通过
- [ ] `npm --prefix client-web run build` 通过
- [ ] 相关文档已更新（至少 roadmap + release-notes）

### 4.2 提交节奏

- 每完成一个 P0/P1 子任务所有步骤后提交
- 提交格式遵循 `docs/conventions/git.md`
- 提交前确认全部质量门禁通过
- 一次提交不混合多个不相关任务

### 4.3 文档维护

每完成一个 P0/P1 大项后更新：
- `docs/current-system-roadmap.md` — 标记完成，更新限制表
- `docs/spec/current/release-notes.md` — 新增版本说明
- `docs/spec/current/api.md` — 如有新端点
- `docs/spec/current/sql.md` — 如有新表/迁移

### 4.4 阻塞处理

任务无法继续时（缺少外部依赖、需要用户决策）：
- 在 `docs/current-system-roadmap.md` 限制表记录阻塞原因
- 跳到下一个非阻塞任务
- 不要反复重试同一个阻塞任务

## 5. 关键文件速查

| 类别 | 路径 |
|---|---|
| 任务服务 | `backend/src/main/java/com/marketing/task/service/task/TaskService.java` |
| 步骤推进引擎 | `backend/src/main/java/com/marketing/task/service/step/StepAdvanceEngine.java` |
| 奖励服务 | `backend/src/main/java/com/marketing/task/service/reward/RewardService.java` |
| 缓存服务 | `backend/src/main/java/com/marketing/task/service/task/TaskDefinitionCacheService.java` |
| 过滤引擎 | `backend/src/main/java/com/marketing/task/service/filter/FilterEvaluator.java` |
| 鉴权拦截器 | `backend/src/main/java/com/marketing/task/interceptor/AdminAuthInterceptor.java` |
| Client Controller | `backend/src/main/java/com/marketing/task/controller/client/ClientTaskController.java` |
| Admin Controller | `backend/src/main/java/com/marketing/task/controller/admin/AdminTaskController.java` |
| Internal Controller | `backend/src/main/java/com/marketing/task/controller/internal/InternalCallbackController.java` |
| Flyway 迁移 | `backend/src/main/resources/db/migration/` |
| Admin 路由 | `admin-web/src/router/index.ts` |
| Client 路由 | `client-web/src/router/index.ts` |

## 6. 禁止事项

- 跳过质量门禁直接提交
- force push 或修改 git config
- 删除或修改 `.claude/settings.local.json`
- 提交 `tsconfig.tsbuildinfo` 等构建产物
- 在设计未确认时开始大规模重构
- 缺少外部依赖时继续阻塞任务
- 引入未经项目使用的第三方库
