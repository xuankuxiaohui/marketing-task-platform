# 活动配置与规则系统设计

> 日期: 2026-05-28
> 状态: 已确认
> 方案: 活动壳 + 可插拔规则链（方案 C）

## 1. 背景与目标

当前系统中没有统一的"活动"实体，`Prize` 上有一个悬空的 `activityId` 字段，签到模块的 `SignInConfig` 自称"签到活动"，任务模块有自己的生命周期。三个域各自为政。

**目标**：创建统一的 Activity 实体作为上层容器，任务、签到、奖品等子模块挂载到活动下（1:N），并提供可插拔的规则链支持参与条件、频率限制、防刷策略。需要考虑高并发场景。

## 2. 数据模型

### 2.1 Activity 表（`activity`）

| 列名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | 主键 |
| code | VARCHAR(64) UNIQUE | 活动编码，如 "double11_2026" |
| name | VARCHAR(128) | 活动名称 |
| description | VARCHAR(512) | 简短描述 |
| status | VARCHAR(16) | DRAFT / PUBLISHED / ONLINE / OFFLINE |
| gray_type | VARCHAR(16) | NONE / RATIO / WHITELIST |
| gray_config | JSON | 灰度配置 |
| start_time | DATETIME | 活动开始时间 |
| end_time | DATETIME | 活动结束时间 |
| participation_rules | JSON | 执行规则配置（checker 链参数） |
| cache_version | INT DEFAULT 1 | 缓存版本号，变更时 +1 |
| created_by | BIGINT | 创建人 |
| updated_by | BIGINT | 更新人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted | TINYINT DEFAULT 0 | 逻辑删除 |

### 2.2 ActivityDisplayRule 表（`activity_display_rule`）

展示规则拆到独立表，避免大文本字段影响活动列表查询性能。

| 列名 | 类型 | 说明 |
|---|---|---|
| activity_id | BIGINT PK / FK | 关联活动 |
| content | MEDIUMTEXT | 富文本/Markdown 内容 |
| content_hash | VARCHAR(64) | 内容 hash，用于 ETag |
| updated_at | DATETIME | 更新时间 |
| updated_by | BIGINT | 更新人 |

### 2.3 ActivityStats 表（`activity_stats`）

| 列名 | 类型 | 说明 |
|---|---|---|
| activity_id | BIGINT | 关联活动 |
| stat_date | DATE | 统计日期 |
| participant_count | INT | 参与人数 |
| completion_count | INT | 完成人数 |
| reward_count | INT | 发奖数 |
| UNIQUE(activity_id, stat_date) | | |

### 2.4 participation_rules JSON 结构

```json
{
  "checkers": [
    {"type": "NEW_USER", "params": {"days": 7}},
    {"type": "WHITELIST", "params": {"listCode": "vip_users"}},
    {"type": "REGION", "params": {"allowed": ["北京", "上海"]}}
  ],
  "limits": [
    {"scope": "USER_DAILY", "max": 3},
    {"scope": "USER_TOTAL", "max": 10},
    {"scope": "GLOBAL_DAILY", "max": 100000}
  ],
  "antiFraud": [
    {"type": "IP_RATE", "params": {"maxPerIp": 10, "windowSeconds": 60}},
    {"type": "DEVICE_FINGERPRINT", "params": {"maxPerDevice": 5}}
  ]
}
```

### 2.5 子模块关联

子模块通过 `activity_code`（而非 `activity_id`）关联活动。原因：编码跨环境一致，ID 自增不一致，测试环境和现网环境难以同步。

现有表新增字段：

```sql
ALTER TABLE task ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE signin_config ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE prize ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
ALTER TABLE prize_record ADD COLUMN activity_code VARCHAR(64) NULL COMMENT '关联活动编码';
```

子模块仍保持各自的生命周期管理（DRAFT/PUBLISHED/OFFLINE），Activity 只做上层容器。

## 3. 模块结构

```
backend/src/main/java/com/marketing/task/activity/
├── domain/
│   ├── entity/        Activity, ActivityDisplayRule, ActivityStats
│   ├── enums/         ActivityStatus, GrayType, CheckerType, LimitScope, AntiFraudType
│   └── dto/           ActivityDetailVO, ActivityListVO, RuleCheckResult
├── mapper/            ActivityMapper, ActivityDisplayRuleMapper, ActivityStatsMapper
├── checker/           ← 可插拔规则链
│   ├── ParticipationChecker.java        (接口)
│   ├── NewUserChecker.java
│   ├── WhitelistChecker.java
│   ├── RegionChecker.java
│   ├── UserDailyLimitChecker.java
│   ├── GlobalDailyLimitChecker.java
│   ├── IpRateChecker.java
│   └── DeviceFingerprintChecker.java
├── service/
│   ├── ActivityService.java             (CRUD + 状态机 + 灰度)
│   ├── ActivityRuleService.java         (规则链编排 + 执行)
│   ├── ActivityCacheService.java        (Caffeine 缓存管理)
│   └── ActivityStatsScheduler.java      (定时统计聚合)
└── controller/
    ├── AdminActivityController.java     (/api/admin/activities)
    └── ClientActivityController.java    (/api/client/activities)
```

