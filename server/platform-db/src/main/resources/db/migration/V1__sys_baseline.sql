-- V1 identity + system baseline (design §3.1 / §3.2). Charset per §3.1.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE sys_admin_user (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  username             VARCHAR(30)  NOT NULL COMMENT '4-30 [a-z0-9_]，应用层转小写；创建后不可修改；ai_ci uk 兼大小写不敏感且逻辑删除后不可复用',
  nickname             VARCHAR(64)  NOT NULL,
  password_hash        VARCHAR(128) NOT NULL COMMENT 'BCrypt cost≥12（R1.5）',
  status               VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED|DISABLED，与 deleted 独立（R3.4）',
  deleted              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除，不可恢复；全部列表默认过滤',
  failed_attempts      INT          NOT NULL DEFAULT 0 COMMENT '连续失败计数，成功清零（R1.4）',
  locked_until         DATETIME(3)  NULL     COMMENT '锁定截止；NULL=未锁（R1.3：5次锁15分钟）',
  must_change_password TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '首启/重置后强制改密（R3.6）',
  last_login_at        DATETIME(3)  NULL,
  created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_username (username),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '后台用户' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(30)  NOT NULL COMMENT '3-30 [a-z0-9_-] 全局唯一',
  name        VARCHAR(64)  NOT NULL,
  description VARCHAR(255) NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT '停用角色权限不计入并集，绑定保留（R2.1）',
  built_in    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '内置超管：不可删、不可改权限集（R2.5）',
  created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '角色' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_permission (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '树形；OPERATION 型必须挂 MENU 节点（R2.2）',
  type       VARCHAR(16)  NOT NULL COMMENT 'MENU|OPERATION（菜单即树节点，操作权限码供 API 鉴权）',
  code       VARCHAR(128) NULL COMMENT 'OPERATION 必填，格式 域:资源:操作（附录 B）；MENU 为空',
  name       VARCHAR(64)  NOT NULL,
  route      VARCHAR(128) NULL COMMENT 'MENU：前端路由路径',
  component  VARCHAR(128) NULL COMMENT 'MENU：前端组件路径',
  icon       VARCHAR(64)  NULL,
  sort       INT          NOT NULL DEFAULT 0,
  status     VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  CHECK (type IN ('MENU','OPERATION')),
  CHECK (type = 'MENU' OR code IS NOT NULL)
) COMMENT '权限树（菜单+操作统一树）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_admin_user_role (
  admin_user_id BIGINT NOT NULL,
  role_id       BIGINT NOT NULL,
  PRIMARY KEY (admin_user_id, role_id),
  KEY idx_role (role_id)
) COMMENT '后台用户-角色（多角色并集，允许空）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  KEY idx_permission (permission_id)
) COMMENT '角色-权限' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_portal_user (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  username             VARCHAR(30)  NOT NULL COMMENT '4-30 [a-z0-9_]，大小写不敏感唯一',
  nickname             VARCHAR(30)  NOT NULL COMMENT '用户自有（R4.10）；注册默认 "用户"+ID后6位',
  password_hash        VARCHAR(128) NOT NULL,
  province             VARCHAR(32)  NULL COMMENT '字典 province（运营维护）',
  user_level           VARCHAR(32)  NULL COMMENT '字典 user_level',
  user_role            VARCHAR(32)  NULL COMMENT '字典 user_role',
  tags                 JSON         NULL COMMENT '字典 user_tag 多值数组，≤20 个（R5.3）',
  org_id               VARCHAR(64)  NULL COMMENT '≤64 [A-Za-z0-9_-]',
  status               VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  deleted              TINYINT(1)   NOT NULL DEFAULT 0,
  failed_attempts      INT          NOT NULL DEFAULT 0,
  locked_until         DATETIME(3)  NULL,
  must_change_password TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '后台重置后强制改密（R5.4）',
  registered_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  last_login_at        DATETIME(3)  NULL,
  created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_username (username),
  KEY idx_profile (province, user_level, status) COMMENT '后台档案筛选（R5.1）',
  KEY idx_registered (registered_at),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '门户用户（档案属性供任务过滤/风控）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_dict_type (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(64) NOT NULL COMMENT '类型编码唯一',
  name       VARCHAR(64) NOT NULL,
  status     VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '停用后两侧查询返回空（R7.3）',
  remark     VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '字典类型' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_dict_entry (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  type_id    BIGINT      NOT NULL,
  label      VARCHAR(64) NOT NULL,
  value      VARCHAR(64) NOT NULL COMMENT '类型内唯一，≤64 [A-Za-z0-9_-.]',
  sort       INT         NOT NULL DEFAULT 0,
  status     VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  remark     VARCHAR(255) NULL,
  UNIQUE KEY uk_type_value (type_id, value),
  KEY idx_type_sort (type_id, sort),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '字典项' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_config (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  config_key   VARCHAR(128) NOT NULL COMMENT '唯一；全量键=附录 A 封闭清单',
  config_group VARCHAR(64)  NOT NULL DEFAULT 'default',
  config_value TEXT         NOT NULL,
  value_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT 'STRING|NUMBER|BOOL|JSON，应用层类型解析（R8.1）',
  masked       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '掩码显示：不回显明文；更新未携带 value=保持原值（R8.2）',
  status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  remark       VARCHAR(255) NULL,
  updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_key (config_key),
  CHECK (value_type IN ('STRING','NUMBER','BOOL','JSON')),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT '系统配置（读取仅经 ConfigService，RL-11）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_audit_log (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  module           VARCHAR(64)  NOT NULL COMMENT '如 task/reward/risk',
  action           VARCHAR(64)  NOT NULL COMMENT '如 publish / cache-evict',
  operator_id      BIGINT       NULL     COMMENT '成功登录/已认证写操作=账号主键；未认证尝试（失败登录等）NULL（R1.6）',
  operator_name    VARCHAR(30)  NOT NULL COMMENT '展示名；未认证=请求提交的用户名截断至 30 字',
  ip               VARCHAR(45)  NULL,
  user_agent       VARCHAR(255) NULL,
  request_summary  VARCHAR(2048) NULL COMMENT '参数 JSON→脱敏→截断 2000+...(truncated)（R10.1）',
  result           VARCHAR(16)  NOT NULL COMMENT 'SUCCESS|FAILURE（与操作成败无关都要记，R10 属性 1）',
  error_message    VARCHAR(512) NULL,
  cost_ms          INT          NULL,
  trace_id         VARCHAR(64)  NULL,
  created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_operator_time (operator_id, created_at),
  KEY idx_module_time (module, created_at),
  KEY idx_created (created_at) COMMENT '保留期清理与按月分区预留（P1）',
  CHECK (result IN ('SUCCESS','FAILURE'))
) COMMENT '审计日志（经 Outbox 异步写入，保留≥180天 retention.audit-days）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_outbox (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_code    VARCHAR(64) NOT NULL COMMENT '事件编码：服务端事件用附录 D；领域内部事件用 com.mkt.contract 事件常量',
  producer      VARCHAR(16) NOT NULL COMMENT 'admin|portal；写入方应用，Relay 只扫本应用（D-11）',
  aggregate_type VARCHAR(32) NOT NULL COMMENT '聚合类型如 task_instance / grant_record',
  aggregate_id  VARCHAR(64) NOT NULL,
  payload       JSON        NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|DEAD；投递成功即删行（终态表已落库），DEAD 保留供后台查询',
  retry_count   INT         NOT NULL DEFAULT 0 COMMENT '≤5 次转 DEAD',
  next_retry_at DATETIME(3) NULL COMMENT '指数退避',
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_producer_status_next (producer, status, next_retry_at),
  CHECK (status IN ('PENDING','DEAD')),
  CHECK (producer IN ('admin','portal'))
) COMMENT '事务性事件外发（与业务同事务写入，RL-07；按 producer 分 Relay）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_internal_app (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  app_id             VARCHAR(32)  NOT NULL,
  app_name           VARCHAR(64)  NOT NULL,
  secret_cipher      VARCHAR(255) NOT NULL COMMENT 'AES-256-GCM 密文（主密钥经环境变量注入，禁止明文落库）',
  prev_secret_cipher VARCHAR(255) NULL COMMENT '轮换双密钥：旧密钥在生效窗口内同验签',
  prev_expire_at     DATETIME(3)  NULL COMMENT '旧密钥失效时刻（rotate 后 = now + 24h，常量）',
  status             VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_app_id (app_id),
  CHECK (status IN ('ENABLED','DISABLED'))
) COMMENT 'internal 调用方登记（登记/轮换/失效，R15.2）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Seeds: super-admin role + user (hash filled by MKT_INIT_ADMIN_PASSWORD), §4.10 menus, appendix A, dicts.

