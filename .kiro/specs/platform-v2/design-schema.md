# 设计文档 · 领域模型与 Schema（§3）

> 本文是 [design.md](design.md) **v2.13** 分册。§ 编号与总册索引一致，引用仍写 design §x.y。
> 需求：[requirements.md](requirements.md) v3.9　选型：[component-selection.md](component-selection.md)
> 总册索引（§ → 锚点）：[design.md](design.md) §0.2。本章跳转：搜索 `<!-- §x.y -->`，不要记行号。

---

<!-- §3 -->
## 3. 领域模型与数据库 Schema

<!-- §3.1 -->
### 3.1 建模总约定（全部表适用）

| 约定 | 规则 |
|------|------|
| 主键 | `BIGINT`；默认 `AUTO_INCREMENT`；仅两张高写入表用雪花 ID（MyBatis-Plus `ASSIGN_ID`）：`evt_event_log`、`task_progress_report`（component-selection §3.6） |
| 时间 | 一律 `DATETIME(3)`，应用层统一写 UTC（附录 C 时间基线）；`created_at`/`updated_at` 由数据库默认值维护 |
| 字符集 | `utf8mb4` / `utf8mb4_0900_ai_ci`（ai_ci 天然大小写不敏感，是"用户名大小写不敏感唯一"的第一道保障，应用层仍统一转小写存储） |
| 布尔 | `TINYINT(1)`，0/1 |
| 枚举 | `VARCHAR(n)` + `CHECK` 约束（MySQL 8.0.16+ 强制），值域在列 COMMENT 与本节各表说明中封闭 |
| JSON | MySQL `JSON` 类型；序列化统一经 kernel JsonUtil（RL-11） |
| 逻辑删除 | 仅 `sys_admin_user`、`sys_portal_user`、`task_definition`、`rwd_prize` 有 `deleted` 字段（与 `status` 独立，R3.4）；其余表物理语义只增或纯配置 |
| 外键 | 不建物理外键（共库大表 + 迁移灵活性），引用完整性由应用层 + 唯一约束保障；关联字段命名 `<表单数>_id` |
| 分区 | `evt_event_log` 按 `server_time` 月 RANGE 分区（首月分区随迁移建立，调度预建未来 3 个月，保留 90 天清理，`retention.event-days`）；`task_progress_report` **不分区**（唯一约束必须含分区键会破坏去重语义，见 §3.3.8），保留 7 天批量清理（代码常量，调整需走附录 A 登记流程） |
| Schema 一致性 | 本节 DDL 即 Flyway 迁移的唯一来源，任务 13/14 据此逐字翻译；字段增删只经新迁移版本（RL-09） |

迁移批次规划（platform-db 内目录）：

| 迁移文件 | 内容 | 对应任务 |
|----------|------|---------|
| `V1__sys_baseline.sql` | 全部 `sys_` 表（含 sys_internal_app）+ 种子（超管角色/初始权限树/预置配置集=附录 A 52 键；含 `track.disabled-event-policy` 默认 drop-count、`reward.recon.auto-refulfill-enabled` 默认 false / 初始字典 province、user_level、user_role、user_tag、task_category、portal_route 8 条目） | 任务 13 |
| `V2__task_core.sql` | 全部 `task_` 表 | 任务 14 |
| `V3__reward_points.sql` | `rwd_`（7 表含分类/对账）+ `pnt_` 表 + 内置分类种子 | 任务 14 / 32 / 35 |
| `V4__risk_tracking.sql` | `risk_` 表 + risk_rule_config 种子（R-a–R-f 六行，默认值=附录 A）+ `evt_event_log`（首月分区，分区名 `pYYYYMM`，`RANGE COLUMNS(server_time) VALUES LESS THAN (次月 1 日 00:00)`，调度预建未来 3 个月）+ `evt_event_metadata`（种子 = 附录 D 全量编码） | 任务 14 / 17–20 |

