-- V10: event_log and task_metrics tables
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
