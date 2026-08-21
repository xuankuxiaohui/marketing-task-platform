# 设计文档 · 算法与测试策略（§5 / §7）

> 本文是 [design.md](design.md) **v2.13** 分册。§ 编号与总册索引一致，引用仍写 design §x.y。
> 需求：[requirements.md](requirements.md) v3.9　选型：[component-selection.md](component-selection.md)
> 总册索引（§ → 锚点）：[design.md](design.md) §0.2。本章跳转：搜索 `<!-- §x.y -->`，不要记行号。

---

<!-- §5 -->
## 5. 核心算法与伪代码

> 本章所有算法与 feasibility-step-engine.md §2 场景矩阵一一对应（每个场景均有实现归宿）；并发正确性统一依赖 feasibility §3.1 的"DB 唯一约束 + 行级乐观锁"双保险，不引入分布式锁保护推进路径（P4 原则）。

<!-- §5.1 -->
### 5.1 步骤推进引擎（R14，feasibility §2/§3.1/§3.2）

#### 5.1.1 四入口与前置检查

| 入口 | 来源 | 前置检查（顺序） |
|------|------|-----------------|
| enter | 领取成功后（§5.5） | 实例刚创建必为 IN_PROGRESS |
| click | C 端 §4.9.2 | 实例 IN_PROGRESS → 用户黑名单冻结检查（R25.4）→ 步骤 ACTIVE |
| callback | internal §4.8 | HMAC 已过 → 实例存在 → IN_PROGRESS → 冻结检查 → 步骤 ACTIVE |
| progress | internal §4.8 | 同 callback + reportId 非空 |

前置检查语义（R14.6/属性 3/4）：实例终态且步骤已完成 → **200 幂等**返回终态快照；实例终态而步骤未完成 → 拒绝（expired/abandoned）；步骤 COMPLETED（实例仍 IN_PROGRESS）重复 click/callback → **200 幂等**返回当前快照，不得二次推进或二次发奖；步骤 INACTIVE/SKIPPED 或乱序 → `task.step.state-mismatch`(400)；用户黑名单冻结 → C 端 click `task.instance.frozen`(403)，internal callback `risk.blocked.account-restricted`(400)（实例保持原状态，R25.4；HTTP 差异有意，码分叉保留）。

#### 5.1.2 步骤完成 CAS（exactly-once 第一道防线）

```sql
UPDATE task_instance_step
SET status = 'COMPLETED', completed_at = NOW(3), version = version + 1
WHERE id = ? AND version = ? AND status = 'ACTIVE';
-- affected = 1：本请求持有完成权
-- affected = 0：重读行 → COMPLETED/SKIPPED = 幂等返回（R14.6）；仍 ACTIVE = 并发冲突，
--              自旋重读重试 ≤3 次，progress 入口耗尽返回 task.progress.processing（HTTP 400，R14.3/R15.4）；
--              click/callback 入口耗尽返回 task.step.processing(400，并发收敛中，可原样重试)
```

`affected = 1` 持有完成权的请求在**同事务**内追加 Outbox 事件 `task.step.complete` `{instanceId, taskId, stepCode, seq}`（服务端事件全集 = D-05，§6.4；R14.11）。

#### 5.1.3 级联推进 cascade（R14.2/14.4/14.5）

```text
cascade(instance, fromSeq, sameRequestInsert=false):   # 与入口调用方同一事务（级联内无外部 IO，feasibility §3.2）
  loop:                                     # 单事务最多 task.step.max-count 轮（50，附录 A）
    next = resolveNextStep(instance, fromSeq)          # §5.2；null = 无后续步骤
    if next == null:
      completeInstance(instance); return              # 实例终态 CAS + Outbox task.instance.complete（R14.11）
    activate(next)                                     # INACTIVE→ACTIVE 的 CAS（同 5.1.2 模式）
    switch next.type:
      case PASSIVE:  completeStep(next); fromSeq = next.seq; continue
      case REWARD:
        # elapsedSeconds（D-09）：本请求刚 INSERT 该 instance（enter/startInstance）→ null；否则 floor((now - instance.started_at) 秒)
        ctx.elapsedSeconds = sameRequestInsert ? null : floor((now - instance.started_at))
        r = RewardPort.grant(prizeId, userId, TASK_STEP, sourceId = next.id /* task_instance_step.id，字符串化 */, ctx)   # 进程内同事务（RL-05）
        if r.status in {GRANTED, WON}: completeStep(next); fromSeq = next.seq; continue
        if r 抛 RetryableGrantException: 步骤保持 ACTIVE，等待重试（R14.5/R18.5）；return
        if r 抛 PermanentGrantException:  # 封闭枚举 PRIZE_DISABLED / PRIZE_DELETED / USER_INVALID
          markStep(next, SKIPPED, failReason=r.reason)  # 发放记录已转 PERMANENT_FAILED，实例继续（R14.5）
          fromSeq = next.seq; continue
      default: return        # CLICK/CALLBACK/PROGRESS：激活等待外部动作，级联终止（R14.2）
```

实例终态：

```sql
UPDATE task_instance
SET status = 'COMPLETED', completed_at = NOW(3),
    cost_seconds = TIMESTAMPDIFF(SECOND, started_at, NOW(3))
WHERE id = ? AND status = 'IN_PROGRESS';     -- 放弃/过期同模式 CAS（abandonSource 填 USER/ADMIN，R13.9/R14.8）
```

#### 5.1.4 progress 累加与 reportId 去重（R14.3，feasibility §3.3）

```text
onProgress(instance, stepCode, value, reportId):        # 单事务
  INSERT task_progress_report(instance_id, step_code, report_id, value)   # §3.3.8 uk_dedup
  → 唯一冲突 = 重复投递：读当前步骤进度，幂等返回（不累加，R14 属性 2）
  step 前置检查（5.1.1）；value ∈ [1,1000]（R11.2）
  retry ≤ 3:
    newCur = step.progress_current + value
    if newCur >= step.progress_target:
      completeStep(step)                                # 5.1.2 CAS
      cascade(instance, step.seq)                       # 达标完成并推进（一次上报即达标合法）
      return COMPLETED
    UPDATE task_instance_step SET progress_current = newCur, version = version+1
      WHERE id=? AND version=? AND status='ACTIVE'
    affected = 1 → return IN_PROGRESS
    affected = 0 → 重读重试（冲突路径）
  return task.progress.processing                       # 外部 at-least-once 重投，reportId 去重兜底（R15.4）
```

<!-- §5.2 -->
### 5.2 分支求值 resolveNextStep（R11.3）

```text
resolveNextStep(instance, completedSeq):
  edges = snapshot.transitions.filter(from == 刚完成步骤.code).sortBy(priority ASC, id ASC)  # 并列稳定
  for e in edges:
    if e.conditionExpr == null: return stepByCode(e.toCode)          # 无条件边直接生效
    if evaluate(e.conditionExpr, BranchContext): return stepByCode(e.toCode)
  return stepBySeq(completedSeq + 1)                                  # 兜底：顺序号+1（R11.3）

enter 入口（fromSeq=0）无"刚完成步骤"：跳过边过滤，直接取 min(seq) 步骤为 next。
```

`BranchContext`（显式参数对象，无 ThreadLocal，RL-07）= 用户过滤属性（同 FilterContext）+ `{stepCode, progressCurrent, progressTarget}`。**FilterContext = {userId} + UserAttributes 全字段投影**（字段与可空语义见 §2.2.3，属性缺失传 null → NULL_ATTR 哨兵）。求值经 §5.10 引擎（编译产物随快照缓存，纯内存，feasibility §3.5）。

<!-- §5.3 -->
### 5.3 可见性判定与灰度分桶（R13.1/R11.8/R11.9）

```text
bucket：md5( userId + ":" + taskId ) **摘要前 8 字节按大端组成 64 位整数**，`Long.remainderUnsigned(v, 100)`（输出 [0,100) 均匀，R11.8）。标准测试向量：(1,1)→81；(1,2)→0；(10001,88)→32

grayHit(gray, bucket):
  NONE   → true
  RATIO  → bucket < gray.ratio                       # 0 恒不可见、100 恒可见
  AB     → (bucket % 2 == 0 ? "A" : "B") ∈ gray.abGroup   # abGroup ∈ {A,B,AB}
  CROWD  → inCrowd(gray.crowdId, userId)             # 允许包；停用/不存在视为不命中并告警
           AND (gray.excludeCrowdId == null
                OR !inCrowd(gray.excludeCrowdId, userId))  # 排除包命中则不可见；与 R11.9 过滤包独立

filterPass(filter, user):
  if filter.expr != null and !evaluate(expr, FilterContext(user)): return false   # 空值语义见 §5.10
  if filter.allowCrowdIds 非空:                                       # 允许包先决（R11.9 判定顺序）
      命中至少一个启用包，否则 return false（包不存在/停用按不可见 + 告警）
  if filter.excludeCrowdIds 非空: 命中任一 → return false（同上停用告警语义）
  return true

visible(task, user, now) = task.status == PUBLISHED
                       ∧ task.startTime <= now < task.endTime
                       ∧ grayHit ∧ filterPass                          # 纯函数（R13 属性 2 灰度稳定性）
用户黑名单（USER 维度）→ 列表对其返回空（R13.1）
返回结构 VisibilityResult{visible, reasons[]}：未命中原因链（时间窗/灰度/过滤各段），供运营排查（§2.5.2）；**reasons 封闭枚举 = {NOT_IN_WINDOW, GRAY_MISS, FILTER_MISS, OFFLINE}**
```

<!-- §5.4 -->
### 5.4 CycleKeyResolver（R11.7，时区 UTC+8）

| cycleType | cycleKey |
|-----------|----------|
| NONE | 固定值 `NONE` |
| DAILY | `yyyyMMdd(now₈)` |
| MONTHLY | `yyyyMM(now₈)` |
| CRON | 周期起点 = ≤ now 的最近一次 cron 触发时刻（5 段表达式分钟粒度），格式化 `yyyyMMddHHmmss` |
| SPECIAL | `format(specialStart) + "-" + format(specialEnd)`（`yyyyMMddHHmmss-yyyyMMddHHmmss`） |

长度 ≤40 由列宽保证；CRON 保存时校验任意两次触发间隔 ≥1 小时（R11.7）。纯函数，属性测试覆盖（§7）。

