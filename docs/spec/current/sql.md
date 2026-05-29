# 当前版本 SQL 文档

最后更新：2026-05-28

当前版本：v0.5.1

## 迁移文件索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_task_core.sql` | 核心任务表 |
| V2 | `V2__seed_demo_data.sql` | 示例任务数据 |
| V3 | `V3__auth_tables.sql` | admin_user, client_user |
| V4 | `V4__task_snapshot.sql` | task_definition_snapshot 版本快照 |
| V5 | `V5__reward_record.sql` | 奖励流水表 |
| V6 | `V6__prize_module.sql` | 奖品子域四表 + prize 字段 |
| V7 | `V7__list_data.sql` | allowlist/denylist 数据表 |
| V8 | `V8__mutex_group.sql` | 互斥组独立表 |
| V9 | `V9__step_platform_action.sql` | 步骤平台 action_type/action_config |
| V10 | `V10__event_log_and_metrics.sql` | 事件日志 + 指标聚合表 |
| V11 | `V11__task_gray_config.sql` | 任务灰度配置 |
| V12 | `V12__mutex_cross_cycle.sql` | 跨周期互斥 |
| V13 | `V13__operation_log.sql` | 操作审计日志 |
| V14 | `V14__step_branching.sql` | 步骤条件分支表 |
| V15 | `V15__task_scheduled_publish.sql` | 定时发布字段 |
| V16 | `V16__task_soft_delete.sql` | 软删除字段 |
| V17 | `V17__signin_module.sql` | 签到模块：signin_config, signin_record, point_account, point_transaction |
| V18 | `V18__activity_module.sql` | 活动模块：activity, activity_display_rule, activity_stats + 子模块 activity_code 关联 |
| V19 | `V19__activity_code_unify.sql` | activity_display_rule 从 activity_id 迁移到 activity_code |
| V20 | `V20__prize_activity_code_unify.sql` | prize/prize_record 删除 activity_id 列 |
| V21 | `V21__activity_stats_code.sql` | activity_stats 从 activity_id 迁移到 activity_code |

---

## V15 — task 定时发布字段 (v0.5.0)

```sql
ALTER TABLE task ADD COLUMN scheduled_publish_at DATETIME NULL COMMENT '定时发布时间';
```

| 字段 | 类型 | 说明 |
|---|---|---|
| scheduled_publish_at | DATETIME | 定时发布时间，NULL 表示未设置定时 |

关联变更：
- `TaskStatus` 枚举新增 `SCHEDULED`
- `TaskPublishScheduler` 每分钟扫描自动发布
- 状态流转：`DRAFT → SCHEDULED → PUBLISHED`，`SCHEDULED → DRAFT`（取消）

---

## V16 — task 软删除字段 (v0.5.0)

```sql
ALTER TABLE task ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0=正常 1=已删除';
```

| 字段 | 类型 | 说明 |
|---|---|---|
| deleted | TINYINT | 逻辑删除标记：0=正常，1=已删除 |

行为变更：
- `DELETE /api/admin/task/{id}` 改为软删除
- 查询默认 `deleted=0`，支持 `status=DELETED` 查看已删除任务

---

## V14 — task_step_transition 表 (v0.4.0)

```sql
CREATE TABLE task_step_transition (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    step_id         BIGINT        NOT NULL,
    target_step_id  BIGINT        NOT NULL,
    condition_expr  VARCHAR(1024) NULL,
    priority        INT           NOT NULL DEFAULT 0,
    description     VARCHAR(256)  NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_step_priority (step_id, priority),
    INDEX idx_transition_step (step_id),
    INDEX idx_transition_target (target_step_id)
);
```

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| step_id | BIGINT | 来源步骤 ID，关联 task_step.id |
| target_step_id | BIGINT | 目标步骤 ID，关联 task_step.id |
| condition_expr | VARCHAR(1024) | QLExpress 条件表达式，NULL = 默认分支 |
| priority | INT | 优先级（升序评估，越小越优先），同 step 唯一 |
| description | VARCHAR(256) | 分支描述 |

约束：
- `(step_id, priority)` 唯一
- `target_step_id` 不可等于 `step_id`（应用层校验）
- 目标步骤 `seq` 必须 > 来源步骤 `seq`（应用层 DAG 约束）
- `condition_expr` 为 NULL 表示默认分支

路由逻辑：
1. `StepAdvanceEngine.resolveNextSeq()` 按 `priority ASC` 排序评估
2. 首个匹配 → 返回 `target_step_id` 对应的 `seq`
3. 全部不匹配 → fallback `step.seq + 1`