种子注记（R3.6）：初始超管账号在 V1 中插入 `password_hash = ''`、`must_change_password = 1`；admin-app 启动 `ApplicationRunner` 检测环境变量 `MKT_INIT_ADMIN_PASSWORD`（存在且哈希为空时写入 BCrypt 哈希），未设置且哈希为空则启动告警日志提示。预置配置集默认值 = 附录 A 逐行翻译。

<!-- §3.2 -->
### 3.2 identity 域 + 系统管理（`sys_` 前缀）

#### 3.2.1 sys_admin_user（后台用户，R1/R3）

```sql
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
) COMMENT '后台用户';
```

#### 3.2.2 sys_role / sys_permission / 绑定表（R2）

绑定表 = `sys_admin_user_role`（用户-角色）与 `sys_role_permission`（角色-权限）；§4.2 角色管理/授权端点读写此二表。

```sql
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
) COMMENT '角色';

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
  created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code),
  CHECK (type IN ('MENU','OPERATION')),
  CHECK (type = 'MENU' OR code IS NOT NULL)
) COMMENT '权限树（菜单+操作统一树）';

CREATE TABLE sys_admin_user_role (
  admin_user_id BIGINT NOT NULL,
  role_id       BIGINT NOT NULL,
  PRIMARY KEY (admin_user_id, role_id),
  KEY idx_role (role_id)
) COMMENT '后台用户-角色（多角色并集，允许空）';

CREATE TABLE sys_role_permission (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  KEY idx_permission (permission_id)
) COMMENT '角色-权限';
```

#### 3.2.3 sys_portal_user（门户用户，R4/R5）

```sql
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
) COMMENT '门户用户（档案属性供任务过滤/风控）';
```

#### 3.2.4 sys_dict_type / sys_dict_entry（R7）

```sql
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
) COMMENT '字典类型';

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
) COMMENT '字典项';
```

V1 字典种子 6 类：`province` / `user_level` / `user_role` / `user_tag` / `task_category` / `portal_route`。`portal_route` 封闭 8 条（R11.10 站内跳转 `route` 只许本表 value；任务 13 种子）：

| value | 用途 |
|-------|------|
| `home` | 门户首页（任务列表） |
| `mine` | 个人中心 |
| `task-detail` | 任务详情 |
| `prize-list` | 我的奖品 |
| `prize-detail` | 奖品详情 |
| `points` | 积分 |
| `password` | 修改密码 |
| `signin` | 签到（P1；P0 种子预登记，页面随任务 44） |

#### 3.2.5 sys_config（R8）

```sql
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
) COMMENT '系统配置（读取仅经 ConfigService，RL-11）';
```

#### 3.2.6 sys_audit_log（R10，只增不改不删 RL-12）

```sql
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
) COMMENT '审计日志（经 Outbox 异步写入，保留≥180天 retention.audit-days）';
```

#### 3.2.7 sys_outbox（Outbox，R10.3/R28.9）

```sql
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
) COMMENT '事务性事件外发（与业务同事务写入，RL-07；按 producer 分 Relay）';
```

#### 3.2.8 sys_internal_app（internal 调用方登记，R15.2，D-07）

```sql
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
) COMMENT 'internal 调用方登记（登记/轮换/失效，R15.2）';
```

管理端点契约见 §4.2（identity 域 internal-apps 端点组）；验签读取：portal-app 内存缓存 5min；secret 常量时间比较（R15.2）。

<!-- §3.3 -->
### 3.3 task 域（`task_` 前缀，R11–R16）

#### 3.3.1 修订草稿存储模型（决策点 D-01）

模型为**单套编辑态 + 修订标志**：`task_definition` 与子表（step/transition/action）表达当前编辑态；线上行为只读版本快照（§3.3.7），二者物理隔离，满足 R12.2「修订草稿不影响线上」。数据流：

