# 可行性分析：风控名单与判定链

> 对象：requirements.md R25 / R26 / R27。算法权威：design §5.9。场景矩阵 **30 行**。
> 步骤推进入口**不**走 `RiskCheckPort`（R25.4/R25.7）；领取/发奖走端口。

## 1. 结论摘要

**可行。** 四场景 × 名单三维 × R-a–R-f 启停均可落成确定 verdict。正确性依赖：黑优先、REGISTER/LOGIN 不跑 R-a–R-f、R-e 仅 GRANT+elapsedSeconds、命中 `REQUIRES_NEW` 留痕、拒绝零副作用。Redis 故障按 `risk.fallback-policy` 降级且必须可观测。

## 2. 场景矩阵

入口：`RiskCheckPort.check(scene, subject)`。`scene ∈ {REGISTER, LOGIN, CLAIM, GRANT}`。

| # | 场景 | 期望行为 | 实现机制 | 需求条款 | 判定 |
|---|------|----------|----------|----------|------|
| 1 | REGISTER + IP 黑名单 | REJECT；不写 risk:cnt | 名单段；REGISTER 在规则段之前 return | R25.4 / R26.3 | ✅ |
| 2 | REGISTER + 设备黑名单 | REJECT | 同上 | R25.4 | ✅ |
| 3 | REGISTER + 仅用户黑名单 | PASS（用户黑名单不拦注册） | 用户黑仅 CLAIM/GRANT（及 LOGIN+denyLogin） | R25.4 | ✅ |
| 4 | LOGIN + 用户黑、未标 denyLogin | PASS（默认不拦登录） | denyLogin 开关 | R25.4 | ✅ |
| 5 | LOGIN + 用户黑且 denyLogin | REJECT | 名单段 | R25.4 | ✅ |
| 6 | LOGIN + IP 黑名单 | REJECT | 名单段 | R25.4 | ✅ |
| 7 | LOGIN + 设备黑名单 | REJECT | 名单段 | R25.4 | ✅ |
| 8 | CLAIM + 用户黑名单 | REJECT；0 实例 | 端口 + 领取链 | R25.4 / R13.6 | ✅ |
| 9 | GRANT + 用户黑名单 | REJECT；发放/库存/积分零变化 | 发放前置 | R18.7 / R25.4 | ✅ |
| 10 | 步骤 click/callback/progress + 用户黑名单 | **不**调本端口；task 域查同一 `risk:list` 投影后冻结推进，实例保持原状 | R25.4 推进入口 | R25.7 | ✅ |
| 11 | 设备黑名单 + 已登录后 CLAIM/GRANT | 设备黑不影响已登录业务；只跑用户名单 + 规则 | R25.4 末句 | R25.4 | ✅ |
| 12 | 同值黑+白并存 | 恒 REJECT（黑优先） | 名单段先于白名单 | R25.6 / 属性 1 | ✅ |
| 13 | CLAIM + 仅用户白名单 | 跳过 R-a–R-f，PASS；限领/库存/互斥仍生效 | 白名单 return PASS | R25.5 | ✅ |
| 14 | 限期黑名单 `expire_at` 已过 | 不拦截；miss 后 DB 回源过滤 | 投影 TTL / 判定时过滤 | R25 属性 2 | ✅ |
| 15 | REGISTER / LOGIN 到达规则段 | **直接 PASS**；不跑 R-a–R-f；不写 risk:cnt | `if scene ∈ {REGISTER,LOGIN}: return PASS` | R26.3 | ✅ |
| 16 | CLAIM + 规则全开 | 跑 R-a/b/c/d/f；**不跑 R-e** | 规则段；R-e 仅 GRANT | R26.1 / D-09 | ✅ |
| 17 | GRANT + `elapsedSeconds == null` | 跳过 R-e；其余规则照跑 | D-09 | R26.1 | ✅ |
| 18 | GRANT + `simulated=true` | R-e 直接 skip；R-a/b/f 不统计；R-c/d 观察不拦截 | simulated 排除 | R24.5 / R26.1 | ✅ |
| 19 | GRANT + `grantSource != TASK_STEP` | 跳过 R-e（补发/签到/活动） | 调用方传 null elapsed | R26.1 | ✅ |
| 20 | 同请求新建实例 + enter 级联 REWARD | 调用方传 `elapsedSeconds=null`；无 R-e 命中；发放成功 | D-09 同请求新建跳过 | R26 属性 4 | ✅ |
| 21 | 已存在实例 click 至 REWARD 且 elapsed < threshold | 按该规则 action 命中；拒绝则发放零副作用 | R-e 比较 | R26 属性 4 / R18.7 | ✅ |
| 22 | 同 #21 但 elapsed ≥ threshold | 不命中 R-e | 边界 | R26 属性 1 | ✅ |
| 23 | R-f 计数口径 | 只计通过认证与账号状态后、到达风控点的 CLAIM/GRANT；更早拒绝不计 | 同步 ZADD 在判定点 | R26.2 | ✅ |
| 24 | 规则 action = MARK | 业务继续；落命中；不拦截 | verdict=MARK | R26.2 | ✅ |
| 25 | action = REJECT vs SILENT_REJECT | 都拒绝写库；提示层不同（C 端均 `risk.blocked.generic`） | 调用方映射 | R26.2 / R34.6 | ✅ |
| 26 | Redis 不可达 + `fallback-policy=allow` | 放行 + 降级事件 + 告警 | §6.8 | R26.3 / 属性 3 | ✅ |
| 27 | Redis 不可达 + `fallback-policy=reject` | 拒绝 + 降级事件 | 配置 | R26.3 | ✅ |
| 28 | 主事务随后回滚 | `risk_hit_log` 仍在（REQUIRES_NEW）；独立写入失败 → 告警计数，不阻断 | 命中留痕边界 | R26.4 / §5.9 | ✅ |
| 29 | 并发导入/移除名单期间连续判定 | 每次结果 ∈ {变更前, 变更后}，无第三态 | C-12 | 可维护性 4 | ✅ |
| 30 | 阈值边界 threshold ± 1 | 命中 / 不命中分界正确；同输入重放两次一致 | jqwik | R26 属性 1 | ✅ |

