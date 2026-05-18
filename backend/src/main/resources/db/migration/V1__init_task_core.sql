CREATE TABLE task (
    id BIGINT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    period_type VARCHAR(16) NOT NULL,
    cron_expr VARCHAR(64) NULL,
    special_cycle_key VARCHAR(64) NULL,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 0,
    mutex_group_key VARCHAR(64) NULL,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_code (code),
    KEY idx_task_status_time (status, start_time, end_time),
    KEY idx_task_mutex_group (mutex_group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_step (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    seq INT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    type VARCHAR(16) NOT NULL,
    target_value INT NULL,
    callback_event_key VARCHAR(64) NULL,
    reward_config_json JSON NULL,
    flow_desc VARCHAR(512) NULL,
    extra_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_step_code (task_id, code),
    UNIQUE KEY uk_task_step_seq (task_id, seq),
    KEY idx_task_step_task (task_id),
    KEY idx_task_step_event (callback_event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_step_platform (
    id BIGINT PRIMARY KEY,
    step_id BIGINT NOT NULL,
    platform VARCHAR(16) NOT NULL,
    button_text VARCHAR(64) NULL,
    jump_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    jump_target VARCHAR(512) NULL,
    extra_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_step_platform (step_id, platform),
    KEY idx_task_step_platform_step (step_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_filter (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    seq INT NOT NULL,
    expression VARCHAR(1024) NOT NULL,
    logic_op VARCHAR(8) NOT NULL DEFAULT 'AND',
    description VARCHAR(256) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_filter_seq (task_id, seq),
    KEY idx_task_filter_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_platform (
    id BIGINT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    platform VARCHAR(16) NOT NULL,
    flow_desc VARCHAR(512) NULL,
    button_text VARCHAR(64) NULL,
    jump_uri VARCHAR(512) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_platform (task_id, platform),
    KEY idx_task_platform_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
