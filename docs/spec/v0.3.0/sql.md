# v0.3.0 SQL 文档

最后更新：2026-05-24

## 迁移文件索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V10 | `V10__event_log_and_metrics.sql` | 事件日志 + 指标聚合表 |

## V10 — event_log 表

```sql
CREATE TABLE event_log (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    event_type    VARCHAR(32)  NOT NULL,
    task_id       BIGINT       DEFAULT NULL,
    instance_id   BIGINT       DEFAULT NULL,
    step_id       BIGINT       DEFAULT NULL,
    user_id       VARCHAR(64)  DEFAULT NULL,
    platform      VARCHAR(16)  DEFAULT NULL,
    event_data    JSON         DEFAULT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_event_type  (event_type),
    INDEX idx_task_id     (task_id),
    INDEX idx_instance_id (instance_id),
    INDEX idx_created_at  (created_at)
);
```

### 事件类型 (EventType 枚举)

| Event Type | 触发位置 | 携带数据 |
|---|---|---|
| `TASK_VIEWED` | `ClientTaskController.list()` | taskId, userId, platform |
| `INSTANCE_CREATED` | `TaskService.getOrCreateInstance()` | taskId, instanceId, userId |
| `STEP_COMPLETED` | `StepAdvanceEngine.completeStep()` | taskId, instanceId, stepId, stepType |
| `REWARD_TRIGGERED` | `RewardStepHandler.onStepEnter()` | taskId, instanceId, stepId, rewardType |
| `REWARD_SUCCESS` | `LogRewardService.reward()` 成功 | taskId, instanceId, stepId, rewardType |
| `REWARD_FAILURE` | `LogRewardService.reward()` 异常 | taskId, instanceId, stepId, error |
| `CLAIM_SUCCESS` | `ClaimService.claim()` 成功 | taskId, instanceId, prizeId, tradeNo |
| `FILTER_EVALUATED` | `FilterExpressionEngine.evaluate()` | taskId, userId, expression, result |

## V10 — task_metrics 表

```sql
CREATE TABLE task_metrics (
    id              BIGINT   AUTO_INCREMENT PRIMARY KEY,
    task_id         BIGINT   NOT NULL,
    metric_date     DATE     NOT NULL,
    views           BIGINT   DEFAULT 0,
    participants    BIGINT   DEFAULT 0,
    completions     BIGINT   DEFAULT 0,
    reward_success  BIGINT   DEFAULT 0,
    reward_failure  BIGINT   DEFAULT 0,
    avg_filter_ms   DOUBLE   DEFAULT 0,
    UNIQUE KEY uk_task_date (task_id, metric_date)
);
```

TaskMetricsScheduler 每 5 分钟从 event_log 聚合写入 task_metrics（ON DUPLICATE KEY UPDATE）。