1. DRAFT 任务：编辑态即草稿，`pending_revision = 0`。
2. PUBLISHED/SCHEDULED 任务被再次编辑：只改编辑态子表，置 `pending_revision = 1`（主状态与线上不变）；C 端读取仍走 `task:published-index` → 快照。
3. 发布修订：version+1、聚合编辑态固化为新快照、`pending_revision = 0`（原子，R12 属性 3）——**仅适用于 PUBLISHED 任务的修订发布；SCHEDULED 任务发布修订草稿 = 置 pending_revision=0、替换待发布内容，不 +version、不生成快照，主状态保持 SCHEDULED（R12.2，端点语义见 §4.4 publish）**。
4. 放弃修订（回到线上版本）：从当前版本快照重置编辑态（读快照 content 写回子表），`pending_revision = 0`。

不设独立修订草稿表集：双套子表同步成本高，需求不要求编辑态长期并存多份。

#### 3.3.2 task_definition（任务主体，R11.1/11.6/11.7/11.8/11.9）

```sql
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
) COMMENT '任务定义（编辑态主体）';
```

#### 3.3.3 task_step / task_step_transition（R11.2/11.3）

```sql
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
) COMMENT '任务步骤（编辑态）';

CREATE TABLE task_step_transition (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id        BIGINT       NOT NULL,
  from_step_id   BIGINT       NOT NULL,
  to_step_id     BIGINT       NOT NULL COMMENT 'to.seq 必须 > from.seq；无环前向由应用层校验器保证（R11 属性 2）',
  condition_expr VARCHAR(1024) NULL COMMENT '空=无条件边；非空走 R11.9 DSL',
  priority       INT          NOT NULL DEFAULT 0 COMMENT '同源边按优先级求值，第一条命中生效（R11.3）',
  UNIQUE KEY uk_edge (from_step_id, to_step_id),
  KEY idx_task_from (task_id, from_step_id, priority)
) COMMENT '步骤转移边（条件分支）';
```

#### 3.3.4 task_platform_action（任务级）/ task_step_platform_action（步骤级）（R11.10/R16）

```sql
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
) COMMENT '任务级平台动作';

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
) COMMENT '步骤级平台动作（合并优先于任务级，R16.2）';
```

#### 3.3.5 task_mutex_group / task_crowd / task_crowd_item（R11.6/11.13）

```sql
CREATE TABLE task_mutex_group (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  code        VARCHAR(64) NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name        VARCHAR(64) NOT NULL,
  cross_cycle TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '跨周期互斥语义（R11.6）',
  created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code)
) COMMENT '互斥组（同组任务周期类型必须一致）';

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
  CHECK (item_count >= 0 AND item_count <= 100000) COMMENT 'crowd.max-size 上限'
) COMMENT '人群包';

CREATE TABLE task_crowd_item (
  id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  crowd_id BIGINT NOT NULL,
  user_id  BIGINT NOT NULL COMMENT '导入时不存在用户跳过（R11.13）',
  UNIQUE KEY uk_crowd_user (crowd_id, user_id),
  KEY idx_user (user_id)
) COMMENT '人群包条目（判定走 task:crowd 缓存）';
```

#### 3.3.6 task_version_snapshot（R12，不可变）

```sql
CREATE TABLE task_version_snapshot (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id      BIGINT      NOT NULL,
  version      INT         NOT NULL,
  content      JSON        NOT NULL COMMENT '全量聚合快照：基本信息+周期+灰度+过滤+步骤+转移+动作+互斥组编码；与实例步骤渲染/分支求值/动作合并的全部输入',
  published_at DATETIME(3) NOT NULL,
  published_by BIGINT      NOT NULL,
  UNIQUE KEY uk_task_version (task_id, version)
) COMMENT '版本快照（写入后禁止 UPDATE/DELETE 服务方法，RL-12 同款架构测试覆盖）';
```

#### 3.3.7 task_instance / task_instance_step（R13/R14）

```sql
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
) COMMENT '任务实例';

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
) COMMENT '实例步骤（冗余快照 code/type；唯一约束+乐观锁双保险）';
```

#### 3.3.8 task_progress_report（进度去重，R14.3/R15.1）

```sql
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
) COMMENT '进度上报去重表（不分区：唯一约束需含分区键会破坏去重语义；保留 7 天定时清理）';
```

