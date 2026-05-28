# 当前版本发布说明

最后更新：2026-05-28

当前版本：v0.5.1

---

## v0.5.1 (2026-05-27)

### 用户管理

- `AdminUserController` + `ClientUserController`（`/api/admin/admin-users`, `/api/admin/client-users`）
- 分页查询、重置密码、启用/停用、踢下线四个管理操作
- Sa-Token `kickout()` 强制踢下线，停用时自动踢
- ErrorCode 新增 `USER_NOT_FOUND`、`CANNOT_KICK_SELF`
- 前端 AdminUserList + ClientUserList 页面，侧边栏"用户管理"子菜单

### 互斥组移除任务关联

- `DELETE /api/admin/mutex-groups/{groupId}/tasks/{taskId}`
- 将任务的 `mutex_group_id` 设为 NULL，清除任务定义缓存
- 互斥组详情页新增移除关联按钮

---

## v0.5.0 (2026-05-27)

### P4-1：Copy 功能增强

- `POST /api/admin/task/{id}/copy` 新增 `TaskCopyRequest` 参数（可选 `name`、`code`）
- 不传参时自动填充：名称 = `"{原名} (副本)"`，code = `"{原code}_copy_{timestamp}"`
- 复制范围包括：主体、步骤、过滤器、平台配置、步骤平台配置、分支配置
- 前端复制弹窗，预填默认名称，允许修改后提交

### P4-2：批量发布/下线

- `POST /api/admin/task/batch-publish` + `batch-offline`
- 独立执行语义，返回成功/失败明细
- 前端多选 checkbox + 批量操作工具栏 + 结果反馈弹窗

### P4-3：定时发布

- `task` 表新增 `scheduled_publish_at` 列 (Flyway V15)
- `TaskStatus` 枚举新增 `SCHEDULED`
- `TaskPublishScheduler` 每分钟扫描并自动发布
- `POST /api/admin/task/{id}/schedule-publish` + `cancel-schedule`
- 前端定时发布弹窗 + 取消定时功能

### DAG 可视化步骤编辑器

- VueFlow 画布 + 拖拽节点创建 + 属性面板
- 自定义节点/边渲染 + DAG 布局算法
- 键盘快捷键 + localStorage 位置持久化

### 多标签页导航

- TabBar 组件 + keep-alive 缓存
- 支持关闭/关闭其他/关闭左侧

### 任务软删除

- `task` 表新增 `deleted` 列 (Flyway V16)
- `DELETE /api/admin/task/{id}` 改为软删除
- 查询支持 `status=DELETED` 查看已删除任务

### 异常码标准化

- ErrorCode 枚举扩展为 28 个，新增 subCode 业务子码
- BusinessException 支持 HTTP status 与业务错误码分离
- 全量替换 31 处 throw 使用具体 ErrorCode

### CI Java 21

- `.github/workflows/ci.yml` Java 版本升级到 21

---

## v0.4.0 (2026-05-26)

### P1：条件分支

- `task_step_transition` 表 (Flyway V14)，支持步骤多分支路由
- `StepAdvanceEngine.resolveNextSeq()` 按 priority 评估 QLExpress condition_expr
- 首个匹配 → target_step_id；全不匹配 → fallback seq + 1
- DAG 约束：target_step_id 不可指向自身，目标 seq > 来源 seq
- 前端 StepsTab 分支配置 UI + 分支 badge 图示

### P2：Sa-Token 鉴权迁移

- Sa-Token 1.44.0 集成，`StpUtil` (admin) + `StpUserUtil` (client) 双账号模式
- SaInterceptor 注册，SaTokenUserContextBridge 桥接，mock 模式兼容
- Token 自动续期、并发登录控制、登出/失效、SSO/OAuth2 扩展点预留

### P3：工程化补齐

- 步骤平台配置纳入快照：TaskSnapshotDTO 新增 stepPlatforms
- HTTP 层 MockMvc 集成测试：AdminTaskControllerTest (4) + ClientTaskControllerTest (5)
- 前端类型刷新：admin-web 类型与后端 OpenAPI 对齐

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
