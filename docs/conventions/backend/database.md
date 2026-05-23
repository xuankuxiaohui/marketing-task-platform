# 数据库规范

表/列/索引命名、Entity 映射、SQL 注入防护、Flyway 迁移策略。

## 目录

1. [表命名](#表命名)
2. [列命名](#列命名)
3. [索引命名](#索引命名)
4. [Entity 映射](#entity-映射)
5. [SQL 注入防护](#sql-注入防护)
6. [迁移策略](#迁移策略)
7. [数据库约束](#数据库约束)

---

## 表命名

全小写 + 下划线分隔，如 `task`、`task_step`、`user_task_instance`。当前合规。

## 列命名

`snake_case`，如 `period_type`、`mutex_group_key`、`callback_event_key`。当前合规。

## 索引命名

- 唯一索引：`uk_` 前缀（如 `uk_task_code`、`uk_task_step_seq`）
- 普通索引：`idx_` 前缀（如 `idx_task_status_time`、`idx_task_mutex_group`）
- 当前合规。

## Entity 映射

- 注解：`@Getter` + `@Setter` + `@TableName("table_name")`
- 主键 `Long id`，必须显式标注 `@TableId(type = IdType.AUTO)`。当前多数 Entity 缺少，**应补上**
- 时间字段 `LocalDateTime createdAt` / `LocalDateTime updatedAt`
- 枚举字段以 `String` 存储枚举名（如 `TaskStatus.PUBLISHED.name()`）
- 所有字段使用包装类型（`Long` 而非 `long`），避免基本类型默认值干扰数据库 null 语义

## SQL 注入防护

**绝对禁止**字符串拼接构造 SQL，必须使用参数化查询。即使是类型安全的 Long 参数也不许拼字符串。

**错误示例**（已修复）：
```java
// 危险: 字符串拼接 taskId 到 inSql()
.inSql(TaskStepPlatform::getStepId,
        "SELECT id FROM task_step WHERE task_id = " + taskId)
```

**正确写法**：
```java
// MyBatis-Plus apply("{0}", param) 参数化
.apply("step_id IN (SELECT id FROM task_step WHERE task_id = {0})", taskId)
```

## 迁移策略

使用 Flyway 管理数据库版本。迁移文件放在 `src/main/resources/db/migration/`。

**命名约定**：`V{version}__{description}.sql`（双下划线分隔版本号和描述）

| 迁移 | 说明 |
|---|---|
| V1__init_task_core.sql | 创建 task, task_step, task_step_platform, task_filter, task_platform |
| V2__init_task_instance.sql | 创建 user_task_instance, user_task_step_progress |
| V3__seed_demo_data.sql | 种子数据：daily_checkin, newbie_quiz, reading_campaign |

**新增迁移规则**：
- 不要在已有迁移文件中追加内容 — 总是新建 V4/V5/... 文件
- 迁移文件名描述性英文，如 `V4__add_reward_idempotency_table.sql`
- 迁移 SQL 必须是幂等的（或至少保证不会重复执行）

## 数据库约束

- 禁止使用存储过程
- 禁止使用外键与级联更新（应用层保证一致性）
- `varchar` 字段长度应合理预估，不允许 `varchar(9999)`
- 表必备三字段：`id`、`created_at`、`updated_at`

## 连接配置

数据库使用 MySQL 8.0+，连接配置见 `backend/src/main/resources/application-dev.yml`。

## 相关文档

- `backend/layering.md` — Entity 层、Mapper 层
- `backend/java-style.md` — POJO 设计、常量
- `spec/current/sql.md` — 当前版本完整表结构