## 4. 规则链执行流程

```
用户请求参与活动
    │
    ▼
ActivityCacheService.getActivity(id)  ← Caffeine 缓存，miss 时查 DB
    │
    ▼
ActivityRuleService.check(activity, userId, context)
    │
    ├── ParticipationChecker 链（顺序执行，任一失败即短路返回）
    │   ├── NewUserChecker
    │   ├── WhitelistChecker
    │   └── RegionChecker
    │
    ├── LimitChecker 链
    │   ├── UserDailyLimitChecker   ← RateLimiter 原子计数
    │   ├── UserTotalLimitChecker
    │   └── GlobalDailyLimitChecker
    │
    └── AntiFraudChecker 链
        ├── IpRateChecker           ← RateLimiter 滑动窗口
        └── DeviceFingerprintChecker
    │
    ▼
全部通过 → 执行业务逻辑
任一失败 → 返回具体原因（failCode + failMessage）
```

## 5. 限流策略（单节点优先，可扩展 Redis）

### 5.1 核心接口

```java
public interface RateLimiter {
    boolean tryAcquire(String key, int maxCount, Duration window);
}
```

### 5.2 单节点实现（Caffeine 滑动窗口）

默认实现，`@ConditionalOnProperty(name = "activity.rate-limiter.type", havingValue = "local", matchIfMissing = true)`。

使用 `Cache<String, Deque<Long>>` 存储每个 key 的请求时间戳队列，窗口内计数判断限流。

### 5.3 Redis 实现（后续扩展）

`@ConditionalOnProperty(name = "activity.rate-limiter.type", havingValue = "redis")`。

使用 ZSET + Lua 脚本实现原子滑动窗口计数。

### 5.4 配置切换

```yaml
activity:
  rate-limiter:
    type: local    # 单节点模式，改 redis 即可切换
```

### 5.5 Key 设计

| 场景 | Key 格式 | 示例 |
|---|---|---|
| IP 限流 | `act:ip:{activityId}:{ip}` | `act:ip:42:1.2.3.4` |
| 设备限流 | `act:dev:{activityId}:{deviceId}` | `act:dev:42:abc123` |
| 用户日限 | `act:uld:{activityId}:{userId}:{date}` | `act:uld:42:1001:20260528` |
| 全局日限 | `act:gd:{activityId}:{date}` | `act:gd:42:20260528` |

### 5.6 单节点 vs Redis

| | 单节点 (Caffeine) | Redis |
|---|---|---|
| 复杂度 | 低 | 中 |
| 多实例一致性 | 不保证 | 精确 |
| 性能 | ~0.01ms | ~1ms |
| 适用场景 | 单机部署、开发测试 | 多实例部署、生产环境 |

## 6. 状态机与灰度发布

### 6.1 状态流转

```
DRAFT ──publish──→ PUBLISHED ──(start_time)──→ ONLINE ──(end_time)──→ OFFLINE
  ↑                    │                            │
  │                    └──offline──→ OFFLINE         └──offline──→ OFFLINE
  └──────────────────────── back to DRAFT ──────────────────────────────┘
```

| 状态 | C 端可见 | 可参与 |
|---|---|---|
| DRAFT | 否 | 否 |
| PUBLISHED | 是（预告） | 否 |
| ONLINE | 是 | 是 |
| OFFLINE | 否 | 否 |

### 6.2 自动流转

- 定时任务扫描 PUBLISHED 且 start_time <= now 的活动 → 自动 ONLINE
- 定时任务扫描 ONLINE 且 end_time <= now 的活动 → 自动 OFFLINE

### 6.3 灰度发布

复用现有 Task 的灰度模式：NONE（全量）、RATIO（按比例）、WHITELIST（白名单）。

### 6.4 配置热更新

修改活动 → DB 更新 + cache_version++ + 主动清除 Caffeine 缓存。版本化缓存 key：`activity:{id}:v{cacheVersion}`。