<!-- §5.5 -->
### 5.5 领取流程 startInstance（R13.6–13.8）

```text
startInstance(user, taskId, ip, deviceId):            # 单事务（隔离级别 READ COMMITTED，§5.7）
  attrs = UserAttributePort.lockAndGet(userId)         # identity 内 FOR UPDATE；禁止本域直查 sys_portal_user（D-12）
  if attrs.accountStatus != ACTIVE → 403 auth.account.disabled   # DISABLED/DELETED/NOT_FOUND 同一对外码，不暴露原因
  cycleKey = CycleKeyResolver(task 定义，此时只读定义不算可见性)
  existing = SELECT 既有实例 WHERE user_id=? AND task_id=? AND cycle_key=?
  if existing: return existing                         # R13.7：任意终态都 200 返回，先于可见性/风控/每日上限
  task = 发布索引/快照
  if !visible(task, user, now) → task.claim.not-visible(400)
  risk.check(scene=CLAIM, user, ip, deviceId)  → REJECT → risk.blocked.generic
  if task.mutexGroupId:
      groupTaskIds = SELECT id FROM task_definition WHERE mutex_group_id=? AND deleted=0
      if group.cross_cycle:                                              # R11.6 跨周期：任意 cycleKey 的未完成即拦
          busy = EXISTS( SELECT 1 FROM task_instance
                         WHERE user_id=? AND task_id IN groupTaskIds AND status='IN_PROGRESS' )
      else:                                                              # 非跨周期：仅同 cycleKey
          busy = EXISTS( SELECT 1 FROM task_instance
                         WHERE user_id=? AND task_id IN groupTaskIds
                           AND status='IN_PROGRESS' AND cycle_key = ? )
      if busy → task.claim.mutex-blocked
  todayCount = COUNT(task_instance WHERE user_id=? AND created_at ∈ [今日00:00,24:00) UTC+8)   # 跨任务全局口径 R13.6
  if todayCount >= config(task.start.daily-limit-per-user) → task.claim.daily-limit
  INSERT task_instance(...)                            # uk_user_task_cycle 冲突并发兜底 → SELECT 既有实例幂等返回（R13.7）
  INSERT task_instance_step ×N（快照冗余 code/type/seq）
  engine.enter → cascade(instance, 0, sameRequestInsert=true)  # §5.1.3（首步骤 PASSIVE 自动完成级联；首步骤 REWARD=纯发奖任务直达完成；同请求新建跳过 R-e）
  Outbox: task.instance.start {instanceId, taskId, userId}（R14.11；步骤/实例状态服务端事件全集 = D-05，§6.4）
```

`expire_at` 计算（R14.10，INSERT 前求值）：`base = min(coalesce(快照.end_time, +∞), coalesce(definition.offline_at, +∞))`；周期任务再 `min(当前周期结束时刻)`（周期结束 = 下一周期起点 − 1ms，由 §5.4 cycleKey 推导）；`base = +∞`（无窗未下线的一次性任务）→ `base = now`；`expire_at = base + task.instance.expire-after-window-days 天`（默认 7，附录 A）。

<!-- §5.6 -->
### 5.6 发放：领取状态机、履约与幂等（R18）

发放记录是领取状态账本。资产在本平台账本（积分）或第三方；履约见 §5.6.3。

#### 5.6.1 领取状态迁移表（唯一合法迁移集，其余拒绝）

| 当前态 | 事件 | 目标态 | 守卫/动作 |
|--------|------|--------|-----------|
| PENDING | AUTO 创建成功 | GRANTED | granted_at；同事务启动履约（§5.6.3） |
| PENDING | MANUAL 创建成功 | WON | expire_at = now + prize.expireHours（R18.2）；fulfillment=NONE |
| PENDING | 创建失败（可重试） | RETRY_PENDING | failReason ∈ {SYSTEM_ERROR, STOCK_INSUFFICIENT}（R14.5） |
| PENDING | 创建失败（永久） | PERMANENT_FAILED | failReason ∈ {PRIZE_DISABLED, PRIZE_DELETED, USER_INVALID} |
| WON | 用户领取 CAS | CLAIMING | `UPDATE ... SET status='CLAIMING' WHERE id=? AND status='WON'`（R19.2） |
| CLAIMING | 领取成功 | GRANTED | claimed_at；同事务启动履约（§5.6.3） |
| CLAIMING | 领取失败 | RETRY_PENDING | retry_count+1；达 `reward.claim.retry-max` → PERMANENT_FAILED（手动/自动共享计数 R19.4） |
| RETRY_PENDING | 用户重试领取 CAS（claim 前置含 RETRY_PENDING，R19.4） | CLAIMING | retry_count+1；达 `reward.claim.retry-max` → PERMANENT_FAILED（与自动重试共享计数） |
| CLAIMING | 超时回滚调度（>`reward.claim.claiming-timeout-seconds`） | WON | retry_count+1（宕机恢复，R19.2） |
| RETRY_PENDING | 自动/人工重试成功 | GRANTED | 下次退避 `reward.grant.retry-interval-seconds`，≤`reward.grant.retry-max`（R18.5）；同事务启动履约 |
| RETRY_PENDING | 重试计数达上限 | PERMANENT_FAILED | 后台人工处理入口（§4.5，R18.6） |
| PERMANENT_FAILED | 后台人工重试成功（`reward:record:retry`，R18.6） | GRANTED | 重走完整规则链与库存扣减；retry_count 重置 0；同事务启动履约 |
| RETRY_PENDING | 过期翻转（expire_at ≤ now） | EXPIRED | 与 WON 同批调度扫描（§6.7 调度 5：status IN ('WON','RETRY_PENDING')） |
| WON | 过期翻转（expire_at ≤ now） | EXPIRED | 定时批量 + 领取兜底双保障（R19.3） |

#### 5.6.2 grant()（与步骤推进同事务，RL-05）

```text
RewardPort.grant(prizeId, userId, grantSource, sourceId, ctx):      # 进程内 Bean 调用，事务传播 REQUIRED
  existing = SELECT * FROM rwd_grant_record WHERE grant_source=? AND source_id=? AND prize_id=?
  if existing is null: 走首次创建
  else if existing.status == GRANTED: return existing                 # 终态短路（R18.4）
  else if existing.status == RETRY_PENDING: 不短路，续跑规则链与库存扣减
  else: return existing                                               # WON/CLAIMING/EXPIRED/PERMANENT_FAILED 等按现态返回
  规则链（有序，R17.3）：账号状态（UserAttributePort.attributes；非 ACTIVE → PermanentGrantException USER_INVALID）
    → 奖品状态 → 库存(§5.7 原子扣减) → 用户当日限领 → 用户累计限领 → 地域 → 等级 → 标签 → 风控
    风控 = risk.check(GRANT, subject{userId, ip, deviceId, elapsedSeconds = ctx.simulated ? null : ctx.elapsedSeconds})
    地域/等级/标签读同一 UserAttributes，禁止直查 sys_portal_user
    任一不过 → 抛业务异常：可重试类（库存不足/系统异常）上层标记 retryable；永久类标记 permanent；
              风控拒绝 → 抛异常回滚整个事务（发放记录/库存/积分三表零副作用，仅独立落 risk_hit_log，R18.7）
  INSERT rwd_grant_record（AUTO → GRANTED；MANUAL → WON；失败路径按 5.6.1）
    category_code / face_fen / cost_fen = 成本快照（§5.6.3）
    recon_status = NONE                              # PENDING 仅按 R37.2 时机写入，见 §5.6.3
  if AUTO: startFulfillment(record, prize)   # §5.6.3，同事务
  INSERT rwd_stock_log（before/after 留痕）
  Outbox: reward.grant.success / reward.grant.failed（附录 D）
```

可重试失败留痕（阻断级澄清）：grant() 捕获可重试异常时，先以 **REQUIRES_NEW 独立事务** UPSERT 发放记录为 RETRY_PENDING（fail_reason、retry_count=0、next_retry_at = now + 首次退避 `reward.grant.retry-interval-seconds`）**并提交**，再抛 `RetryableGrantException` 回滚步骤主事务（步骤保持 ACTIVE，R14.5/R18.5）。该独立事务是 RL-05"同事务发放"的唯一显式例外（失败留痕优先于原子回滚，依据本条），主事务回滚不抹除 RETRY_PENDING 记录。

重试调度（admin-app，锁恰一，§6.7 调度 3）：扫 `RETRY_PENDING ∧ next_retry_at ≤ now ∧ expire_at > NOW()`（已过期者交调度 5 翻转 EXPIRED，不再执行）→ 重新执行 grant 逻辑。**重试入口的幂等语义与首次不同**：existing 命中且 status=GRANTED → 直接返回（终态短路）；命中且 status=RETRY_PENDING → **不短路**，继续执行规则链与库存扣减（uk_idempotent 保证不重复入库）。重试成功后恢复推进：TASK_STEP 来源按 sourceId 反查 `task_instance_step` → §5.1.2 CAS 完成步骤 → `cascade(instance, step.seq)`；实例已终态（EXPIRED/ABANDONED）时发放记录仍转 GRANTED（权益保留），不复活实例、不推进步骤（R14.10 到期不可推进）；SIGNIN_DAY / ACTIVITY_PARTICIPATION / SIMULATE 来源无步骤关联，仅状态翻转。CLAIMING 超时回滚与 WON 过期翻转同批调度（§2.4.1 调度清单）。

#### 5.6.3 履约（R18.3）

进入 `GRANTED` 时调用 `startFulfillment`（与领取事务同事务；第三方 IO 仅 afterCommit）：

```text
startFulfillment(record, prize):
  if prize.fulfillmentMode == INSTANT:          # 仅 PLATFORM（R17.1）
    if prize.category_code == POINTS:
      PointsPort.earn(userId, typeParams.points,
                      expireAt = now + prize.expireHours, biz=(grantSource, sourceId))
    record.fulfillmentStatus = ARRIVED
    record.fulfilled_at = now
    if prize.recon_required: record.recon_status = PENDING   # R37.2
    Outbox: reward.fulfill.arrived
  else:                                         # ASYNC：PLATFORM 或 THIRD_PARTY
    record.fulfillmentStatus = SENDING
    record.next_fulfill_retry_at = now          # afterCommit 立即派发一次
```

`afterCommit` 派发（`FulfillmentDispatcher`，`domain-reward` 内接口，非跨域端口）：