<!-- §3.4 -->
### 3.4 reward 域（`rwd_` 前缀，R17–R19、R37）

#### 3.4.0 内置分类种子（R17.8，V3 写入 `rwd_prize_category`）

| code | 名称 | target | fulfill | cost_mode | recon | recon_action_policy | 默认适配器 | 参数 |
|------|------|--------|---------|-----------|-------|---------------------|------------|------|
| POINTS | 积分 | PLATFORM | INSTANT | NONE | 0 | REVIEW（忽略） | — | `{points:正整数}` |
| ALIPAY_RED | 支付宝红包 | THIRD_PARTY | ASYNC | FACE_VALUE | 1 | REVIEW | alipay-red | `{faceFen:正整数}` |
| WECHAT_RED | 微信红包 | THIRD_PARTY | ASYNC | FACE_VALUE | 1 | REVIEW | wechat-red | `{faceFen:正整数}` |
| PHONE_CREDIT | 话费 | THIRD_PARTY | ASYNC | FACE_VALUE | 1 | REVIEW | phone-credit | `{faceFen:正整数}` |
| COUPON | 优惠券 | PLATFORM | INSTANT | NONE | 0 | REVIEW（忽略） | — | 展示名即可 |
| BADGE | 徽章 | PLATFORM | INSTANT | NONE | 0 | REVIEW（忽略） | — | 展示名即可 |
| PHYSICAL | 实物 | PLATFORM | ASYNC | FIXED_UNIT | 0 | REVIEW（忽略） | — | 奖品 `unitCostFen` 必填 |

第三方适配器 P0 为桩：受理后 `SENDING`，回执/确认后到账。新增分类 = INSERT 本表 +（若第三方）登记同名适配器。

#### 3.4.1 rwd_prize_category / rwd_prize_group / rwd_prize（R17.1/R17.8）

```sql
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
) COMMENT '奖品分类目录（可扩展，R17.8）';

CREATE TABLE rwd_prize_group (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  code       VARCHAR(64) NOT NULL COMMENT '4-64 [a-z0-9_-] 唯一',
  name       VARCHAR(64) NOT NULL,
  remark     VARCHAR(255) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_code (code)
) COMMENT '奖品组（业务圈选）';

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
) COMMENT '奖品（分类可扩展；成本与目标自分类快照）';
```

#### 3.4.2 rwd_grant_record（发放记录，R18）

```sql
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
) COMMENT '发放记录（领取七态 + 履约四态 + 成本快照；记录不持有资产）';
```

#### 3.4.3 rwd_stock_log（库存留痕，R17.4）

```sql
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
) COMMENT '库存变更流水（只增）';
```

#### 3.4.4 rwd_recon_batch / rwd_recon_item（渠道对账，R37）

```sql
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
) COMMENT '对账批次（分类+账单日）';

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
) COMMENT '对账明细（只增改动作列与核渠列）';
```

`uk_batch_grant` / `uk_batch_ref` 对 NULL 允许多行（MySQL 唯一索引），CHANNEL_ONLY 无 grant、PLATFORM_ONLY 无 ref 时各走有值一侧。

<!-- §3.5 -->
### 3.5 points 域（`pnt_` 前缀，R20）

```sql
CREATE TABLE pnt_account (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT      NOT NULL,
  balance    BIGINT      NOT NULL DEFAULT 0 COMMENT '≥0；原子 UPDATE 更新，禁读-改-写（R20.6）',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_user (user_id),
  CHECK (balance >= 0)
) COMMENT '积分账户（每门户用户唯一，R20.1）';

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
) COMMENT '积分流水（与变动同事务；扣减截断至零时差额说明写 remark）';
```

<!-- §3.6 -->
### 3.6 risk 域（`risk_` 前缀，R25–R27）

