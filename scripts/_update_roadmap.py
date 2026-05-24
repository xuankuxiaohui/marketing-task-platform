with open('docs/current-system-roadmap.md', 'r', encoding='utf-8') as f:
    content = f.read()

# Add prize module to completed items
old_bullet = "- 奖励处理："
new_bullet = """- 奖品模块（独立 prize package）：
  - prize / prize_record / prize_claim_lock / prize_inventory_record 四表。
  - PrizeService 统一发奖入口 + PrizeHandler 策略模式 (Point/Coupon/Badge/Internal)。
  - 7 个 PrizeLimiter 校验链（状态/库存/互斥/省份/等级/标签/用户频率）。
  - ClaimService 领奖 + prize_claim_lock 防重锁 + 自动/手动领奖模式。
  - PrizeExpiryScheduler 三机制过期（用户进专区 + 每小时扫描 + 领奖时校验）。
  - ClientPrizeController 领奖专区 API + AdminPrizeController 奖品管理 API。
  - task_step 新增 prize_id / prize_quantity，RewardStepHandler 接入 PrizeService。
  - 39 个单元测试。
- 奖励处理："""

content = content.replace(old_bullet, new_bullet)

# Update P0-2 in future plans
old_p02 = """2. ~~完成真实奖励系统对接~~ ✅ 已完成 (2026-05-24)：
   - reward_record 表 (instance_id, step_id) 唯一约束实现幂等。
   - FAILED 记录可重试，异常不抛出，不阻塞任务实例。
   - PENDING/SUCCESS/FAILED 状态 + error_message 记录失败原因。"""

new_p02 = """2. ~~完成真实奖励系统对接~~ ✅ 已完成 (2026-05-24)：
   - reward_record 表 + 幂等 + 失败重试（P0-2 阶段）。
   - 独立 prize 模块：PrizeService + PrizeHandler 策略 + 7 个 Limiter 校验链。
   - prize_claim_lock 防重锁 + ClaimService 领奖 + 自动/手动模式。
   - 三机制过期处理 + 领奖专区 API + 管理后台 API。
   - 39 个单元测试 (PrizeServiceTest 13 + ClaimServiceTest 9 + PrizeLimiterTest 17)。"""

content = content.replace(old_p02, new_p02)

# Update test count
content = content.replace('60 Tests', '109 Tests')

with open('docs/current-system-roadmap.md', 'w', encoding='utf-8') as f:
    f.write(content)
print('Done: roadmap updated')
