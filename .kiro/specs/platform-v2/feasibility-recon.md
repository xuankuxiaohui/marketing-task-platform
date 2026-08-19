# 可行性分析：渠道对账与补发门禁

> 对象：requirements.md R37 / R18.3 / R17.5。算法权威：design §5.11（D-10）。场景矩阵 **28 行**。
> 发放/库存副作用仍走 §5.6 / §5.7，本矩阵不重写领取七态。

## 1. 结论摘要

**可行。** 匹配四结果 × 履约四态 × 政策 REVIEW/AUTO × 核渠三态均可落成确定动作。正确性依赖：`fulfillmentRef` 等值连接穷尽、差异项 `action` 只许一次成功迁移、`MANUAL_GRANT` 与关原单同事务、原 `fulfillmentRef` 回调短路。AUTO 路径**不得**产生补发。

## 2. 场景矩阵

平台集 P = 该分类、账单日 UTC+8、`recon_status=PENDING`、`simulated=0` 且 `fulfillment ∈ {ARRIVED, SENDING, FULFILL_FAILED}`。渠道集 C = 本批已导入行（按 `fulfillmentRef` 去重）。

| # | 场景 | 期望行为 | 实现机制 | 需求条款 | 判定 |
|---|------|----------|----------|----------|------|
| 1 | 双方有且 `costFen == amountFen` | `MATCHED`；`review_status=NONE`；无动作 | 等值连接 + 金额相等 | R37.4 | ✅ |
| 2 | 双方有且金额不等 | `AMOUNT_MISMATCH`；仅 `ABSORB` | 等值连接 + 金额不等；禁止改 `costFen` | R37.4/37.5 | ✅ |
| 3 | 仅 P 且履约 `ARRIVED` | `PLATFORM_ONLY`；仅 `ABSORB`；禁 REFULFILL / MANUAL_GRANT | 动作允许集 | R37.5 | ✅ |
| 4 | 仅 P 且履约 `SENDING` | `PENDING_REVIEW`；未核渠点动作 → 400 `review-required` | `needsChannelReview` | R37.7 | ✅ |
| 5 | 仅 P 且 `FULFILL_FAILED` + `TIMEOUT` | 同 #4，须核渠 | `needsChannelReview` | R37.7 | ✅ |
| 6 | 仅 P 且失败原因缺失或非封闭值 | 同 #4，须核渠 | 原因 ∉ 封闭枚举视同 TIMEOUT 路径 | R18.3 / R37.7 | ✅ |
| 7 | 仅 P 且 `FULFILL_FAILED` + `CHANNEL_REJECT`，政策 REVIEW | `review_status=NONE`；人工可直接 REFULFILL / MANUAL_GRANT | `autoRefulfillEligible=false` | R37.7 | ✅ |
| 8 | 同 #7 但政策 AUTO 且总闸 `true` | 匹配 afterCommit **只**自动 `REFULFILL`（同一笔）；零条 MANUAL_GRANT | `autoRefulfillEligible` | R37.7 / 属性 3 | ✅ |
| 9 | 同 #8 但总闸 `false`（P0 默认） | 不自动动作；人工可 REFULFILL | 配置短路 | R37.7 | ✅ |
| 10 | 仅 P 且 `ADAPTER_ERROR` + AUTO + 总闸 `true` | 同 #8 | 与 CHANNEL_REJECT 同类 | R37.7 | ✅ |
| 11 | 仅 C | `CHANNEL_ONLY`；`LEDGER_ONLY` / `ABSORB`；一律人工 | 无自动入口 | R37.5 | ✅ |
| 12 | 导入重复 `fulfillmentRef` | 导入拒绝，批次不进入匹配 | C 去重 | R37.3 | ✅ |
| 13 | `simulated=1` 发放记录 | 不进 P | 平台集过滤 | R37.1 / R24 | ✅ |
| 14 | `reconRequired=false`（积分/徽章） | 不进 P | 分类开关 | R37.2 | ✅ |
| 15 | `FULFILL_FAILED` 记录 | **进入** P（与 ARRIVED / SENDING 并列） | 平台集 fulfillment 集合 | R37.2 | ✅ |
| 16 | 一批次四类样本齐全 | 每条平台记录、每条渠道行恰好落一项；无遗漏无双计 | 连接穷尽 | R37 属性 1 | ✅ |
| 17 | `MATCHED` 点任何动作 | 拒绝 `reward.recon.action-forbidden` | 动作允许集 | §5.11 | ✅ |
| 18 | 同一 item 第二次成功动作 | 拒绝；第一次结果保留 | `action` 从 NONE 只许一次迁移 | R37.5 | ✅ |
| 19 | `TIMEOUT` 未核渠点 REFULFILL | 400 `review-required`；发放/库存零变化 | 门禁先行 | R37 属性 3 | ✅ |
| 20 | `TIMEOUT` 未核渠点 MANUAL_GRANT | 同 #19 | 门禁不区分动作 | R37 属性 3 | ✅ |
| 21 | `SENDING` 未核渠点任一补发动作 | 同 #19 | `needsChannelReview` | R37.7 | ✅ |
| 22 | 核渠 `REJECTED` | 仅 `ABSORB` | 动作允许集 | R37.7 | ✅ |
| 23 | 核渠 `CONFIRMED` | **不**自动补发；操作人再点动作 | `POST .../review` 只改 review_status | R37.7 | ✅ |
| 24 | MANUAL_GRANT 成功 | 新单走 grant；原单 `FULFILL_FAILED`+`MANUAL`；库存扣一次；原 ref 留原单 | 同事务 CAS | R37.5 / 属性 2 | ✅ |
| 25 | 补发后再投原 `fulfillmentRef` 回调 / confirm | HTTP 200 幂等；原单保持失败；不得改成 ARRIVED | §5.6.3 MANUAL 短路 | R37.5 / 属性 2 | ✅ |
| 26 | REFULFILL | 同一笔重试履约；不新建记录、不二次扣库存 | §5.6.3 | R37.5 | ✅ |
| 27 | 任意 AUTO 路径 | 零条 `MANUAL_GRANT` | 自动入队只许 REFULFILL | R37 属性 3 | ✅ |
| 28 | 并发双点 MANUAL_GRANT / REFULFILL | 用户侧 ARRIVED ≤ 1；库存按规则只扣一次 | `uk_idempotent` + item.action CAS | R37 属性 2 | ✅ |

