# 奖品模块 SQL

最后更新：2026-05-29

## 迁移索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V6 | V6__prize_module.sql | 奖品核心表 |
| V20 | V20__prize_activity_code_unify.sql | 移除 activity_id 列 |

---

## V6 — 奖品核心表

task_step 新增列:

| 字段 | 类型 | 说明 |
|---|---|---|
| prize_id | BIGINT | 关联奖品 ID |
| prize_quantity | INT DEFAULT 1 | 发放数量 |

### prize

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| type | VARCHAR(32) NOT NULL | 奖品类型 |
| name | VARCHAR(128) NOT NULL | 奖品名称 |
| description | VARCHAR(512) | 描述 |
| handler_bean | VARCHAR(64) NOT NULL | 处理器 Bean 名称 |
| params_json | JSON NOT NULL | 奖品参数 |
| total_stock | INT | 总库存 |
| monthly_stock | INT | 月库存 |
| daily_stock | INT | 日库存 |
| user_total_limit | INT | 用户总限领 |
| user_monthly_limit | INT | 用户月限领 |
| user_daily_limit | INT | 用户日限领 |
| limits_json | JSON | 限制配置 |
| activity_code | VARCHAR(64) | 活动编码 (V18 新增) |
| group_key | VARCHAR(64) | 奖品组标识 |
| group_strategy | VARCHAR(32) | 组策略 |
| group_weight | INT DEFAULT 1 | 组权重 |
| icon_url | VARCHAR(512) | 图标 URL |
| claim_zone_image_url | VARCHAR(512) | 领取区图片 |
| auto_grant | TINYINT(1) DEFAULT 0 | 是否自动发放 |
| claim_expire_type | VARCHAR(16) NOT NULL | 领取过期类型 |
| claim_expire_value | VARCHAR(64) NOT NULL | 领取过期值 |
| max_retry | INT DEFAULT 3 | 最大重试次数 |
| enabled | TINYINT(1) DEFAULT 1 | 是否启用 |
| start_time | DATETIME | 有效期开始 |
| end_time | DATETIME | 有效期结束 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `idx_activity_group(activity_id, group_key)`, `idx_type(type)`, `idx_prize_activity_code(activity_code)`

### prize_record

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID |
| instance_id | BIGINT NOT NULL | 任务实例 ID |
| task_id | BIGINT NOT NULL | 任务 ID |
| step_id | BIGINT NOT NULL | 步骤 ID |
| prize_id | BIGINT NOT NULL | 奖品 ID |
| quantity | INT DEFAULT 1 | 数量 |
| idempotent_key | VARCHAR(128) NOT NULL | 幂等键 |
| prize_type | VARCHAR(32) NOT NULL | 奖品类型快照 |
| prize_name | VARCHAR(128) NOT NULL | 奖品名称快照 |
| prize_icon | VARCHAR(512) | 图标快照 |
| prize_image | VARCHAR(512) | 图片快照 |
| prize_params_json | JSON NOT NULL | 奖品参数快照 |
| activity_code | VARCHAR(64) | 活动编码 (V18 新增) |
| status | VARCHAR(16) NOT NULL | 状态 |
| expire_time | DATETIME NOT NULL | 过期时间 |
| retry_count | INT DEFAULT 0 | 重试次数 |
| error_message | VARCHAR(1024) | 错误信息 |
| external_trade_no | VARCHAR(128) | 外部交易号 |
| won_at | DATETIME NOT NULL | 中奖时间 |
| claimed_at | DATETIME | 领取时间 |
| granted_at | DATETIME | 发放时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引: `uk_idempotent(idempotent_key)`, `idx_user_status(user_id, status)`, `idx_expire(status, expire_time)`, `idx_prize_record_activity_code(activity_code)`

### prize_claim_lock

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| record_id | BIGINT UNIQUE NOT NULL | 关联记录 ID |
| created_at | DATETIME | 创建时间 |

### prize_inventory_record

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| prize_id | BIGINT NOT NULL | 奖品 ID |
| record_id | BIGINT NOT NULL | 记录 ID |
| quantity | INT NOT NULL | 扣减数量 |
| created_at | DATETIME | 创建时间 |

索引: `uk_prize_record(prize_id, record_id)`, `idx_prize_created(prize_id, created_at)`

---

## V20 — 移除 activity_id 列

移除 `prize.activity_id` 和 `prize_record.activity_id`，统一使用 `activity_code` 字段关联活动。