INSERT INTO sys_role (id, code, name, description, status, built_in) VALUES
(1, 'super-admin', '超级管理员', '内置超管，不可删、不可改权限集', 'ENABLED', 1);

INSERT INTO sys_admin_user (id, username, nickname, password_hash, status, deleted, must_change_password) VALUES
(1, 'admin', '超级管理员', '', 'ENABLED', 0, 1);

INSERT INTO sys_admin_user_role (admin_user_id, role_id) VALUES (1, 1);

INSERT INTO sys_permission (id, parent_id, type, code, name, route, component, icon, sort, status) VALUES
(1,  0, 'MENU', NULL, '登录', '/login', 'login/index', NULL, 1, 'ENABLED'),
(2,  0, 'MENU', NULL, '工作台', '/dashboard', 'dashboard/index', NULL, 2, 'ENABLED'),
(3,  0, 'MENU', NULL, '后台用户', '/system/users', 'system/user/index', NULL, 3, 'ENABLED'),
(4,  0, 'MENU', NULL, '角色权限', '/system/roles', 'system/role/index', NULL, 4, 'ENABLED'),
(5,  0, 'MENU', NULL, '会话管理', '/system/sessions', 'system/session/index', NULL, 5, 'ENABLED'),
(6,  0, 'MENU', NULL, '门户用户', '/system/portal-users', 'system/portal-user/index', NULL, 6, 'ENABLED'),
(7,  0, 'MENU', NULL, 'internal 调用方', '/system/internal-apps', 'system/internal-app/index', NULL, 7, 'ENABLED'),
(8,  0, 'MENU', NULL, '字典管理', '/system/dicts', 'system/dict/index', NULL, 8, 'ENABLED'),
(9,  0, 'MENU', NULL, '参数配置', '/system/configs', 'system/config/index', NULL, 9, 'ENABLED'),
(10, 0, 'MENU', NULL, '缓存管理', '/system/cache', 'system/cache/index', NULL, 10, 'ENABLED'),
(11, 0, 'MENU', NULL, '操作审计', '/system/audits', 'system/audit/index', NULL, 11, 'ENABLED'),
(12, 0, 'MENU', NULL, '任务列表', '/task/definitions', 'task/definition/index', NULL, 12, 'ENABLED'),
(13, 0, 'MENU', NULL, '任务编辑（画布）', '/task/definitions/edit/:id?', 'task/definition/edit', NULL, 13, 'ENABLED'),
(14, 0, 'MENU', NULL, '任务版本', '/task/definitions/:id/versions', 'task/definition/version', NULL, 14, 'ENABLED'),
(15, 0, 'MENU', NULL, '互斥组', '/task/mutex-groups', 'task/mutex-group/index', NULL, 15, 'ENABLED'),
(16, 0, 'MENU', NULL, '人群包', '/task/crowds', 'task/crowd/index', NULL, 16, 'ENABLED'),
(17, 0, 'MENU', NULL, '实例管理', '/task/instances', 'task/instance/index', NULL, 17, 'ENABLED'),
(18, 0, 'MENU', NULL, '奖品分类', '/reward/categories', 'reward/category/index', NULL, 18, 'ENABLED'),
(19, 0, 'MENU', NULL, '奖品管理', '/reward/prizes', 'reward/prize/index', NULL, 19, 'ENABLED'),
(20, 0, 'MENU', NULL, '发放记录', '/reward/records', 'reward/record/index', NULL, 20, 'ENABLED'),
(21, 0, 'MENU', NULL, '渠道对账', '/reward/recon', 'reward/recon/index', NULL, 21, 'ENABLED'),
(22, 0, 'MENU', NULL, '积分账户', '/points/accounts', 'points/account/index', NULL, 22, 'ENABLED'),
(23, 0, 'MENU', NULL, '积分流水', '/points/transactions', 'points/transaction/index', NULL, 23, 'ENABLED'),
(24, 0, 'MENU', NULL, '风控名单', '/risk/list-items', 'risk/list-item/index', NULL, 24, 'ENABLED'),
(25, 0, 'MENU', NULL, '风控规则', '/risk/rules', 'risk/rule/index', NULL, 25, 'ENABLED'),
(26, 0, 'MENU', NULL, '命中与处置', '/risk/cases', 'risk/case/index', NULL, 26, 'ENABLED'),
(27, 0, 'MENU', NULL, '埋点元数据', '/track/metadata', 'track/metadata/index', NULL, 27, 'ENABLED'),
(28, 0, 'MENU', NULL, '事件调试', '/track/events', 'track/event/index', NULL, 28, 'ENABLED');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission;

