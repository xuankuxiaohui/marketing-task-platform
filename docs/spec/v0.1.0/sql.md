# v0.1.0 SQL 文档

## 迁移文件索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `backend/src/main/resources/db/migration/V1__init_task_core.sql` | 任务配置核心表：`task`、`task_step`、`task_step_platform`、`task_filter`、`task_platform` |
| V2 | `backend/src/main/resources/db/migration/V2__init_task_instance.sql` | 用户任务实例表：`user_task_instance`、`user_task_step_progress` |

Flyway 会在 Spring Boot 启动时自动执行这些迁移。配置见 `backend/src/main/resources/application-dev.yml`。

## 表关系

```text
task ──┬── task_step ── task_step_platform
       ├── task_filter
       └── task_platform

user_task_instance ── user_task_step_progress
```

## `task`

任务主体表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | MyBatis-Plus assign_id |
| `code` | VARCHAR(64) | NOT NULL, UNIQUE | 业务编码 |
| `name` | VARCHAR(128) | NOT NULL | 任务名称 |
| `description` | VARCHAR(512) | NULL | 任务描述 |
| `period_type` | VARCHAR(16) | NOT NULL | ONCE / DAILY / MONTHLY / CRON / SPECIAL |
| `cron_expr` | VARCHAR(64) | NULL | CRON 任务表达式 |
| `special_cycle_key` | VARCHAR(64) | NULL | SPECIAL 任务周期键 |
| `start_time` | DATETIME | NULL | 上架时间 |
| `end_time` | DATETIME | NULL | 下架时间 |
| `status` | VARCHAR(16) | NOT NULL DEFAULT DRAFT | DRAFT / PUBLISHED / OFFLINE |
| `version` | INT | NOT NULL DEFAULT 0 | 发布版本 |
| `mutex_group_key` | VARCHAR(64) | NULL | 互斥组，占位 |
| `created_by` | VARCHAR(64) | NULL | 创建人 |
| `updated_by` | VARCHAR(64) | NULL | 更新人 |
| `created_at` | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| `updated_at` | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

### 索引

| 索引 | 字段 | 作用 |
|---|---|---|
| `uk_task_code` | `code` | 防止任务业务编码重复 |
| `idx_task_status_time` | `status`, `start_time`, `end_time` | 支持查询可展示的已发布任务 |
| `idx_task_mutex_group` | `mutex_group_key` | 为后续互斥查询预留 |

## `task_step`

任务步骤表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 步骤 ID |
| `task_id` | BIGINT | NOT NULL | 所属任务 |
| `seq` | INT | NOT NULL | 步骤顺序，从 1 开始 |
| `code` | VARCHAR(64) | NOT NULL | 任务内步骤编码 |
| `name` | VARCHAR(128) | NOT NULL | 步骤名称 |
| `description` | VARCHAR(512) | NULL | 步骤描述 |
| `type` | VARCHAR(16) | NOT NULL | CLICK / CALLBACK / PROGRESS / REWARD / PASSIVE |
| `target_value` | INT | NULL | PROGRESS 目标值 |
| `callback_event_key` | VARCHAR(64) | NULL | CALLBACK / PROGRESS 业务事件键 |
| `reward_config_json` | JSON | NULL | REWARD 发奖配置 |
| `flow_desc` | VARCHAR(512) | NULL | 流程展示文案 |
| `extra_json` | JSON | NULL | 扩展字段 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

### 索引

| 索引 | 字段 | 作用 |
|---|---|---|
| `uk_task_step_code` | `task_id`, `code` | 同一任务内步骤编码唯一 |
| `uk_task_step_seq` | `task_id`, `seq` | 同一任务内步骤顺序唯一 |
| `idx_task_step_task` | `task_id` | 按任务查询步骤 |
| `idx_task_step_event` | `callback_event_key` | 后续回调事件匹配 |

## `task_step_platform`

步骤级端特化表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `step_id` | BIGINT | NOT NULL | 步骤 ID |
| `platform` | VARCHAR(16) | NOT NULL | WEB / IOS / ANDROID / MINIAPP / ADMIN |
| `button_text` | VARCHAR(64) | NULL | 按钮文案 |
| `jump_type` | VARCHAR(32) | NOT NULL DEFAULT NONE | URL / NATIVE_SCHEME / MINIAPP_PATH / API_CALL / NONE |
| `jump_target` | VARCHAR(512) | NULL | 跳转目标 |
| `extra_json` | JSON | NULL | 扩展字段 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

