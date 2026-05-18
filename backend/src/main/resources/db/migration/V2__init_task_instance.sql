CREATE TABLE user_task_instance (
    id BIGINT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    task_id BIGINT NOT NULL,
    task_version INT NOT NULL,
    cycle_key VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    current_step_seq INT NOT NULL DEFAULT 1,
    start_time DATETIME NULL,
    complete_time DATETIME NULL,
    reward_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_task_cycle (user_id, task_id, cycle_key),
    KEY idx_instance_user_status (user_id, status),
    KEY idx_instance_task_cycle (task_id, cycle_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_task_step_progress (
    id BIGINT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    progress_value INT NOT NULL DEFAULT 0,
    complete_time DATETIME NULL,
    extra_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_instance_step (instance_id, step_id),
    KEY idx_step_progress_instance (instance_id),
    KEY idx_step_progress_step (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
