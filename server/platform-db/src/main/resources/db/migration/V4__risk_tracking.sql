-- V4 risk + tracking (design §3.1 / §3.6 / §3.7). Charset per §3.1.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE risk_list_item (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  dimension    VARCHAR(16) NOT NULL COMMENT 'USER|IP|DEVICE',
  list_type    VARCHAR(16) NOT NULL COMMENT 'BLACK|WHITE（同值黑白各一条，判定黑优先 R25.6）',
  list_value   VARCHAR(64) NOT NULL COMMENT '用户ID/IP/设备标识',
  reason       VARCHAR(255) NOT NULL COMMENT '必填',
  deny_login   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '仅用户黑名单可置 1=同时禁止登录（R25.4）',
  effective_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expire_at    DATETIME(3) NULL COMMENT 'NULL=永久；判定按当前时间过滤（R25 属性 2）',
  operator_id  BIGINT      NOT NULL,
  remark       VARCHAR(255) NULL,
  created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_dim_type_value (dimension, list_type, list_value) COMMENT '重复添加幂等返回（R25.2）',
  KEY idx_value (list_value),
  CHECK (dimension IN ('USER','IP','DEVICE')),
  CHECK (list_type IN ('BLACK','WHITE'))
) COMMENT '风控名单（Redis 点查投影，DB 为事实源）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE risk_rule_config (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  rule_code      VARCHAR(16) NOT NULL COMMENT 'R-a|R-b|R-c|R-d|R-e|R-f（R26.1 封闭内置集）',
  enabled        TINYINT(1)  NOT NULL DEFAULT 1,
  threshold      BIGINT      NOT NULL COMMENT '阈值；R-e=最小完成秒数',
  window_seconds BIGINT      NULL COMMENT '滑动窗口；R-e 为空',
  action         VARCHAR(16) NOT NULL COMMENT 'REJECT|SILENT_REJECT|MARK（R26.2）',
  updated_by     BIGINT      NOT NULL,
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_rule (rule_code),
  CHECK (action IN ('REJECT','SILENT_REJECT','MARK')),
  CHECK (threshold > 0)
) COMMENT '风控规则配置（默认值=附录 A；变更实时生效+审计 risk:rule:config）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE risk_hit_log (
  id             BIGINT      NOT NULL COMMENT '雪花 ASSIGN_ID',
  hit_type       VARCHAR(16) NOT NULL COMMENT 'RULE|LIST',
  rule_code      VARCHAR(24) NOT NULL COMMENT '规则编码或名单描述 DIMENSION:TYPE（如 IP:BLACK）',
  user_id        BIGINT      NULL COMMENT '可空（IP 命中可能无用户）',
  dimension_value VARCHAR(64) NULL COMMENT 'IP/设备标识（关联分析）',
  context        JSON        NOT NULL COMMENT '上下文快照：入口(领取/发奖/注册/登录)、taskId/prizeId、IP、UA 等',
  hit_value      VARCHAR(64) NOT NULL COMMENT '命中值（如窗口内计数 12）',
  threshold      VARCHAR(64) NOT NULL,
  action_result  VARCHAR(24) NOT NULL COMMENT 'REJECTED|SILENT_REJECTED|MARKED|FROZEN，处置结果；用户黑名单冻结推进（R25.4）',
  simulated      TINYINT(1)  NOT NULL DEFAULT 0,
  occurred_at    DATETIME(3) NOT NULL,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at) COMMENT '单用户视图（R27.3）',
  KEY idx_rule_time (rule_code, created_at) COMMENT '统计口径（R27.4）',
  CHECK (hit_type IN ('RULE','LIST'))
) COMMENT '风控命中记录（唯一事实源；只增不改不删 RL-12）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE risk_handle_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  hit_log_id BIGINT       NULL COMMENT '关联命中记录（加黑场景）',
  user_id    BIGINT       NULL,
  action     VARCHAR(32)  NOT NULL COMMENT 'ADD_BLACK|REMOVE_BLACK|MARK_FALSE_POSITIVE（R27.2）',
  to_whitelist TINYINT(1) NOT NULL DEFAULT 0 COMMENT '解除黑名单时可选移入白名单',
  operator_id BIGINT      NOT NULL,
  reason     VARCHAR(255) NOT NULL,
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_user_time (user_id, created_at)
) COMMENT '人工处置留痕（处置动作本身改名单表，本表只增记录，R27 属性 1）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE evt_event_log (
  id           BIGINT      NOT NULL COMMENT '雪花 ASSIGN_ID',
  source       VARCHAR(16) NOT NULL COMMENT 'CLIENT|SERVER（R28.2）',
  event_code   VARCHAR(64) NOT NULL COMMENT '<域>.<对象>.<动作>，附录 D 封闭基线',
  user_id      BIGINT      NULL COMMENT '登录上报身份',
  device_id    VARCHAR(36) NULL COMMENT '匿名身份（R28.3）；归因打通不做（R28.11）',
  platform     VARCHAR(16) NULL,
  app_version  VARCHAR(32) NULL,
  ip           VARCHAR(45) NULL,
  events       JSON        NOT NULL COMMENT '客户端=批次事件数组[{code,props,clientTime}]（R28.9 单行批次）；服务端=单元素数组',
  batch_size   INT         NOT NULL DEFAULT 1,
  registered   TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '未登记事件按策略打标/拒绝（R28.6/R29.2）',
  simulated    TINYINT(1)  NOT NULL DEFAULT 0,
  server_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '接收时间=分析口径（R28.7）',
  PRIMARY KEY (id, server_time),
  KEY idx_code_time (event_code, server_time),
  KEY idx_user_time (user_id, server_time),
  KEY idx_device_time (device_id, server_time)
) COMMENT '统一事件表（按 server_time 月 RANGE 分区，调度预建未来 3 个月，保留 90 天；只增 RL-12；客户端行为约束 R28.10/13/14 由前端实现）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 首月分区 pYYYYMM；RANGE COLUMNS(server_time) VALUES LESS THAN (次月 1 日 00:00)（§3.1 / §3.7）
SET @p_name = DATE_FORMAT(UTC_TIMESTAMP(), '%Y%m');
SET @p_bound = DATE_FORMAT(DATE_ADD(DATE_FORMAT(UTC_TIMESTAMP(), '%Y-%m-01'), INTERVAL 1 MONTH), '%Y-%m-%d 00:00:00');
SET @p_ddl = CONCAT(
  'ALTER TABLE evt_event_log PARTITION BY RANGE COLUMNS(server_time) (',
  'PARTITION p', @p_name, ' VALUES LESS THAN (''', @p_bound, '''))'
);
PREPARE evt_part FROM @p_ddl;
EXECUTE evt_part;
DEALLOCATE PREPARE evt_part;

CREATE TABLE evt_event_metadata (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_code  VARCHAR(64) NOT NULL,
  name        VARCHAR(64) NOT NULL,
  prop_schema JSON        NULL COMMENT '[{name, type, required, remark}]（§4.7 契约）',
  status      VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  owner       VARCHAR(32) NULL,
  remark      VARCHAR(255) NULL,
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_event_code (event_code),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '事件元数据（R28.12/R29.1；V4 种子 = 附录 D 全量）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- R-a–R-f：阈值/窗口 = 附录 A；action 附录 A 未列，NOT NULL 取 REJECT（R26 拦截语义）；updated_by=V1 超管
INSERT INTO risk_rule_config (rule_code, enabled, threshold, window_seconds, action, updated_by) VALUES
('R-a', 1, 30, 3600,  'REJECT', 1),
('R-b', 1, 50, 86400, 'REJECT', 1),
('R-c', 1, 10, 3600,  'REJECT', 1),
('R-d', 1, 5,  86400, 'REJECT', 1),
('R-e', 1, 5,  NULL,  'REJECT', 1),
('R-f', 1, 60, 60,    'REJECT', 1);

-- 附录 D 全量（含 P1 登记行；ad.<form> 按 carousel/splash/popup/float/image 展开）
INSERT INTO evt_event_metadata (event_code, name, prop_schema, status, owner, remark) VALUES
('auth.register.success', '注册成功', CAST('[{"name":"userId","type":"long","required":true}]' AS JSON), 'ENABLED', 'identity', 'server'),
('auth.login.success', '登录成功', CAST('[{"name":"userId","type":"long","required":true}]' AS JSON), 'ENABLED', 'identity', 'server'),
('task.instance.start', '任务实例创建成功', CAST('[{"name":"instanceId","type":"long","required":true},{"name":"taskId","type":"long","required":true},{"name":"userId","type":"long","required":true}]' AS JSON), 'ENABLED', 'task', 'server'),
('task.step.complete', '步骤完成权持有后', CAST('[{"name":"instanceId","type":"long","required":true},{"name":"taskId","type":"long","required":true},{"name":"stepCode","type":"string","required":true},{"name":"seq","type":"int","required":true}]' AS JSON), 'ENABLED', 'task', 'server'),
('task.instance.complete', '实例完成', CAST('[{"name":"instanceId","type":"long","required":true},{"name":"taskId","type":"long","required":true},{"name":"userId","type":"long","required":true},{"name":"costSeconds","type":"int","required":true}]' AS JSON), 'ENABLED', 'task', 'server'),
('task.instance.abandon', '用户放弃或运营终止实例', CAST('[{"name":"instanceId","type":"long","required":true},{"name":"taskId","type":"long","required":true},{"name":"abandonSource","type":"string","required":true}]' AS JSON), 'ENABLED', 'task', 'server'),
('task.instance.expire', '实例过期翻转', CAST('[{"name":"instanceId","type":"long","required":true},{"name":"taskId","type":"long","required":true}]' AS JSON), 'ENABLED', 'task', 'server'),
('risk.hit.recorded', '风控命中留痕', CAST('[{"name":"hitId","type":"long","required":true},{"name":"scene","type":"string","required":true},{"name":"source","type":"string","required":true},{"name":"action","type":"string","required":true}]' AS JSON), 'ENABLED', 'risk', 'server'),
('page.view', '路由切换进入页面', CAST('[{"name":"route","type":"string","required":true},{"name":"refRoute","type":"string","required":false}]' AS JSON), 'ENABLED', 'tracking', 'client'),
('page.leave', '路由离开/页面卸载', CAST('[{"name":"route","type":"string","required":true},{"name":"durationSeconds","type":"int","required":true}]' AS JSON), 'ENABLED', 'tracking', 'client'),
('task.card.exposure', '任务卡片有效曝光', CAST('[{"name":"taskId","type":"long","required":true},{"name":"taskCode","type":"string","required":true}]' AS JSON), 'ENABLED', 'task', 'client'),
('task.detail.view', '任务详情页浏览', CAST('[{"name":"taskId","type":"long","required":true}]' AS JSON), 'ENABLED', 'task', 'client'),
('task.start.click', '点击领取任务', CAST('[{"name":"taskId","type":"long","required":true}]' AS JSON), 'ENABLED', 'task', 'client'),
('task.step.click', '点击步骤', CAST('[{"name":"instanceId","type":"long","required":true},{"name":"stepId","type":"long","required":true},{"name":"stepCode","type":"string","required":true}]' AS JSON), 'ENABLED', 'task', 'client'),
('task.complete.view', '查看任务完成结果', CAST('[{"name":"instanceId","type":"long","required":true},{"name":"taskId","type":"long","required":true}]' AS JSON), 'ENABLED', 'task', 'client'),
('task.abandon.click', '点击放弃任务', CAST('[{"name":"instanceId","type":"long","required":true}]' AS JSON), 'ENABLED', 'task', 'client'),
('reward.grant.success', '发放记录写入成功', CAST('[{"name":"recordId","type":"long","required":true},{"name":"prizeId","type":"long","required":true},{"name":"grantSource","type":"string","required":true}]' AS JSON), 'ENABLED', 'reward', 'server'),
('reward.grant.failed', '发放失败', CAST('[{"name":"recordId","type":"long","required":true},{"name":"prizeId","type":"long","required":true},{"name":"failReason","type":"string","required":true}]' AS JSON), 'ENABLED', 'reward', 'server'),
('reward.fulfill.arrived', '履约到账', CAST('[{"name":"recordId","type":"long","required":true},{"name":"prizeId","type":"long","required":true},{"name":"fulfillmentRef","type":"string","required":false}]' AS JSON), 'ENABLED', 'reward', 'server'),
('reward.fulfill.failed', '履约失败', CAST('[{"name":"recordId","type":"long","required":true},{"name":"prizeId","type":"long","required":true},{"name":"fulfillFailReason","type":"string","required":true}]' AS JSON), 'ENABLED', 'reward', 'server'),
('reward.claim.click', '点击领取奖品', CAST('[{"name":"recordId","type":"long","required":true},{"name":"prizeId","type":"long","required":true}]' AS JSON), 'ENABLED', 'reward', 'client'),
('reward.list.view', '奖品列表页浏览', CAST('[{"name":"tab","type":"string","required":true}]' AS JSON), 'ENABLED', 'reward', 'client'),
('points.page.view', '积分页浏览', NULL, 'ENABLED', 'reward', 'client'),
('signin.page.view', '签到页浏览', CAST('[{"name":"configId","type":"long","required":true}]' AS JSON), 'ENABLED', 'signin', 'client P1'),
('signin.sign.click', '签到点击', CAST('[{"name":"configId","type":"long","required":true}]' AS JSON), 'ENABLED', 'signin', 'client P1'),
('signin.catchup.click', '补签点击', CAST('[{"name":"configId","type":"long","required":true},{"name":"signDate","type":"string","required":true}]' AS JSON), 'ENABLED', 'signin', 'client P1'),
('ad.carousel.exposure', '广告素材曝光', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.splash.exposure', '广告素材曝光', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.popup.exposure', '广告素材曝光', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.float.exposure', '广告素材曝光', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.image.exposure', '广告素材曝光', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.carousel.click', '广告素材点击', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.splash.click', '广告素材点击', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.popup.click', '广告素材点击', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.float.click', '广告素材点击', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1'),
('ad.image.click', '广告素材点击', CAST('[{"name":"positionCode","type":"string","required":true},{"name":"materialTrackId","type":"string","required":true}]' AS JSON), 'ENABLED', 'ad', 'client P1');
