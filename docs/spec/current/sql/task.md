# 任务模块 SQL

最后更新：2026-05-29

## 迁移索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | V1__init_task_core.sql | 核心任务表 |
| V2 | V2__seed_demo_data.sql | 演示种子数据 |
| V4 | V4__task_snapshot.sql | 任务定义快照 |
| V5 | V5__reward_record.sql | 奖励发放记录 |
| V7 | V7__list_data.sql | 黑白名单数据 |
| V8 | V8__mutex_group.sql | 互斥组独立表 |
| V9 | V9__step_platform_action.sql | 步骤平台动作 |
| V11 | V11__task_gray_config.sql | 灰度发布配置 |
| V12 | V12__cross_cycle_mutex.sql | 跨周期互斥 |
| V14 | V14__step_branching.sql | 步骤分支流转 |
| V15 | V15__task_scheduled_publish.sql | 定时发布 |
| V16 | V16__task_soft_delete.sql | 逻辑删除 |

---

## V1 — 核心任务表

### task

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| code | VARCHAR(64) NOT NULL | 任务编码 (唯一) |
| name | VARCHAR(128) NOT NULL | 任务名称 |
| description | VARCHAR(512) | 描述 |
| period_type | VARCHAR(16) NOT NULL | 周期类型: DAILY/WEEKLY/MONTHLY/ONCE |
| cron_expr | VARCHAR(64) | cron 表达式 |
| special_cycle_key | VARCHAR(64) | 特殊周期标识 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| status | VARCHAR(16) NOT NULL DEFAULT 'DRAFT' | 状态: DRAFT/PUBLISHED/ONLINE/OFFLINE |
| version | INT NOT NULL DEFAULT 0 | 版本号 |
| mutex_group_key | VARCHAR(64) | 互斥组标识 (V8 迁移后移除) |
| created_by | VARCHAR(64) | 创建人 |
| updated_by | VARCHAR(64) | 更新人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_task_code(code)`, `idx_task_status_time(status, start_time, end_time)`

### task_step

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| task_id | BIGINT NOT NULL | 关联任务 |
| seq | INT NOT NULL | 步骤序号 |
| code | VARCHAR(64) NOT NULL | 步骤编码 |
| name | VARCHAR(128) NOT NULL | 步骤名称 |
| description | VARCHAR(512) | 描述 |
| type | VARCHAR(16) NOT NULL | 类型: PASSIVE/CLICK/CALLBACK/PROGRESS/REWARD |
| target_value | INT | 进度目标值 (PROGRESS 类型) |
| callback_event_key | VARCHAR(64) | 回调事件标识 |
| reward_config_json | JSON | 奖励配置 |
| flow_desc | VARCHAR(512) | 流程描述 |
| extra_json | JSON | 扩展字段 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_task_step_code(task_id, code)`, `uk_task_step_seq(task_id, seq)`

### task_step_platform

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| step_id | BIGINT NOT NULL | 关联步骤 |
| platform | VARCHAR(16) NOT NULL | 平台: WEB/ADMIN/APP |
| button_text | VARCHAR(64) | 按钮文案 |
| jump_type | VARCHAR(32) NOT NULL DEFAULT 'NONE' | 跳转类型 |
| jump_target | VARCHAR(512) | 跳转目标 |
| extra_json | JSON | 扩展字段 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_step_platform(step_id, platform)`

### task_filter

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| task_id | BIGINT NOT NULL | 关联任务 |
| seq | INT NOT NULL | 过滤器序号 |
| expression | VARCHAR(1024) NOT NULL | QLExpress 表达式 |
| logic_op | VARCHAR(8) NOT NULL DEFAULT 'AND' | 逻辑运算符 |
| description | VARCHAR(256) | 描述 |
| enabled | TINYINT(1) NOT NULL DEFAULT 1 | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_task_filter_seq(task_id, seq)`

### task_platform

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| task_id | BIGINT NOT NULL | 关联任务 |
| platform | VARCHAR(16) NOT NULL | 平台 |
| flow_desc | VARCHAR(512) | 流程描述 |
| button_text | VARCHAR(64) | 按钮文案 |
| jump_uri | VARCHAR(512) | 跳转 URI |
| enabled | TINYINT(1) NOT NULL DEFAULT 1 | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_task_platform(task_id, platform)`

### user_task_instance

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID |
| task_id | BIGINT NOT NULL | 关联任务 |
| task_version | INT NOT NULL | 任务版本 |
| cycle_key | VARCHAR(32) NOT NULL | 周期标识 |
| status | VARCHAR(16) NOT NULL DEFAULT 'PENDING' | 状态 |
| current_step_seq | INT NOT NULL DEFAULT 1 | 当前步骤序号 |
| start_time | DATETIME | 开始时间 |
| complete_time | DATETIME | 完成时间 |
| reward_time | DATETIME | 领奖时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_user_task_cycle(user_id, task_id, cycle_key)`

