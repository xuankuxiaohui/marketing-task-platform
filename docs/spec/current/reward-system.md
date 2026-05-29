# 奖励系统架构

## 概述

系统中存在两套奖励机制，通过 `RewardStepHandler` 桥接：

```
步骤类型=REWARD
    │
    ├── step.prizeId != null  →  Prize 模块（推荐）
    │
    └── step.prizeId == null  →  Legacy RewardService（遗留）
```

## Legacy 奖励系统（遗留）

**入口**: `RewardStepHandler` → `RewardService.reward()`

**实现**: `LogRewardService` — 基于 `RewardHandler` 策略模式

| 组件 | 路径 | 说明 |
|---|---|---|
| `RewardService` | `service/reward/RewardService.java` | 接口 |
| `LogRewardService` | `service/reward/LogRewardService.java` | 唯一实现，幂等 + 审计 |
| `RewardHandler` | `service/reward/RewardHandler.java` | 策略接口 |
| `PointRewardHandler` | `service/reward/PointRewardHandler.java` | 积分发放 |
| `CouponRewardHandler` | `service/reward/CouponRewardHandler.java` | 优惠券发放 |
| `BadgeRewardHandler` | `service/reward/BadgeRewardHandler.java` | 徽章发放 |
| `RewardConfigParser` | `service/reward/RewardConfigParser.java` | 解析 rewardConfigJson |

**数据表**: `reward_record`（instance_id + step_id 唯一约束）

**幂等保证**: `(instance_id, step_id)` 唯一约束 + 状态检查（SUCCESS 则跳过）

**状态机**: PENDING → SUCCESS / FAILED

## Prize 奖品模块（推荐）

**入口**: `RewardStepHandler` → `PrizeService.grant()`

**实现**: `PrizeService` + `ClaimService` + `PrizeHandler` 策略模式

| 组件 | 路径 | 说明 |
|---|---|---|
| `PrizeService` | `prize/service/PrizeService.java` | 发奖入口，库存 + 限流 |
| `ClaimService` | `prize/service/ClaimService.java` | 领取入口，分布式锁 + 重试 |
| `PrizeHandler` | `prize/service/PrizeHandler.java` | 策略接口 |
| `PointPrizeHandler` | `prize/service/handlers/PointPrizeHandler.java` | 积分 |
| `CouponPrizeHandler` | `prize/service/handlers/CouponPrizeHandler.java` | 优惠券 |
| `BadgePrizeHandler` | `prize/service/handlers/BadgePrizeHandler.java` | 徽章 |
| `InternalPrizeHandler` | `prize/service/handlers/InternalPrizeHandler.java` | 内部奖品 |

**数据表**: `prize`、`prize_record`、`prize_claim_lock`、`prize_inventory_record`

**限流器**（7 个）: Inventory、Level、Mutex、PrizeStatus、Province、Tag、UserRate

**幂等保证**: `(instance_id, step_id)` 唯一约束 + 分布式领取锁

**状态机**: WON → CLAIMING → GRANTED / FAILED → FAILED_PERMANENTLY / EXPIRED

## 桥接逻辑

`RewardStepHandler.onStepEnter()` 中的路由规则：

```java
if (step.getPrizeId() != null) {
    // 走 Prize 模块：PrizeService.grant()
    // 需要 UserContext（province, role, tags, orgId, level）
    prizeService.grant(user, step.getPrizeId(), quantity, grantCtx);
} else {
    // 走 Legacy：LogRewardService.reward()
    legacyRewardService.reward(instance, step);
}
```

## 如何添加新的奖励类型

### 在 Prize 模块中添加（推荐）

1. 在 `prize/service/handlers/` 下新建 `XxxPrizeHandler implements PrizeHandler`
2. 实现 `supports(Prize prize)` 和 `doGrant(Prize prize, PrizeRecord record, GrantContext ctx)`
3. 在 `prize` 表中配置 `handler_bean = "xxxPrizeHandler"`

### 在 Legacy 系统中添加（不推荐）

1. 在 `service/reward/` 下新建 `XxxRewardHandler implements RewardHandler`
2. 实现 `supports(RewardConfig config)` 和 `distribute(instance, step, config)`
3. 在步骤的 `reward_config_json` 中配置 `type` 字段

## 废弃路径

Legacy 系统最终应被 Prize 模块完全替代。当前迁移状态：

- [ ] 步骤配置 UI 引导用户选择 Prize 而非手动配置 rewardConfigJson
- [ ] 迁移现有使用 Legacy 的步骤到 Prize 模块
- [ ] 废弃 `LogRewardService` 和相关 `RewardHandler`

## 扩展阅读

- 奖品配置: `docs/spec/current/features.md` → 奖品模块
- 领取流程: `ClaimService` 的分布式锁和重试机制
- 库存管理: `PrizeInventoryRecord` 审计追踪
