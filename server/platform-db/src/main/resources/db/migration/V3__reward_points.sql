-- V3 reward + points (design §3.1 / §3.4 / §3.5). Charset per §3.1.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE rwd_prize_category (
  code              VARCHAR(32)  NOT NULL COMMENT '4-32 [A-Z0-9_]',
  name              VARCHAR(64)  NOT NULL,
  reward_target     VARCHAR(16)  NOT NULL COMMENT 'PLATFORM|THIRD_PARTY',
  fulfillment_mode  VARCHAR(16)  NOT NULL COMMENT 'INSTANT|ASYNC',
  cost_mode         VARCHAR(16)  NOT NULL COMMENT 'NONE|FIXED_UNIT|FACE_VALUE（R17.9）',
  recon_required    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=须渠道对账（R37）',
  recon_action_policy VARCHAR(16) NOT NULL DEFAULT 'REVIEW' COMMENT 'REVIEW|AUTO（R37.7；recon_required=0 时忽略）',
  adapter_code      VARCHAR(32)  NULL COMMENT 'THIRD_PARTY 默认适配器',
  param_schema      JSON         NULL COMMENT 'type_params 契约（points / faceFen 等）',
  builtin           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '1=内置不可删',
  status            VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED|DISABLED',
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (code),
  CHECK (reward_target IN ('PLATFORM','THIRD_PARTY')),
  CHECK (fulfillment_mode IN ('INSTANT','ASYNC')),
  CHECK (cost_mode IN ('NONE','FIXED_UNIT','FACE_VALUE')),
  CHECK (status IN ('ENABLED','DISABLED')),
  CHECK (recon_action_policy IN ('REVIEW','AUTO')),
  CHECK (reward_target <> 'THIRD_PARTY' OR fulfillment_mode = 'ASYNC'),
  CHECK (NOT (code = 'POINTS') OR (reward_target = 'PLATFORM' AND fulfillment_mode = 'INSTANT' AND cost_mode = 'NONE'))
) COMMENT '奖品分类目录（可扩展，R17.8）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rwd_prize_group (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(64) NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name       VARCHAR(64) NOT NULL,
  remark     VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code)
) COMMENT '奖品组（业务圈选）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rwd_prize (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  code              VARCHAR(64)  NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name              VARCHAR(128) NOT NULL,
  image_url         VARCHAR(512) NULL COMMENT '外链',
  description       VARCHAR(1024) NULL,
  category_code     VARCHAR(32)  NOT NULL COMMENT 'rwd_prize_category.code',
  type_params       JSON         NULL COMMENT '按分类 param_schema：POINTS.points / FACE_VALUE.faceFen / adapterCode 覆盖',
  reward_target     VARCHAR(16)  NOT NULL COMMENT '草稿保存自分类拷贝；启用后冻结',
  fulfillment_mode  VARCHAR(16)  NOT NULL COMMENT '同上',
  unit_cost_fen     INT          NULL COMMENT 'FIXED_UNIT 必填 >0（人民币分）',
  total_stock       INT          NOT NULL COMMENT '正整数',
  remaining_stock   INT          NOT NULL COMMENT '≥0 CHECK + 原子 UPDATE 扣减（R17.2）',
  daily_claim_limit INT          NOT NULL DEFAULT 0 COMMENT '0=不限',
  total_claim_limit INT          NOT NULL DEFAULT 0 COMMENT '0=不限',
  region_limit      JSON         NULL COMMENT '省份码数组，空=不限',
  level_limit       JSON         NULL COMMENT '等级码数组，空=不限',
  tag_limit         JSON         NULL COMMENT '标签码数组，空=不限',
  claim_mode        VARCHAR(16)  NOT NULL COMMENT 'AUTO|MANUAL',
  recon_action_policy VARCHAR(16) NULL COMMENT 'NULL=继承分类；REVIEW|AUTO 覆盖（R37.7，启用后可改）',
  expire_hours      INT          NULL COMMENT '发放后 N 小时过期；NULL=永不；积分类=积分 expireAt 唯一来源（R17.1/R20.4）',
  group_id          BIGINT       NULL,
  status            VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|ENABLED|DISABLED；流转 R17.1',
  ext_config        JSON         NULL,
  deleted           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '仅 DRAFT 可删；被在线快照引用禁删（R11.12/R17.1，应用层扫 task_version_snapshot.content）',
  created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  KEY idx_category_status (category_code, status, deleted),
  CHECK (reward_target IN ('PLATFORM','THIRD_PARTY')),
  CHECK (fulfillment_mode IN ('INSTANT','ASYNC')),
  CHECK (status IN ('DRAFT','ENABLED','DISABLED')),
  CHECK (claim_mode IN ('AUTO','MANUAL')),
  CHECK (recon_action_policy IN ('REVIEW','AUTO') OR recon_action_policy IS NULL),
  CHECK (reward_target <> 'THIRD_PARTY' OR fulfillment_mode = 'ASYNC'),
  CHECK (unit_cost_fen IS NULL OR unit_cost_fen > 0),
  CHECK (remaining_stock >= 0 AND remaining_stock <= total_stock),
  CHECK (total_stock > 0)
) COMMENT '奖品（分类可扩展；成本与目标自分类快照）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rwd_grant_record (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  prize_id          BIGINT      NOT NULL,
  prize_code        VARCHAR(64) NOT NULL COMMENT '冗余展示',
  category_code     VARCHAR(32) NOT NULL COMMENT '发放时分类快照（R17.9）',
  face_fen          INT         NULL COMMENT '面额快照（分）；FACE_VALUE 必填',
  cost_fen          INT         NOT NULL DEFAULT 0 COMMENT '成本快照（分）；NONE=0（R17.9）',
  recon_status      VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|PENDING|MATCHED|DIFF（R37；无须对账=NONE）',
  user_id           BIGINT      NOT NULL,
  grant_source      VARCHAR(32) NOT NULL COMMENT 'TASK_STEP|SIGNIN_DAY|ACTIVITY_PARTICIPATION|MANUAL_GRANT|SIMULATE（R18.4 封闭枚举）',
  source_id         VARCHAR(64) NOT NULL COMMENT '实例步骤执行记录ID/签到记录ID/活动参与记录ID/补发申请单ID/模拟操作单ID',
  status            VARCHAR(20) NOT NULL COMMENT '领取七态 PENDING|WON|CLAIMING|GRANTED|RETRY_PENDING|PERMANENT_FAILED|EXPIRED（R18.5）',
  fulfillment_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '履约 NONE|SENDING|ARRIVED|FULFILL_FAILED（R18.3）；记录不持有资产',
  fulfillment_ref   VARCHAR(64) NULL COMMENT '第三方差号 / 本平台确认单号',
  fail_reason       VARCHAR(32) NULL COMMENT '可重试：SYSTEM_ERROR|STOCK_INSUFFICIENT；永久：PRIZE_DISABLED|PRIZE_DELETED|USER_INVALID（R14.5 封闭枚举）',
  fulfill_fail_reason VARCHAR(32) NULL COMMENT 'TIMEOUT|ADAPTER_ERROR|CHANNEL_REJECT|CALLBACK_FAILED|MANUAL（R18.3）',
  retry_count       INT         NOT NULL DEFAULT 0 COMMENT '自动+手动共享上限 reward.grant.retry-max / claim 重试共享 reward.claim.retry-max',
  next_retry_at     DATETIME(3) NULL COMMENT '重试退避到期时刻（§6.7 调度 3 扫描列）',
  next_fulfill_retry_at DATETIME(3) NULL COMMENT '履约重试到期（§6.7 调度 10）',
  expire_at         DATETIME(3) NULL COMMENT 'MANUAL 型=进入 WON 时刻+expire_hours；NULL=永不',
  claimed_at        DATETIME(3) NULL,
  granted_at        DATETIME(3) NULL,
  fulfilled_at      DATETIME(3) NULL,
  simulated         TINYINT(1)  NOT NULL DEFAULT 0,
  created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_idempotent (grant_source, source_id, prize_id) COMMENT '发放恰一次的第二道防线（R18.4）',
  KEY idx_user_status (user_id, status, created_at) COMMENT '我的奖品（R19.1）',
  KEY idx_status_expire (status, expire_at) COMMENT '过期翻转扫描（R19.3）与 CLAIMING 超时回滚扫描（R19.2）',
  KEY idx_fulfill_retry (fulfillment_status, next_fulfill_retry_at) COMMENT '履约重试与发送中超时（§6.7 调度 10）',
  UNIQUE KEY uk_fulfillment_ref (fulfillment_ref),
  KEY idx_prize (prize_id, status) COMMENT '限领计数（发放事务内）',
  KEY idx_recon (category_code, recon_status, granted_at) COMMENT '待对账扫描（R37）',
  CHECK (grant_source IN ('TASK_STEP','SIGNIN_DAY','ACTIVITY_PARTICIPATION','MANUAL_GRANT','SIMULATE')),
  CHECK (status <> 'RETRY_PENDING' OR next_retry_at IS NOT NULL),
  CHECK (status IN ('PENDING','WON','CLAIMING','GRANTED','RETRY_PENDING','PERMANENT_FAILED','EXPIRED')),
  CHECK (fulfillment_status IN ('NONE','SENDING','ARRIVED','FULFILL_FAILED')),
  CHECK (recon_status IN ('NONE','PENDING','MATCHED','DIFF')),
  CHECK (cost_fen >= 0),
  CHECK (fail_reason IN ('SYSTEM_ERROR','STOCK_INSUFFICIENT','PRIZE_DISABLED','PRIZE_DELETED','USER_INVALID') OR fail_reason IS NULL),
  CHECK (fulfill_fail_reason IN ('TIMEOUT','ADAPTER_ERROR','CHANNEL_REJECT','CALLBACK_FAILED','MANUAL') OR fulfill_fail_reason IS NULL)
) COMMENT '发放记录（领取七态 + 履约四态 + 成本快照；记录不持有资产）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rwd_stock_log (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  prize_id    BIGINT      NOT NULL,
  change_type VARCHAR(24) NOT NULL COMMENT 'GRANT|ROLLBACK|MANUAL_GRANT|REPLENISH|SIMULATE_REVERSE',
  amount      INT         NOT NULL COMMENT '扣减为负/回补为正',
  before_value INT        NOT NULL,
  after_value  INT        NOT NULL COMMENT '= before + amount（不变量）',
  biz_source  VARCHAR(32) NULL COMMENT '关联 grant_record.grant_source',
  biz_id      VARCHAR(64) NULL COMMENT '关联发放记录 ID / 补发单 ID',
  operator_id BIGINT      NULL COMMENT '手动操作',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_prize_time (prize_id, created_at),
  CHECK (after_value = before_value + amount),
  CHECK (after_value >= 0)
) COMMENT '库存变更流水（只增）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rwd_recon_batch (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_code   VARCHAR(32) NOT NULL,
  bill_date       DATE        NOT NULL COMMENT '账单日（业务日 UTC+8）',
  status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|MATCHING|DONE',
  platform_count  INT         NOT NULL DEFAULT 0,
  channel_count   INT         NOT NULL DEFAULT 0,
  matched_count   INT         NOT NULL DEFAULT 0,
  platform_only   INT         NOT NULL DEFAULT 0,
  channel_only    INT         NOT NULL DEFAULT 0,
  amount_mismatch INT         NOT NULL DEFAULT 0,
  operator_id     BIGINT      NULL,
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_cat_day (category_code, bill_date),
  CHECK (status IN ('DRAFT','MATCHING','DONE'))
) COMMENT '对账批次（分类+账单日）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE rwd_recon_item (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  batch_id            BIGINT      NOT NULL,
  grant_record_id     BIGINT      NULL COMMENT '平台侧；CHANNEL_ONLY 可空',
  fulfillment_ref     VARCHAR(64) NULL,
  platform_cost_fen   INT         NULL,
  channel_amount_fen  INT         NULL,
  result              VARCHAR(24) NOT NULL COMMENT 'MATCHED|PLATFORM_ONLY|CHANNEL_ONLY|AMOUNT_MISMATCH',
  action              VARCHAR(24) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|REFULFILL|MANUAL_GRANT|ABSORB|LEDGER_ONLY',
  action_ref          VARCHAR(64) NULL COMMENT '补发 grant id / 认领单号',
  review_status       VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE|PENDING_REVIEW|CONFIRMED|REJECTED（R37.7）',
  reviewer_id         BIGINT      NULL,
  reviewed_at         DATETIME(3) NULL,
  review_remark       VARCHAR(255) NULL,
  remark              VARCHAR(255) NULL,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_batch (batch_id, result),
  UNIQUE KEY uk_batch_grant (batch_id, grant_record_id),
  UNIQUE KEY uk_batch_ref (batch_id, fulfillment_ref),
  CHECK (result IN ('MATCHED','PLATFORM_ONLY','CHANNEL_ONLY','AMOUNT_MISMATCH')),
  CHECK (action IN ('NONE','REFULFILL','MANUAL_GRANT','ABSORB','LEDGER_ONLY')),
  CHECK (review_status IN ('NONE','PENDING_REVIEW','CONFIRMED','REJECTED'))
) COMMENT '对账明细（只增改动作列与核渠列）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pnt_account (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT      NOT NULL,
  balance    BIGINT      NOT NULL DEFAULT 0 COMMENT '≥0；原子 UPDATE 更新，禁读-改-写（R20.6）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user (user_id),
  CHECK (balance >= 0)
) COMMENT '积分账户（每门户用户唯一，R20.1）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pnt_transaction (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT      NOT NULL,
  type          VARCHAR(16) NOT NULL COMMENT 'EARN|CONSUME|EXPIRE|ADJUST|REVERSAL（R20.2）',
  amount        BIGINT      NOT NULL COMMENT 'EARN/ADJUST正为正数；CONSUME/EXPIRE/REVERSAL为负',
  balance_after BIGINT      NOT NULL COMMENT '轧平：上一条 balance_after ± amount（R20 属性 2）',
  biz_source    VARCHAR(32) NULL COMMENT '同 grant_source 枚举语义',
  biz_id        VARCHAR(64) NULL,
  expire_at     DATETIME(3) NULL COMMENT '仅 EARN：来自奖品 expire_hours（唯一来源 R17.1/R20.4）',
  remark        VARCHAR(255) NULL COMMENT '手动调整必填原因（R20.3）',
  simulated     TINYINT(1)  NOT NULL DEFAULT 0,
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_user_time (user_id, created_at) COMMENT 'C 端流水分页',
  KEY idx_expire_scan (type, expire_at) COMMENT '每日 00:05 过期扫描（R20.4）',
  CHECK (type IN ('EARN','CONSUME','EXPIRE','ADJUST','REVERSAL')),
  CHECK (balance_after >= 0)
) COMMENT '积分流水（与变动同事务；扣减截断至零时差额说明写 remark）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 内置分类种子（design §3.4.0 / R17.8）；须对账三类 recon_action_policy=REVIEW
INSERT INTO rwd_prize_category (
  code, name, reward_target, fulfillment_mode, cost_mode,
  recon_required, recon_action_policy, adapter_code, param_schema, builtin, status
) VALUES
('POINTS',       '积分',     'PLATFORM',     'INSTANT', 'NONE',       0, 'REVIEW', NULL,            '{"points":"正整数"}',  1, 'ENABLED'),
('ALIPAY_RED',   '支付宝红包', 'THIRD_PARTY', 'ASYNC',   'FACE_VALUE', 1, 'REVIEW', 'alipay-red',    '{"faceFen":"正整数"}', 1, 'ENABLED'),
('WECHAT_RED',   '微信红包',   'THIRD_PARTY', 'ASYNC',   'FACE_VALUE', 1, 'REVIEW', 'wechat-red',    '{"faceFen":"正整数"}', 1, 'ENABLED'),
('PHONE_CREDIT', '话费',     'THIRD_PARTY',  'ASYNC',   'FACE_VALUE', 1, 'REVIEW', 'phone-credit',  '{"faceFen":"正整数"}', 1, 'ENABLED'),
('COUPON',       '优惠券',    'PLATFORM',     'INSTANT', 'NONE',       0, 'REVIEW', NULL,            NULL,                   1, 'ENABLED'),
('BADGE',        '徽章',     'PLATFORM',     'INSTANT', 'NONE',       0, 'REVIEW', NULL,            NULL,                   1, 'ENABLED'),
('PHYSICAL',     '实物',     'PLATFORM',     'ASYNC',   'FIXED_UNIT', 0, 'REVIEW', NULL,            NULL,                   1, 'ENABLED');
