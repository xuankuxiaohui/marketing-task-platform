-- V5 signin domain (design §3.11.1 / R21). Charset per §3.1. Do not edit V1–V4.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE sgn_activity (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  code                VARCHAR(64)  NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name                VARCHAR(128) NOT NULL,
  start_time          DATETIME(3)  NOT NULL,
  end_time            DATETIME(3)  NOT NULL COMMENT '开始<结束',
  status              VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SCHEDULED|PUBLISHED|OFFLINE',
  version             INT          NOT NULL DEFAULT 0 COMMENT '未发布=0，发布+1',
  schedule_publish_at DATETIME(3)  NULL COMMENT '定时发布时刻',
  draft_content       JSON         NULL COMMENT '编辑态梯度；发布时拷入快照',
  pending_revision    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '修订草稿标记（R12.2）',
  deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '仅未发布可删',
  created_by          BIGINT       NULL,
  created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  KEY idx_status (status, deleted),
  KEY idx_publish_scan (status, schedule_publish_at),
  CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','OFFLINE'))
) COMMENT '签到活动（编辑态，R21）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sgn_activity_snapshot (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  activity_id  BIGINT       NOT NULL,
  version      INT          NOT NULL,
  content      JSON         NOT NULL COMMENT '梯度（第 N 天 → prize_id）随快照固化',
  published_at DATETIME(3)  NOT NULL,
  published_by BIGINT       NULL,
  UNIQUE KEY uk_activity_version (activity_id, version)
) COMMENT '签到活动不可变快照（RL-12）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sgn_record (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  activity_id  BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  sign_date    DATE        NOT NULL COMMENT 'UTC+8 自然日',
  source       VARCHAR(16) NOT NULL COMMENT 'CHECKIN|CATCHUP',
  snapshot_id  BIGINT      NOT NULL,
  simulated    TINYINT(1)  NOT NULL DEFAULT 0,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_activity_user_date (activity_id, user_id, sign_date),
  KEY idx_user_activity_date (user_id, activity_id, sign_date),
  KEY idx_catchup_day (activity_id, user_id, source, created_at),
  CHECK (source IN ('CHECKIN','CATCHUP'))
) COMMENT '签到记录（连签事实源，R21.2）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (id, parent_id, type, code, name, route, component, icon, sort, status) VALUES
(29, 0,  'MENU',      NULL,                      '签到活动', '/signin/activities', 'signin/activity/index', NULL, 29, 'ENABLED'),
(30, 0,  'MENU',      NULL,                      '签到记录', '/signin/records',    'signin/record/index',   NULL, 30, 'ENABLED'),
(31, 29, 'OPERATION', 'signin:config:query',     '查询签到活动', NULL, NULL, NULL, 1, 'ENABLED'),
(32, 29, 'OPERATION', 'signin:config:create',    '新建签到活动', NULL, NULL, NULL, 2, 'ENABLED'),
(33, 29, 'OPERATION', 'signin:config:update',    '更新签到活动', NULL, NULL, NULL, 3, 'ENABLED'),
(34, 29, 'OPERATION', 'signin:config:delete',    '删除签到活动', NULL, NULL, NULL, 4, 'ENABLED'),
(35, 29, 'OPERATION', 'signin:config:publish',   '发布签到活动', NULL, NULL, NULL, 5, 'ENABLED'),
(36, 29, 'OPERATION', 'signin:config:schedule',  '定时发布签到', NULL, NULL, NULL, 6, 'ENABLED'),
(37, 29, 'OPERATION', 'signin:config:offline',   '下线签到活动', NULL, NULL, NULL, 7, 'ENABLED'),
(38, 30, 'OPERATION', 'signin:record:query',     '查询签到记录', NULL, NULL, NULL, 1, 'ENABLED');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 29 AND 38;
