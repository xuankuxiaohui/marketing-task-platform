CREATE TABLE operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator_id VARCHAR(64) NOT NULL COMMENT '操作人ID',
    operator_name VARCHAR(128) COMMENT '操作人名称',
    operation_type VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/PUBLISH/OFFLINE/DELETE',
    target_type VARCHAR(32) NOT NULL COMMENT 'TASK/PRIZE/MUTEX_GROUP',
    target_id BIGINT COMMENT '目标ID',
    target_name VARCHAR(256) COMMENT '目标名称',
    detail JSON COMMENT '变更详情(JSON)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target (target_type, target_id),
    INDEX idx_operator (operator_id),
    INDEX idx_created_at (created_at)
);
