# 活动模块 SQL

最后更新：2026-05-29

## 迁移索引

| 版本 | 文件 | 内容 |
|---|---|---|
| V18 | V18__activity_module.sql | 活动核心表 |
| V19 | V19__activity_code_unify.sql | display_rule 迁移至 activity_code |
| V21 | V21__activity_stats_code.sql | stats 迁移至 activity_code |

---

## V18 — 活动核心表

### activity

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| code | VARCHAR(64) NOT NULL | 活动编码 (唯一) |
| name | VARCHAR(128) NOT NULL | 活动名称 |
| description | VARCHAR(512) | 简短描述 |
| status | VARCHAR(16) NOT NULL DEFAULT 'DRAFT' | DRAFT/PUBLISHED/ONLINE/OFFLINE |
| gray_type | VARCHAR(16) NOT NULL DEFAULT 'NONE' | NONE/RATIO/WHITELIST |
| gray_config | JSON | 灰度配置 |
| start_time | DATETIME NOT NULL | 开始时间 |
| end_time | DATETIME NOT NULL | 结束时间 |
| participation_rules | JSON | 参与规则配置 |
| cache_version | INT NOT NULL DEFAULT 1 | 缓存版本号 |
| created_by | VARCHAR(64) | 创建人 |
| updated_by | VARCHAR(64) | 更新人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted | TINYINT NOT NULL DEFAULT 0 | 逻辑删除 |

索引: `uk_activity_code(code)`

### activity_display_rule

| 字段 | 类型 | 说明 |
|---|---|---|
| activity_id | BIGINT NOT NULL PK | 关联活动 ID (V19 迁移后移除) |
| content | MEDIUMTEXT | 富文本/Markdown 内容 |
| content_hash | VARCHAR(64) | 内容 hash (ETag) |
| updated_at | DATETIME | 更新时间 |
| updated_by | VARCHAR(64) | 更新人 |

### activity_stats

| 字段 | 类型 | 说明 |
|---|---|---|
| activity_id | BIGINT NOT NULL | 活动 ID (V21 迁移后移除) |
| stat_date | DATE NOT NULL | 统计日期 |
| participant_count | INT NOT NULL DEFAULT 0 | 参与人数 |
| completion_count | INT NOT NULL DEFAULT 0 | 完成人数 |
| reward_count | INT NOT NULL DEFAULT 0 | 奖励数 |

主键: `(activity_id, stat_date)` (V21 迁移后变更)

### 子模块关联

V18 为 task、signin_config、prize、prize_record 表新增 `activity_code VARCHAR(64)` 列及索引。

---

## V19 — display_rule 迁移至 activity_code

重构 `activity_display_rule`:
1. 新增 `activity_code VARCHAR(64) NOT NULL` 列
2. 从 activity 表回填数据
3. 主键改为自增 `id BIGINT`
4. 新增唯一索引 `uk_activity_code(activity_code)`
5. 移除 `activity_id` 列

最终结构:

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| activity_code | VARCHAR(64) NOT NULL | 活动编码 (唯一) |
| content | MEDIUMTEXT | 富文本/Markdown 内容 |
| content_hash | VARCHAR(64) | 内容 hash (ETag) |
| updated_at | DATETIME | 更新时间 |
| updated_by | VARCHAR(64) | 更新人 |

---

## V21 — stats 迁移至 activity_code

重构 `activity_stats`:
1. 主键改为自增 `id BIGINT`
2. 新增 `activity_code VARCHAR(64) NOT NULL` 列
3. 从 activity 表回填数据
4. 新增唯一索引 `uk_activity_code_date(activity_code, stat_date)`
5. 移除 `activity_id` 列

最终结构:

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 主键 |
| activity_code | VARCHAR(64) NOT NULL | 活动编码 |
| stat_date | DATE NOT NULL | 统计日期 |
| participant_count | INT NOT NULL DEFAULT 0 | 参与人数 |
| completion_count | INT NOT NULL DEFAULT 0 | 完成人数 |
| reward_count | INT NOT NULL DEFAULT 0 | 奖励数 |

索引: `uk_activity_code_date(activity_code, stat_date)`