| rewardTarget | 行为 |
|--------------|------|
| PLATFORM + ASYNC | 等待后台 `fulfill-confirm`（实物等）；无外部调用 |
| THIRD_PARTY + ASYNC | **P0 桩**：afterCommit 只返回 ACCEPTED，写 `fulfillment_ref`=`{adapterCode}:{stubId}`，保持 SENDING；**禁止桩在进程内返回 DONE 直接 ARRIVED**。到账只经 `/internal/reward/fulfillment/callback` 或后台 `fulfill-confirm`（R18.3）。真适配器 DONE→ARRIVED 属分类启用后的增量，不在 P0 |

履约迁移（与领取七态正交；仅 `status=GRANTED` 可离开 NONE）：

| 当前履约 | 事件 | 目标 | 动作 |
|----------|------|------|------|
| NONE | INSTANT 入账成功 | ARRIVED | 同事务 |
| NONE | ASYNC 启动 | SENDING | 同事务 |
| SENDING | 适配器 DONE / 回调 SUCCESS / 后台 confirm | ARRIVED | fulfilled_at；recon_required 则 recon_status=PENDING（R37.2）；事件 arrived |
| SENDING | 适配器/回调失败未超限 | SENDING | retry+1；next_fulfill_retry_at |
| SENDING | 失败达 `reward.fulfill.retry-max` 或发送超过 `reward.fulfill.sending-timeout-hours` | FULFILL_FAILED | 超限/适配器失败写 `ADAPTER_ERROR` 或 `CHANNEL_REJECT`；超时写 `TIMEOUT`；须对账则 `recon_status=PENDING`（R37.2）；事件 failed |
| FULFILL_FAILED | 后台 fulfill-retry | SENDING | 计数归零；再派发 |

`ARRIVED` 短路：重复回调 / 重复 confirm 原样返回。POINTS 入账仅 INSTANT 路径一次。
`FULFILL_FAILED` 且 `fulfill_fail_reason=MANUAL`（对账补发关单，R37.5）：回调与 `fulfill-confirm` **拒绝改态**（HTTP 200，body 标明 superseded），不得回到 `SENDING`/`ARRIVED`。

成本快照（进入 GRANTED 或 INSERT 时一次写入，之后只读）：

```text
face_fen = typeParams.faceFen          # FACE_VALUE；其余 null
cost_fen = cost_mode NONE → 0
         | FIXED_UNIT → prize.unit_cost_fen
         | FACE_VALUE → face_fen
```

`recon_status` 写入时机（R37.2，与领取状态机正交）：

- 履约进入 `ARRIVED` 且分类 `recon_required` → 同事务置 `PENDING`。
- 履约转入 `FULFILL_FAILED` 且分类 `recon_required` → 同事务置 `PENDING`。
- 履约仍为 `SENDING` 且 `granted_at` 的业务日（UTC+8）早于今日 → 调度 10 扫描置 `PENDING`（纳入上一账单日待对账）。
- 其余保持 `NONE`。成本列在 INSERT / 进入 GRANTED 时一次固化，之后只读。

<!-- §5.7 -->
### 5.7 库存原子扣减与限制链计数（R17.2/17.3）

```sql
UPDATE rwd_prize SET remaining_stock = remaining_stock - 1
WHERE id = ? AND remaining_stock >= 1;      -- affected=0 → STOCK_INSUFFICIENT（可重试失败）；补发/回补为 +n 同模式
```

不变量：任意序列后 `成功发放数 + remaining_stock = total_stock` 恒成立（含补发与冲正，R17 属性 1；jqwik 并发测试 §7）。限领计数（发放事务内、走 `idx_prize`）：

```sql
SELECT COUNT(*) FROM rwd_grant_record
WHERE user_id=? AND prize_id=? AND status IN ('GRANTED','WON','CLAIMING','RETRY_PENDING')
AND created_at >= <今日 00:00 UTC+8>;      -- 当日限领；去掉时间条件 = 累计限领（R17.3）
```

地域/等级/标签规则从 UserAttributePort.attributes(userId) 读用户属性（签名见 §2.2.3；`identity:user-attr` 两级缓存，D-06/§6.2），命中数组任一即通过（R17.1）。

并发正确性（阻断级澄清）：发放/补发事务隔离级别 = **READ COMMITTED**（kernel 事务模板统一声明；§5.5 领取事务同）；**限领计数 SQL 在库存 UPDATE 之后同事务执行**——此时本事务已持有 `rwd_prize` 行锁，同奖品的全部发放/补发全局串行，"计数-判断-INSERT"在锁内完成，C-4/C-5 恒成立（不同用户仅共享该行锁的短暂排队，无用户级锁）。

<!-- §5.8 -->
### 5.8 积分原子变动与过期（R20）

```text
earn / deduct / adjust（与调用方同事务）:
  earn 前置：INSERT IGNORE INTO pnt_account(user_id, balance = 0)   # 首笔懒创建（uk_user 冲突忽略）
  UPDATE pnt_account SET balance = balance + ?delta WHERE user_id=? AND balance + ?delta >= 0
  affected = 0 → SELECT 行存在性：无行 → points.account.not-found(400)；
              有行且为扣减致负 → 抛 points.account.insufficient-balance，不留流水（R20 属性 1）
  affected = 1 → SELECT balance（同连接可见己方更新）→ INSERT pnt_transaction(balance_after = 该值)   # 轧平 R20 属性 2
  禁止读-改-写（R20.6，ArchUnit/评审规约）

过期调度（每日 UTC+8 00:05，锁恰一，R20.4）:
  candidates = SELECT * FROM pnt_transaction WHERE type='EARN' AND expire_at <= now
               AND NOT EXISTS(SELECT 1 FROM pnt_transaction e2 WHERE e2.type='EXPIRE' AND e2.biz_id = 本条id)
               ORDER BY expire_at ASC, id ASC LIMIT 5000     # 先过期先扣；分批循环直至空批（批间提交）；EXPIRE 流水的 biz_id 反指被处理的 EARN = 幂等标记
  for each e in candidates: 逐账户扣 min(账户余额, 应扣额)：余额不足截断至零，差额写入 remark（R20.4）
```

<!-- §5.9 -->
### 5.9 风控判定链（R25/R26）

> 场景穷举见 [feasibility-risk.md](feasibility-risk.md)。算法正文如下；实现不得偏离本伪代码。

```text
risk.check(scene ∈ {REGISTER, LOGIN, CLAIM, GRANT}, subject{userId?, ip, deviceId?, elapsedSeconds?}):
  名单判定（黑优先 R25.6；时效过滤 expire_at，R25 属性 2）:
    userBlack = risk:list:USER:BLACK:{userId}（Redis 点查，DB 回源回填）
    if userBlack ∧ scene ∈ {CLAIM, GRANT}: return REJECT（领取/发奖拒绝；步骤推进不经本端口，task 域内查同一投影后冻结推进，R25.4）
    if userBlack.denyLogin ∧ scene == LOGIN: return REJECT
    if risk:list:IP:BLACK:{ip}    ∧ scene ∈ {REGISTER, LOGIN}: return REJECT（R25.4）
    if risk:list:DEVICE:BLACK:{dev} ∧ scene ∈ {REGISTER, LOGIN}: return REJECT（设备黑名单不影响已登录业务）
    if risk:list:USER:WHITE:{userId}: 跳过规则判定（产品规则仍生效，R25.5）→ return PASS
  if scene ∈ {REGISTER, LOGIN}: return PASS            # R26.3：注册/登录只跑名单，不跑 R-a–R-f，不写 risk:cnt
  规则判定（R-a–R-f，仅 enabled；仅 CLAIM / GRANT）:
    R-a / R-b / R-c / R-d / R-f：计数源 risk:cnt ZSET 滑窗 = §3.10
      R-a 用户任务完成数 / R-b 用户奖励次数 / R-c 同IP关联账号数（窗内同 IP 登录或注册成功的不同账号）
      R-d 同设备关联账号数（同 R-c 口径）/ R-f 同 IP 领取发奖请求量（仅到达风控点的请求，R26.2）
      窗口计数 ≥ threshold → 按 action：REJECT=拒绝 / SILENT_REJECT=拒绝（提示层差异）/ MARK=仅记录（R26.2）
    R-e（D-09，**不是滑窗，禁止写入 risk:cnt**）：
      仅 scene==GRANT 且 elapsedSeconds != null 时判定：elapsedSeconds < threshold → 按 action
      CLAIM 不跑 R-e
      elapsedSeconds==null（非 TASK_STEP / 同请求新建实例 / simulated GRANT）→ 直接 skip
      simulated=true 的 GRANT：直接 skip（不拦截、不记 MARK；与 R24.5 频率/行为类不统计模拟一致）
  任何系统异常 → 降级：config(risk.fallback-policy)（默认 allow 放行）+ 降级事件留痕 + 告警（R26.3，属性 3）
  命中（名单或规则）→ INSERT risk_hit_log（唯一事实源，含上下文快照/命中值/阈值/处置结果）+ 异步埋点投影事件（R26.4）
  simulated 行为不计入 R-a/R-b/R-f 统计；R-e 对 simulated GRANT 直接 skip；R-c/R-d 纳入观察不拦截（R24.5）
```

命中留痕事务边界：risk_hit_log 经 **REQUIRES_NEW 独立事务**在判定点同步 INSERT（主业务事务随后回滚不丢命中记录）；独立写入失败 → 降级为告警计数，不阻断业务（与 §6.8 风控行一致）。

risk:cnt 写入规格（ZSET，score = epoch millis；窗口裁剪 = ZREMRANGEBYSCORE，计数 = ZCOUNT）：R-a / R-b 由事件消费**异步**写（task.instance.complete / reward.grant.success 消费器，成员 = userId / recordId，声明接受 ≤5s 滞后）；R-c / R-d 由 auth.register.success / auth.login.success 消费器写（成员 = 去重 accountId）；R-f 在判定点**同步** ZADD（成员 = `{ts}:{自增序列}` 唯一化，请求计数需实时）。**R-e 不出现在本规格、不写入 risk:cnt。**

<!-- §5.10 -->
### 5.10 表达式引擎：AviatorScript AST 白名单（R11.9；T2 Spike 验证判据见 component-selection §6）

