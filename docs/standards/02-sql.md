# 02 · SQL 与数据库规范

> 适用范围：`platform-db` 迁移脚本、Mapper XML、注解 SQL、手工排查脚本。  
> 数据库：MySQL 8.0。共库，表按域前缀。  
> 读者：后端开发者、DBA、AI。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| MySQL 8.0 Reference Manual | https://dev.mysql.com/doc/refman/8.0/en/ | 类型、索引、分区、JSON、CHECK |
| utf8mb4 | https://dev.mysql.com/doc/refman/8.0/en/charset-unicode-utf8mb4.html | 4 字节 Unicode，禁止 `utf8`/`utf8mb3` |
| utf8mb4_0900_ai_ci | https://dev.mysql.com/doc/refman/8.0/en/charset-collation-names.html | 默认校对；ai_ci = accent insensitive + case insensitive |
| CHECK 约束（8.0.16+ 强制） | https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html | 枚举值域 |
| JSON 类型 | https://dev.mysql.com/doc/refman/8.0/en/json.html | 事件属性、标签 |
| 分区 | https://dev.mysql.com/doc/refman/8.0/en/partitioning.html | `evt_event_log` 月 RANGE |
| Flyway Migrations | https://documentation.red-gate.com/flyway/flyway-concepts/migrations | 版本化、幂等 migrate、locations |
| Flyway Versioned migrations | https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations | 只应用一次；checksum；禁止改已应用脚本 |
| Alibaba Java Coding Guidelines · MySQL Specification | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ | 表/列/索引命名、禁止浮点存钱、控制索引数 |
| design §3.1 / §6.9 | 本仓库规格 | **DDL 形状的唯一权威** |

### 本项目覆盖

| 阿里 / 通用习惯 | 本项目 |
|-----------------|--------|
| `gmt_create` / `gmt_modified` | `created_at` / `updated_at` `DATETIME(3)` |
| 物理外键 | **不建**（design §3.1） |
| 枚举用 MySQL `ENUM` | `VARCHAR(n) + CHECK` |
| 主键 `UNSIGNED` | `BIGINT`（与 MyBatis-Plus / JS 安全整数协作；雪花表亦 `BIGINT`） |
| 业务库里随便建表 | 仅 `platform-db` 的 Flyway（RL-09） |

## 2. Flyway

官方规则（Versioned migrations）：

- 版本化迁移按版本号**恰好执行一次**。
- `flyway_schema_history` 保存 checksum；已应用到持久环境的脚本 **MUST NOT** 再改内容。
- 需要修正时 **MUST** 新增更高版本脚本向前修。
- `migrate` 幂等：已到最新则什么都不做。

### 2.1 本项目命名

```text
V<序号>__<域>_<描述>.sql
```

- 前缀 `V`、分隔符两个下划线 `__`：Flyway 默认（官方文档 Naming）。
- 序号：整数递增。P0 批次已规划：

| 文件 | 内容 | 任务 |
|------|------|------|
| `V1__sys_baseline.sql` | 全部 `sys_` + 种子 | 13 |
| `V2__task_core.sql` | 全部 `task_` | 14 |
| `V3__reward_points.sql` | `rwd_` + `pnt_` + 分类种子 | 14 / 32 / 35 |
| `V4__risk_tracking.sql` | `risk_` + `evt_` + 种子与首月分区 | 14 / 17–20 |

后续：`V5__<域>_<描述>.sql`，禁止插队改历史号。

### 2.2 位置与执行者

1. **MUST** 生产迁移只放 `platform-db`：`src/main/resources/db/migration/`。
2. **MUST NOT** 业务域模块包含 `*.sql`（RL-09，ArchUnit）。
3. **MUST** admin-app 默认 `spring.flyway.enabled=true`；portal-app 默认 `false`（design §6.9）。
4. 测试种子 **MUST** 放测试 classpath：`src/test/resources/db/migration/R__test-seed.sql`（Flyway Repeatable，前缀 `R`）。**MUST NOT** 打进生产包。
5. **MUST** CI 对空库：全量 migrate → `flyway validate` → 再 migrate 一次断言幂等（R31 属性 1）。
6. **MUST NOT** 使用 Flyway Undo（`U` 前缀）。回滚靠向前兼容的新脚本。
7. **MUST NOT** 在迁移里写存储过程 / 触发器实现业务（业务在应用层）。分区预建调度除外，见 design §6.7-8。

