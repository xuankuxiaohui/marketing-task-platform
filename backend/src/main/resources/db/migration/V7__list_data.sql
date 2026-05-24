-- V7: Allowlist / denylist data table

CREATE TABLE list_data (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  list_type VARCHAR(16) NOT NULL COMMENT 'ALLOWLIST / DENYLIST',
  list_key VARCHAR(64) NOT NULL COMMENT '名单标识',
  user_id VARCHAR(64) NOT NULL COMMENT '用户ID',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_list_entry (list_type, list_key, user_id),
  INDEX idx_list_query (list_type, list_key)
);
