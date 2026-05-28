-- V18__activity_module.sql

CREATE TABLE activity (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    code                VARCHAR(64) NOT NULL COMMENT '活动编码',
    name                VARCHAR(128) NOT NULL COMMENT '活动名称',
    description         VARCHAR(512) NULL COMMENT '简短描述',
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ONLINE/OFFLINE',
    gray_type           VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/RATIO/WHITELIST',
    gray_config         JSON NULL COMMENT '灰度配置',
    start_time          DATETIME NOT NULL COMMENT '活动开始时间',
    end_time            DATETIME NOT NULL COMMENT '活动结束时间',
    participation_rules JSON NULL COMMENT '执行规则配置',
    cache_version       INT NOT NULL DEFAULT 1 COMMENT '缓存版本号',
    created_by          VARCHAR(64) NULL,
    updated_by          VARCHAR(64) NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted             TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_activity_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动配置';

CREATE TABLE activity_display_rule (
    activity_id   BIGINT NOT NULL PRIMARY KEY COMMENT '关联活动ID',
    content       MEDIUMTEXT NULL COMMENT '富文本/Markdown内容',
    content_hash  VARCHAR(64) NULL COMMENT '内容hash用于ETag',
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by    VARCHAR(64) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动展示规则';

CREATE TABLE activity_stats (
    activity_id       BIGINT NOT NULL,
    stat_date         DATE NOT NULL,
    participant_count INT NOT NULL DEFAULT 0,
    completion_count  INT NOT NULL DEFAULT 0,
    reward_count      INT NOT NULL DEFAULT 0,
    PRIMARY KEY (activity_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动统计数据';

-- Sub-module association
ALTER TABLE task ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE signin_config ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE prize ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE prize_record ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';

CREATE INDEX idx_task_activity_code ON task(activity_code);
CREATE INDEX idx_signin_config_activity_code ON signin_config(activity_code);
CREATE INDEX idx_prize_activity_code ON prize(activity_code);
CREATE INDEX idx_prize_record_activity_code ON prize_record(activity_code);
