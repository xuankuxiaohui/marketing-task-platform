# 奖品模块设计规格

日期: 2026-05-24 | 版本: v1.0 | 分支: main

## 1. 目标

将发奖逻辑从任务模块解耦为独立 `prize` package，统一发奖入口，支持多奖品类型、库存管理、限制规则、自动/手动领奖、领奖专区、过期回收、防重复到账。

## 2. 架构概览

```
com.marketing.task.prize/
  ├── domain/
  │   ├── entity/           Prize, PrizeRecord, PrizeClaimLock, PrizeInventoryRecord
  │   ├── enums/            PrizeType, PrizeRecordStatus, ClaimExpireType, GroupStrategy
  │   └── config/           PrizeParams (接口), PointParams, CouponParams, BadgeParams ...
  ├── mapper/
  │   ├── PrizeMapper
  │   ├── PrizeRecordMapper
  │   ├── PrizeClaimLockMapper
  │   └── PrizeInventoryRecordMapper
  ├── service/
  │   ├── PrizeService           统一发奖入口: grant(userId, prizeId, quantity, bizCtx)
  │   ├── PrizeHandler           策略接口: supports(), validate(), grant(), queryBalance()
  │   ├── handlers/              PointPrizeHandler, CouponPrizeHandler, BadgePrizeHandler,
  │   │                          PhysicalPrizeHandler, InternalPrizeHandler ...
  │   ├── PrizeLimiter           限制校验链接口: check(ctx, prize)
  │   ├── limiters/              InventoryLimiter, MutexLimiter, ProvinceLimiter,
  │   │                          UserRateLimiter, PrizeStatusLimiter
  │   ├── ClaimService           领奖: claim(recordId)
  │   └── PrizeExpiryScheduler   到期扫描 + 过期处理
  └── controller/
      ├── ClientPrizeController     领奖专区 API
      └── AdminPrizeController      奖品管理 API
```

**与任务模块的交互：**

```
StepAdvanceEngine.cascade()
  └─ RewardStepHandler.onStepEnter()
       ├─ 从 task_step 读取 prize_id, prize_quantity
       └─ prizeService.grant(userId, prizeId, quantity, bizContext)
            └─ {... 奖品模块内部逻辑 ...}

任务模块只需要 prize_id + prize_quantity，不关心奖品类型和参数。
```

## 3. 数据模型

### 3.1 prize — 奖品配置

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | 奖品 ID |
| type | VARCHAR(32) NOT NULL | `point` / `coupon` / `badge` / `physical` / `membership` / `internal` |
| name | VARCHAR(128) NOT NULL | 奖品名称 |
| description | VARCHAR(512) | 描述 |
| handler_bean | VARCHAR(64) NOT NULL | Spring bean 名称，如 `pointPrizeHandler` |
| params_json | JSON NOT NULL | 类型化参数（见 3.1a） |
| total_stock | INT NULL | 总库存，NULL=不限 |
| monthly_stock | INT NULL | 月库存，NULL=不限 |
| daily_stock | INT NULL | 日库存，NULL=不限 |
| user_total_limit | INT NULL | 单人总领取上限 |
| user_monthly_limit | INT NULL | 单人月领取上限 |
| user_daily_limit | INT NULL | 单人日领取上限 |
| limits_json | JSON NULL | 规则校验链配置（见 3.1b） |
| activity_id | BIGINT NULL | 归属活动，用于统计和分组 |
| group_key | VARCHAR(64) NULL | 分组标识，同活动同组可参与抽奖策略 |
| group_strategy | VARCHAR(32) NULL | `random` / `weighted` / `sequential` |
| group_weight | INT DEFAULT 1 | 分组权重 |
| icon_url | VARCHAR(512) NULL | 列表 icon |
| claim_zone_image_url | VARCHAR(512) NULL | 领奖专区大图 |
| auto_grant | TINYINT(1) DEFAULT 0 | 1=中奖后自动到账，0=手动领奖 |
| claim_expire_type | VARCHAR(16) NOT NULL | `DAYS` / `CALENDAR_MONTH` / `FIXED_DATE` |
| claim_expire_value | VARCHAR(64) NOT NULL | 7, 1, 2026-06-30 等 |
| max_retry | INT DEFAULT 3 | 失败最大重试次数 |
| enabled | TINYINT(1) DEFAULT 1 | 启用/停用 |
| start_time | DATETIME NULL | 奖品有效期起始 |
| end_time | DATETIME NULL | 奖品有效期结束 |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | |
| updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE | |

索引: `idx_activity_group (activity_id, group_key)`，`idx_type`。

#### 3.1a params_json — 类型化参数

每种类型定义自己的 Params 类（JSON 序列化存入 params_json）：

```java
// point 积分
public class PointParams { int amount; String reason; }

// coupon 优惠券
public class CouponParams { String templateId; int amount; int expireDays; }

// badge 徽章
public class BadgeParams { String badgeId; String name; String iconUrl; }

// physical 实物
public class PhysicalParams { String skuId; String name; boolean requireAddress; }

// membership 会员卡
public class MembershipParams { int level; int durationDays; }
```

#### 3.1b limits_json — 校验规则链

```json
{
  "mutex_prize_ids": [1, 3, 5],
  "provinces": {"type": "allow", "list": ["BJ", "SH"]},
  "min_level": 3,
  "tags": {"type": "deny", "list": ["blacklist"]}
}
```

`PrizeLimiter` 接口根据此 JSON 动态构建校验链。

### 3.2 prize_inventory_record — 库存扣减记录

每次发奖扣减库存时插入一条记录。日/月库存通过时间范围查询，无需 cycle_key 分桶。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| prize_id | BIGINT NOT NULL | |
| record_id | BIGINT NOT NULL | 关联 prize_record.id |
| quantity | INT NOT NULL | 扣减数量 |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | 扣减时间 |

唯一约束: `uk_prize_record (prize_id, record_id)`。
索引: `idx_prize_created (prize_id, created_at)`。

库存校验逻辑:

```sql
-- 总库存: SELECT SUM(quantity) FROM prize_inventory_record WHERE prize_id = ?
-- 月库存: SELECT SUM(quantity) FROM prize_inventory_record
--          WHERE prize_id = ? AND created_at >= '2026-05-01 00:00:00'
-- 日库存: SELECT SUM(quantity) FROM prize_inventory_record
--          WHERE prize_id = ? AND created_at >= '2026-05-24 00:00:00'
```

`(prize_id, created_at)` 索引覆盖日/月范围查询，性能无问题。过期回滚库存时插入负数 quantity 记录即可。

### 3.3 prize_record — 中奖记录

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| user_id | VARCHAR(64) NOT NULL | |
| instance_id | BIGINT NOT NULL | 任务实例 ID |
| task_id | BIGINT NOT NULL | 任务 ID（冗余） |
| step_id | BIGINT NOT NULL | 步骤 ID |
| prize_id | BIGINT NOT NULL | 奖品 ID |
| quantity | INT DEFAULT 1 | 数量 |
| idempotent_key | VARCHAR(128) NOT NULL | `{instance_id}:{step_id}:{prize_id}` |
| **快照（冗余，不可变）** | | |
| prize_type | VARCHAR(32) NOT NULL | 奖品类型快照 |
| prize_name | VARCHAR(128) NOT NULL | 奖品名称快照 |
| prize_icon | VARCHAR(512) NULL | icon 快照 |
| prize_image | VARCHAR(512) NULL | 领奖专区图快照 |
| prize_params_json | JSON NOT NULL | 参数快照 |
| activity_id | BIGINT NULL | 活动 ID 快照 |
| **状态 & 时效** | | |
| status | VARCHAR(16) NOT NULL | WON / CLAIMING / GRANTED / FAILED / FAILED_PERMANENTLY / EXPIRED |
| expire_time | DATETIME NOT NULL | 领取截止时间 |
| retry_count | INT DEFAULT 0 | 失败重试次数 |
| error_message | VARCHAR(1024) NULL | 最近一次失败原因 |
| external_trade_no | VARCHAR(128) NULL | 外部 API 交易号 |
| **时间戳** | | |
| won_at | DATETIME NOT NULL | 中奖时间 |
| claimed_at | DATETIME NULL | 发起领奖时间 |
| granted_at | DATETIME NULL | 到账时间 |
| updated_at | DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE | |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | |

唯一约束: `uk_idempotent (idempotent_key)`。
索引: `idx_user_status (user_id, status)`，`idx_expire (status, expire_time)`，`idx_prize_id`。

### 3.4 prize_claim_lock — 领奖锁

数据库级分布式锁，保证同一条中奖记录串行领奖。

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| record_id | BIGINT UNIQUE NOT NULL | 关联 prize_record.id |
| created_at | DATETIME DEFAULT CURRENT_TIMESTAMP | |