## 7. 缓存分层

```
┌─────────────────────────────────────┐
│  Caffeine (本地)                     │
│  - activity:{id}        TTL 10min   │  ← 活动元数据（轻量）
│  - activity_rule:{id}   TTL 30min   │  ← 展示规则（大文本）
│  - activity:{id}:v{ver}             │  ← 版本化缓存
└──────────────┬──────────────────────┘
               │ miss
┌──────────────▼──────────────────────┐
│  MySQL                               │
│  - activity                          │
│  - activity_display_rule             │
└──────────────┬──────────────────────┘
               │ 限流/计数
┌──────────────▼──────────────────────┐
│  Redis（后续扩展）                    │
│  - limit:{activity}:{scope}:{key}   │
│  - antifraud:ip:{ip}:{activity}     │
│  - antifraud:device:{d}:{activity}  │
└─────────────────────────────────────┘
```

## 8. API 设计

### 8.1 管理端（`/api/admin/activities`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 分页列表 |
| GET | `/{id}` | 详情（不含展示规则富文本） |
| GET | `/{id}/display-rule` | 单独获取展示规则 |
| POST | `/` | 创建（草稿） |
| PUT | `/{id}` | 更新（仅 DRAFT/OFFLINE） |
| PUT | `/{id}/display-rule` | 更新展示规则 |
| DELETE | `/{id}` | 逻辑删除（仅 DRAFT） |
| POST | `/{id}/publish` | 发布 |
| POST | `/{id}/offline` | 下线 |
| GET | `/{id}/stats` | 统计数据 |
| GET | `/{id}/sub-modules` | 子模块列表 |

### 8.2 C 端（`/api/client/activities`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/` | 活动列表（轻量，不含展示规则） |
| GET | `/{id}` | 活动详情 |
| GET | `/{id}/display-rule` | 展示规则（ETag 缓存） |
| POST | `/{id}/participate` | 参与入口（校验规则链） |

### 8.3 展示规则 HTTP 优化

- 独立接口，不随活动列表返回
- `ETag: {content_hash}`，客户端 `If-None-Match` 返回 304
- `Cache-Control: max-age=1800`

## 9. 错误处理

### 9.1 统一返回格式

```java
public class RuleCheckResult {
    private boolean passed;
    private String failCode;      // 如 "LIMIT_EXCEEDED", "NOT_IN_WHITELIST"
    private String failMessage;   // 用户可读提示
    private String checkerType;   // 拦截的 checker
}
```

### 9.2 边界场景

| 场景 | 处理 |
|---|---|
| 活动已下线，用户正在参与 | 前置检查状态，返回"活动已结束" |
| checker 实现类不存在 | 启动时校验，缺失则拒绝发布 |
| checker 抛异常 | catch 后降级（可配置），记录告警日志 |
| 展示规则为空 | `hasDisplayRule` 标记 false |
| 子模块被删除 | 查询时自动过滤 |

## 10. Flyway 迁移（V18）

```sql
-- 活动表
CREATE TABLE activity (...);
CREATE TABLE activity_display_rule (...);
CREATE TABLE activity_stats (...);

-- 子模块关联字段
ALTER TABLE task ADD COLUMN activity_code VARCHAR(64) NULL;
ALTER TABLE signin_config ADD COLUMN activity_code VARCHAR(64) NULL;
ALTER TABLE prize ADD COLUMN activity_code VARCHAR(64) NULL;
ALTER TABLE prize_record ADD COLUMN activity_code VARCHAR(64) NULL;

-- 索引
CREATE INDEX idx_task_activity_code ON task(activity_code);
CREATE INDEX idx_signin_config_activity_code ON signin_config(activity_code);
CREATE INDEX idx_prize_activity_code ON prize(activity_code);
CREATE INDEX idx_prize_record_activity_code ON prize_record(activity_code);
```

## 11. 范围界定

### 本次实施

1. `activity` + `activity_display_rule` + `activity_stats` 表
2. `ActivityService` + `ActivityRuleService` + `ActivityCacheService`
3. `RateLimiter` 接口 + 单节点 Caffeine 实现
4. 可插拔 checker 链
5. 管理端 CRUD + 状态机 + 灰度
6. C 端活动列表 + 详情 + 展示规则（ETag）
7. 子模块通过 `activity_code` 关联（task、signin_config、prize、prize_record）
8. Flyway V18 迁移

### 列入计划但暂不实施

- 广告位系统（ad_slot）：独立的广告位管理，支持图片展示、跳转链接、嵌套广告位，作为 C 端活动聚合页的展示层
