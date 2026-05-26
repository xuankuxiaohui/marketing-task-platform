-- H2-compatible schema for integration tests
-- Covers V1-V6 migrations

CREATE TABLE IF NOT EXISTS task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    period_type VARCHAR(16) NOT NULL,
    cron_expr VARCHAR(64) NULL,
    special_cycle_key VARCHAR(64) NULL,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    version INT NOT NULL DEFAULT 0,
    mutex_group_id BIGINT NULL,
    gray_type VARCHAR(16) DEFAULT 'NONE',
    gray_config JSON NULL,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_code (code),
    INDEX idx_task_status_time (status, start_time, end_time),
    INDEX idx_task_mutex_group_id (mutex_group_id)
);

CREATE TABLE IF NOT EXISTS task_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    seq INT NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    type VARCHAR(16) NOT NULL,
    target_value INT NULL,
    callback_event_key VARCHAR(64) NULL,
    reward_config_json TEXT NULL,
    prize_id BIGINT NULL,
    prize_quantity INT DEFAULT 1,
    flow_desc VARCHAR(512) NULL,
    extra_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_step_code (task_id, code),
    UNIQUE KEY uk_task_step_seq (task_id, seq),
    INDEX idx_task_step_task (task_id),
    INDEX idx_task_step_event (callback_event_key)
);

CREATE TABLE IF NOT EXISTS task_step_platform (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    step_id BIGINT NOT NULL,
    platform VARCHAR(16) NOT NULL,
    button_text VARCHAR(64) NULL,
    jump_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    jump_target VARCHAR(512) NULL,
    action_type VARCHAR(32) NULL,
    action_config VARCHAR(512) NULL,
    extra_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_step_platform (step_id, platform),
    INDEX idx_task_step_platform_step (step_id)
);

CREATE TABLE IF NOT EXISTS task_filter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    seq INT NOT NULL,
    expression VARCHAR(1024) NOT NULL,
    logic_op VARCHAR(8) NOT NULL DEFAULT 'AND',
    description VARCHAR(256) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_filter_seq (task_id, seq),
    INDEX idx_task_filter_task (task_id)
);

CREATE TABLE IF NOT EXISTS task_platform (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    platform VARCHAR(16) NOT NULL,
    flow_desc VARCHAR(512) NULL,
    button_text VARCHAR(64) NULL,
    jump_uri VARCHAR(512) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_platform (task_id, platform),
    INDEX idx_task_platform_task (task_id)
);

CREATE TABLE IF NOT EXISTS user_task_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    task_id BIGINT NOT NULL,
    task_version INT NOT NULL,
    cycle_key VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    current_step_seq INT NOT NULL DEFAULT 1,
    start_time TIMESTAMP NULL,
    complete_time TIMESTAMP NULL,
    reward_time TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_task_cycle (user_id, task_id, cycle_key),
    INDEX idx_instance_user_status (user_id, status),
    INDEX idx_instance_task_cycle (task_id, cycle_key)
);

CREATE TABLE IF NOT EXISTS user_task_step_progress (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    progress_value INT NOT NULL DEFAULT 0,
    complete_time TIMESTAMP NULL,
    extra_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_instance_step (instance_id, step_id),
    INDEX idx_step_progress_instance (instance_id),
    INDEX idx_step_progress_step (step_id)
);

CREATE TABLE IF NOT EXISTS task_definition_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    version INT NOT NULL,
    snapshot_json CLOB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_version (task_id, version),
    INDEX idx_task_id (task_id)
);

CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    nickname VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_admin_user_username (username)
);

CREATE TABLE IF NOT EXISTS client_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    nickname VARCHAR(64),
    province VARCHAR(32),
    role VARCHAR(32),
    tags VARCHAR(512),
    org_id VARCHAR(64),
    level INT DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_client_user_username (username)
);

CREATE TABLE IF NOT EXISTS reward_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    instance_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    reward_type VARCHAR(32) NOT NULL,
    reward_config_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    idempotent_key VARCHAR(128) NOT NULL,
    error_message VARCHAR(1024) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reward_record (instance_id, step_id)
);