`needsChannelReview` 与 `autoRefulfillEligible` 已覆盖 TIMEOUT / SENDING / 空原因 / CHANNEL_REJECT / ADAPTER_ERROR。下列组合 **§5.11 动作表未单列**，实现前必须先补设计、禁止静默选边：

| 未列组合 | 已知约束 | 不得自行发明 |
|----------|----------|--------------|
| `FULFILL_FAILED` + `CALLBACK_FAILED` | 封闭枚举值，故**不**进 `needsChannelReview`；也**不**进 `autoRefulfillEligible` | 不得当成 ADAPTER_ERROR 自动 REFULFILL |
| `FULFILL_FAILED` + `MANUAL`（补发关原单后的原单） | 原单已关，不应再入待对账；若误入批次 | 不得再开第二次 MANUAL_GRANT |

任务 34 落地前把上表两行补进 design §5.11 动作允许集，或显式写「拒绝 `action-forbidden`」。

## 3. 关键机制

### 3.1 穷尽匹配

一次 `match(batch)` 对 P、C 做 `fulfillmentRef` 外连接。写入 `rwd_recon_item` 后断言：`count(P) + count(仅 C) == count(items)`。重复导入在匹配前拒绝，避免双计。

### 3.2 核渠与 AUTO 正交

`needsChannelReview` **不论政策**。AUTO 只作用于「渠道已明确未出款」的 `CHANNEL_REJECT` / `ADAPTER_ERROR`（及同口径的 `CALLBACK_FAILED`），且只自动 `REFULFILL`。P0 `reward.recon.auto-refulfill-enabled=false`，适配器为桩，避免空转。

### 3.3 关原单与回调短路

`MANUAL_GRANT` 的 `sourceId=recon:{itemId}`。关原单 CAS：`fulfillment_status IN (SENDING, FULFILL_FAILED) AND <> ARRIVED`。原 `fulfillment_ref` 不搬到新单。此后该 ref 的回调走 §5.6.3 MANUAL 短路。不调渠道撤销。

### 3.4 被否决的备选

| 备选 | 否决原因 |
|------|----------|
| AUTO 也可自动 MANUAL_GRANT | 违反 R37.7「补发永不自动」；新开库存不可无人值守 |
| 金额不等时改 `costFen` | 违反成本快照不可变（R17.9 / R37.6） |
| 匹配后 CONFIRMED 自动补发 | 核渠只证明「渠道未出款」，补发仍须人点 |

## 4. 残余风险

| 风险 | 等级 | 缓解 |
|------|------|------|
| 渠道账单缺 `fulfillmentRef` 或拼写与平台不一致 | 中 | 导入校验拒绝；运营按 CHANNEL_ONLY / PLATFORM_ONLY 人工处理 |
| 桩适配器下误开 AUTO 总闸 | 低 | P0 默认 false；任务 34 验收断言默认值 |
| `CALLBACK_FAILED` / `MANUAL` 动作行未写入 §5.11 | 中 | **规格缺口**：任务 34 前补设计；在此之前测试按「未列 = 拒绝动作」红灯即停，不猜 |

## 5. 需求约束

1. **R37.2**：待对账含 `ARRIVED` / 跨日仍 `SENDING` / `FULFILL_FAILED`；`simulated=1` 不计。
2. **R37.5 / 属性 2**：补发成功必须关原单；原 ref 回调不得把原单打成 ARRIVED。
3. **R37.7 / 属性 3**：TIMEOUT / SENDING / 非法原因必须核渠；AUTO 不得产生 MANUAL_GRANT。
