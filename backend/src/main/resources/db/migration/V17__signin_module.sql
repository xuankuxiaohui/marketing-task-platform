-- V17: Sign-in module tables (signin_config, signin_record, point_account, point_transaction)

CREATE TABLE signin_config (
    id                 BIGINT        AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(128)  NOT NULL,
    status             VARCHAR(16)   NOT NULL DEFAULT 'DRAFT'   COMMENT 'DRAFT/PUBLISHED/OFFLINE',
    period_type        VARCHAR(16)   NOT NULL DEFAULT 'MONTHLY' COMMENT 'WEEKLY/MONTHLY',
    base_points        INT           NOT NULL DEFAULT 10,
    streak_config      JSON          NULL     COMMENT '{"maxStreak":30,"tiers":[{"day":3,"bonus":20},{"day":7,"bonus":50}]}',
    point_expire_days  INT           NULL     COMMENT '积分过期天数, NULL=永不过期',
    catch_up_enabled   TINYINT       NOT NULL DEFAULT 0 COMMENT '是否允许补签',
    catch_up_cost      INT           NOT NULL DEFAULT 0 COMMENT '补签消耗积分',
    catch_up_max_days  INT           NULL     COMMENT '最大可补签天数',
    start_time         DATETIME      NULL,
    end_time           DATETIME      NULL,
    description        VARCHAR(512)  NULL,
    created_by         VARCHAR(64)   NULL,
    updated_by         VARCHAR(64)   NULL,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE signin_record (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    config_id     BIGINT       NOT NULL,
    user_id       VARCHAR(64)  NOT NULL,
    signin_date   DATE         NOT NULL,
    period_key    VARCHAR(16)  NOT NULL COMMENT 'WEEKLY: yyyy-Www / MONTHLY: yyyy-MM',
    streak_day    INT          NOT NULL DEFAULT 1,
    base_points   INT          NOT NULL,
    bonus_points  INT          NOT NULL DEFAULT 0,
    total_points  INT          NOT NULL,
    tier_reached  INT          NULL     COMMENT '本次达到的梯度天数',
    is_catch_up   TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_config_user_date (config_id, user_id, signin_date),
    INDEX idx_user_period (user_id, period_key),
    INDEX idx_config_date (config_id, signin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE point_account (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id        VARCHAR(64)  NOT NULL,
    balance        BIGINT       NOT NULL DEFAULT 0,
    total_earned   BIGINT       NOT NULL DEFAULT 0,
    total_spent    BIGINT       NOT NULL DEFAULT 0,
    total_expired  BIGINT       NOT NULL DEFAULT 0,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE point_transaction (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id        VARCHAR(64)  NOT NULL,
    type           VARCHAR(16)  NOT NULL COMMENT 'EARN/EXPIRE/DEDUCT',
    amount         BIGINT       NOT NULL COMMENT '积分数量(正数)',
    source_type    VARCHAR(32)  NOT NULL COMMENT 'SIGNIN/SIGNIN_STREAK/TASK_REWARD/CATCH_UP/ADMIN_GRANT',
    source_id      BIGINT       NULL     COMMENT '关联ID(signin_record.id等)',
    balance_after  BIGINT       NOT NULL COMMENT '变动后余额',
    expire_at      DATETIME     NULL     COMMENT '积分过期时间',
    status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/EXPIRED',
    description    VARCHAR(256) NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_id, type),
    INDEX idx_user_status (user_id, status),
    INDEX idx_expire (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
