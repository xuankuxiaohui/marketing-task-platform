-- V22: Activity participation log

CREATE TABLE participation_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    activity_code   VARCHAR(64) NOT NULL COMMENT '活动编码',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    client_ip       VARCHAR(64) NULL COMMENT '客户端IP',
    checker_result  VARCHAR(16) NOT NULL DEFAULT 'PASS' COMMENT 'PASS/FAIL',
    fail_code       VARCHAR(64) NULL COMMENT '失败编码',
    fail_message    VARCHAR(256) NULL COMMENT '失败原因',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_activity_code (activity_code),
    INDEX idx_user_activity (user_id, activity_code),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动参与记录';
