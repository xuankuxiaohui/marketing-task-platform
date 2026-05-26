CREATE TABLE task_step_transition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    step_id BIGINT NOT NULL,
    target_step_id BIGINT NOT NULL,
    condition_expr VARCHAR(1024) NULL,
    priority INT NOT NULL DEFAULT 0,
    description VARCHAR(256) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_step_priority (step_id, priority),
    KEY idx_transition_step (step_id),
    KEY idx_transition_target (target_step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