### user_task_step_progress

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| instance_id | BIGINT NOT NULL | 关联实例 |
| step_id | BIGINT NOT NULL | 关联步骤 |
| status | VARCHAR(16) NOT NULL DEFAULT 'PENDING' | 状态 |
| progress_value | INT NOT NULL DEFAULT 0 | 当前进度值 |
| complete_time | DATETIME | 完成时间 |
| extra_json | JSON | 扩展字段 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_instance_step(instance_id, step_id)`

---

## V2 — 种子数据

演示数据，包含 3 个示例任务: daily_checkin、monthly_survey、reading_challenge。仅用于开发测试。

---

## V4 — 任务定义快照

### task_definition_snapshot

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| task_id | BIGINT NOT NULL | 关联任务 |
| version | INT NOT NULL | 快照版本 |
| snapshot_json | MEDIUMTEXT NOT NULL | 完整定义 JSON |
| created_at | DATETIME | 创建时间 |

索引: `uk_task_version(task_id, version)`

---

## V5 — 奖励发放记录

### reward_record

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| instance_id | BIGINT NOT NULL | 关联实例 |
| step_id | BIGINT NOT NULL | 关联步骤 |
| reward_type | VARCHAR(32) NOT NULL | 奖励类型 |
| reward_config_json | JSON | 奖励配置 |
| status | VARCHAR(16) NOT NULL DEFAULT 'PENDING' | 状态 |
| idempotent_key | VARCHAR(128) NOT NULL | 幂等键 |
| error_message | VARCHAR(1024) | 错误信息 |
| created_at | DATETIME | 创建时间 |

索引: `uk_instance_step(instance_id, step_id)`, `idx_status(status)`

---

## V7 — 黑白名单数据

### list_data

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| list_type | VARCHAR(16) NOT NULL | ALLOWLIST / DENYLIST |
| list_key | VARCHAR(64) NOT NULL | 名单标识 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID |
| created_at | TIMESTAMP | 创建时间 |

索引: `uk_list_entry(list_type, list_key, user_id)`, `idx_list_query(list_type, list_key)`

---

## V8 — 互斥组独立表

新增 `mutex_group` 表，将 `task.mutex_group_key` 迁移为 `task.mutex_group_id` 外键关联。

### mutex_group

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| name | VARCHAR(64) NOT NULL | 互斥组名称 |
| description | VARCHAR(256) | 描述 |
| scope | VARCHAR(32) NOT NULL DEFAULT 'SAME_CYCLE' | 作用域 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

task 表变更: 新增 `mutex_group_id BIGINT`，移除 `mutex_group_key` 列及索引。

---

## V9 — 步骤平台动作

`task_step_platform` 新增列:

| 字段 | 类型 | 说明 |
|---|---|---|
| action_type | VARCHAR(32) | NONE/CLAIM_REWARD/OPEN_URL/NATIVE_SCHEME/MINIAPP_PATH/SHARE |
| action_config | VARCHAR(512) | 动作参数 JSON |

---

## V11 — 灰度发布配置

`task` 表新增列:

| 字段 | 类型 | 说明 |
|---|---|---|
| gray_type | VARCHAR(16) DEFAULT 'NONE' | 灰度类型: NONE/RATIO/WHITELIST |
| gray_config | JSON | 灰度配置 |

---

## V12 — 跨周期互斥

`mutex_group` 表新增列:

| 字段 | 类型 | 说明 |
|---|---|---|
| cross_cycle | TINYINT(1) DEFAULT 0 | 是否跨周期互斥 |

---

## V14 — 步骤分支流转

### task_step_transition

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| step_id | BIGINT NOT NULL | 源步骤 |
| target_step_id | BIGINT NOT NULL | 目标步骤 |
| condition_expr | VARCHAR(1024) | 分支条件表达式 |
| priority | INT NOT NULL DEFAULT 0 | 优先级 |
| description | VARCHAR(256) | 描述 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_step_priority(step_id, priority)`

---

## V15 — 定时发布

`task` 表新增列:

| 字段 | 类型 | 说明 |
|---|---|---|
| scheduled_publish_at | DATETIME | 定时发布时间 |

---

## V16 — 逻辑删除

`task` 表新增列:

| 字段 | 类型 | 说明 |
|---|---|---|
| deleted | TINYINT NOT NULL DEFAULT 0 | 逻辑删除: 0=正常 1=已删除 |
