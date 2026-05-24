# v0.2.0 SQL 文档

最后更新：2026-05-24

## 迁移文件索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_task_core.sql` | v0.1.0 任务配置核心表 |
| V2 | `V2__init_task_instance.sql` | v0.1.0 用户任务实例表 |
| V2 | `V2__seed_demo_data.sql` | v0.2.0 种子数据：3 个演示任务 |
| V3 | `V3__auth_tables.sql` | v0.2.x 鉴权用户表：admin_user + client_user |
| V4 | `V4__task_snapshot.sql` | v0.2.1 任务配置快照表：task_definition_snapshot |
| V5 | `V5__reward_record.sql` | v0.2.2 发奖流水表：reward_record |
| V6 | `V6__prize_module.sql` | v0.2.3 奖品模块 4 表 + task_step 奖品字段 |
| V7 | `V7__list_data.sql` | v0.2.7 名单数据表：list_data |
| V8 | `V8__mutex_group.sql` | v0.2.10 互斥组表 + task 迁移 mutex_group_key → mutex_group_id |
| V9 | `V9__step_platform_action.sql` | v0.2.10 步骤平台操作配置字段 |

## V3 鉴权用户表

`backend/src/main/resources/db/migration/V3__auth_tables.sql`

### admin_user

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| username | VARCHAR(64) NOT NULL | 用户名，UNIQUE |
| password_hash | VARCHAR(256) NOT NULL | BCrypt 密码哈希 |
| nickname | VARCHAR(64) | 昵称 |
| enabled | TINYINT(1) DEFAULT 1 | 是否启用 |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

默认账号: `admin` / `admin123`（BCrypt）

### client_user

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| username | VARCHAR(64) NOT NULL | 用户名，UNIQUE |
| password_hash | VARCHAR(256) NOT NULL | BCrypt 密码哈希 |
| nickname | VARCHAR(64) | 昵称 |
| province | VARCHAR(32) | 省份 |
| role | VARCHAR(32) | 角色 |
| tags | VARCHAR(512) | 逗号分隔标签 |
| org_id | VARCHAR(64) | 组织 ID |
| level | INT DEFAULT 0 | 等级 |
| enabled | TINYINT(1) DEFAULT 1 | 是否启用 |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

默认测试账号: `demo` / `demo123`（BCrypt），预设用户画像（BJ, vip, level=5）

## V4 任务配置快照表

`backend/src/main/resources/db/migration/V4__task_snapshot.sql`

### task_definition_snapshot

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| task_id | BIGINT NOT NULL | 任务 ID |
| version | INT NOT NULL | 任务版本号 |
| snapshot_json | MEDIUMTEXT NOT NULL | 完整配置 JSON (TaskSnapshotDTO) |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |

唯一约束: `(task_id, version)`，索引: `idx_task_id`。

`snapshot_json` 内容为 `TaskSnapshotDTO` 的 JSON 序列化：`{ task, steps[], filters[], platforms[] }`。

## V5 发奖流水表

`backend/src/main/resources/db/migration/V5__reward_record.sql`

### reward_record

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| instance_id | BIGINT NOT NULL | 用户任务实例 ID |
| step_id | BIGINT NOT NULL | 奖励步骤 ID |
| reward_type | VARCHAR(32) NOT NULL | 奖励类型：point / coupon / badge |
| reward_config_json | JSON NULL | 原始奖励配置 JSON |
| status | VARCHAR(16) DEFAULT 'PENDING' | PENDING / SUCCESS / FAILED |
| idempotent_key | VARCHAR(128) NOT NULL | 幂等键：`{instance_id}:{step_id}` |
| error_message | VARCHAR(1024) NULL | 失败原因（仅 FAILED 状态） |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |

唯一约束: `(instance_id, step_id)`，索引: `idx_instance_id`, `idx_status`。

## V6 奖品模块

`backend/src/main/resources/db/migration/V6__prize_module.sql`

### task_step 字段扩展

| 列 | 类型 | 说明 |
|---|---|---|
| prize_id | BIGINT NULL | 关联奖品 ID |
| prize_quantity | INT DEFAULT 1 | 奖励数量 |