```sql
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
) COMMENT '风控名单（Redis 点查投影，DB 为事实源）';

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
) COMMENT '风控规则配置（默认值=附录 A；变更实时生效+审计 risk:rule:config）';

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
) COMMENT '风控命中记录（唯一事实源；只增不改不删 RL-12）';

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
) COMMENT '人工处置留痕（处置动作本身改名单表，本表只增记录，R27 属性 1）';
```

<!-- §3.7 -->
### 3.7 tracking 域（`evt_` 前缀，R28–R29）

> 事件表统一归 tracking 域、前缀 `evt_`（README 前缀规划）；旧稿的 task_event_log 概念由本表承接（服务端事件经 Outbox 写入，客户端事件经上报端点写入）。

```sql
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
) COMMENT '统一事件表（按 server_time 月 RANGE 分区，调度预建未来 3 个月，保留 90 天；只增 RL-12；客户端行为约束 R28.10/13/14 由前端实现）';
```

#### 3.7.2 evt_event_metadata（事件元数据，R29.1，D-07）

```sql
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
) COMMENT '事件元数据（R28.12/R29.1；V4 种子 = 附录 D 全量）';
```

<!-- §3.8 -->
### 3.8 关键不变量的 DB 层保障汇总

| 需求正确性属性 | DB 层机制 |
|----------------|----------|
| R13.7 实例唯一 | `task_instance.uk_user_task_cycle` + INSERT 冲突转幂等查询 |
| R14 属性 1 推进恰一次 | `task_instance_step.uk_instance_code` + status CHECK + `version` 乐观锁 CAS |
| R14 属性 2 进度去重 | `task_progress_report.uk_dedup`（与累加同事务） |
| R14 属性 3 步骤状态机 | status CHECK + CAS 前置条件（WHERE status='ACTIVE'） |
| R17 属性 1 不超发 | `rwd_prize.remaining_stock >= 0` CHECK + `UPDATE ... SET remaining_stock=remaining_stock-1 WHERE id=? AND remaining_stock>=1` 原子扣减 |
| R17 属性 2 限领原子 | 限领计数走 `rwd_grant_record.idx_prize` 事务内计数 + 限制链 |
| R18 属性 1 发放恰一次 | `rwd_grant_record.uk_idempotent`（资金第二道防线，feasibility §3.1） |
| R18 属性 2 风控零副作用 | 命中即抛异常回滚整个发放事务，仅 `risk_hit_log` 独立落库 |
| R19 属性 1 领取恰一次 | `CLAIMING` CAS（`WHERE status='WON'`）+ Redisson 锁（无唯一约束场景） |
| R19 属性 2 过期不可领取 | 领取事务内兜底校验 expire_at + 定时批量翻转 |
| R20 属性 1 余额非负 | `pnt_account.balance >= 0` CHECK + 原子 UPDATE（禁读-改-写） |
| R20 属性 2 流水轧平 | balance_after 应用层计算 + jqwik 随机序列属性测试（§7） |
| R12 属性 1 快照不可变 | 快照表无 UPDATE/DELETE 服务方法（架构测试） |
| R3/R4 用户名唯一（大小写不敏感） | ai_ci 排序规则 uk + 应用层小写归一 |
| R25.2 名单唯一/黑优先 | `uk_dim_type_value` + 判定顺序黑名单先行 |
| R10/R28 只增不改不删 | 三张日志表无更新删除方法与端点（RL-12） |
| P0 补发不超发 | 补发同走原子扣减（R17.5）+ `rwd_stock_log` 留痕 CHECK |

<!-- §3.9 -->
### 3.9 错误码分段规划（附录 C 格式 `<域>.<场景>.<原因>`）

> 本章落定**域与场景分段**；每个端点的完整错误码清单在 §4 API 契约逐端点列出（同一分段内新增原因值不需扩展本表）。**原因值拼写以 §4 逐端点清单为唯一权威，本列仅为高频值索引，二者不一致时以 §4 为准。**