无索引（仅唯一约束）。该表数据量极小（仅执行中的领奖操作，完成后立即删除）。

## 4. 状态机

```
              ┌─────────────────────────────┐
              │         任务完成触发发奖        │
              │   prizeService.grant(...)     │
              └─────────────┬───────────────┘
                            │
                   ┌────────v──────────┐
                   │  校验链: 库存/互斥  │
                   │  /省/等级/用户频率   │
                   └────────┬──────────┘
                            │ 全部通过
                   ┌────────v──────────┐
                   │  扣库存             │
                   │  生成 prize_record  │
                   │  状态: WON          │
                   └────────┬──────────┘
                            │
              ┌─────────────┴─────────────┐
              │ auto_grant = 1             │ auto_grant = 0
              │ 自动触发领奖                │ 待用户手动领取
              v                            v
    ┌─────────────────┐     ┌─────────────────────────┐
    │  领奖锁 INSERT    │     │ 用户进领奖专区 GET /records│
    │  → CLAIMING      │     │ → expire_time < NOW()   │
    │  → 调外部 API     │     │ → 标记 EXPIRED (主动)    │
    │  → GRANTED/FAILED│     └───────────┬─────────────┘
    │  → 释放锁 DELETE  │                 │ 未过期，用户点"领取"
    └─────────────────┘     ┌────────────v──────────────┐
                            │  校验 expire_time > NOW()  │
                            │  → 领奖锁 INSERT           │
                            │  → CLAIMING               │
                            │  → 调外部 API              │
                            │  → GRANTED / FAILED       │
                            │  → 释放锁 DELETE           │
                            └───────────────────────────┘

WON ──(过期)──→ EXPIRED      (用户进专区时主动标记 + 定时任务每小时兜底)
FAILED ─(retry_count < max)──→ CLAIMING ─→ GRANTED
FAILED ─(retry_count >= max)─→ FAILED_PERMANENTLY  (终态)
```

### 4.1 状态说明

| 状态 | 含义 | 触达路径 |
|---|---|---|
| WON | 已中奖，待领取/待自动发放 | 发奖入口 |
| CLAIMING | 领奖进行中（调用外部 API） | 自动触发 或 用户手动领取 |
| GRANTED | 已到账（终态） | handler 返回成功 |
| FAILED | 发放失败，可重试 | handler 抛出异常 |
| FAILED_PERMANENTLY | 超过最大重试，不可恢复（终态） | retry_count >= max_retry |
| EXPIRED | 超期未领取（终态） | 用户进专区 / 定时扫描 |

### 4.2 到期处理（三机制）

| 机制 | 触发条件 | 行为 |
|---|---|---|
| 用户进领奖专区 | `GET /prize/records` 请求时 | 遍历用户 WON 记录，`expire_time < NOW()` → UPDATE `EXPIRED` |
| 定时扫描 | `@Scheduled cron="0 7 * * * ?"` 每小时第7分钟 | `SELECT ... WHERE status='WON' AND expire_time < NOW() LIMIT 200` → UPDATE `EXPIRED` |
| 领奖时最后校验 | `POST /claim` 时 | 若 `expire_time < NOW()` → 拒绝并标记 EXPIRED（gate check 兜底） |

定时扫描理由: 用户长期不访问时仍需回收库存和统计，每小时一次 + LIMIT 200 分批，走索引无性能压力。

## 5. 核心接口

### 5.1 PrizeService — 统一发奖入口

```java
public class PrizeService {

    /**
     * @param userId     用户 ID
     * @param prizeId    奖品 ID
     * @param quantity   数量
     * @param bizContext 业务上下文 (instanceId, taskId, stepId, cycleKey, userProfile)
     * @return PrizeRecord (status = WON)
     */
    @Transactional
    public PrizeRecord grant(String userId, Long prizeId, int quantity, GrantContext bizContext) {
        // 1. 幂等检查: prize_record 是否已有 idempotent_key 的 SUCCESS/GRANTED 记录
        // 2. 加载 prize 配置
        // 3. 执行限制校验链 (库存 / 互斥 / 省份 / 等级 / 用户频率)
        // 4. 扣库存 → prize_inventory_record
        // 5. 快照 prize 配置 → prize_record (WON)
        // 6. if auto_grant → claim(recordId)
        // 7. return record
    }
}
```

### 5.2 PrizeHandler — 奖品策略接口