CREATE TABLE IF NOT EXISTS prize (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    handler_bean VARCHAR(64) NOT NULL,
    params_json TEXT NOT NULL,
    total_stock INT NULL,
    monthly_stock INT NULL,
    daily_stock INT NULL,
    user_total_limit INT NULL,
    user_monthly_limit INT NULL,
    user_daily_limit INT NULL,
    limits_json TEXT NULL,
    activity_id BIGINT NULL,
    group_key VARCHAR(64) NULL,
    group_strategy VARCHAR(32) NULL,
    group_weight INT DEFAULT 1,
    icon_url VARCHAR(512) NULL,
    claim_zone_image_url VARCHAR(512) NULL,
    auto_grant BOOLEAN DEFAULT FALSE,
    claim_expire_type VARCHAR(16) NOT NULL,
    claim_expire_value VARCHAR(64) NOT NULL,
    max_retry INT DEFAULT 3,
    enabled BOOLEAN DEFAULT TRUE,
    start_time TIMESTAMP NULL,
    end_time TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_group (activity_id, group_key),
    INDEX idx_type (type)
);

CREATE TABLE IF NOT EXISTS prize_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    instance_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    prize_id BIGINT NOT NULL,
    quantity INT DEFAULT 1,
    idempotent_key VARCHAR(128) NOT NULL,
    prize_type VARCHAR(32) NOT NULL,
    prize_name VARCHAR(128) NOT NULL,
    prize_icon VARCHAR(512) NULL,
    prize_image VARCHAR(512) NULL,
    prize_params_json JSON NOT NULL,
    activity_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    expire_time TIMESTAMP NOT NULL,
    retry_count INT DEFAULT 0,
    error_message VARCHAR(1024) NULL,
    external_trade_no VARCHAR(128) NULL,
    won_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP NULL,
    granted_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotent (idempotent_key),
    INDEX idx_user_status (user_id, status),
    INDEX idx_expire (status, expire_time),
    INDEX idx_prize_id (prize_id)
);

CREATE TABLE IF NOT EXISTS prize_claim_lock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    record_id BIGINT UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prize_inventory_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prize_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prize_record (prize_id, record_id),
    INDEX idx_prize_created (prize_id, created_at)
);

-- V8: mutex_group
CREATE TABLE IF NOT EXISTS mutex_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(256) NULL,
    scope VARCHAR(32) NOT NULL DEFAULT 'SAME_CYCLE',
    cross_cycle TINYINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- V10: event_log and task_metrics
CREATE TABLE IF NOT EXISTS event_log (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    event_type    VARCHAR(32)  NOT NULL,
    task_id       BIGINT       DEFAULT NULL,
    instance_id   BIGINT       DEFAULT NULL,
    step_id       BIGINT       DEFAULT NULL,
    user_id       VARCHAR(64)  DEFAULT NULL,
    platform      VARCHAR(16)  DEFAULT NULL,
    event_data    JSON         DEFAULT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_el_event_type  (event_type),
    INDEX idx_el_task_id     (task_id),
    INDEX idx_el_instance_id (instance_id),
    INDEX idx_el_created_at  (created_at)
);

CREATE TABLE IF NOT EXISTS task_metrics (
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

-- V13: operation_log
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id VARCHAR(64) NOT NULL,
    operator_name VARCHAR(128),
    operation_type VARCHAR(32) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT,
    target_name VARCHAR(256),
    detail JSON,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ol_target (target_type, target_id),
    INDEX idx_ol_operator (operator_id),
    INDEX idx_ol_created_at (created_at)
);

-- V14: step branching
CREATE TABLE IF NOT EXISTS task_step_transition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    step_id BIGINT NOT NULL,
    target_step_id BIGINT NOT NULL,
    condition_expr VARCHAR(1024) NULL,
    priority INT NOT NULL DEFAULT 0,
    description VARCHAR(256) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_step_priority (step_id, priority),
    INDEX idx_transition_step (step_id),
    INDEX idx_transition_target (target_step_id)
);

-- V7: list_data
CREATE TABLE IF NOT EXISTS list_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    list_type VARCHAR(16) NOT NULL,
    list_key VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_list_entry (list_type, list_key, user_id),
    INDEX idx_list_query (list_type, list_key)
);