### prize（奖品配置）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| type | VARCHAR(32) NOT NULL | 奖品类型 |
| name | VARCHAR(128) NOT NULL | 奖品名称 |
| description | VARCHAR(512) | 描述 |
| handler_bean | VARCHAR(64) NOT NULL | 处理器 Bean 名 |
| params_json | JSON NOT NULL | 奖品参数 |
| total_stock / monthly_stock / daily_stock | INT NULL | 三级库存 |
| user_total_limit / user_monthly_limit / user_daily_limit | INT NULL | 用户三级领取限制 |
| limits_json | JSON NULL | 限制配置（省份/等级/标签） |
| activity_id | BIGINT NULL | 关联活动 ID |
| group_key / group_strategy / group_weight | — | 奖品组（互斥/权重） |
| icon_url / claim_zone_image_url | VARCHAR(512) | 图标/领奖区图片 |
| auto_grant | TINYINT(1) DEFAULT 0 | 是否自动领取 |
| claim_expire_type | VARCHAR(16) | DAYS / CALENDAR_MONTH / FIXED_DATE |
| claim_expire_value | VARCHAR(64) | 有效期值 |
| max_retry | INT DEFAULT 3 | 最大重试次数 |
| enabled | TINYINT(1) DEFAULT 1 | 是否启用 |
| start_time / end_time | DATETIME NULL | 时间窗口 |

索引: `idx_activity_group(activity_id, group_key)`, `idx_type(type)`.

### prize_record（中奖记录）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID |
| instance_id | BIGINT NOT NULL | 任务实例 ID |
| task_id | BIGINT NOT NULL | 任务 ID |
| step_id | BIGINT NOT NULL | 步骤 ID |
| prize_id | BIGINT NOT NULL | 奖品 ID |
| quantity | INT DEFAULT 1 | 数量 |
| idempotent_key | VARCHAR(128) NOT NULL UNIQUE | 幂等键 |
| prize_type / prize_name / prize_icon / prize_image / prize_params_json | — | 奖品快照字段 |
| activity_id | BIGINT NULL | 活动 ID |
| status | VARCHAR(16) NOT NULL | WON / CLAIMING / GRANTED / FAILED / FAILED_PERMANENTLY / EXPIRED |
| expire_time | DATETIME NOT NULL | 过期时间 |
| retry_count | INT DEFAULT 0 | 重试次数 |
| error_message | VARCHAR(1024) | 错误信息 |
| external_trade_no | VARCHAR(128) | 外部交易号 |
| won_at / claimed_at / granted_at | DATETIME | 时间戳 |

索引: `idx_user_status(user_id, status)`, `idx_expire(status, expire_time)`, `idx_prize_id(prize_id)`.

### prize_claim_lock（防重领取锁）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| record_id | BIGINT UNIQUE NOT NULL | prize_record ID，UNIQUE 防并发重复领取 |

### prize_inventory_record（库存扣减流水）

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| prize_id | BIGINT NOT NULL | 奖品 ID |
| record_id | BIGINT NOT NULL | prize_record ID |
| quantity | INT NOT NULL | 扣减数量 |

唯一约束: `(prize_id, record_id)`，索引: `idx_prize_created(prize_id, created_at)`.

## V7 名单数据表

`backend/src/main/resources/db/migration/V7__list_data.sql`

### list_data

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| list_type | VARCHAR(16) NOT NULL | ALLOWLIST / DENYLIST |
| list_key | VARCHAR(64) NOT NULL | 名单标识 |
| user_id | VARCHAR(64) NOT NULL | 用户 ID |
| created_at | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | 创建时间 |

唯一约束: `(list_type, list_key, user_id)`，索引: `idx_list_query(list_type, list_key)`.

## V8 互斥组

`backend/src/main/resources/db/migration/V8__mutex_group.sql`

### mutex_group

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| name | VARCHAR(64) NOT NULL | 互斥组名称 |
| description | VARCHAR(256) | 描述 |
| scope | VARCHAR(32) DEFAULT 'SAME_CYCLE' | 互斥范围 |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

### task 表变更