```text
校验流程（保存时执行，编译产物随快照缓存，运行时复用——feasibility §3.5）:
  1 parse(expr) → 语法错误 → 报告行列位置
  2 AST 遍历：节点类型 ∈ 白名单 {字面量(单引号串/整数), 比较(= != > >= < <= in not-in),
    逻辑(AND OR NOT), 括号, 函数调用}；出现方法调用/属性访问/变量引用/其他节点 → 拒绝（T2 验收）
  3 节点总数 ≤ 200；整数字面量解析为 Java long（超出 long 域拒绝，M-08）；表达式总长度 ≤ 1024 字符（保存入参校验，M-07）
  4 函数 ∈ 封闭白名单（下表）且参数个数/类型匹配
  5 空值语义模拟求值：对属性缺失样例报告将判 false 的条件（R11.5 校验接口）
```

| 函数 | 签名 | 数据源 |
|------|------|--------|
| province / userRole / orgId | `() → string` | UserAttributePort（identity:user-attr 缓存，§2.2.3） |
| userLevel | `() → int` | 同上 |
| hasTag | `(string) → bool` | 同上（标签集合包含） |
| registerWithinDays | `(int) → bool` | 同上（注册时间 ≤ N 天） |
| inCrowd | `(string) → bool` | task:crowd 缓存（人群包成员判定，停用包 false+告警） |

空值语义实现（R11.9"属性缺失时含该属性的条件求值为 false"）：属性缺失的函数返回哨兵 `NULL_ATTR`；任何比较运算含 `NULL_ATTR` 一律 `false`，**再**按常规布尔逻辑求值（`NOT(false)=true` 合法——缺失即"不满足条件"）。**布尔函数（hasTag / registerWithinDays / inCrowd）属性缺失（无 tags / 无 registeredAt / 包不存在）一律返回 false，不走 NULL_ATTR 哨兵**。求值上下文 FilterContext/BranchContext 显式传参（RL-07），FilterContext = {userId} + UserAttributes 全字段投影、BranchContext = FilterContext + {stepCode, progressCurrent, progressTarget}（§2.2.3/§5.2）；求值纯内存无 IO（性能预算 feasibility §3.5：P99 < 1ms，T2 基准）。

<!-- §5.11 -->
### 5.11 渠道对账匹配与补发门禁（R37，D-10）

> 场景穷举见 [feasibility-recon.md](feasibility-recon.md)。算法正文如下；实现不得偏离本伪代码。

```text
effectivePolicy(prize) = prize.recon_action_policy ?? category.recon_action_policy   # REVIEW|AUTO

needsChannelReview(item):          # 不论政策，以下必须人工核渠
  result == PLATFORM_ONLY ∧ (
    fulfillment == SENDING
    ∨ fulfill_fail_reason ∈ {TIMEOUT, ∅, 非封闭值}
  )

autoRefulfillEligible(item):
  config(reward.recon.auto-refulfill-enabled) == true
  ∧ effectivePolicy == AUTO
  ∧ result == PLATFORM_ONLY
  ∧ fulfillment == FULFILL_FAILED
  ∧ fulfill_fail_reason ∈ {CHANNEL_REJECT, ADAPTER_ERROR}

match(batch):
  平台集 P = 该分类、账单日 UTC+8、recon_status=PENDING、simulated=0
              且 fulfillment IN (ARRIVED, SENDING, FULFILL_FAILED) 的发放记录
  渠道集 C = 本批已导入行（按 fulfillmentRef 去重，重复行导入时拒绝）
  按 fulfillmentRef 等值连接：
    双方有且 platform.cost_fen == channel.amountFen → MATCHED；记录 recon_status=MATCHED
    双方有且金额不等 → AMOUNT_MISMATCH；记录 DIFF
    仅 P → PLATFORM_ONLY；记录 DIFF
    仅 C → CHANNEL_ONLY
  写 rwd_recon_item：
    needsChannelReview → review_status=PENDING_REVIEW
    其余 → review_status=NONE
  回写批次四类计数；batch.status=DONE
  afterCommit：autoRefulfillEligible 的项入队 REFULFILL（同一笔，§5.6.3）；禁止入队 MANUAL_GRANT
```

动作允许集（其余拒绝 `reward.recon.action-forbidden`）：

| 结果 | 履约 / 原因 | 允许动作 | 前置 |
|------|------------|----------|------|
| MATCHED | — | 无 | — |
| PLATFORM_ONLY | `ARRIVED` | 仅 `ABSORB` | — |
| PLATFORM_ONLY | `SENDING` | `REFULFILL` / `MANUAL_GRANT` | `review_status=CONFIRMED` |
| PLATFORM_ONLY | `FULFILL_FAILED` + `TIMEOUT` | `REFULFILL` / `MANUAL_GRANT` | `review_status=CONFIRMED` |
| PLATFORM_ONLY | `FULFILL_FAILED` + `CHANNEL_REJECT`/`ADAPTER_ERROR` | `REFULFILL` / `MANUAL_GRANT` | `REVIEW` 可直接动作；`AUTO` 可自动 `REFULFILL` |
| CHANNEL_ONLY | — | `LEDGER_ONLY` / `ABSORB` | 一律人工 |
| AMOUNT_MISMATCH | — | 仅 `ABSORB` | — |
| 任一 | `review_status=REJECTED` | 仅 `ABSORB` | — |

未列组合（含 `FULFILL_FAILED` + `CALLBACK_FAILED` / `MANUAL`，以及任何未出现在上表的结果/履约/原因）一律拒绝 `reward.recon.action-forbidden`。不得按 `ADAPTER_ERROR` 类推自动 `REFULFILL`。`MANUAL` 原单已关，禁止第二次 `MANUAL_GRANT`。

核渠（§4.5 `POST .../review`）：`PENDING_REVIEW` → `CONFIRMED`（备注必填：已核渠、渠道未出款）或 `REJECTED`（渠道已出款 / 无需处理）。`CONFIRMED` 不自动补发，操作人再点动作。

差异动作实现：`REFULFILL` 调 §5.6.3 履约重试；`LEDGER_ONLY` 补一条 `GRANTED+ARRIVED` 记录（`costFen=channel.amountFen`，不调适配器）；`ABSORB` 只改 item.action，不写发放。每条 item 的 action 从 NONE 出发只许一次成功迁移。**`MANUAL_GRANT` 无自动入口。**

`MANUAL_GRANT`（与 grant 同事务，R37.5）：
```text
newRec = grant(MANUAL_GRANT, sourceId=`recon:{itemId}`, ctx)   # 扣库存；失败则整笔回滚，原单不动
UPDATE rwd_grant_record
  SET fulfillment_status='FULFILL_FAILED', fulfill_fail_reason='MANUAL'
  WHERE id = 原单.id
    AND fulfillment_status IN ('SENDING','FULFILL_FAILED')
    AND fulfillment_status <> 'ARRIVED'     -- ARRIVED 路径本就不允许补发
-- 原 fulfillment_ref 留在原单（uk 仍指向原单）；新单另写新 ref
-- 此后该 ref 的回调 / confirm 走 §5.6.3 MANUAL 短路
-- 不调用渠道撤销
```

---

---

<!-- §7 -->
## 7. 测试策略

> 目标：requirements 的 **66** 条正确性属性与 NFR 可维护性 4 的 9 条并发幂等路径全部落为**可重复执行的自动化测试**，测试与构建不依赖任何外部手工环境（NFR 可维护性 3：数据库/缓存经 Testcontainers 容器化提供）。工具基线（component-selection §3.6）：JUnit 5 + Testcontainers（MySQL 8 / Redis 7）+ jqwik（属性测试）+ ArchUnit（架构红线）+ k6（压测）+ Playwright（E2E）+ Vitest（前端组件测试）。

<!-- §7.1 -->
### 7.1 测试分层与门禁分级

| 层 | 工具 | 覆盖对象 | 命名/运行插件 | 门禁级别 |
|----|------|---------|--------------|---------|
| 单元测试 | JUnit 5 + Mockito（不起 Spring 上下文） | §5 全部纯函数算法：分桶、CycleKeyResolver、分支求值、动作合并、限制链顺序、脱敏截断管线、默认昵称生成（R4.10） | `*Test`，Maven surefire | 阻断（CI 必须全绿） |
| 属性测试 | jqwik | 66 条正确性属性中的可随机生成子集（见 §7.3 映射表） | `*PropertyTest`，surefire | 阻断 |
| 集成测试 | JUnit 5 + Spring Boot Test + Testcontainers | DB 依赖属性、端点契约（§4）、错误码、Outbox 异步语义 | `*IT`，Maven failsafe | 阻断 |
| 多实例集成 | 同上 + 双应用上下文（§7.2 拓扑 B） | 全局一致性属性（R6/R7/R8/R9/R33）与调度恰一（NFR 可用性 3） | `*IT`，failsafe | 阻断 |
| 并发不变量 | JUnit 5 + 虚拟线程 + Testcontainers | §7.4 清单 C-1~C-12 | `*IT`，failsafe | 阻断 |
| 架构测试 | ArchUnit | RL-01~12 + 本节自有规则 AT-C01（§7.6） | `*ArchTest`，surefire | 阻断 |
| E2E | Playwright（Chromium） | P0 核心旅程（§7.9） | `*.spec.ts`，前端仓库 CI job | 阻断（自 P0 前端页交付起） |
| 压测 | k6 | NFR 性能 1–8 逐条（§7.8） | `perf/*.js`，staging 手动触发 | 发布门禁（上线检查清单签署项，R31.2），不进例行 CI |

规则：surefire 只跑 `*Test`/`*PropertyTest`/`*ArchTest`，failsafe 只跑 `*IT`（阶段绑定 verify）；`@Disabled`/`@Tag` 跳过在 `*IT`/`*PropertyTest`/`*ArchTest` 中出现即 CI 失败（§7.10 grep 门禁）。

<!-- §7.2 -->
### 7.2 测试基础设施（Testcontainers 拓扑、时钟注入、故障注入）

**拓扑 A——单实例基础拓扑**（全部 `*IT` 的默认基类 `BaseIntegrationTest`，随 platform-kernel 的 test-jar（maven-jar-plugin `test-jar` goal）发布，各域/应用测试模块依赖复用）：

