# v0.2.0 SQL 文档

## 迁移文件索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V1 | `V1__init_task_core.sql` | v0.1.0 任务配置核心表 |
| V2 | `V2__init_task_instance.sql` | v0.1.0 用户任务实例表 |
| V2 | `V2__seed_demo_data.sql` | v0.2.0 种子数据：3 个演示任务 |
| V3 | `V3__auth_tables.sql` | v0.2.x 鉴权用户表：admin_user + client_user |
| V4 | `V4__task_snapshot.sql` | v0.2.1 任务配置快照表：task_definition_snapshot |
| V5 | `V5__reward_record.sql` | v0.2.2 发奖流水表：reward_record |

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