### 2.3 滚动发布兼容（R31.4）

结构变更拆两段：

1. 先发**兼容**脚本：只加可空列 / 新表 / 新索引，旧代码仍能跑。
2. 代码上线读写新列。
3. 再发**收紧**脚本：回填、`NOT NULL`、删废弃列。禁止单次迁移「加列 + 删列 + 改语义」。

## 3. 库与字符集

1. **MUST** 库、表、连接字符集 `utf8mb4`，校对 `utf8mb4_0900_ai_ci`（design §3.1；MySQL 官方 utf8mb4）。
2. **MUST NOT** 使用 `utf8`（MySQL 里是 utf8mb3，不能存 4 字节字符，官方对照见 utf8mb4 手册）。
3. 用户名等「大小写不敏感唯一」依赖 `ai_ci` **加上** 应用层转小写存储（design §3.1）。二者都要，缺一不可。
4. **MUST** 建表语句带 `COMMENT`。列有业务封闭值域时 COMMENT 写明枚举。

## 4. 表与列命名

| 对象 | 规则 | 正例 | 反例 |
|------|------|------|------|
| 表 | 小写 + 下划线；**域前缀**；单数 | `task_instance`、`rwd_prize` | `TaskInstance`、`prizes` |
| 列 | 小写 + 下划线 | `user_id`、`expire_at` | `userId`、`ExpireAt` |
| 主键 | `id` `BIGINT` | `id` | `prizeId` 作主键名 |
| 关联 | `<表单数>_id` | `task_id`、`prize_id` | `tid`、`prizeid` |
| 布尔 | `TINYINT(1)` 0/1，名称不用 `is_` | `deleted`、`simulated` | `is_deleted`、`bit` |
| 时间 | `*_at`，`DATETIME(3)` | `granted_at` | `grant_time` `TIMESTAMP` |
| 唯一索引 | `uk_<列或语义>` | `uk_username`、`uk_idempotent` | `unique_1` |
| 普通索引 | `idx_<列或语义>` | `idx_prize` | `index1` |
| 禁止 | 保留字做未转义标识符 | — | `order`、`status` 作无反引号表名（列名 `status` 本项目已用，保持一致即可，不要再引入 `order`/`range`） |

域前缀封闭清单（design §2.9）：

| 前缀 | 域 |
|------|-----|
| `sys_` | identity、系统管理、outbox |
| `task_` | 任务 |
| `rwd_` | 奖品与发放 |
| `pnt_` | 积分 |
| `risk_` | 风控 |
| `evt_` | 埋点事件 |
| `sgn_` / `act_` / `ad_` / `mtr_` | P1 |

**MUST NOT** 跨前缀建「对方域」的表。积分表在 reward 模块语义下，但仍用 `pnt_` 前缀（已冻结）。

## 5. 类型选择

权威：design §3.1。

| 用途 | 类型 | 禁止 |
|------|------|------|
| 主键 / 外键引用 | `BIGINT` | `INT` 作新表主键；`UUID` 作默认主键 |
| 雪花 ID（仅两表） | `BIGINT`，应用 `ASSIGN_ID` | 其它表用雪花 |
| 金额、成本 | `BIGINT` 分（`cost_fen` / `face_fen` / `amount_fen`） | `FLOAT` / `DOUBLE` / `DECIMAL` 混用（本项目统一分） |
| 时间 | `DATETIME(3)` UTC | `TIMESTAMP`（受时区/2038）；`DATETIME` 无精度 |
| 字符串 | `VARCHAR(n)`，n 按规格 | `TEXT` 作可索引业务键 |
| 枚举 | `VARCHAR(n) + CHECK (col IN (...))` | MySQL `ENUM`（改值需重建列） |
| JSON | `JSON` | 用 `VARCHAR` 塞 JSON 当主存储 |
| 布尔 | `TINYINT(1)` | `CHAR(1)` `'Y'/'N'` |
| 计数 | `INT` / `BIGINT` 按上限 | 无符号与有符号混比 |