| 域前缀 | 场景（<域>.<场景>.<原因> 的中段） | 典型原因值示例 |
|--------|-----------------------------------|----------------|
| `auth` | `login` / `captcha` / `session` / `profile` / `username` / `account` / `password` / `register` / `role` / `user` | locked、invalid-credential、captcha-expired、kicked-concurrent、kicked-admin、expired、missing、invalid、disabled、old-mismatch、policy-violated、rate-limited、built-in、self-protected、duplicate、invalid-format、nickname-invalid |
| `dict` / `config` / `cache` | `dict` / `entry`、`config` / `value`、`cache` / `namespace` | duplicate-code、duplicate-value、invalid-value-type、type-mismatch、unknown、not-found |
| `task` | `definition` / `publish` / `expression` / `mutex` / `crowd` / `claim` / `step` / `progress` / `instance` | validate-failed、mutex-cycle-mismatch、not-visible、mutex-blocked、daily-limit、state-mismatch |
| `reward` | `prize` / `stock` / `grant` / `claim` / `fulfill` / `category` / `recon` | disabled、insufficient、not-retryable、not-won、limit-exceeded、combo-invalid、adapter-required、not-sending、cost-required、face-required、duplicate-day、review-required、action-forbidden、not-pending-review |
| `points` | `account` / `transaction` | insufficient-balance、reason-required |
| `risk` | `blocked`（C 端仅通用，不暴露具体规则，R34.6）/ `list` / `rule` / `case`（管理端） | generic、account-restricted、register、login、duplicate-returned、range-violated |
| `track` | `batch` / `metadata` / `query` | overflow、rate-limited、duplicate-code |
| `internal` | `sign` / `app` / `nonce` / `timestamp` | invalid-signature、disabled、replayed、skew-exceeded |
| `ad` / `signin` / `activity`（P1） | `position` / `material` / `signin` / `activity` | — |
| `common`（两段式公共码，无场景段） | —（§4.1 定义封闭 5 值） | param-invalid、permission-denied、rate-limited、not-found、server-error |

<!-- §3.10 -->
### 3.10 Redis 侧数据结构索引（DB 之外的事实/投影）

| 键模式 | 类型 | 说明 | 需求依据 |
|--------|------|------|---------|
| Sa-Token 会话（admin/client 双 StpLogic） | 内置 | Redis 集中会话（R6.4）；踢下线/并发上限原生能力 | R1/R4/R6 |
| `captcha:{id}` | STRING + TTL = `auth.captcha.ttl-seconds`（默认 120） | 验证码答案，一次性 | R1.7 |
| `lock:{scope}` | Redisson RLock | 调度恰一、奖品领取（锁键族 = `lock:*` / `sched:*` / `outbox:relay:*`；领取锁 scope = `rwd-claim:{recordId}`） | NFR 可用性 3 |
| `rl:{dim}:{key}` | ZSET + Lua 滑窗 | 限流（附录 A 阈值；同一脚本，按 key 分桶） | R1.11 等 |
| `risk:list:{dim}:{type}:{value}` | STRING（值 = expire_at 的 epoch millis；**永久 = -1**；限期条目键 TTL = 剩余时长，到期自动消失，miss 后 DB 回源过滤） | 名单点查投影（DB 事实源，变更同步） | R25 |
| `risk:cnt:{rule}:{dim}:{value}` | ZSET（score=时间戳） | 频率/关联类规则滑窗计数 | R26.2 |
| `nonce:{appId}:{nonce}` | SETNX + TTL = `internal.nonce.ttl-seconds`（默认 600） | internal 防重放 | R15.2 |
| `session:kick-reason:{loginType}:{token}` | STRING 短 TTL | 踢下线原因（D-02，§6.1） | R6.2 |
| `cache:evict` | pub/sub 频道 | 两级缓存本地失效广播（§6.2） | R9.3 |
| `<namespace>:<业务键>`（R9.1 封闭清单） | Spring Cache 两级（Caffeine L1 + Redis L2） | dict/config/rbac:permission/task:snapshot/task:published-index/task:crowd/risk:rule/identity:user-attr（D-06）；`ad:position` TTL 60s（任务 48 接线） | R9.1 |
