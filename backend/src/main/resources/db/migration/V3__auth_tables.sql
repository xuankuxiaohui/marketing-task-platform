CREATE TABLE admin_user (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            username VARCHAR(64) NOT NULL,
                            password_hash VARCHAR(256) NOT NULL,
                            nickname VARCHAR(64),
                            enabled TINYINT(1) NOT NULL DEFAULT 1,
                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                            UNIQUE KEY uk_admin_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- BCrypt hash for 'admin123'
INSERT INTO admin_user (username, password_hash, nickname, enabled)
VALUES ('admin', '$2a$10$4TUW9golP3ZUKbKT/F1qouGEqkmfgMvBsT5KSY9x8h/o/17sDXJoW', '系统管理员', 1);

CREATE TABLE client_user (
                             id BIGINT PRIMARY KEY AUTO_INCREMENT,
                             username VARCHAR(64) NOT NULL,
                             password_hash VARCHAR(256) NOT NULL,
                             nickname VARCHAR(64),
                             province VARCHAR(32),
                             role VARCHAR(32),
                             tags VARCHAR(512),
                             org_id VARCHAR(64),
                             level INT DEFAULT 0,
                             enabled TINYINT(1) NOT NULL DEFAULT 1,
                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                             UNIQUE KEY uk_client_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Test client user: demo / demo123
INSERT INTO client_user (username, password_hash, nickname, province, role, tags, org_id, level, enabled)
VALUES ('demo', '$2a$10$4TUW9golP3ZUKbKT/F1qouGEqkmfgMvBsT5KSY9x8h/o/17sDXJoW', '测试用户', 'BJ', 'vip', 'vip,active', 'org_001', 5, 1);