```java
public interface PrizeHandler {
    /** 支持的奖品类型 */
    PrizeType supports();

    /** 发奖前校验参数 */
    void validate(Prize prize);

    /**
     * 执行发奖（调外部 API 或内部逻辑）
     * @return GrantResult: success + tradeNo
     * @throws GrantException: 失败原因
     */
    GrantResult grant(PrizeRecord record, Prize prize);

    /** 查询用户余额（可选，用于积分/优惠券显示） */
    default Optional<Long> queryBalance(String userId) { return Optional.empty(); }
}
```

### 5.3 PrizeLimiter — 校验链

```java
public interface PrizeLimiter {
    /**
     * @return 校验不通过时返回拒绝原因，通过返回 Optional.empty()
     */
    Optional<String> check(UserProfile user, Prize prize, LocalDateTime now);
}
```

内置 Limiter：

| Limiter | 职责 | 数据来源 |
|---|---|---|
| PrizeStatusLimiter | 奖品是否启用、是否在有效期 | prize.enabled / start_time / end_time |
| InventoryLimiter | 总/月/日库存可用 | prize_inventory_record |
| UserRateLimiter | 单人总/月/日限制 | prize_record GROUP BY user_id |
| MutexLimiter | 互斥奖品已中奖/已领取 | prize_record status IN (WON,CLAIMING,GRANTED) |
| ProvinceLimiter | 省份黑白名单 | userProfile.province |
| LevelLimiter | 等级下限/上限 | userProfile.level |
| TagLimiter | 标签黑白名单 | userProfile.tags |

### 5.4 ClaimService — 领奖

```java
public class ClaimService {

    /**
     * 用户发起领奖。同一时刻只有一个线程可执行。
     */
    @Transactional
    public ClaimResult claim(Long recordId) {
        // 1. 加载 prize_record，校验 status = WON 或 FAILED
        // 2. 校验 expire_time > NOW() —— 否则标记 EXPIRED 返回
        // 3. 尝试 INSERT prize_claim_lock(record_id)
        //      → 冲突: 返回 "正在处理中，请勿重复操作"
        // 4. UPDATE prize_record SET status = 'CLAIMING'
        // 5. 加载 prize，PrizeHandler.grant(record, prize)
        //      → 成功: UPDATE status='GRANTED', granted_at=NOW()
        //      → 失败: UPDATE status='FAILED', error_message, retry_count+1
        //              if retry_count >= max_retry → FAILED_PERMANENTLY
        // 6. DELETE prize_claim_lock
        // 7. return ClaimResult
    }
}
```

### 5.5 PrizeExpiryScheduler

```java
@Component
public class PrizeExpiryScheduler {

    @Scheduled(cron = "0 7 * * * ?")  // 每小时第7分钟，避开整点
    @Transactional
    public void expireOverdueRecords() {
        // SELECT id FROM prize_record
        // WHERE status = 'WON' AND expire_time < NOW()
        // LIMIT 200
        //
        // FOR each: UPDATE status = 'EXPIRED', updated_at = NOW()
        // （可选：回滚库存 → prize_inventory_record 插入负数记录）
    }
}
```

## 6. 领奖专区 API

### 6.1 ClientPrizeController

```
GET  /api/client/prize/records
     → 返回用户所有中奖记录（分 WON / GRANTED / EXPIRED tab）
     请求时触发主动过期检查: 扫描 WON 记录中 expire_time < NOW() 的标记为 EXPIRED

POST /api/client/prize/{recordId}/claim
     → 领取指定奖品
     → 返回 ClaimResult { status, tradeNo, errorMessage }

GET  /api/client/prize/{recordId}
     → 中奖记录详情（含奖品快照信息）
```

### 6.2 领奖专区前端展示

```
┌─────────────────────────────────┐
│  领奖专区                          │
│  ┌───┬───┬───┐                   │
│  │待领取│已到账│已过期│  ← Tab 切换    │
│  └───┴───┴───┘                   │
│                                 │
│  ┌─────────────────────────┐    │
│  │ 🏷 新人积分包             │    │
│  │ +10 积分                 │    │
│  │ 有效期至 2026-06-01       │    │
│  │              [立即领取]   │    │
│  └─────────────────────────┘    │
│  ┌─────────────────────────┐    │
│  │ 🎫 满减优惠券             │    │
│  │ ¥10 满 ¥50 可用          │    │
│  │ 已到账 2026-05-25         │    │
│  └─────────────────────────┘    │
└─────────────────────────────────┘
```

### 6.3 AdminPrizeController