- 容器：`mysql:8.0`（UTF8MB4）+ `redis:7-alpine`，**singleton container 模式**（JVM 级静态复用，随机映射端口注入数据源/Sa-Token/Redisson 配置）。
- 每测试类开始执行 `flyway:clean` + 全量迁移（V1 起当前版本），迁移失败即测试失败（兼作 §6.9 的迁移冒烟）。
- 类间数据清理：按外键逆序 `TRUNCATE` 全部业务表（Fixtures 提供表清单常量）；方法级隔离靠数据构造器随机后缀，不依赖事务回滚（Outbox/异步线程脱离测试事务，回滚隔离不可用）。
- 应用上下文：portal-app 测试默认装配全部 P0 域（与生产装配矩阵一致，RL-05）；admin 端接口的测试经 admin-app 上下文（`AdminBaseIT`）。
- Outbox 同步化：测试 profile 将 Relay 调度周期压至 200ms；提供 `awaitOutboxDrain()` 辅助（轮询 `sys_outbox` 待投递数 = 0 且目标表落库可见，超时 5s 失败）——R1.2/R10.1/R28.3 的异步断言统一经此收敛。

**拓扑 B——双实例拓扑**（`TwoPortalAppIT` 基类，portal-app 测试模块）：同一 JVM 内用 `SpringApplicationBuilder` 启动两个 portal-app 上下文（`server.port=18081/18082`），共享拓扑 A 的 MySQL/Redis 容器；请求经两个端口的 `TestRestTemplate` 分别发出。用于 R6.1（A 实例踢下线 → B 实例校验 401）、R7.1/R8.1/R9.1（A 实例写 → 断言 B 实例 1 秒内读到新值）、R33.1、调度恰一（admin-app 双上下文同法，`TwoAdminAppIT`：两实例同时开调度，断言任务执行计数恰一，NFR 可用性 3）。

**时钟注入约定（设计内决策 D-03，实现层编码约定）**：kernel 定义 `@Bean Clock clock()`（生产 = `Clock.systemUTC()`，与 §2.9 时间基线一致）；test-jar 提供 `MutableClock`（`setInstant()` 拨动）。时间敏感组件**必须构造注入 Clock，禁止 `LocalDateTime.now()` / `Instant.now()` / `System.currentTimeMillis()` 直调**，覆盖清单：登录锁定判定（R1.3）、名单时效过滤（R25.2）、实例 expireAt 计算与过期翻转（R14.10）、任务时间窗/灰度窗口判定（R13.1）、奖品过期（R19.3）、积分过期扫描（R20.4）、排期判定（R30.2）、风控滑窗取当前时刻（R26.2）、验证码 TTL（R1.7）、internal 时间戳容差（R15.2）。ArchUnit 自有规则 AT-C01 强制（§7.6）。

**Redis 故障注入**：`RedisPauseSupport`（test-jar）经 `container.getDockerClient().pauseContainerCmd(...)/unpauseContainerCmd(...)` 暂停/恢复 Redis 容器，真实模拟 §6.8 降级矩阵的"Redis 不可达"场景（R26.3、R28.1、会话拒绝不静默放行）。不引入 Toxiproxy。

**数据构造**：每域提供 `Fixtures` 静态构造器（如 `Fixtures.task()` 返回聚合 Builder，链式补步骤/分支/动作/灰度；`Fixtures.portalUser()`、`Fixtures.prize()`、`Fixtures.blacklist()` 等），随机后缀保证唯一性；种子数据文件（词典 `province/user_level/user_role/user_tag/portal_route`、事件元数据附录 D 全量编码）经 platform-db 的 `src/test/resources/db/migration/R__test-seed.sql`（**测试 classpath，不进生产迁移**）或测试内 SQL 装载。

<!-- §7.3 -->
### 7.3 正确性属性 → 测试映射表（66 条全量）

> 列"类型"：jqwik = 纯属性测试（随机生成器）；集成 = Testcontainers 集成；双实例 = 拓扑 B；故障注入 = RedisPauseSupport/bean 覆盖；前端 = Vitest/Playwright；CI 编排 = 脚本级。测试类归属模块按 §2.2.1 包名。P1 行仅在对应需求交付时生效。

**模块 A 身份与权限（9 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R1.1 锁定触发不变量 | jqwik | `identity LoginLockPropertyTest` | 随机 0–12 次成功/失败交错登录序列（生成器保证出现连续失败段）→ 恰在第 5 次连续失败后返回 423 `auth.login.locked` 且剩余分钟数 = 15；期间不校验凭据不计失败；任一成功将计数清零重新累计 |
| R1.2 审计完整性 | 集成 | `identity LoginAuditIT` | 64 并发线程随机成败登录共 200 次 → `awaitOutboxDrain()` 后 `sys_audit_log` 登录类记录数 = 200，逐条含账号/IP/UA/结果/时间 |
| R1.3 改密会话失效正确性 | 集成 | `identity PasswordChangeSessionIT` | 3 会话登录 → 旧密码错误拒绝 → 改密成功后当前会话 200、其余 2 会话 401 |
| R2.1 权限判定确定性 | jqwik | `identity PermissionUnionPropertyTest` | 随机用户—角色—权限图（≤8 角色 × ≤20 权限码，含停用角色）→ 并集判定 == 任一启用角色持有该码；停用角色权限不计入且绑定关系保留 |
| R2.2 权限实时生效 | 集成 | `identity PermissionImmediateEffectIT` | 移除角色权限 → 变更接口返回后**无任何等待**立即以原会话调用被移除权限的接口 → 403 + 一条审计（同时验证 §6.2 afterCommit evict 协议的同步性） |
| R3.1 用户名唯一不变量 | 集成 | `identity AdminUsernameUniqueIT` | 64 线程并发创建同用户名（混大小写 3 种写法）→ 恰 1 成功；逻辑删除后复用 → 拒绝 |
| R4.1 账号体系隔离 | 集成 | `portal NamespaceIsolationIT` | 后台令牌遍历 §4.9 全部 20 个 common 端点 → 全 401；门户令牌访问 `/admin/**` 抽样 → 全 401；两应用互访对方命名空间 → 404（RL-08 联合验证） |
| R5.1 档案变更即时生效 | 集成 | `identity ProfileEffectVisibilityIT` | 编辑档案（标签数组整体替换语义）→ 下一次任务列表可见性按新值（过滤表达式含标签/省份/等级三函数） |
| R6.1 踢下线全局一致性 | 双实例 | `portal KickoutConsistencyIT` | 实例 A 强制下线 → 1 秒内实例 B 同令牌请求 401；失效令牌重复使用恒 401 |

**模块 B 系统管理（4 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R7.1 字典缓存一致性 | 双实例 | `identity DictCacheConsistencyIT` | 实例 A 变更字典 → 轮询 ≤ 1s 断言 A/B 两实例均返回新值；停用类型 → 两实例均返回空列表（R7.3） |
| R8.1 配置缓存一致性 | 双实例 | `identity ConfigCacheConsistencyIT` | 实例 A 变更配置 → ≤ 1s 两实例新值；掩码项更新报文不携带 value = 保持原值、携带 null = 拒绝（R8.2） |
| R9.1 清理全局一致性 | 双实例 | `identity CacheEvictConsistencyIT` | 改数据源 → 依次执行精确键/键前缀/命名空间三级清理 → 两实例下一次读均回源最新值 |
| R10.1 审计完整性 | 集成 | `identity AuditCompletenessIT` | 随机 30 个后台写操作序列（含 10 个注定失败：无权限/参数非法/唯一冲突）→ `awaitOutboxDrain()` 后审计条数 = 30（与成败无关） |

**模块 C 任务引擎（18 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R11.1 聚合保存原子性 | 集成 | `task TaskAggregateAtomicIT` | 三组注入失败：步骤数 > `task.step.max-count`、分支指向不存在步骤、步骤编码重复（DB 唯一冲突）→ 任务主体与全部子配置零落库 |
| R11.2 流程图无环不变量 | jqwik | `task TaskGraphAcyclicPropertyTest` | 随机 ≤ 8 步骤 + 随机边集 → 校验器接受 ⇔ 边集无环且每条边目标顺序号 > 来源顺序号（双向蕴含）；每个非终点步骤至少一条出边、终点可达性检查 |
| R12.1 快照不可变性 | 集成 | `task SnapshotImmutabilityIT` | 领取实例绑定 v3 → 修改配置并发布 v4 → 实例详情渲染/步骤推进/分支求值全按 v3；`task_version_snapshot` v3 行内容哈希不变 |
| R12.2 修订草稿隔离性 | 集成 | `task RevisionIsolationIT` | 并发 20 轮修订草稿保存/放弃期间执行 C 端浏览/领取/推进 → 响应与无编辑基线逐字段一致 |
| R12.3 批量任务级原子性 | 集成 | `task BatchPublishAtomicIT` | 批量 10 任务（5 合法 + 5 非法：无步骤/表达式非法/时间窗倒置/互斥组周期不一致/定时时间晚于窗口结束）→ 逐条明细：合法 5 个版本 +1 生效，非法 5 个状态与快照零变化 |
| R13.1 实例唯一性不变量 | 并发集成 | `task InstanceUniquenessIT` | 见 §7.4 C-1 |
| R13.2 灰度稳定性 | jqwik | `task GrayStabilityPropertyTest` | 随机 1000 用户 × 随机灰度配置（百分比/AB/人群包）→ 同配置重复判定 100 次恒定；percent=0 恒不可见、100 恒可见；AB 命中组 == bucket 奇偶性；分桶 = md5 前 8 字节大端 remainderUnsigned（向量 (1,1)→81） |
| R13.3 埋点非阻塞 | 故障注入 | `task TrackingNonBlockingIT` | 测试 bean 覆盖 EventPublisher 抛异常 + track 端点限流封禁 → 列表/详情/领取全部 200，响应时间不劣化 |
| R13.4 风控拒绝幂等 | 集成 | `risk RiskRejectIdempotentIT` | 黑名单用户重复领取 10 次 → 0 实例、10 次响应一致（`risk.blocked.generic`，与 §4.9.2/§5.5 契约同码）、`risk_hit_log` 恰 10 条 |
| R14.1 推进恰一次 | 并发集成 | `task StepAdvanceExactlyOnceIT` | 见 §7.4 C-2 |
| R14.2 进度去重正确性 | 并发集成 | `task ProgressDedupIT` | 见 §7.4 C-3 |
| R14.3 状态机合法性 | jqwik | `task StepStateMachinePropertyTest` | 随机合法操作序列（生成器产四入口 × 当前/重复/乱序三类目标）→ 任意时刻步骤状态 ∈ {未激活, ACTIVE, COMPLETED, SKIPPED} 且无回退；乱序/INACTIVE/SKIPPED → `task.step.state-mismatch`；已完成步骤重复 click/callback → 200 幂等 |
| R14.4 过期终局性 | 集成 | `task ExpiredFinalityIT` | `MutableClock` 拨过 expireAt → 手动触发过期调度 → click/progress/callback/enter 四入口全拒且实例状态不再变化；不触发调度时兜底校验同样拒绝 |
| R15.1 防重放不变量 | 集成 | `internal ReplayAttackIT` | §7.7 恶意样本库逐条：重放/篡改/过期时间戳全部拒绝（`internal.*` 分段错误码）且业务表零变化 |
| R16.1 动作合并确定性 | jqwik | `task ActionMergePropertyTest` | 随机快照（任务级/步骤级 × 5 端动作配置）× 端枚举 → 合并结果 == §4.9 回退链首命中；重复求值一致；非法/缺失端标识按 WEB |
| R16.2 回退链完备性 | jqwik | `task ActionFallbackPropertyTest` | 缺步骤级 / 缺任务级 / 仅 WEB / 全空四类快照 → 首命中即停；全空返回 NONE 占位，不抛异常 |
| R16.3 动作配置随快照固化 | 集成 | `task ActionSnapshotFixityIT` | 发布含动作版本 → 修改任务级动作配置 → 既有实例详情 action 输出仍取旧快照值（不随定义漂移） |
| R16.4 端标识服务端解析白名单 | 单元 | `common ClientPlatformResolverTest` | 5 端枚举（WEB/ANDROID/IOS/MINIAPP/SIMULATOR）原样解析；非法/缺失按 WEB（R16.4）并计入 unknown-platform 指标 |

