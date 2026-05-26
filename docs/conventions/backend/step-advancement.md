# 步骤推进规则

## 步骤类型

| 类型 | 触发方式 | 行为 | 处理器 |
|---|---|---|---|
| `PASSIVE` | 自动（进入时） | 立即完成，级联到下一步 | `PassiveStepHandler` |
| `CLICK` | C 端点击 `/api/client/task/:taskId/step/:stepId/click` | 等待用户操作，完成后级联 | `ClickStepHandler` |
| `CALLBACK` | 外部回调 `/api/internal/task/callback` | 等待匹配 `callbackEventKey` 的回调，完成后级联 | `CallbackStepHandler` |
| `PROGRESS` | 外部进度上报 `/api/internal/task/progress` | 更新进度值；达到目标值后完成并级联；未达标保持 IN_PROGRESS | `ProgressStepHandler` |
| `REWARD` | 自动（进入时） | 调用 `RewardService.reward()`，标记 REWARDED，**停止级联**（任务终点） | `RewardStepHandler` |

参见 `backend/src/main/java/com/marketing/task/service/step/StepAdvanceEngine.java`。

## 状态机模型

### 实例生命周期（主状态机）

```
                    ┌──────────────────────────┐
                    │                          │
                    ▼                          │
  ┌─────────┐  enter()  ┌─────────────┐  complete  ┌───────────┐
  │ PENDING │ ────────► │ IN_PROGRESS │ ─────────► │ COMPLETED │
  └─────────┘           └─────────────┘            └───────────┘
                              │                          ▲
                              │    enter(REWARD)         │
                              └──────────────────────────┤
                                                         │
                                            ┌────────────┴──┐
                                            │   REWARDED    │ (终态)
                                            └───────────────┘
```

状态转换：
- `PENDING → IN_PROGRESS`：首次进入 `cascade()` 且当前步骤为 CLICK/CALLBACK/PROGRESS（非自动步骤）
- `PENDING → COMPLETED`：所有步骤均为 PASSIVE/REWARD 且全部自动完成
- `IN_PROGRESS → COMPLETED`：最后一步完成
- `IN_PROGRESS → REWARDED`：最后一步为 REWARD 且发奖成功

### 步骤进度（子状态机）

```
  ┌───────────┐   click/callback/progress   ┌───────────┐
  │ NOT_START  │ ──────────────────────────► │ COMPLETED │
  └───────────┘                             └───────────┘
        │                                         ▲
        │  progress(未达标)                        │
        ▼                                         │
  ┌─────────────┐    progress(达标)    ┌──────────┴──┐
  │ IN_PROGRESS │ ──────────────────► │  COMPLETED   │
  └─────────────┘                     └──────────────┘
```

### 设计约束
- 状态转换必须原子性（通过 `@Transactional` 保证）
- 不允许逆向转换（COMPLETED → IN_PROGRESS 禁止）
- 终态（COMPLETED / REWARDED）不可再变更

## 级联行为

入口：`StepAdvanceEngine.enter(instance)` 或任意步骤完成后的 `cascade()` 调用。

**循环逻辑**：
1. 从缓存加载当前任务的步骤列表
2. 找到 `instance.currentStepSeq` 对应的步骤
3. 若 `PASSIVE`：调用 handler → `completeStep` → seq+1 → 继续循环
4. 若 `REWARD`：调用 handler → `completeStep` → `markRewarded` → **停止**
5. 若 `CLICK` / `CALLBACK` / `PROGRESS`：`markInProgress` → **停止**（等待外部触发）
6. 若无更多步骤：`markCompleted` → **停止**

`PASSIVE` 和 `REWARD` 是自动级联的；其余类型需要外部触发后再次进入级联。

## 幂等要求

- `completeStep()`：检查 `UserTaskStepProgress` 是否已 `COMPLETED`，是则直接返回（不重复写）
- `progress()`：达标后 `COMPLETED` 的步骤，再次上报直接返回当前实例状态
- `callback()`：当前步骤不是 `CALLBACK` 或 `eventKey` 不匹配时抛 `BusinessException`
- 重复 click / callback / progress 对已完成步骤无副作用

