-- V6 activity domain (design §3.11.2 / R22). Charset per §3.1. Do not edit V1–V5.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE act_activity (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  code                   VARCHAR(64)  NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name                   VARCHAR(128) NOT NULL,
  rich_text              MEDIUMTEXT   NULL COMMENT '服务端白名单消毒后 HTML；C 端只渲染此列',
  content_hash           VARCHAR(64)  NULL COMMENT 'SHA-256 hex of rich_text（ETag）',
  start_time             DATETIME(3)  NOT NULL,
  end_time               DATETIME(3)  NOT NULL COMMENT '开始<结束',
  status                 VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SCHEDULED|PUBLISHED|OFFLINE',
  version                INT          NOT NULL DEFAULT 0 COMMENT '未发布=0，发布+1',
  schedule_publish_at    DATETIME(3)  NULL COMMENT '定时发布时刻',
  schedule_offline_at    DATETIME(3)  NULL COMMENT '定时下线时刻',
  gray_type              VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE|RATIO',
  gray_ratio             INT          NULL COMMENT '0-100，RATIO 时生效',
  submodules             JSON         NULL COMMENT '有序引用 [{type,refId,sort}] type=TASK|PRIZE_GROUP|SIGNIN',
  participation_prize_id BIGINT       NULL COMMENT '可选参与奖；非空才调 RewardPort',
  allow_user_ids         JSON         NULL COMMENT '活动级允许用户 ID 列表（非 R25）',
  allow_crowd_codes      JSON         NULL COMMENT '活动级允许人群包编码（非 R25）',
  new_user_only          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '仅新用户',
  new_user_days          INT          NOT NULL DEFAULT 7 COMMENT '1-365，附录 A 默认 7',
  user_daily_limit       INT          NULL COMMENT 'NULL=不限',
  user_total_limit       INT          NULL COMMENT 'NULL=不限',
  global_daily_limit     INT          NULL COMMENT 'NULL=不限',
  regions                JSON         NULL COMMENT '省份码列表，空或不配=不限',
  quota_day              DATE         NULL COMMENT '全局日限量计数日（UTC+8）',
  quota_count            INT          NOT NULL DEFAULT 0 COMMENT '当日 PASS 计数，行锁 CAS',
  draft_content          JSON         NULL COMMENT '编辑态；发布时拷入 live 列',
  pending_revision       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '修订草稿标记（R12.2）',
  deleted                TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '仅未发布可删',
  created_by             BIGINT       NULL,
  created_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  KEY idx_status (status, deleted),
  KEY idx_publish_scan (status, schedule_publish_at),
  KEY idx_offline_scan (status, schedule_offline_at),
  CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','OFFLINE')),
  CHECK (gray_type IN ('NONE','RATIO'))
) COMMENT '专题活动（R22）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE act_participation (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  activity_id  BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  period_key   VARCHAR(16) NOT NULL COMMENT 'UTC+8 自然日 yyyy-MM-dd',
  result       VARCHAR(16) NOT NULL COMMENT 'PASS|REJECT',
  hit_rule     VARCHAR(32) NULL COMMENT '拒绝命中规则；PASS 为空',
  simulated    TINYINT(1)  NOT NULL DEFAULT 0,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_activity_user (activity_id, user_id, result),
  KEY idx_activity_period (activity_id, period_key, result),
  KEY idx_user_period (user_id, period_key),
  CHECK (result IN ('PASS','REJECT'))
) COMMENT '活动参与记录（R22.3）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (id, parent_id, type, code, name, route, component, icon, sort, status) VALUES
(39, 0,  'MENU',      NULL,                           '活动管理', '/activity/activities',     'activity/index',              NULL, 39, 'ENABLED'),
(40, 0,  'MENU',      NULL,                           '活动参与', '/activity/participations', 'activity/participation/index', NULL, 40, 'ENABLED'),
(41, 39, 'OPERATION', 'activity:query',               '查询活动', NULL, NULL, NULL, 1, 'ENABLED'),
(42, 39, 'OPERATION', 'activity:create',              '新建活动', NULL, NULL, NULL, 2, 'ENABLED'),
(43, 39, 'OPERATION', 'activity:update',              '更新活动', NULL, NULL, NULL, 3, 'ENABLED'),
(44, 39, 'OPERATION', 'activity:delete',              '删除活动', NULL, NULL, NULL, 4, 'ENABLED'),
(45, 39, 'OPERATION', 'activity:publish',             '发布活动', NULL, NULL, NULL, 5, 'ENABLED'),
(46, 39, 'OPERATION', 'activity:offline',             '下线活动', NULL, NULL, NULL, 6, 'ENABLED'),
(47, 40, 'OPERATION', 'activity:participation:query', '查询参与记录', NULL, NULL, NULL, 1, 'ENABLED');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 39 AND 47;