**模块 D 奖励与积分（12 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R17.1 不超发不变量 | 并发集成 | `reward StockNoOversellIT` | 见 §7.4 C-4 |
| R17.2 限领原子性 | 并发集成 | `reward ClaimLimitConcurrentIT` | 见 §7.4 C-5 |
| R17.3 成本快照不可变 | 集成 | `reward CostSnapshotIT` | 发放 FACE_VALUE 红包 → 改奖品面额/单价 → 记录 costFen/faceFen 不变；花销汇总仍用快照 |
| R18.1 发放恰一次 | 并发集成 | `reward GrantExactlyOnceIT` | 见 §7.4 C-6 |
| R18.2 风控拦截零副作用 | 集成 | `reward GrantZeroSideEffectIT` | 黑名单用户 + 规则命中用户分别完成 REWARD 步骤 → `rwd_grant_record` / `rwd_prize.remaining_stock` / `pnt_account` 三表零变化，`risk_hit_log` 有命中记录 |
| R19.1 领取恰一次 | 并发集成 | `reward PrizeClaimExactlyOnceIT` | 见 §7.4 C-7 |
| R19.2 过期不可领取 | 集成 | `reward PrizeExpireIT` | 场景一：先跑 WON 过期翻转调度 → 记录 EXPIRED、领取拒绝；场景二：直改 DB 制造"已过期未翻转"→ 领取时兜底校验拒绝（双重保障，R19.3） |
| R20.1 余额非负不变量 | 并发集成 | `points PointsNonNegativeIT` | 见 §7.4 C-8 |
| R20.2 流水轧平 | jqwik | `points PointsLedgerPropertyTest` | 随机 ± 变动序列（获得/消耗/调整/冲正/过期截断混合，落在集成容器上执行）→ 全量 `pnt_transaction` 满足 balance_after[i] = balance_after[i-1] ± amount[i]；致负请求被拒且不留流水 |
| R37.1 对账穷尽 | 集成 | `reward ReconExhaustiveIT` | 一批次内匹配/仅平台/仅渠道/金额不等四类各 ≥1（含 FULFILL_FAILED）→ 每条平台记录与每条渠道行恰好落一项；无遗漏无双计 |
| R37.2 对账补发不重复到账 | 集成 | `reward ReconReissueIT` | PLATFORM_ONLY 点两次 MANUAL_GRANT / REFULFILL → ARRIVED ≤1，库存只扣一次；补发后原单 `FULFILL_FAILED`+`MANUAL`，重放原 `fulfillmentRef` 回调不得改态 |
| R37.3 补发门禁 | 集成 | `reward ReconReviewGateIT` | TIMEOUT / SENDING 项未核渠点 REFULFILL 与 MANUAL_GRANT → 400 `review-required` 且发放/库存无变化；`REJECTED` 后仅 ABSORB；开 AUTO 总闸跑匹配 → 零条 MANUAL_GRANT，仅 CHANNEL_REJECT/ADAPTER_ERROR 可自动 REFULFILL |

**模块 E 风控（7 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R25.1 名单判定确定性与优先级 | jqwik | `risk ListDecisionPropertyTest` | 随机名单组合（黑/白 × 用户/IP/设备 × 永久/限期/已过期）→ 判定恒 == 黑优先规则；同值黑白并存恒为拒；白名单跳规则不跳产品规则（限领仍生效） |
| R25.2 名单时效性 | 集成 | `risk ListExpiryIT` | `MutableClock` 拨过条目 expireAt → 同主体登录/领取行为恢复（判定按当前时间过滤） |
| R26.1 规则判定确定性 | jqwik | `risk RuleDeterminismPropertyTest` | 随机行为序列重放两次判定一致；R-a~R-f 阈值边界值 ± 1 的命中/不命中分界正确 |
| R26.2 拒绝零副作用 | 集成 | `risk RuleRejectZeroSideEffectIT` | 阈值调至 1 后并发重放被拒领取 32 次 → 0 实例、32 条命中、响应一致 |
| R26.3 降级可观测 | 故障注入 | `risk RuleFallbackIT` | `RedisPauseSupport` 暂停 Redis（滑窗计数不可达）→ 默认 `risk.fallback-policy=allow` 放行 + 降级事件计数 +1 + 告警日志；策略 = reject 时拒绝 |
| R26.4 R-e 判定范围 | 集成 | `risk ReElapsedScopeIT` | 领取即发（enter 级联 REWARD）→ 无 R-e 命中、发放成功；先 start 再 click 至 REWARD 且 `elapsedSeconds < threshold` → GRANT 按 action 拒绝且发放/库存/积分零变化 |
| R27.1 处置留痕完整 | 集成 | `risk CaseHandleAuditIT` | 处置序列（加黑/解黑/标记误报，含必填原因缺失被拒）→ `risk_handle_log` 与审计记录一一对应 |

**模块 F 埋点（4 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R28.1 业务非阻塞不变量 | 故障注入 | `tracking TrackOutageNonBlockingIT` | track 端点整体不可用（路由封禁返回 503）→ 登录/任务列表/领取/发奖/积分链路全绿 |
| R28.2 事件不可变性 | 架构测试 | `tracking EventImmutabilityArchTest` | RL-12 组成部分（§7.6）：`evt_event_log` 的 Mapper 方法名白名单 ^insert/^select；全代码库无该表 update/delete 服务方法与端点 |
| R28.3 服务端事件与业务一致性 | 集成 | `tracking ServerEventTransactionalIT` | 成对执行业务成功/失败（事务回滚）样本 → `awaitOutboxDrain()`：成功必有对应服务端事件、回滚零事件 |
| R29.1 调试查询无副作用 | 集成 | `tracking DebugQueryNoSideEffectIT` | 调试查询前后 `evt_event_log` 行数与内容哈希一致；抽样比例 `track.query.sample-ratio-percent` 生效（10,000 行 → 返回 ≤ 100 + 分页容差） |

**模块 G 广告位（2 条，P1）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R30.1 频控不超限不变量 | 并发集成 | `ad AdFrequencyIT` | 见 §7.4 C-11 |
| R30.2 排期正确性 | 集成 | `ad AdScheduleIT` | `MutableClock` 拨至排期外 → 拉取响应不含该素材（任何用户） |

**模块 H 扩展功能（4 条，P1）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R21.1 签到唯一性 | 并发集成 | `signin SigninUniqueIT` | 见 §7.4 C-9 |
| R22.1 限量不突破不变量 | 并发集成 | `activity ActivityQuotaIT` | 见 §7.4 C-10 |
| R23.1 聚合幂等 | 集成 | `metrics AggregationIdempotentIT` | 重放聚合窗口 3 次 → 指标值与首次执行一致（重跑不重复计数） |
| R24.1 模拟隔离 | 集成 | `simulate SimulationIsolationIT` | 执行模拟操作后查询 R23 聚合口径与 R27 风控统计 → 模拟数据计数 = 0；R-a/R-b/R-f 不统计模拟行为、R-e 对 simulated GRANT 直接 skip、R-c/R-d 纳入观察但不拦截 |

**模块 I 门户体验（5 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R32.1 匿名不可达不变量 | 集成 | `portal AnonymousBoundaryIT` | 无令牌遍历 §4.9 全部门户业务端点 → 除匿名白名单外全 401；匿名白名单 = captcha / username-available / register / login / track/batch / P1 ad，可达且行为正确。`/api/common/dict/**` 须登录，不在匿名白名单 |
| R33.1 退出即时失效 | 双实例 | `portal LogoutImmediateIT` | 实例 A 登录 + 登出 → 实例 B 以原令牌调用业务接口 401 |
| R34.1 列表状态一致性 | 集成 | `portal ListStateConsistencyIT` | 随机推进实例至全部状态枚举（未开始/进行中/COMPLETED/ABANDONED/EXPIRED）→ 列表 `status` 字段与实例状态按 §4.9 状态-按钮映射逐枚举断言 |
| R35.1 按钮状态一致性 | 前端 Vitest | `web PrizeButtonStateTest` | 领取七态 × 履约四态按钮映射（R35.2：待领取/领取中/已到账/发送中/发送失败/发放重试/永久失败/过期）逐一断言；后端字段契约由 §4.9 契约测试保证 |
| R36.1 日历状态一致性（P1） | 前端 Vitest | `web SigninCalendarStateTest` | 随机签到/补签序列（含断链重置、跨月延续）→ 日历四态格子与记录集严格一致 |

**模块 J 平台基线（1 条）**