逻辑删除：**仅** `sys_admin_user`、`sys_portal_user`、`task_definition`、`rwd_prize` 有 `deleted`。其余表禁止复制这列。

## 6. 约束、索引、完整性

1. **MUST NOT** 建物理外键（design §3.1）。引用完整性 = 应用层 + 唯一约束。
2. **MUST** 幂等键、业务唯一键在 DB 层落 `UNIQUE`。并发正确性以唯一约束为准（P4）。
3. **MUST** 枚举列有 `CHECK`。MySQL 8.0.16+ 强制执行（官方手册）。
4. **MUST** 索引服务具体查询。禁止「先建 8 个单列索引再说」。
5. **SHOULD** 联合索引遵循最左前缀，区分度高的列在前；等值列在范围列前。
6. **MUST NOT** 在低区分度布尔列上建独立索引（`deleted=0`）。
7. 分区表：`evt_event_log` 按 `server_time` 月 `RANGE COLUMNS`。分区名 `pYYYYMM`。**MUST** 唯一/主键包含分区键（MySQL 分区限制）。`task_progress_report` **禁止分区**（design §3.1：唯一约束不能被分区键破坏去重）。
8. **MUST** 不可变表（快照、审计、事件、风控命中）无业务 `UPDATE`/`DELETE` 服务（RL-12）。清理只走调度批量删除（审计 180 天、事件丢分区、进度 7 天）。

### 6.1 索引命名示例

```sql
UNIQUE KEY uk_username (username),
UNIQUE KEY uk_idempotent (grant_source, source_id, prize_id),
KEY idx_user_cycle (user_id, task_id, cycle_key)
```

## 7. DDL 形态

> **形态示意，禁止抄进 `V*`。** 列集、注释、索引、CHECK 以 design §3 对应 `CREATE TABLE` 逐字为准。`task_instance.version` 是绑定的**发布版本号**，不是乐观锁列；乐观锁列在 `task_instance_step.version`。

```sql
-- 只演示：主键 / 时间默认值 / 唯一键 / CHECK / 字符集。不要拿本块当 V2。
CREATE TABLE example_shape (
  id         BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  status     VARCHAR(16) NOT NULL COMMENT '封闭枚举写在 COMMENT 与 CHECK',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_biz (status),
  CHECK (status IN ('A','B'))
) COMMENT '形态示意' DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

规则：

1. **MUST** `created_at` / `updated_at` 由 DB 默认值维护（design §3.1）。应用层不要自己写「更新时间」除非要覆盖。
2. **MUST** 种子数据在对应版本脚本中，可重复执行时用 `INSERT ... SELECT ... WHERE NOT EXISTS` 或一次性初始化（V1 空库假设）。
3. **MUST NOT** 在 DDL 中硬编码生产密码。超管哈希为空，由 `MKT_INIT_ADMIN_PASSWORD` 注入（design §3.1 种子注记）。

## 8. DML 与查询

1. **MUST** 参数化。禁止 `${}` 拼接列值。MyBatis 动态列名必须白名单。
2. **MUST NOT** `SELECT *` 作为跨层契约（可在单测夹具使用）。列表接口按 design §4.1 投影，排除 `password` / `secret` / 密文。
3. **MUST** 分页走 `page` + `pageSize`（上限 100）。大表禁止无条件 `COUNT(*)` 扫描全表作为默认；管理端查询必须带可走索引的过滤或接受深分页成本。
4. **MUST** 条件更新带状态前置。有乐观锁列的表（`task_instance_step`）才写 `version=version+1 AND version=?`；库存见本篇第 7 条。
5. **MUST NOT** 在循环里逐条 SQL 拼「N+1」。批量用 `IN`（注意上限）或临时键。
6. **MUST NOT** 用 `OFFSET` 翻超大页做对账扫描；对账/清理用键集分页（`WHERE id > ? ORDER BY id LIMIT 5000`）。调度清理批次 5000（design §6.7）。
7. **MUST** 库存扣减用单条原子 SQL：`UPDATE rwd_prize SET remaining_stock = remaining_stock - ? WHERE id=? AND remaining_stock >= ?`（design §5.7）。
8. Outbox Relay **MUST** `WHERE producer = :thisApp`（D-11）。禁止跨应用抢行。
9. **MUST NOT** 在业务事务里跑 `information_schema` 全库扫描（分区预建调度除外，且在 admin 调度中）。

```sql
-- 正例：库存原子扣减（design §5.7；rwd_prize 无 version 列）
UPDATE rwd_prize
   SET remaining_stock = remaining_stock - #{qty}
 WHERE id = #{id}
   AND deleted = 0
   AND remaining_stock >= #{qty};