### 索引

| 索引 | 字段 | 作用 |
|---|---|---|
| `uk_step_platform` | `step_id`, `platform` | 一个步骤在一个端只有一份配置 |
| `idx_task_step_platform_step` | `step_id` | 按步骤查询端配置 |

## `task_filter`

任务展示过滤器表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `task_id` | BIGINT | NOT NULL | 任务 ID |
| `seq` | INT | NOT NULL | 过滤器顺序 |
| `expression` | VARCHAR(1024) | NOT NULL | QLExpress 表达式 |
| `logic_op` | VARCHAR(8) | NOT NULL DEFAULT AND | v0.1.0 只实现 AND |
| `description` | VARCHAR(256) | NULL | 运营备注 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否启用 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

### 索引

| 索引 | 字段 | 作用 |
|---|---|---|
| `uk_task_filter_seq` | `task_id`, `seq` | 同一任务过滤器顺序唯一 |
| `idx_task_filter_task` | `task_id` | 按任务查询过滤器 |

## `task_platform`

任务级端入口配置表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `task_id` | BIGINT | NOT NULL | 任务 ID |
| `platform` | VARCHAR(16) | NOT NULL | 端类型 |
| `flow_desc` | VARCHAR(512) | NULL | 端特化流程描述 |
| `button_text` | VARCHAR(64) | NULL | 入口按钮文案 |
| `jump_uri` | VARCHAR(512) | NULL | 入口跳转地址 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否启用 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

### 索引

| 索引 | 字段 | 作用 |
|---|---|---|
| `uk_task_platform` | `task_id`, `platform` | 一个任务在一个端只有一份入口配置 |
| `idx_task_platform_task` | `task_id` | 按任务查询端配置 |

## `user_task_instance`

用户任务实例表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 实例 ID |
| `user_id` | VARCHAR(64) | NOT NULL | 外部用户 ID |
| `task_id` | BIGINT | NOT NULL | 任务 ID |
| `task_version` | INT | NOT NULL | 创建实例时的任务版本 |
| `cycle_key` | VARCHAR(32) | NOT NULL | 周期键 |
| `status` | VARCHAR(16) | NOT NULL DEFAULT PENDING | PENDING / IN_PROGRESS / COMPLETED / REWARDED / EXPIRED |
| `current_step_seq` | INT | NOT NULL DEFAULT 1 | 当前步骤序号 |
| `start_time` | DATETIME | NULL | 开始时间 |
| `complete_time` | DATETIME | NULL | 完成时间 |
| `reward_time` | DATETIME | NULL | 发奖时间 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

### 索引

| 索引 | 字段 | 作用 |
|---|---|---|
| `uk_user_task_cycle` | `user_id`, `task_id`, `cycle_key` | 防止同用户同任务同周期重复创建实例 |
| `idx_instance_user_status` | `user_id`, `status` | 查询某用户任务状态 |
| `idx_instance_task_cycle` | `task_id`, `cycle_key` | 查询某任务某周期实例 |

## `user_task_step_progress`

用户步骤进度表。

| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| `id` | BIGINT | PK | 主键 |
| `instance_id` | BIGINT | NOT NULL | 用户任务实例 ID |
| `step_id` | BIGINT | NOT NULL | 步骤 ID |
| `status` | VARCHAR(16) | NOT NULL DEFAULT PENDING | PENDING / IN_PROGRESS / COMPLETED |
| `progress_value` | INT | NOT NULL DEFAULT 0 | PROGRESS 当前进度 |
| `complete_time` | DATETIME | NULL | 步骤完成时间 |
| `extra_json` | JSON | NULL | 扩展信息 |
| `created_at` | DATETIME | NOT NULL | 创建时间 |
| `updated_at` | DATETIME | NOT NULL | 更新时间 |

### 索引

| 索引 | 字段 | 作用 |
|---|---|---|
| `uk_instance_step` | `instance_id`, `step_id` | 一个实例的一个步骤只有一条进度 |
| `idx_step_progress_instance` | `instance_id` | 查询实例所有步骤进度 |
| `idx_step_progress_step` | `step_id` | 查询步骤维度进度 |

## 当前建表 SQL 快照

完整 SQL 以 Flyway 文件为准：

- [V1__init_task_core.sql](sql/V1__init_task_core.sql)
- [V2__init_task_instance.sql](sql/V2__init_task_instance.sql)