| 属性 | 类型 | 测试类 | 场景与断言要点 |
|------|------|--------|---------------|
| R31.1 部署幂等 | CI 编排 | `ci/deploy-smoke.sh`（脚本级，非 JUnit） | compose 健康集群连续两次 `up -d` → 两应用健康检查全绿、Flyway validate 通过、冒烟数据集（注册-领取-发奖-积分）两次执行结果一致；DB 层二次迁移幂等断言已在 §6.9 测试化 |

属性测试代码形态示例（jqwik，其余属性同风格；生成器种子写入日志保证可复现）：

```java
@Property(generation = GenerationMode.EXHAUSTIVE)
void grayVisibilityIsStableWhileConfigUnchanged(@ForAll @Size(min=1, max=50) List<@Long(min=1, max=100000) Long> userIds,
                                                @ForAll GrayConfig grayConfig) {
    taskId = Fixtures.publishedTask(b -> b.gray(grayConfig));
    userIds.forEach(uid -> {
        boolean first = visibilityService.isVisible(uid, taskId);
        for (int i = 0; i < 100; i++)
            Assertions.assertEquals(first, visibilityService.isVisible(uid, taskId));
    });
}
```

<!-- §7.4 -->
### 7.4 并发不变量测试清单（NFR 可维护性 4 的 9 条路径 + 签到补全）

执行规范（全部条目适用）：

- 并发框架：`Executors.newVirtualThreadPerTaskExecutor()`（虚拟线程）+ `CyclicBarrier` 对齐起跑；每条不变量重复 ≥ 20 轮（每轮随机化用户/任务/奖品数据），全轮通过才判绿；失败即阻断修复，禁止重试掩盖（§7.10）。
- 断言直连容器 MySQL（`JdbcTemplate`）查最终态，不依赖应用层查询。
- 单轮整体时长断言 < 5s（防锁等待/死锁回归）。

| # | 不变量（需求） | 测试类 | 并发设计 | 收敛机制（§3.8） | 通过断言 |
|---|---------------|--------|---------|-----------------|---------|
| C-1 | 实例唯一（R13.7） | `task InstanceUniquenessIT` | 128 线程并发同（用户, 任务, cycleKey）领取 | `task_instance.uk_user_task_cycle` + 冲突转幂等查询 | `SELECT COUNT(*) FROM task_instance WHERE ...` = 1；全部响应 200 且 instanceId 相同 |
| C-2 | 推进恰一次（R14 属性 1） | `task StepAdvanceExactlyOnceIT` | 64 线程并发 click 同一 ACTIVE 步骤；另两组并发 callback（CALLBACK 步骤）、并发 progress（不同 reportId） | `task_instance_step.uk_instance_code` + version CAS | 每步骤恰 1 行 COMPLETED（`completed_at` 单值）；progress 累计 = Σ 各 reportId value 恰一份；后续步骤状态确定唯一 |
| C-3 | 进度去重（R14 属性 2） | `task ProgressDedupIT` | 64 线程混发：同 reportId 30 次 + 不同 reportId 各 1 次 | `task_progress_report.uk_dedup` 与累加同事务 | 进度增量恰一份；重复 reportId 全部幂等响应（非错误）；崩溃窗口用"重复上报夹杀提交边界"补充用例 |
| C-4 | 不超发（R17 属性 1） | `reward StockNoOversellIT` | 总库存 50，200 线程各持独立实例并发触发 REWARD 发放 | `rwd_prize.remaining_stock >= 1` 条件原子 UPDATE | 成功发放数 + 剩余库存 = 50；`rwd_stock_log` 前后值链连续无跳变；补发与冲正路径后等式仍成立 |
| C-5 | 限领原子（R17 属性 2） | `reward ClaimLimitConcurrentIT` | dailyClaimLimit=2 / totalClaimLimit=3 两轮，64 线程同用户并发领取 | 限制链事务内计数（`rwd_grant_record` 计数查询） | 恰 2 / 恰 3 成功，其余全部拒绝且错误码可区分限领原因 |
| C-6 | 发放恰一次（R18 属性 1） | `reward GrantExactlyOnceIT` | 构造 RETRY_PENDING 记录后：自动重试调度 + 后台人工重试 + 步骤重触发三方并发 | `rwd_grant_record.uk_idempotent` | GRANTED 成功记录 ≤ 1；INSTANT 积分类 `pnt_transaction` 入账恰一份且 fulfillment=ARRIVED；ASYNC 回调重放 ARRIVED 仍一份 |
| C-7 | 领取恰一次（R19 属性 1） | `reward PrizeClaimExactlyOnceIT` | 64 线程并发领取同一 WON 记录 | WON→CLAIMING CAS + Redisson 锁（§6.8 锁退化路径另测：锁不可用时纯 CAS 仍恰一次） | 恰 1 次成功走完 CLAIMING→GRANTED；INSTANT 则 ARRIVED，ASYNC 则 SENDING；其余 400 `reward.claim.conflict` 或幂等返回 |
| C-8 | 余额非负（R20 属性 1） | `points PointsNonNegativeIT` | 余额 100，128 线程并发扣减 1（adjust）与过期扫描调度并发 | `pnt_account.balance >= 0` CHECK + 原子 UPDATE | 最终余额 ≥ 0 且 = 100 − 成功扣减数；致负请求被拒且 `pnt_transaction` 无对应流水 |
| C-9 | 签到唯一（R21.1，P1） | `signin SigninUniqueIT` | 64 线程并发同用户同日签到 | 签到记录唯一键 | 恰 1 成功、其余幂等"今日已签到"；梯度奖励恰一次（`grantSource=SIGNIN_DAY` 幂等） |
| C-10 | 活动限量（R22 属性 1，P1） | `activity ActivityQuotaIT` | 全局日限量 10，128 线程并发参与 | 参与记录计数事务 | 恰 10 成功；超量拒绝并落参与记录（含命中规则） |
| C-11 | 广告频控（R30 属性 1，P1） | `ad AdFrequencyIT` | 日展示上限 10，64 线程并发拉取同广告位 | 频控计数原子操作 | 素材实际下发次数 ≤ 10（响应内容统计） |
| C-12 | 名单判定（可维护性 4） | `risk ListConcurrentDecisionIT` | 并发导入/移除名单期间持续判定 200 次 | `risk_list_item` uk + 变更后缓存失效 | 每次判定结果 ∈ {按变更前名单, 按变更后名单}，无未定义第三态（黑名单不因并发出现丢失窗口） |

<!-- §7.5 -->
### 7.5 步骤引擎场景矩阵测试（feasibility §2 的 24 场景全量落用例）

测试类 `task ScenarioMatrixIT`（portal-app 测试模块，Testcontainers 拓扑 A + internal 端点直调）。**24 个用例**与 feasibility §2 场景表**一一对应**（该表实测 24 行，原文"25"为编号错位误计，零偏离复审已修正），方法名 `scenario01_首步骤PASSIVE自动级联` ~ `scenario24_定时发布多实例恰一触发`（编号顺序 = 场景表行序），断言口径 = 场景表"期望行为"列原文。测试内维护**场景号 → 期望行为映射常量**（与 feasibility §2 行序绑定，新增场景必须先登记 feasibility 再扩用例）。要点补充（行序均指 feasibility §2 表）：

- 场景 10/13/14/16（并发收敛：放弃与推进并发、同步骤双 click、click×callback 同步骤、progress 双上报）在用例内并发执行（与 C-2 同语义，保留独立用例，方法级 20 轮）。
- 场景 23（下线后存量实例按快照继续）断言推进成功且新领取被拒。
- 场景 17（REWARD 可重试失败回滚）用测试 bean 注入发放超时异常 → 断言步骤仍 ACTIVE、实例未推进、RETRY_PENDING 已由独立事务留痕（§5.6.2）。
- 场景 18（REWARD 永久失败跳过）用停用奖品构造 → 步骤 SKIPPED（skip_reason=GRANT_PERMANENT_FAILED）、实例继续、`rwd_grant_record` PERMANENT_FAILED。
- 场景 24（定时发布恰一）**不在**拓扑 A 的 `ScenarioMatrixIT` 里跑：归任务 41，用 `TwoAdminAppIT` 执行。任务 40 落地 scenario01–23。

该矩阵是步骤引擎的**场景完备性验收**：任何引擎实现变更必须全量通过后方可合入。

<!-- §7.6 -->
### 7.6 架构红线测试（RL-01~12 + AT-C01）

测试套件 `ArchSuiteTest`（归属 admin-app 测试模块——依赖全部域，类路径可见性完整；RL-05 的应用装配部分在 portal-app 侧）：

| 规则 | ArchUnit/断言形态 | 测试类 |
|------|------------------|--------|
| RL-01 模块依赖单向 | `layeredArchitecture(...)` 按包名分层，禁止反向与跨层环 | `ArchLayerRuleTest` |
| RL-02 域间直依为零 | 域模块之间无 Maven 依赖；`com.mkt.task` / `identity` / `reward` / `risk` / `tracking` 互不 import。积分是 `com.mkt.reward.points` 子包，不是独立模块，不存在 `com.mkt.reward → com.mkt.points` 例外 | `ArchLayerRuleTest` |
| RL-03 跨域 Mapper/Entity 禁入 | `noClasses().that().resideInAPackage("com.mkt.<域>..").should().dependOnClassesThat().resideInAPackage("com.mkt.<他域>.(mapper\|entity)..")` 全组合 | `ArchLayerRuleTest` |
| RL-04 控制器分包 | 控制器类必须位于 `..controller.(admin\|portal\|internal)..` 且与所属域模块一致；admin-app 组件扫描过滤器单元测试 | `ArchControllerRuleTest` |
| RL-05 portal-app 装配 task 与 reward | portal-app 测试上下文启动断言 RewardPort、步骤引擎、积分入账 Bean 存在且为进程内 Bean（非远程代理）；POM 依赖 task 与 reward 经 enforcer/构建断言 | `portal PortalAssemblyIT` |
| RL-06 contract 纯度 | `noClasses().that().resideInAPackage("com.mkt.contract..").should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.jdbc..", "com.baomidou..")` | `ArchContractPurityTest` |
| RL-07 事务内禁远程 | `@Transactional` 方法调用栈内不得出现 RestClient/WebClient/HttpClient 类型调用（`SlicesRule`/自定义 ArchCondition） | `ArchTxRemoteCallTest` |
| RL-08 命名空间互斥 | `NamespaceGuardFilter` 集成测试（越界前缀 404）+ 启动路径断言 fail-fast 用例 | `NamespaceIsolationIT` + `StartupNamespaceCheckTest` |
| RL-09 迁移集中 | 域模块资源扫描不含 `*.sql`；`sys_*` Mapper 之外无 DataSource 直配 | `ArchMigrationRuleTest` |
| RL-10 无鉴权后门 | admin 控制器公共方法必须有 `@SaCheckPermission` 或在显式白名单常量表；`@Profile("local")` 的 mock 类不出现于生产编译产物（依赖树断言） | `ArchAuthAnnotationTest` |
| RL-11 配置与 JSON 收口 | `SysConfigMapper` 仅 identity 配置包可 import；`new ObjectMapper(` 仅出现在 kernel JsonUtil | `ArchConfigAccessTest` |
| RL-12 只增不改不删 | `sys_audit_log` / `evt_event_log` / `risk_hit_log` / `task_version_snapshot` 四表 Mapper 方法名白名单 `^(insert\|select).*`；全代码库无四表 update/delete 端点与服务方法 | `EventImmutabilityArchTest`（含 R28.2 属性与 R12 快照不可变） |
| AT-C01 时钟直调禁令（本节 D-03） | §7.2 时钟注入清单所属包禁 `LocalDateTime.now()` / `Instant.now()` / `System.currentTimeMillis()` 直调 | `ArchClockRuleTest` |