INSERT INTO sys_dict_type (id, code, name, status) VALUES
(1, 'province', '省份', 'ENABLED'),
(2, 'user_level', '用户等级', 'ENABLED'),
(3, 'user_role', '用户角色', 'ENABLED'),
(4, 'user_tag', '用户标签', 'ENABLED'),
(5, 'task_category', '任务分类', 'ENABLED'),
(6, 'portal_route', '门户站内路由', 'ENABLED');

INSERT INTO sys_dict_entry (type_id, label, value, sort, status) VALUES
(6, '门户首页', 'home', 1, 'ENABLED'),
(6, '个人中心', 'mine', 2, 'ENABLED'),
(6, '任务详情', 'task-detail', 3, 'ENABLED'),
(6, '我的奖品', 'prize-list', 4, 'ENABLED'),
(6, '奖品详情', 'prize-detail', 5, 'ENABLED'),
(6, '积分', 'points', 6, 'ENABLED'),
(6, '修改密码', 'password', 7, 'ENABLED'),
(6, '签到', 'signin', 8, 'ENABLED');

INSERT INTO sys_config (config_key, config_group, config_value, value_type, masked, status, remark) VALUES
('auth.captcha.ttl-seconds', 'auth', '120', 'NUMBER', 0, 'ENABLED', 'R1.7/R4 验证码有效期'),
('auth.admin.session.max-concurrent', 'auth', '5', 'NUMBER', 0, 'ENABLED', 'R1.9 后台并发会话上限'),
('auth.portal.session.max-concurrent', 'auth', '3', 'NUMBER', 0, 'ENABLED', 'R4.3 门户并发会话上限'),
('ratelimit.login.ip.per-minute', 'ratelimit', '20', 'NUMBER', 0, 'ENABLED', 'R1.11/R4.2 登录 IP 限流'),
('ratelimit.login.account.per-minute', 'ratelimit', '10', 'NUMBER', 0, 'ENABLED', 'R1.11/R4.2 登录账号限流'),
('ratelimit.portal-write.user.per-second', 'ratelimit', '10', 'NUMBER', 0, 'ENABLED', 'R19.5 等门户写接口用户限流'),
('ratelimit.internal.accesskey.per-second', 'ratelimit', '200', 'NUMBER', 0, 'ENABLED', 'R15.5 internal 调用方限流'),
('ratelimit.track.batch.per-user-per-minute', 'ratelimit', '60', 'NUMBER', 0, 'ENABLED', 'R28.3 埋点上报限流'),
('task.start.daily-limit-per-user', 'task', '20', 'NUMBER', 0, 'ENABLED', 'R13.6 每用户每日领取任务上限'),
('task.step.max-count', 'task', '50', 'NUMBER', 0, 'ENABLED', 'R11.2 单任务步骤数上限'),
('task.instance.expire-after-window-days', 'task', '7', 'NUMBER', 0, 'ENABLED', 'R14.10 实例过期天数'),
('reward.grant.retry-max', 'reward', '3', 'NUMBER', 0, 'ENABLED', 'R18.5 发放自动重试上限'),
('reward.grant.retry-interval-seconds', 'reward', '30', 'NUMBER', 0, 'ENABLED', 'R18.5 发放自动重试间隔'),
('reward.claim.retry-max', 'reward', '3', 'NUMBER', 0, 'ENABLED', 'R19.4 领取重试上限'),
('reward.fulfill.retry-max', 'reward', '3', 'NUMBER', 0, 'ENABLED', 'R18.3 履约自动重试上限'),
('reward.fulfill.retry-interval-seconds', 'reward', '30', 'NUMBER', 0, 'ENABLED', 'R18.3 履约自动重试间隔'),
('reward.fulfill.sending-timeout-hours', 'reward', '24', 'NUMBER', 0, 'ENABLED', 'R18.3 发送中超时转失败'),
('risk.rule.user-task-complete.window-seconds', 'risk', '3600', 'NUMBER', 0, 'ENABLED', 'R26 R-a 窗口'),
('risk.rule.user-task-complete.threshold', 'risk', '30', 'NUMBER', 0, 'ENABLED', 'R26 R-a 阈值'),
('risk.rule.user-reward-count.window-seconds', 'risk', '86400', 'NUMBER', 0, 'ENABLED', 'R26 R-b 窗口'),
('risk.rule.user-reward-count.threshold', 'risk', '50', 'NUMBER', 0, 'ENABLED', 'R26 R-b 阈值'),
('risk.rule.ip-account-assoc.window-seconds', 'risk', '3600', 'NUMBER', 0, 'ENABLED', 'R26 R-c 窗口'),
('risk.rule.ip-account-assoc.threshold', 'risk', '10', 'NUMBER', 0, 'ENABLED', 'R26 R-c 阈值'),
('risk.rule.device-account-assoc.window-seconds', 'risk', '86400', 'NUMBER', 0, 'ENABLED', 'R26 R-d 窗口'),
('risk.rule.device-account-assoc.threshold', 'risk', '5', 'NUMBER', 0, 'ENABLED', 'R26 R-d 阈值'),
('risk.rule.task-complete-min-seconds', 'risk', '5', 'NUMBER', 0, 'ENABLED', 'R26 R-e 完成耗时下限'),
('risk.rule.ip-claim-rate.window-seconds', 'risk', '60', 'NUMBER', 0, 'ENABLED', 'R26 R-f 窗口'),
('risk.rule.ip-claim-rate.threshold', 'risk', '60', 'NUMBER', 0, 'ENABLED', 'R26 R-f 阈值'),
('risk.fallback-policy', 'risk', 'allow', 'STRING', 0, 'ENABLED', 'R26.3 风控异常降级策略'),
('track.batch.max-size', 'track', '50', 'NUMBER', 0, 'ENABLED', 'R28.3 单批事件数上限'),
('track.event.max-payload-kb', 'track', '8', 'NUMBER', 0, 'ENABLED', 'R28.3 单条 payload 上限'),
('track.unregistered-policy', 'track', 'accept', 'STRING', 0, 'ENABLED', 'R28.6 未登记事件策略'),
('track.disabled-event-policy', 'track', 'drop-count', 'STRING', 0, 'ENABLED', 'R29.2 停用事件策略'),
('track.query.sample-ratio-percent', 'track', '1', 'NUMBER', 0, 'ENABLED', 'R29.3 调试查询抽样比例'),
('ad.material.daily-impression-limit-per-user', 'ad', '10', 'NUMBER', 0, 'ENABLED', 'R30.4 素材日展示上限'),
('ad.popup.cooldown-seconds', 'ad', '600', 'NUMBER', 0, 'ENABLED', 'R30.4 弹窗全局冷却'),
('ad.splash.duration-seconds', 'ad', '3', 'NUMBER', 0, 'ENABLED', 'R30.11 开屏展示时长'),
('ad.carousel.interval-seconds', 'ad', '5', 'NUMBER', 0, 'ENABLED', 'R30.11 轮播切换间隔'),
('internal.timestamp.tolerance-seconds', 'internal', '300', 'NUMBER', 0, 'ENABLED', 'R15.2 时间戳容差'),
('internal.nonce.ttl-seconds', 'internal', '600', 'NUMBER', 0, 'ENABLED', 'R15.2 nonce 去重窗口'),
('metrics.aggregate.max-delay-minutes', 'metrics', '5', 'NUMBER', 0, 'ENABLED', 'R23.6 聚合最大延迟'),
('signin.catchup.window-days', 'signin', '7', 'NUMBER', 0, 'ENABLED', 'R21.3 补签窗口'),
('signin.catchup.daily-limit', 'signin', '1', 'NUMBER', 0, 'ENABLED', 'R21.3 每日补签上限'),
('signin.catchup.cost-points', 'signin', '100', 'NUMBER', 0, 'ENABLED', 'R21.3 补签消耗积分'),
('crowd.max-size', 'crowd', '100000', 'NUMBER', 0, 'ENABLED', 'R11.13 单人群包条目上限'),
('reward.claim.claiming-timeout-seconds', 'reward', '30', 'NUMBER', 0, 'ENABLED', 'R19.2 领取中状态超时回滚'),
('reward.recon.auto-refulfill-enabled', 'reward', 'false', 'BOOL', 0, 'ENABLED', 'R37.7 AUTO 履约重试总闸'),
('activity.new-user-days', 'activity', '7', 'NUMBER', 0, 'ENABLED', 'R22.3 新用户判定天数'),
('retention.audit-days', 'retention', '180', 'NUMBER', 0, 'ENABLED', '数据合规：审计保留天数'),
('retention.event-days', 'retention', '90', 'NUMBER', 0, 'ENABLED', '数据合规：事件保留天数'),
('retention.metrics-days', 'retention', '365', 'NUMBER', 0, 'ENABLED', '数据合规：聚合表保留天数'),
('ad.carousel.max-items', 'ad', '5', 'NUMBER', 0, 'ENABLED', 'R30.5 轮播位单次下发条数上限');
