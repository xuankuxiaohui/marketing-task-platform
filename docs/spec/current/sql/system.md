# 系统模块 SQL

最后更新：2026-05-29

## 迁移索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V3 | V3__auth_tables.sql | 认证用户表 |
| V10 | V10__event_log_and_metrics.sql | 事件日志与任务指标 |
| V13 | V13__operation_log.sql | 操作审计日志 |

---

## V3 — 认证用户表

### admin_user

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| username | VARCHAR(64) NOT NULL | 用户名 (唯一) |
| password_hash | VARCHAR(256) NOT NULL | BCrypt 密码哈希 |
| nickname | VARCHAR(64) | 昵称 |
| enabled | TINYINT(1) NOT NULL DEFAULT 1 | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_admin_user_username(username)`

内置管理员: admin / admin123

### client_user

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| username | VARCHAR(64) NOT NULL | 用户名 (唯一) |
| password_hash | VARCHAR(256) NOT NULL | BCrypt 密码哈希 |
| nickname | VARCHAR(64) | 昵称 |
| province | VARCHAR(32) | 省份 |
| role | VARCHAR(32) | 角色 |
| tags | VARCHAR(512) | 标签 (逗号分隔) |
| org_id | VARCHAR(64) | 组织 ID |
| level | INT DEFAULT 0 | 等级 |
| enabled | TINYINT(1) NOT NULL DEFAULT 1 | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_client_user_username(username)`

内置测试用户: demo / demo123

---

## V10 — 事件日志与任务指标

### event_log

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| event_type | VARCHAR(32) NOT NULL | 事件类型 |
| task_id | BIGINT | 任务 ID |
| instance_id | BIGINT | 实例 ID |
| step_id | BIGINT | 步骤 ID |
| user_id | VARCHAR(64) | 用户 ID |
| platform | VARCHAR(16) | 平台 |
| event_data | JSON | 事件数据 |
| created_at | DATETIME | 创建时间 |

索引: `idx_event_type(event_type)`, `idx_task_id(task_id)`, `idx_instance_id(instance_id)`, `idx_created_at(created_at)`

### task_metrics

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| task_id | BIGINT NOT NULL | 任务 ID |
| metric_date | DATE NOT NULL | 统计日期 |
| views | BIGINT DEFAULT 0 | 浏览数 |
| participants | BIGINT DEFAULT 0 | 参与数 |
| completions | BIGINT DEFAULT 0 | 完成数 |
| reward_success | BIGINT DEFAULT 0 | 奖励成功数 |
| reward_failure | BIGINT DEFAULT 0 | 奖励失败数 |
| avg_filter_ms | DOUBLE DEFAULT 0 | 平均过滤耗时 (ms) |

索引: `uk_task_date(task_id, metric_date)`

---

## V13 — 操作审计日志

### operation_log

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| operator_id | VARCHAR(64) NOT NULL | 操作人 ID |
| operator_name | VARCHAR(128) | 操作人名称 |
| operation_type | VARCHAR(32) NOT NULL | CREATE/UPDATE/PUBLISH/OFFLINE/DELETE |
| target_type | VARCHAR(32) NOT NULL | TASK/PRIZE/MUTEX_GROUP |
| target_id | BIGINT | 目标 ID |
| target_name | VARCHAR(256) | 目标名称 |
| detail | JSON | 变更详情 |
| created_at | DATETIME | 创建时间 |

索引: `idx_target(target_type, target_id)`, `idx_operator(operator_id)`, `idx_created_at(created_at)`
