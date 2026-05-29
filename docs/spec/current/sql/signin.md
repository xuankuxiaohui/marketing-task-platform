# 签到模块 SQL

最后更新：2026-05-29

## 迁移索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V17 | V17__signin_module.sql | 签到配置、签到记录、积分账户、积分流水 |

---

## V17 — 签到模块

### signin_config

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| name | VARCHAR(128) NOT NULL | 签到活动名称 |
| status | VARCHAR(16) NOT NULL DEFAULT 'DRAFT' | DRAFT/PUBLISHED/OFFLINE |
| period_type | VARCHAR(16) NOT NULL DEFAULT 'MONTHLY' | WEEKLY/MONTHLY |
| base_points | INT NOT NULL DEFAULT 10 | 每日基础积分 |
| streak_config | JSON | 连签梯度配置 |
| point_expire_days | INT | 积分过期天数 (NULL=永不过期) |
| catch_up_enabled | TINYINT NOT NULL DEFAULT 0 | 是否允许补签 |
| catch_up_cost | INT NOT NULL DEFAULT 0 | 补签消耗积分 |
| catch_up_max_days | INT | 最大可补签天数 |
| start_time | DATETIME | 有效期开始 |
| end_time | DATETIME | 有效期结束 |
| description | VARCHAR(512) | 描述 |
| activity_code | VARCHAR(64) | 关联活动编码 (V18 新增) |
| created_by | VARCHAR(64) | 创建人 |
| updated_by | VARCHAR(64) | 更新人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `idx_status(status)`, `idx_signin_config_activity_code(activity_code)`

### signin_record

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| config_id | BIGINT NOT NULL | 关联签到配置 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID |
| signin_date | DATE NOT NULL | 签到日期 |
| period_key | VARCHAR(16) NOT NULL | 周期标识: yyyy-Www / yyyy-MM |
| streak_day | INT NOT NULL DEFAULT 1 | 连续签到天数 |
| base_points | INT NOT NULL | 基础积分 |
| bonus_points | INT NOT NULL DEFAULT 0 | 额外积分 |
| total_points | INT NOT NULL | 总积分 |
| tier_reached | INT | 本次达到的梯度天数 |
| is_catch_up | TINYINT NOT NULL DEFAULT 0 | 是否补签 |
| created_at | DATETIME | 创建时间 |

索引: `uk_config_user_date(config_id, user_id, signin_date)`, `idx_user_period(user_id, period_key)`

### point_account

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID (唯一) |
| balance | BIGINT NOT NULL DEFAULT 0 | 当前余额 |
| total_earned | BIGINT NOT NULL DEFAULT 0 | 累计获取 |
| total_spent | BIGINT NOT NULL DEFAULT 0 | 累计消耗 |
| total_expired | BIGINT NOT NULL DEFAULT 0 | 累计过期 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_user(user_id)`

### point_transaction

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID |
| type | VARCHAR(16) NOT NULL | EARN/EXPIRE/DEDUCT |
| amount | BIGINT NOT NULL | 积分数量 (正数) |
| source_type | VARCHAR(32) NOT NULL | SIGNIN/SIGNIN_STREAK/TASK_REWARD/CATCH_UP/ADMIN_GRANT |
| source_id | BIGINT | 关联 ID |
| balance_after | BIGINT NOT NULL | 变动后余额 |
| expire_at | DATETIME | 积分过期时间 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' | ACTIVE/EXPIRED |
| description | VARCHAR(256) | 描述 |
| created_at | DATETIME | 创建时间 |

索引: `idx_user_type(user_id, type)`, `idx_user_status(user_id, status)`, `idx_expire(expire_at, status)`
