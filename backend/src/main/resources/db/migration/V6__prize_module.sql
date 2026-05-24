-- V6: Prize module tables + task_step prize columns

ALTER TABLE task_step
  ADD COLUMN prize_id BIGINT NULL AFTER reward_config_json,
  ADD COLUMN prize_quantity INT DEFAULT 1 AFTER prize_id;

CREATE TABLE prize (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    handler_bean VARCHAR(64) NOT NULL,
    params_json JSON NOT NULL,
    total_stock INT NULL,
    monthly_stock INT NULL,
    daily_stock INT NULL,
    user_total_limit INT NULL,
    user_monthly_limit INT NULL,
    user_daily_limit INT NULL,
    limits_json JSON NULL,
    activity_id BIGINT NULL,
    group_key VARCHAR(64) NULL,
    group_strategy VARCHAR(32) NULL,
    group_weight INT DEFAULT 1,
    icon_url VARCHAR(512) NULL,
    claim_zone_image_url VARCHAR(512) NULL,
    auto_grant TINYINT(1) DEFAULT 0,
    claim_expire_type VARCHAR(16) NOT NULL,
    claim_expire_value VARCHAR(64) NOT NULL,
    max_retry INT DEFAULT 3,
    enabled TINYINT(1) DEFAULT 1,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_activity_group (activity_id, group_key),
    INDEX idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prize_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
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
    expire_time DATETIME NOT NULL,
    retry_count INT DEFAULT 0,
    error_message VARCHAR(1024) NULL,
    external_trade_no VARCHAR(128) NULL,
    won_at DATETIME NOT NULL,
    claimed_at DATETIME NULL,
    granted_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotent (idempotent_key),
    INDEX idx_user_status (user_id, status),
    INDEX idx_expire (status, expire_time),
    INDEX idx_prize_id (prize_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prize_claim_lock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id BIGINT UNIQUE NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE prize_inventory_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    prize_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_prize_record (prize_id, record_id),
    INDEX idx_prize_created (prize_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