-- 正例：步骤完成 CAS（乐观锁列在 task_instance_step）
UPDATE task_instance_step
   SET status = 'COMPLETED', completed_at = NOW(3), version = version + 1
 WHERE id = #{id}
   AND version = #{version}
   AND status = 'ACTIVE';
```

```sql
-- 反例：读出库存再写回；给 rwd_prize 发明 version
UPDATE rwd_prize SET remaining_stock = #{newStock} WHERE id = #{id};
```

行级乐观锁与 01 §7.4 对齐：只用于 **design 声明了乐观锁列** 的表。入参 **MUST** 为 `@Param` 基本类型，**MUST NOT** 传入带 `@Version` 的 Entity。普通单行更新（改名称、改开关）走实体 `@Version` + `updateById`，不要在 XML 里重复 `version + 1`。

## 9. 事务与锁

1. 默认隔离级别 READ COMMITTED（design §5.7）。**MUST NOT** 为了「省事」升到 SERIALIZABLE。
2. `SELECT ... FOR UPDATE` **只允许** identity 在 `UserAttributePort.lockAndGet` 对 `sys_portal_user` 使用（D-12）。task / reward **MUST NOT** 对用户表加锁。
3. 长事务 **MUST NOT** 包含外部 HTTP、文件 IO、sleep。
4. 只读查询 **SHOULD** 不加行锁。

## 10. 安全

1. **MUST** 应用账号最小权限：DML + 执行已有例程；生产应用账号 **MUST NOT** 拥有 `DROP DATABASE` / `GRANT`。迁移使用独立迁移账号（部署规范 14）。
2. **MUST NOT** 把终端用户输入拼进 `ORDER BY` / `LIMIT`。
3. 审计、事件查询对管理端输出仍要脱敏（R10.6）。

## 11. 评审 SQL 时看什么

- 是否改了已应用的 `V*` 文件（直接拒绝）。
- 是否缺 CHECK / UNIQUE / COMMENT。
- 是否引入物理 FK 或 `ENUM` 类型。
- 条件更新是否带状态与 version；原子 SQL 的 `version + 1` 是否只出现一次。
- 是否 `SELECT *` 把哈希带到 API。
- 分区与唯一键是否冲突。
- 字符集是否误写成 utf8。

## 12. AI 检查清单

- [ ] 脚本在 `platform-db`，命名 `V<n>__<域>_<描述>.sql`
- [ ] 未修改已存在的 V1–Vn 内容
- [ ] utf8mb4 / utf8mb4_0900_ai_ci
- [ ] 表前缀属于正确域
- [ ] 时间 `DATETIME(3)`，布尔 `TINYINT(1)`，枚举 VARCHAR+CHECK
- [ ] 无物理外键
- [ ] 幂等路径有 UNIQUE
- [ ] 金钱用分
- [ ] 原子 CAS 的 `version + 1` 只出现一次，未与 `@Version` 插件叠用
- [ ] XML SQL 全是 `#{}` 不是 `${}`（白名单列名除外）
