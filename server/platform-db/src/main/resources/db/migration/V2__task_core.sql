-- V2 task domain (design §3.1 / §3.3). Charset per §3.1.
SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE task_definition (
  id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
  code                    VARCHAR(64)  NOT NULL COMMENT '4-64 [a-z0-9_-] 全局唯一',
  name                    VARCHAR(128) NOT NULL,
  description             VARCHAR(1024) NULL,
  category                VARCHAR(64)  NULL COMMENT '字典 task_category（R11.1）',
  icon_url                VARCHAR(512) NULL,
  badge_text              VARCHAR(16)  NULL COMMENT '角标如 热门/新人',
  start_time              DATETIME(3)  NULL,
  end_time                DATETIME(3)  NULL COMMENT '开始<结束 校验（R12.3）',
  sort_weight             INT          NOT NULL DEFAULT 0,
  status                  VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT|SCHEDULED|PUBLISHED|OFFLINE，状态机 R12.1',
  version                 INT          NOT NULL DEFAULT 0 COMMENT '未发布=0，发布+1（R12.1）',
  schedule_publish_at     DATETIME(3)  NULL COMMENT '定时发布时刻；须早于时间窗结束（R12.3）',
  cycle_type              VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE|DAILY|MONTHLY|CRON|SPECIAL（R11.7）',
  cron_expr               VARCHAR(32)  NULL COMMENT '5段标准表达式，最小粒度分钟，间隔≥1小时（R11.7）',
  special_start           DATETIME(3)  NULL COMMENT '自定义时段起（SPECIAL 用）',
  special_end             DATETIME(3)  NULL COMMENT '自定义时段止',
  mutex_group_id          BIGINT       NULL COMMENT '至多一个互斥组（R11.6）；同组任务周期类型必须一致（应用层校验）',
  gray_type               VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE|RATIO|AB|CROWD（R11.8）',
  gray_ratio              TINYINT      NULL COMMENT 'RATIO：0-100，bucket<ratio 可见',
  gray_ab_group           VARCHAR(4)   NULL COMMENT 'AB：A|B|AB 为可见组',
  gray_crowd_id           BIGINT       NULL COMMENT 'CROWD：允许人群包（R11.8）',
  gray_exclude_crowd_id   BIGINT       NULL COMMENT 'CROWD：排除人群包，可空；与过滤人群包独立',
  filter_expr             VARCHAR(1024) NULL COMMENT '过滤表达式（R11.9 DSL）',
  filter_allow_crowd_ids  JSON         NULL COMMENT '允许人群包 ID 数组',
  filter_exclude_crowd_ids JSON        NULL COMMENT '排除人群包 ID 数组',
  pending_revision        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '修订草稿标记（D-01）',
  offline_at              DATETIME(3)  NULL COMMENT '最近下线时刻，expireAt 计算输入（R14.10）',
  deleted                 TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '仅未发布可删（R11.12）',
  created_by              BIGINT       NULL,
  created_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  KEY idx_status_sort (status, deleted, sort_weight) COMMENT '管理列表',
  KEY idx_publish_scan (status, schedule_publish_at) COMMENT '定时发布扫描（R12.5）',
  CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','OFFLINE')),
  CHECK (cycle_type IN ('NONE','DAILY','MONTHLY','CRON','SPECIAL')),
  CHECK (gray_type IN ('NONE','RATIO','AB','CROWD'))
) COMMENT '任务定义（编辑态主体）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_step (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id         BIGINT      NOT NULL,
  seq             INT         NOT NULL COMMENT '顺序号；转移边目标恒大于来源（R11 属性 2）',
  code            VARCHAR(64) NOT NULL COMMENT '任务内唯一',
  name            VARCHAR(128) NOT NULL,
  type            VARCHAR(16) NOT NULL COMMENT 'PASSIVE|CLICK|CALLBACK|PROGRESS|REWARD',
  progress_target INT         NULL COMMENT 'PROGRESS 必填，1–2^31-1（R11.2）',
  prize_id        BIGINT      NULL COMMENT 'REWARD 必填，引用 rwd_prize.id（发布校验启用状态）',
  UNIQUE KEY uk_task_code (task_id, code),
  KEY idx_task_seq (task_id, seq),
  CHECK (type IN ('PASSIVE','CLICK','CALLBACK','PROGRESS','REWARD')),
  CHECK (type <> 'PROGRESS' OR progress_target IS NOT NULL),
  CHECK (type <> 'REWARD' OR prize_id IS NOT NULL)
) COMMENT '任务步骤（编辑态）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_step_transition (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id        BIGINT       NOT NULL,
  from_step_id   BIGINT       NOT NULL,
  to_step_id     BIGINT       NOT NULL COMMENT 'to.seq 必须 > from.seq；无环前向由应用层校验器保证（R11 属性 2）',
  condition_expr VARCHAR(1024) NULL COMMENT '空=无条件边；非空走 R11.9 DSL',
  priority       INT          NOT NULL DEFAULT 0 COMMENT '同源边按优先级求值，第一条命中生效（R11.3）',
  UNIQUE KEY uk_edge (from_step_id, to_step_id),
  KEY idx_task_from (task_id, from_step_id, priority)
) COMMENT '步骤转移边（条件分支）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_platform_action (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id     BIGINT       NOT NULL,
  platform    VARCHAR(16)  NOT NULL COMMENT 'WEB|ANDROID|IOS|MINIAPP|SIMULATOR',
  action_type VARCHAR(16)  NOT NULL COMMENT 'NONE|ROUTE|LINK|SCHEME（R11.10 封闭 schema）',
  params      JSON         NULL COMMENT '按类型：{"route","params"} / {"url"} / {"scheme"}',
  button_text VARCHAR(16)  NULL COMMENT '公共可选参数',
  UNIQUE KEY uk_task_platform (task_id, platform),
  CHECK (platform IN ('WEB','ANDROID','IOS','MINIAPP','SIMULATOR')),
  CHECK (action_type IN ('NONE','ROUTE','LINK','SCHEME'))
) COMMENT '任务级平台动作' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_step_platform_action (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  step_id     BIGINT       NOT NULL,
  platform    VARCHAR(16)  NOT NULL,
  action_type VARCHAR(16)  NOT NULL,
  params      JSON         NULL,
  button_text VARCHAR(16)  NULL,
  UNIQUE KEY uk_step_platform (step_id, platform),
  CHECK (platform IN ('WEB','ANDROID','IOS','MINIAPP','SIMULATOR')),
  CHECK (action_type IN ('NONE','ROUTE','LINK','SCHEME'))
) COMMENT '步骤级平台动作（合并优先于任务级，R16.2）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_mutex_group (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(64) NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name        VARCHAR(64) NOT NULL,
  cross_cycle TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '跨周期互斥语义（R11.6）',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code)
) COMMENT '互斥组（同组任务周期类型必须一致）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_crowd (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(64) NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name       VARCHAR(64) NOT NULL,
  item_count INT         NOT NULL DEFAULT 0 COMMENT '导入去重后的有效条目数（冗余，事务内维护）',
  status     VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '停用=判定不命中并告警（R11.9）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  CHECK (status IN ('ENABLED','DISABLED')),
  CHECK (item_count >= 0 AND item_count <= 100000)
) COMMENT '人群包' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_crowd_item (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  crowd_id BIGINT NOT NULL,
  user_id  BIGINT NOT NULL COMMENT '导入时不存在用户跳过（R11.13）',
  UNIQUE KEY uk_crowd_user (crowd_id, user_id),
  KEY idx_user (user_id)
) COMMENT '人群包条目（判定走 task:crowd 缓存）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_version_snapshot (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id      BIGINT      NOT NULL,
  version      INT         NOT NULL,
  content      JSON        NOT NULL COMMENT '全量聚合快照：基本信息+周期+灰度+过滤+步骤+转移+动作+互斥组编码；与实例步骤渲染/分支求值/动作合并的全部输入',
  published_at DATETIME(3) NOT NULL,
  published_by BIGINT      NOT NULL,
  UNIQUE KEY uk_task_version (task_id, version)
) COMMENT '版本快照（写入后禁止 UPDATE/DELETE 服务方法，RL-12 同款架构测试覆盖）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_instance (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id        BIGINT      NOT NULL,
  task_code      VARCHAR(64) NOT NULL COMMENT '冗余，三元组定位（R15.1）',
  version        INT         NOT NULL,
  snapshot_id    BIGINT      NOT NULL COMMENT '绑定快照，全生命周期不变（R12 属性 1）',
  user_id        BIGINT      NOT NULL,
  cycle_key      VARCHAR(40) NOT NULL COMMENT '≤40，生成规则 R11.7（UTC+8）',
  status         VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS|COMPLETED|ABANDONED|EXPIRED（终态 CAS）',
  abandon_source VARCHAR(16) NULL COMMENT 'USER|ADMIN（R13.9/R14.8）',
  abandoned_at   DATETIME(3) NULL,
  expire_at      DATETIME(3) NOT NULL COMMENT 'min(时间窗结束,下线时刻)+N天（R14.10）',
  started_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  completed_at   DATETIME(3) NULL,
  cost_seconds   INT         NULL COMMENT '实例终态耗时（秒）；查询/看板用。R-e 不读本列，见 §5.9 / D-09',
  simulated      TINYINT(1)  NOT NULL DEFAULT 0,
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user_task_cycle (user_id, task_id, cycle_key) COMMENT '实例唯一性与幂等领取（R13.7）',
  KEY idx_task_status (task_id, status) COMMENT '互斥占用查询（R11.6）',
  KEY idx_user_status (user_id, status, created_at) COMMENT '我的任务视图（R13.3）',
  KEY idx_expire (status, expire_at) COMMENT '过期批量扫描（R14.10）',
  CHECK (status IN ('IN_PROGRESS','COMPLETED','ABANDONED','EXPIRED')),
  CHECK (abandon_source IN ('USER','ADMIN') OR abandon_source IS NULL)
) COMMENT '任务实例' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_instance_step (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  instance_id      BIGINT      NOT NULL,
  step_code        VARCHAR(64) NOT NULL COMMENT '引用快照内步骤编码（不引用编辑态 step 行，编辑态变更不影响实例）',
  seq              INT         NOT NULL COMMENT '快照冗余',
  type             VARCHAR(16) NOT NULL COMMENT '快照冗余（PASSIVE|CLICK|CALLBACK|PROGRESS|REWARD）',
  status           VARCHAR(16) NOT NULL DEFAULT 'INACTIVE' COMMENT 'INACTIVE|ACTIVE|COMPLETED|SKIPPED（状态机 R14 属性 3）',
  progress_current INT         NOT NULL DEFAULT 0 COMMENT 'PROGRESS 累计值',
  version          INT         NOT NULL DEFAULT 0 COMMENT '乐观锁（推进 CAS，feasibility §3.1）',
  activated_at     DATETIME(3) NULL,
  completed_at     DATETIME(3) NULL,
  skip_reason      VARCHAR(32) NULL COMMENT 'SKIPPED 时原因：GRANT_PERMANENT_FAILED（R14.5；其余跳过场景 NULL）',
  last_biz_no      VARCHAR(64) NULL COMMENT '最近一次 CALLBACK 携带的 bizNo（对账线索，多次回调覆盖为最新，R15.1）',
  created_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_instance_code (instance_id, step_code),
  KEY idx_instance_seq (instance_id, seq),
  CHECK (status IN ('INACTIVE','ACTIVE','COMPLETED','SKIPPED')),
  CHECK (skip_reason IS NULL OR skip_reason = 'GRANT_PERMANENT_FAILED'),
  CHECK (type IN ('PASSIVE','CLICK','CALLBACK','PROGRESS','REWARD'))
) COMMENT '实例步骤（冗余快照 code/type；唯一约束+乐观锁双保险）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_progress_report (
  id          BIGINT      NOT NULL COMMENT '雪花 ASSIGN_ID（高并发插入）',
  instance_id BIGINT      NOT NULL,
  step_code   VARCHAR(64) NOT NULL,
  report_id   VARCHAR(64) NOT NULL COMMENT '调用方业务流水号',
  value       INT         NOT NULL COMMENT '单次上报值 1–1000（R11.2）',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dedup (instance_id, step_code, report_id) COMMENT '与累加同事务的崩溃安全去重',
  KEY idx_created (created_at) COMMENT '7 天保留期批量清理'
) COMMENT '进度上报去重表（不分区：唯一约束需含分区键会破坏去重语义；保留 7 天定时清理）' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