### 幂等保障机制总结

| 操作 | 幂等检查 | 重复调用结果 |
|---|---|---|
| `enter()` | 实例状态非 PENDING 时跳过级联 | 返回当前状态 |
| `click()` | 步骤已 COMPLETED 时不重复写 | 继续级联到下一步 |
| `callback()` | 步骤类型 + eventKey 校验 | 不匹配抛异常 |
| `progress()` | 已达标的步骤跳过 | 返回当前实例 |
| `reward()` | `instance.rewardTime != null` 跳过 | 不做任何操作 |

## 实例创建幂等

`TaskService.getOrCreateInstance()`：
- `(user_id, task_id, cycle_key)` 数据库唯一约束保证同周期仅一个实例
- 并发创建时 catch `DuplicateKeyException`，重新查询已有实例

## 事务边界

`enter()` / `click()` / `callback()` / `progress()` 均标注 `@Transactional`。

整个级联 + 步骤完成在同一事务中执行。若任一步骤失败（如发奖 handler 不存在），事务回滚，实例不会停留在半推进状态。

## 并发控制

**当前机制**：
- 实例创建：数据库唯一约束 + `DuplicateKeyException` 重试查询（防止并发创建重复实例）
- 步骤推进：无显式并发控制，依赖 `@Transactional` 隔离级别（MySQL 默认可重复读）

**潜在风险**（当前未处理）：
- 并发回调：两个外部系统同时回调 `callback()`，可能导致步骤完成两次
- 并发进度上报：两个进度上报同时达到目标值，可能导致两次级联

**建议改进（P1）**：
- 乐观锁：在 `UserTaskInstance` 增加 `version` 字段，步骤推进时 `updateById` 附带 version 条件，更新失败则重试
- 悲观锁：对关键操作使用 `SELECT ... FOR UPDATE`（更安全但影响并发性能）
- 分布式锁：未来多实例部署时考虑 Redis 分布式锁

## 奖励步骤错误处理

`LogRewardService.reward()`：
1. 若 `instance.rewardTime != null`（已发奖），跳过（幂等）
2. 解析 `RewardConfig`（从 `step.rewardConfigJson`）
3. 查找匹配的 `RewardHandler`（`handler.supports(config)`）
4. 无匹配 handler → `BusinessException("未找到匹配的发奖处理器")` → 事务回滚
5. 有匹配 handler → `handler.distribute(instance, step, config)`

奖励失败**不能**将实例标记为已发奖。`RewardConfig.parse()` 对 null / 空 / 非法 JSON 安全处理，返回 `type=null` 或 `type="unknown"`。

## 互斥组约束

`TaskService.checkMutex()`：
- 仅当 `task.mutexGroupKey` 非 null 且非空白时生效
- 查询同一互斥组内其他任务，再检查用户是否有 PENDING 或 IN_PROGRESS 的实例
- 存在活跃实例 → `BusinessException("您有一个互斥任务正在进行中，请先完成它")`
- 空白互斥组等同于不参与互斥

## 分布式与扩展性考虑

**当前架构**：单体 Spring Boot 应用，`@Transactional` 覆盖所有推进逻辑。

**未来多服务拆分后**（P1+ 规划）：
- 奖励发放可能拆为独立服务 → 步骤推进与发奖不在同一数据库事务中
- 建议引入最终一致性方案：
  - **Outbox Pattern**：发奖请求写入 outbox 表（与步骤完成在同一事务），后台 worker 投递到消息队列，奖励服务消费
  - **Saga**：步骤完成 → 发奖请求 → 发奖成功回调确认；发奖失败时补偿（标记实例需要人工处理）
  - **Idempotency Key**：每次发奖请求带唯一幂等键（`instanceId + stepId`），奖励服务侧保证不重复发

这些方案留待 P1 阶段细化设计。