```
GET    /api/admin/prize                分页查询奖品列表
POST   /api/admin/prize                创建奖品
PUT    /api/admin/prize/{id}           更新奖品
POST   /api/admin/prize/{id}/toggle    启用/停用
GET    /api/admin/prize/{id}           奖品详情
GET    /api/admin/prize/{id}/records   该奖品的中奖记录分页
```

## 7. 任务模块改造

### 7.1 task_step 加列

Flyway V6 迁移:

```sql
ALTER TABLE task_step
  ADD COLUMN prize_id BIGINT NULL AFTER reward_config_json,
  ADD COLUMN prize_quantity INT DEFAULT 1 AFTER prize_id;
```

`reward_config_json` 保留但标记废弃，后续版本移除。

### 7.2 RewardStepHandler 改造

```java
@Component
@RequiredArgsConstructor
public class RewardStepHandler implements StepHandler {
    private final PrizeService prizeService;

    @Override
    public void onStepEnter(StepContext context) {
        TaskStep step = context.getStep();
        if (step.getPrizeId() == null) {
            // 兼容旧 reward_config_json 方式 (废弃)
            legacyRewardService.reward(context.getInstance(), step);
            return;
        }
        prizeService.grant(
            context.getInstance().getUserId(),
            step.getPrizeId(),
            step.getPrizeQuantity() != null ? step.getPrizeQuantity() : 1,
            GrantContext.of(context)
        );
    }
}
```

### 7.3 旧 reward_record 表

保留不删除，标记 `@Deprecated`。旧数据继续可读，新发奖走 prize_record。

## 8. 现有文件变更总结

| 文件 | 操作 | 说明 |
|---|---|---|
| `.../prize/**` (新 package) | 新增 | 全部奖品模块代码 |
| `Flyway V6` | 新增 | task_step 加列 + prize/prize_record/prize_inventory_record/prize_claim_lock 建表 |
| `Flyway V5__reward_record.sql` | 保留 | 不删除，旧数据兼容 |
| `TaskStep.java` | 修改 | 增加 `prizeId`, `prizeQuantity` 字段 |
| `RewardStepHandler.java` | 修改 | 改为调用 `prizeService.grant()` |
| `LogRewardService.java` | 标记 @Deprecated | 保留兼容路径 |

## 9. 测试策略

| 层级 | 测试内容 | 文件 |
|---|---|---|
| 单元 | 每个 PrizeHandler 的 grant() | *PrizeHandlerTest |
| 单元 | 每个 Limiter 的 check() | *LimiterTest |
| 单元 | ClaimService 领奖 + 锁 + 状态流转 | ClaimServiceTest |
| 单元 | PrizeService 发奖 + 幂等 + 库存扣减 | PrizeServiceTest |
| 单元 | PrizeExpiryScheduler 过期标记 | PrizeExpirySchedulerTest |
| 集成 | 全链路: 任务完成 → 发奖 → 领奖 → 到账 | PrizeLifecycleIntegrationTest |

目标: 至少 20 个新增单元测试。

## 10. 实施步骤

| 步骤 | 产出 | 估时 |
|---|---|---|
| 1. 创建 domain entity + enums + config 类 | Prize, PrizeRecord, PrizeClaimLock, PrizeInventoryRecord, enums | 中期 |
| 2. Flyway V6 建表 + task_step 改造 | 迁移脚本 + 实体字段 | 中期 |
| 3. 创建 Mapper 接口 | PrizeMapper, PrizeRecordMapper 等 | 短 |
| 4. 实现 PrizeHandler 策略模式 | 接口 + PointPrizeHandler + CouponPrizeHandler + BadgePrizeHandler | 中期 |
| 5. 实现 PrizeLimiter 校验链 | Limiter 接口 + 6 个内置 Limiter | 中期 |
| 6. 实现 PrizeService.grant() | 统一发奖入口 + 幂等 + 库存扣减 + 快照 | 中期 |
| 7. 实现 ClaimService + PrizeClaimLock | 领奖逻辑 + 锁 + 状态流转 + 重试 | 中期 |
| 8. 实现 PrizeExpiryScheduler | 主动过期 + 定时扫描 | 短 |
| 9. 实现 Controller | ClientPrizeController + AdminPrizeController | 短 |
| 10. 改造 RewardStepHandler | 调用 prizeService.grant() | 短 |
| 11. 单元测试 | >20 tests | 中期 |
| 12. 文档更新 | roadmap + release-notes + sql + api | 短 |
