# v0.4.0 SQL 文档

最后更新：2026-05-26

## 迁移文件索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V14 | `V14__step_branching.sql` | 步骤条件分支表 |

## V14 — task_step_transition 表

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

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| step_id | BIGINT | 来源步骤 ID，关联 task_step.id |
| target_step_id | BIGINT | 目标步骤 ID，关联 task_step.id |
| condition_expr | VARCHAR(1024) | QLExpress 条件表达式，NULL = 默认分支 |
| priority | INT | 优先级（升序评估，越小越优先），同 step 唯一 |
| description | VARCHAR(256) | 分支描述 |

### 约束

- `(step_id, priority)` 唯一 — 同一来源步骤不可有重复优先级
- `target_step_id` 不可等于 `step_id`（应用层校验）
- 目标步骤 `seq` 必须 > 来源步骤 `seq`（应用层 DAG 约束，禁止回退）
- `condition_expr` 为 NULL 表示默认分支（全不匹配时走此分支）

### 路由逻辑

1. `StepAdvanceEngine.resolveNextSeq(step, instance)` 查询 `step_id` 的所有 transitions，按 `priority ASC` 排序
2. 依次用 `FilterExpressionEngine.evaluate(conditionExpr, context)` 评估
3. 首个匹配 → 返回 `target_step_id` 对应的 `seq`
4. 全部不匹配 → fallback `step.seq + 1`（向后兼容无 transition 的步骤）
5. PASSIVE/REWARD 步骤也走 `resolveNextSeq`，保证分支一致性