<!-- §7.7 -->
### 7.7 恶意样本库

**(1) 表达式恶意样本**：文件 `domain-task/src/test/resources/expression/malicious-*.txt`（每行一个样本，`#` 注释），运行器 `task ExpressionSandboxMaliciousTest` 逐行断言。样本清单（P0 交付时随测试落盘，扩样本只加文件不改运行器）：

| # | 样本 | 期望 |
|---|------|------|
| M-01 | `"".getClass().forName("java.lang.Runtime")` | 编译拒绝（AST 白名单：方法调用/反射不在白名单，`task.expression.validate-failed`） |
| M-02 | `new java.io.File("/etc/passwd")` | 编译拒绝（new 对象禁用） |
| M-03 | `sysDate() > '2020-01-01'` | 编译拒绝（非 7 函数白名单） |
| M-04 | `include("other.expr")` | 词法层拒绝（include 指令禁用） |
| M-05 | `province() == 'x' \|\| (1 == 1` | 语法错误拒绝 |
| M-06 | 5000 层嵌套括号 | 栈深度保护拒绝，进程不 OOM |
| M-07 | 1MB 长度表达式 | 长度上限拒绝（保存接口入参上限） |
| M-08 | `userLevel() > 99999999999` | 整数越界拒绝（DSL 整数域） |
| M-09 | `userLevel() > 1.5e300` | 浮点/科学计数拒绝（字面量仅整数与单引号字符串） |
| M-10 | `NOT NOT NOT ...（1000 次） province() == 'x'` | **按复杂度上限拒绝**（节点总数 2000+ 必然超出 ≤200 上限，唯一期望；禁止求值异常逃逸） |
| M-11 | `hasTag('; DROP TABLE task_definition --')` | 求值正常返回 false（参数为普通字符串，无注入面） |
| M-12 | `hasTag('\u0027') \|\| province() == 'x'` | Unicode 转义不构成引号逃逸，按字面求值 |
| M-13 | `inCrowd('not-exist-crowd')` | 求值 false + 告警日志（包不存在按不可见，R11.9），不抛异常 |
| M-14 | 属性缺失样例用户求值 `province() == 'GD'` | false（空值哨兵语义，§5.10） |
| M-15 | `hasTag('vip') AND (userLevel() >= 3 OR registerWithinDays(7))` | **接受**（合法边界样本，防白名单误杀） |

**(2) internal 签名攻击样本**：`portal-app/src/test/resources/internal-attack/`，运行器 `internal ReplayAttackIT`（覆盖 R15 属性 1）：

| # | 样本 | 期望 |
|---|------|------|
| A-01 | 完全重放（同 nonce 同签名重发合法请求） | 拒绝 `internal.nonce.replayed`，业务表零变化 |
| A-02 | 篡改 body 后沿用原签名 | 拒绝 `internal.sign.invalid-signature` |
| A-03 | 时间戳偏移 +301s / −301s | 拒绝 `internal.timestamp.skew-exceeded` |
| A-04 | 时间戳在未来（容差内）+ 新 nonce | 正常处理（仅容差窗口判定，不区分未来/过去） |
| A-05 | 未知 appId / 已失效 appId | 拒绝 `internal.app.disabled`（或 not-found 分段值） |
| A-06 | 空签名头 / 畸形十六进制签名 | 拒绝 `internal.sign.invalid-signature` |

<!-- §7.8 -->
### 7.8 性能基准与门槛（NFR 性能 1–8 逐条映射）

执行环境：staging compose（portal-app ×2 + MySQL 8 + Redis 7，规格与生产同档，R31.1 编排复用）；工具 k6，脚本位于仓库 `perf/`；每场景稳定加压 ≥ 5 分钟取 P95；结果存档 `perf/reports/<日期>/`，作为上线检查清单性能验证签署项（R31.2）。种子数据脚本 `perf/seed/` 统一提供（规模对齐 NFR 性能 8：100 万用户档案、50 万实例/日、500 万事件/日按月分区）。

| NFR | k6 脚本 | 场景与数据规模 | 门槛 | 执行时机 |
|-----|---------|---------------|------|---------|
| 性能 1 列表 500 QPS | `perf/list.js` | 登录态用户池循环拉任务列表；1000 个已发布任务（含灰度/过滤命中分布） | 2 实例下 P95 ≤ 300 ms，错误率 = 0 | 发布前 |
| 性能 2 推进/回调 300 QPS | `perf/advance.js` | internal progress/callback 混合（appId 限流临时调至 5000，附录 A 上限内） | P95 ≤ 200 ms | 发布前 |
| 性能 3 发奖事务 | `perf/complete.js` | 领取 → 50 步满配级联 → REWARD 完成链（最坏级联深度） | 事务 P95 ≤ 500 ms（同时作为级联上界常驻集成断言：`task CascadeBudgetIT` 断言单事务 < 500 ms） | 发布前 + CI 常驻断言 |
| 性能 4 风控增量 ≤ 20 ms | `perf/risk-delta.js` | 同一领取/发奖场景两轮：全部规则启用 vs 全部停用 | 两轮 P95 差值 ≤ 20 ms | 发布前 |
| 性能 5 埋点吞吐 | `perf/track.js` | 50 条满批上报（含 8KB 单条），独立施压 | 集群吞吐 ≥ 6000 events/s、P95 ≤ 100 ms、丢弃率 < 0.1% | 发布前 |
| 性能 6 广告拉取（P1） | `perf/ad.js` | 广告位拉取 | P95 ≤ 100 ms @ 300 QPS | P1 交付前 |
| 性能 7 后台查询 | `perf/admin-list.js` | 任务/实例/流水典型列表（50 万实例数据量） | P95 ≤ 800 ms | 发布前 |
| 性能 8 容量假设验证 | `perf/seed/*` | 上述全部脚本的种子规模即容量假设落库验证 | 种子装载后全部脚本门槛达标 | P1 任务 49 / 容量复验（非 P0 发布门禁） |

补充：Spike T2 的 Aviator 求值 P99 < 1ms 结论（feasibility §3.5）以 `perf/expression-benchmark.js` 复测归档，不设 CI 门槛。

<!-- §7.9 -->
### 7.9 前端测试与契约联动（R32–R36 客户端条款）

- **Vitest 组件单测**（web 仓库 `*.test.ts`）：R34.2 状态按钮状态机、R35.2 领取七态 × 履约四态按钮映射（即 R35.1 属性）、R34.3 步骤时间线/进度条 x/N、R32.2 验证码交互（刷新/单独提示）、R32.4 注册协议勾选与密码强度提示、空态文案（R33.4）、待领取倒计时（R35.1）、外链图片失败占位（模块 I 运行环境基线通用兜底）。
- **Playwright E2E**（`web/e2e/journey-core.spec.ts`，对 staging compose 全链路）：匿名访问业务页 → 重定向登录并保留回跳（R32.1）→ 注册（勾选协议）自动登录（R32.4）→ 列表曝光埋点发出（R34.5：拦截 `/api/common/track/batch` 断言 `task.card.exposure`）→ 领取 → 点击完成 → 时间线即时更新与结果反馈（R34.4）→ 我的奖品领取 → 积分页余额即时刷新（R35.4）→ 退出登录原会话失效（R33.5）。管理端 `journey-admin.spec.ts`：登录 → 任务聚合保存 → 表达式校验 → 发布 → 实例查询（R14.9）。
- **契约联动（NFR 可维护性 2）**：CI 执行 openapi-typescript 重新生成并断言无 diff（禁止手写重复类型）；前端 mock（msw）数据源为 springdoc 导出的 OpenAPI JSON，契约变更即测试红。

<!-- §7.10 -->
### 7.10 覆盖率与质量门禁（决策点 D-04）

- **JaCoCo 行覆盖门禁**：全局 ≥ 70%；关键包 ≥ 85%——`com.mkt.task.engine`、`com.mkt.task.expression`、`com.mkt.reward.grant`、`com.mkt.reward.points`、`com.mkt.risk.engine`、`com.mkt.kernel`（错误处理与 JsonUtil）。
- **跳过禁令**：CI 脚本 grep `*IT`/`*PropertyTest`/`*ArchTest` 源码中的 `@Disabled`、`assumeTrue`、`@Tag` 跳过 = 0 处，出现即构建失败。
- **flaky 治理**：并发/属性测试失败即阻断；同轮允许至多 1 次重跑且必须附失败定位记录（日志/线程转储），连续两轮重跑即判实败。
- **门禁汇总**：阻断级 = 单元 + 属性 + 集成 + 多实例 + 并发 + 架构 + E2E + 覆盖率 + 跳过禁令；**P0 发布门禁** = k6 性能 1–5、7（§7.8）+ 部署幂等冒烟（R31.1）+ 上线检查清单签署（R31.2）。性能 6/8 属 P1 任务 49，不进 P0 门禁。