R-a/R-b 计数由事件消费异步写（允许 ≤5s 滞后）；R-c/R-d 由注册/登录成功消费器写；R-f 判定点同步 ZADD。**R-e 不出现在 `risk:cnt`。**

## 3. 关键机制

### 3.1 场景切分

```text
名单（黑优先）→ REGISTER/LOGIN 在此结束
           → CLAIM/GRANT 再跑 R-a–R-f（R-e 另见生效条件）
步骤推进：task 只查用户黑名单投影，不进本端口
```

把「登录风控」做成完整 R-a–R-f 会误伤正常登录，且与 R26.3 互斥。

### 3.2 R-e 不是滑窗

R-e 比较的是调用方传入的 `elapsedSeconds`，无窗口、不写 Redis。必须跳过的三类（非 TASK_STEP / simulated / 同请求新建）由调用方传 `null` 或 `simulated=true` 在端口内 skip。实现若在 CLAIM 跑 R-e，本矩阵 #16 会红。

### 3.3 拒绝零副作用

风控拒绝发生在 task 写实例 / reward 扣库存之前。命中记录独立事务，不随主事务回滚丢失。重复被拒请求各记一条命中（R13.4 / R26 属性 2）。

### 3.4 被否决的备选

| 备选 | 否决原因 |
|------|----------|
| 步骤推进也跑 R26 | 违反 R25.7；回调重投会被频率规则误伤冻结期 |
| R-e 写入 risk:cnt | 无窗口语义，污染滑窗；D-09 已否 |
| 风控异常 fail-closed 且无配置 | 与 R26.3 默认可放行 + 必须留痕冲突；策略必须可配 |

## 4. 残余风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| R-a/R-b 异步计数滞后 ≤5s 漏拦 | 中 | 产品接受；P1 模型评分补；文档写明 |
| 调用方漏传 elapsedSeconds 导致该拦不拦 | 中 | GrantContext 由 task 引擎统一填；单测覆盖 #20/#21 |
| 名单投影与 DB 短暂不一致 | 低 | 变更 afterCommit 同步投影；C-12 禁第三态 |

## 5. 需求约束

1. **R26.3**：REGISTER/LOGIN 只跑名单；步骤推进入口不跑 R26。
2. **R26.1 / D-09**：R-e 仅 GRANT + 非空 elapsed + 非 simulated + 非同请求新建。
3. **R26 属性 2 / R18.7**：规则拒绝发放零副作用。
4. **R25.6**：黑白并存恒拒。
