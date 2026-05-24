CREATE TABLE reward_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id BIGINT NOT NULL,
    step_id BIGINT NOT NULL,
    reward_type VARCHAR(32) NOT NULL,
    reward_config_json JSON NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    idempotent_key VARCHAR(128) NOT NULL,
    error_message VARCHAR(1024) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_instance_step (instance_id, step_id),
    INDEX idx_instance_id (instance_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
