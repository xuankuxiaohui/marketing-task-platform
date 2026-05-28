# v0.5.0 SQL 文档

最后更新：2026-05-28

## 迁移文件索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V15 | `V15__task_scheduled_publish.sql` | 任务定时发布字段 |
| V16 | `V16__task_soft_delete.sql` | 任务软删除字段 |

---

## V15 — task 定时发布字段

```sql
ALTER TABLE task ADD COLUMN scheduled_publish_at DATETIME NULL COMMENT '定时发布时间';
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| scheduled_publish_at | DATETIME | 定时发布时间，NULL 表示未设置定时 |

### 关联变更

- `TaskStatus` 枚举新增 `SCHEDULED` 状态
- 任务设置定时发布后状态变为 `SCHEDULED`
- `TaskPublishScheduler` 每分钟扫描 `status=SCHEDULED AND scheduled_publish_at <= now()` 的任务并自动发布
- 取消定时发布后状态回退为 `DRAFT`

### 状态流转

```
DRAFT → schedule-publish → SCHEDULED → (到达时间) → PUBLISHED
SCHEDULED → cancel-schedule → DRAFT
```

---

## V16 — task 软删除字段

```sql
ALTER TABLE task ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除';
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| deleted | TINYINT | 逻辑删除标记：0=正常，1=已删除 |

### 行为变更

- `DELETE /api/admin/task/{id}` 从硬删除改为软删除（`SET deleted=1`）
- 所有任务查询默认添加 `deleted=0` 过滤条件
- 支持 `status=DELETED` 查询已删除任务（`deleted=1`）
- `TaskMapper` 新增 `selectDeletedPage()` 自定义查询方法
- 已删除任务的关联数据（实例、快照、指标）保留不删除
