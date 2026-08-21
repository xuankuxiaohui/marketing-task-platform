-- V7 ad domain (design §3.11.5 / R30). Charset per §3.1. Do not edit V1–V6_2.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE ad_position (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(64)  NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一，如 home_banner',
  name       VARCHAR(128) NOT NULL,
  form       VARCHAR(16)  NOT NULL COMMENT 'CAROUSEL|IMAGE|SPLASH|POPUP|FLOAT',
  platforms  JSON         NOT NULL COMMENT '适用端 WEB/ANDROID/IOS/MINIAPP',
  status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED|DISABLED',
  deleted    TINYINT(1)   NOT NULL DEFAULT 0,
  created_by BIGINT       NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  KEY idx_status (status, deleted),
  CHECK (form IN ('CAROUSEL','IMAGE','SPLASH','POPUP','FLOAT')),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '广告位（R30）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ad_material (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  title        VARCHAR(128) NOT NULL,
  subtitle     VARCHAR(256) NULL,
  image_url    VARCHAR(512) NOT NULL COMMENT '外链 https',
  jump_type    VARCHAR(16)  NOT NULL COMMENT 'NONE|ROUTE|LINK|SCHEME，schema 同 R11.10',
  jump_params  JSON         NULL,
  weight       INT          NOT NULL COMMENT '1-999，素材排序控制字段',
  start_time   DATETIME(3)  NOT NULL,
  end_time     DATETIME(3)  NOT NULL COMMENT '开始<结束',
  status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED|DISABLED',
  deleted      TINYINT(1)   NOT NULL DEFAULT 0,
  created_by   BIGINT       NULL,
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_status (status, deleted),
  KEY idx_window (start_time, end_time),
  CHECK (jump_type IN ('NONE','ROUTE','LINK','SCHEME')),
  CHECK (status IN ('ENABLED','DISABLED')),
  CHECK (weight BETWEEN 1 AND 999)
) COMMENT '广告素材（R30）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ad_position_material (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  position_id BIGINT      NOT NULL,
  material_id BIGINT      NOT NULL,
  weight      INT         NOT NULL COMMENT '投放权重 1-999，输出按此字段',
  start_time  DATETIME(3) NOT NULL,
  end_time    DATETIME(3) NOT NULL,
  platforms   JSON        NULL COMMENT '端定向，空=继承广告位',
  gray_type   VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|RATIO',
  gray_ratio  INT         NULL COMMENT '0-100，RATIO 时生效',
  crowd_id    BIGINT      NULL COMMENT '人群包 ID；匿名不生效；跨域不直访 task_',
  status      VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED|DISABLED',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_position_material (position_id, material_id),
  KEY idx_position (position_id, status),
  KEY idx_material (material_id),
  CHECK (gray_type IN ('NONE','RATIO')),
  CHECK (status IN ('ENABLED','DISABLED')),
  CHECK (weight BETWEEN 1 AND 999)
) COMMENT '广告位-素材投放关系（R30）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (id, parent_id, type, code, name, route, component, icon, sort, status) VALUES
(53, 0,  'MENU',      NULL,                    '广告位',   '/ad/positions', 'ad/position/index', NULL, 53, 'ENABLED'),
(54, 53, 'OPERATION', 'ad:position:query',     '查询广告位', NULL, NULL, NULL, 1, 'ENABLED'),
(55, 53, 'OPERATION', 'ad:position:create',    '新建广告位', NULL, NULL, NULL, 2, 'ENABLED'),
(56, 53, 'OPERATION', 'ad:position:update',    '更新广告位', NULL, NULL, NULL, 3, 'ENABLED'),
(57, 53, 'OPERATION', 'ad:position:delete',    '删除广告位', NULL, NULL, NULL, 4, 'ENABLED'),
(58, 0,  'MENU',      NULL,                    '广告素材', '/ad/materials', 'ad/material/index', NULL, 58, 'ENABLED'),
(59, 58, 'OPERATION', 'ad:material:query',     '查询素材',   NULL, NULL, NULL, 1, 'ENABLED'),
(60, 58, 'OPERATION', 'ad:material:create',    '新建素材',   NULL, NULL, NULL, 2, 'ENABLED'),
(61, 58, 'OPERATION', 'ad:material:update',    '更新素材',   NULL, NULL, NULL, 3, 'ENABLED'),
(62, 58, 'OPERATION', 'ad:material:delete',    '删除素材',   NULL, NULL, NULL, 4, 'ENABLED');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE id BETWEEN 53 AND 62;
