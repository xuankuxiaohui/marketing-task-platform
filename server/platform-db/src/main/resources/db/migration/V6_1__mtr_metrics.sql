-- V6.1 metrics daily aggregates (design §3.11.3 / R23). Do not edit V1–V6.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE mtr_task_funnel_d (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  day             DATE         NOT NULL COMMENT 'UTC+8 自然日',
  dim_key         VARCHAR(128) NOT NULL COMMENT 'taskId',
  exposure_count  BIGINT       NOT NULL DEFAULT 0 COMMENT 'task.card.exposure 事件条数',
  start_count     BIGINT       NOT NULL DEFAULT 0 COMMENT 'task.instance.start 事件条数',
  complete_count  BIGINT       NOT NULL DEFAULT 0 COMMENT 'task.instance.complete 事件条数',
  UNIQUE KEY uk_day_dim (day, dim_key),
  KEY idx_day (day)
) COMMENT '任务漏斗按日聚合（R23）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mtr_reward_spend_d (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  day               DATE         NOT NULL COMMENT 'UTC+8 自然日',
  dim_key           VARCHAR(128) NOT NULL COMMENT 'category_code',
  arrived_count     BIGINT       NOT NULL DEFAULT 0,
  arrived_cost_fen  BIGINT       NOT NULL DEFAULT 0,
  sending_count     BIGINT       NOT NULL DEFAULT 0,
  sending_cost_fen  BIGINT       NOT NULL DEFAULT 0,
  UNIQUE KEY uk_day_dim (day, dim_key),
  KEY idx_day (day)
) COMMENT '奖励成本按日聚合（R23）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mtr_risk_hit_d (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  day              DATE         NOT NULL COMMENT 'UTC+8 自然日',
  dim_key          VARCHAR(128) NOT NULL COMMENT 'rule_code',
  hit_count        BIGINT       NOT NULL DEFAULT 0,
  intercept_count  BIGINT       NOT NULL DEFAULT 0 COMMENT 'REJECTED/SILENT_REJECTED/FROZEN',
  UNIQUE KEY uk_day_dim (day, dim_key),
  KEY idx_day (day)
) COMMENT '风控命中按日聚合（R23）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mtr_ad_material_d (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  day             DATE         NOT NULL COMMENT 'UTC+8 自然日',
  dim_key         VARCHAR(128) NOT NULL COMMENT 'positionCode:materialTrackId',
  exposure_count  BIGINT       NOT NULL DEFAULT 0 COMMENT 'ad.*.exposure 事件条数',
  click_count     BIGINT       NOT NULL DEFAULT 0 COMMENT 'ad.*.click 事件条数',
  UNIQUE KEY uk_day_dim (day, dim_key),
  KEY idx_day (day)
) COMMENT '广告素材按日聚合（R23）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (id, parent_id, type, code, name, route, component, icon, sort, status) VALUES
(48, 0,  'MENU',      NULL,                     '运营看板', '/metrics', 'metrics/index', NULL, 48, 'ENABLED'),
(49, 48, 'OPERATION', 'metrics:dashboard:view', '查看看板', NULL, NULL, NULL, 1, 'ENABLED');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 48 AND 49;
