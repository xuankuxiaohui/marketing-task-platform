# v0.5.0 发布说明

最后更新：2026-05-28

## 概述

v0.5.0 聚焦运营效率与发布能力增强：批量发布/下线、定时发布、复制任务增强、DAG 可视化步骤编辑器、多标签页导航、任务软删除、异常码标准化。

总测试数：171+（153 unit + 18 integration），全部通过。

---

## P4-1：Copy 功能增强

### 动机

原有复制任务端点不支持自定义名称/code，前端无交互弹窗。

### 变更

- **后端**：`POST /api/admin/task/{id}/copy` 新增 `TaskCopyRequest` 参数（可选 `name`、`code`）
  - 不传参时自动填充：名称 = `"{原名} (副本)"`，code = `"{原code}_copy_{timestamp}"`
  - 复制内容包括：主体、步骤、过滤器、平台配置、步骤平台配置、分支配置、互斥组、灰度配置
  - 新任务状态为 DRAFT，version=0
- **前端**：复制弹窗，预填默认名称，允许修改后提交

### 新增文件

| 文件 | 说明 |
|---|---|
| `backend/.../domain/dto/TaskCopyRequest.java` | 复制请求 DTO |

---

## P4-2：批量发布/下线

### 动机

任务列表逐个发布/下线效率低，运营需要批量操作能力。

### 变更

- **后端**：
  - `POST /api/admin/task/batch-publish` — 批量发布
  - `POST /api/admin/task/batch-offline` — 批量下线
  - 独立执行语义：逐个调用 `publish()`/`offline()`，单个失败不阻塞其他
  - 返回成功/失败明细
- **前端**：任务列表多选 checkbox + 批量操作工具栏 + 结果反馈弹窗

### 新增文件

| 文件 | 说明 |
|---|---|
| `backend/.../domain/dto/BatchTaskRequest.java` | 批量请求 DTO |
| `backend/.../domain/dto/BatchTaskResult.java` | 批量结果 DTO（含 FailedItem） |

### BatchTaskResult 结构

```json
{
  "success": [1, 2, 3],
  "failed": [
    { "id": 4, "reason": "任务状态不合法" }
  ]
}
```

---

## P4-3：定时发布

### 动机

运营需要在指定时间自动发布任务，而非手动操作。

### 变更

- **数据库**：`task` 表新增 `scheduled_publish_at` 列 (Flyway V15)
- **枚举**：`TaskStatus` 新增 `SCHEDULED` 状态
- **后端**：
  - `POST /api/admin/task/{id}/schedule-publish` — 设置定时发布
  - `POST /api/admin/task/{id}/cancel-schedule` — 取消定时发布
  - `TaskPublishScheduler` 每分钟扫描 `SCHEDULED` 且 `scheduled_publish_at <= now` 的任务，自动调用 `publish()`
- **前端**：定时发布弹窗（选择日期时间）、取消定时功能、任务列表显示定时状态

### 新增文件

| 文件 | 说明 |
|---|---|
| `backend/.../domain/dto/SchedulePublishRequest.java` | 定时发布请求 DTO |
| `backend/.../service/task/TaskPublishScheduler.java` | 定时发布调度器 |
| `backend/.../db/migration/V15__task_scheduled_publish.sql` | DDL |

### 调度逻辑

```java
@Scheduled(cron = "0 * * * * ?")  // 每分钟执行
public void scanAndPublishScheduledTasks() {
    // 查询 status=SCHEDULED 且 scheduled_publish_at <= now
    // 逐个调用 taskService.publish(id)
    // 单个失败不影响其他任务
}
```

---

## DAG 可视化步骤编辑器

### 动机

原有步骤配置为表单列表方式，分支配置需要手动输入步骤 code，不够直观。需要可视化的 DAG 编辑器。

### 变更

- **前端**：基于 VueFlow 实现可视化步骤编辑器
  - FlowCanvas.vue — VueFlow 画布容器
  - FlowEditor.vue — 编辑器主组件
  - NodePalette.vue — 步骤节点面板（拖拽创建）
  - PropertyPanel.vue — 选中节点/边的属性编辑面板
  - StepNode.vue — 自定义步骤节点渲染
  - TransitionEdge.vue — 自定义分支边渲染
  - useFlowEditor.ts — 编辑器状态管理 composable
  - useNodePalette.ts — 节点面板逻辑
  - useValidation.ts — DAG 校验逻辑
  - localStorage 持久化节点位置
  - DAG 布局算法自动排列
  - 键盘快捷键支持

### 新增依赖

- `@vue-flow/core`、`@vue-flow/background`、`@vue-flow/controls`

---

## 多标签页导航

### 动机

管理端原有路由为单页面切换，频繁在任务列表和任务编辑之间跳转时需要反复加载。

### 变更

- TabBar.vue — 标签页栏组件，支持关闭/关闭其他/关闭左侧
- tab.ts (Pinia store) — 标签页状态管理
- useTabKeepAlive.ts — keep-alive 缓存管理
- useTabTitle.ts — 标签页标题管理
- 路由切换时自动添加标签，keep-alive 缓存已打开页面

---

## 任务软删除

### 动机

硬删除任务会丢失关联数据（实例、快照、指标），需要软删除能力。

### 变更

- **数据库**：`task` 表新增 `deleted` 列 (Flyway V16)，`TINYINT NOT NULL DEFAULT 0`
- **后端**：
  - `DELETE /api/admin/task/{id}` 改为软删除（设置 `deleted=1`）
  - 查询默认过滤 `deleted=0`，支持 `status=DELETED` 查看已删除任务
  - `TaskMapper` 新增 `selectDeletedPage()` 自定义查询
- **前端**：任务列表支持查看已删除任务（状态筛选 DELETED）

### 新增文件

| 文件 | 说明 |
|---|---|
| `backend/.../db/migration/V16__task_soft_delete.sql` | DDL |

---

## 异常码标准化

### 动机

原有 ErrorCode 枚举仅 5 个，错误信息散落在各处 throw 中，HTTP 状态码与业务码混在一起。

### 变更

- `ErrorCode` 枚举扩展为 28 个，新增 `subCode` 业务子码
- `BusinessException` 支持 HTTP status 与业务错误码分离
- `GlobalExceptionHandler` 统一异常处理增强
- 全量替换 31 处 `throw new BusinessException` 使用具体 ErrorCode
- 不再泄露 `ex.getMessage()`，只返回通用"服务器内部错误"

---

## CI Java 21

### 变更

- `.github/workflows/ci.yml` Java 版本从 17 升级到 21

---

## 升级说明

### 从 v0.4.x 升级

1. **数据库**：执行 Flyway V15（定时发布字段）、V16（软删除字段）
2. **依赖**：`pnpm install` 更新前端 VueFlow 依赖
3. **配置**：无新增配置项

### 不兼容变更

- `DELETE /api/admin/task/{id}` 行为从硬删除改为软删除
- `TaskStatus` 枚举新增 `SCHEDULED`，如有前端/外部系统硬编码状态列表需适配

---

## 已知限制

| 限制 | 说明 |
|---|---|
| 定时发布精度 | 每分钟扫描一次，实际发布时间可能延迟最多 1 分钟 |
| 批量操作上限 | 未限制单次批量任务数量，大量任务时响应可能较慢 |
| 软删除恢复 | 暂不支持恢复已删除任务 |
