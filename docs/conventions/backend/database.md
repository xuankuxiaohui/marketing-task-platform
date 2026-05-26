# 数据库规范

表、列、索引命名，Entity 映射，SQL 注入防护和 Flyway 迁移策略。

## 表命名

- 全小写，使用下划线分隔。
- 表名表达领域对象，不使用无意义缩写。
- 示例：`task`、`task_step`、`user_task_instance`、`prize_record`。

## 列命名

- 使用 `snake_case`。
- 布尔含义字段使用清晰语义，避免只写 `flag`。
- 时间字段优先使用 `created_at`、`updated_at`。
- 示例：`period_type`、`mutex_group_key`、`callback_event_key`。

## 索引命名

- 唯一索引使用 `uk_` 前缀，如 `uk_task_code`。
- 普通索引使用 `idx_` 前缀，如 `idx_task_status_time`。
- 索引名应体现主要字段或业务约束。

## Entity 映射

- Entity 使用 `@TableName("table_name")`。
- 主键 `Long id` 显式标注 `@TableId(type = IdType.AUTO)`。
- 时间字段使用 `LocalDateTime createdAt` / `LocalDateTime updatedAt`。
- 枚举字段当前以 String 存储枚举名。
- 所有可空字段使用包装类型，避免基本类型默认值干扰数据库 null 语义。

## SQL 注入防护

- 禁止字符串拼接构造 SQL。
- 优先使用 MyBatis-Plus 的类型安全 Wrapper。
- 必须写原生条件时，使用参数绑定，不拼接用户输入或外部参数。

错误示例：

```java
.inSql(TaskStepPlatform::getStepId,
        "SELECT id FROM task_step WHERE task_id = " + taskId)
```

正确示例：

```java
.apply("step_id IN (SELECT id FROM task_step WHERE task_id = {0})", taskId)
```

## 迁移策略

使用 Flyway 管理数据库版本。迁移文件放在 `backend/src/main/resources/db/migration/`。

命名约定：

```text
V{version}__{description}.sql
```

规则：

- 不修改已经合入或发布过的迁移文件。
- 新增表、列、索引、种子数据时新建下一号迁移。
- 文件名使用描述性英文，例如 `V4__add_reward_idempotency_table.sql`。
- 迁移 SQL 应能在目标环境稳定执行，不依赖本地临时状态。

## 数据库约束

- 禁止使用存储过程。
- 禁止使用数据库外键和级联更新，应用层保证一致性。
- `varchar` 长度必须有业务依据。
- 业务唯一性优先用唯一索引兜底。
- 核心业务表必须有 `id`、`created_at`、`updated_at`。

## 连接配置

数据库使用 MySQL 8.0+，连接配置见 `backend/src/main/resources/application-dev.yml`。