- **新增**: `mutex_group_id BIGINT NULL`（外键关联 mutex_group）+ `INDEX idx_task_mutex_group_id`
- **删除**: `mutex_group_key` 列 + `idx_task_mutex_group` 索引
- **自动迁移**: 现有 `mutex_group_key` 不为空的 task 自动创建对应 mutex_group 并关联

## V9 步骤平台操作配置

`backend/src/main/resources/db/migration/V9__step_platform_action.sql`

### task_step_platform 字段扩展

| 列 | 类型 | 说明 |
|---|---|---|
| action_type | VARCHAR(32) NULL | NONE / CLAIM_REWARD / OPEN_URL / NATIVE_SCHEME / MINIAPP_PATH / SHARE |
| action_config | VARCHAR(512) NULL | 操作参数 JSON |

## V2 种子数据

`backend/src/main/resources/db/migration/V2__seed_demo_data.sql`

Flyway 在启动时自动执行（仅一次）。所有硬编码 ID < 100，不与 Snowflake 生成的生产 ID 冲突。

### 任务 1: daily_checkin（日签到）

| 属性 | 值 |
|---|---|
| 周期 | DAILY |
| 步骤 | PASSIVE(进入) → CLICK(签到) → REWARD(+10积分) |
| 过滤器 | `inProvince(['BJ']) && levelGte(3)` — 仅 BJ + 等级≥3 |
| 端 | WEB, ADMIN |

```text
task.id=1
  ├─ task_step.id=10: PASSIVE "进入任务"
  ├─ task_step.id=11: CLICK "点击签到"
  ├─ task_step.id=12: REWARD "领取积分" (point × 10)
  ├─ task_filter.id=20: inProvince(['BJ']) && levelGte(3)
  ├─ task_platform.id=30: WEB
  └─ task_platform.id=31: ADMIN
```

### 任务 2: monthly_survey（月度调研）

| 属性 | 值 |
|---|---|
| 周期 | MONTHLY |
| 步骤 | CALLBACK(填写问卷, eventKey=survey_completed) → REWARD(优惠券) |
| 过滤器 | 无（全员可见） |
| 端 | WEB |

```text
task.id=2
  ├─ task_step.id=13: CALLBACK "填写问卷" (callback_event_key=survey_completed)
  ├─ task_step.id=14: REWARD "领取奖励" (coupon × 1)
  └─ task_platform.id=32: WEB
```

### 任务 3: reading_challenge（阅读挑战）

| 属性 | 值 |
|---|---|
| 周期 | ONCE |
| 步骤 | PROGRESS(阅读文章, targetValue=3) → REWARD(读者勋章) |
| 过滤器 | 无（全员可见） |
| 端 | WEB, ADMIN |

```text
task.id=3
  ├─ task_step.id=15: PROGRESS "阅读文章" (target_value=3)
  ├─ task_step.id=16: REWARD "领取奖励" (badge: reader)
  ├─ task_platform.id=33: WEB
  └─ task_platform.id=34: ADMIN
```

## 端到端验证路径

启动后端后，可执行以下 curl 验证种子数据：

```bash
# BJ + level 5 用户可看到 daily_checkin 和 reading_challenge（monthly_survey 无过滤器全员可见）
curl http://localhost:8080/api/client/task/list \
  -H 'X-User-Id: u_test' \
  -H 'X-User-Province: BJ' \
  -H 'X-User-Level: 5'

# 查看日签到详情 → 自动创建实例，PASSIVE 自动完成，停在 CLICK 步骤
curl http://localhost:8080/api/client/task/1 \
  -H 'X-User-Id: u_test' \
  -H 'X-Platform: WEB'

# 点击签到推进 → 级联触发 REWARD
curl -X POST http://localhost:8080/api/client/task/1/step/11/click \
  -H 'X-User-Id: u_test'

# CALLBACK 回调 → 调研完成
curl -X POST http://localhost:8080/api/internal/task/callback \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u_test","taskId":2,"cycleKey":"202605","callbackEventKey":"survey_completed"}'

# PROGRESS 进度推进（逐步）
curl -X POST http://localhost:8080/api/internal/task/progress \
  -H 'Content-Type: application/json' \
  -d '{"userId":"u_test","taskId":3,"cycleKey":"ONCE","stepId":15,"progressValue":1}'
```
