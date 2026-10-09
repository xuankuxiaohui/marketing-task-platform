# 产品规格导航

本目录保留业务约定与设计依据，当前阶段与代码重构范围见 [PROJECT_STATUS](../../../PROJECT_STATUS.md)。需求优先于设计；历史交付计划不参与业务冲突裁决。

| 文档 | 作用 |
|---|---|
| [requirements.md](requirements.md) | R1–R37、正确性属性、附录 A–D；行为与验收权威 |
| [design.md](design.md) | §0.2 章节索引、原则、追溯与设计决策；先按索引找分册 |
| [design-architecture.md](design-architecture.md) | §2 架构与红线、§6 横切机制、§3.11 扩展领域 |
| [design-schema.md](design-schema.md) | §3.1–3.10 表、Redis 键与数据不变量 |
| [design-api.md](design-api.md) | §4 HTTP 契约、匿名边界与管理菜单 |
| [design-algorithm.md](design-algorithm.md) | §5 算法与事务、§7 测试策略 |
| [feasibility-step-engine.md](feasibility-step-engine.md) | 步骤引擎 24 场景 |
| [feasibility-recon.md](feasibility-recon.md) | 对账与补发 28 场景 |
| [feasibility-risk.md](feasibility-risk.md) | 风控 30 场景 |
| [component-selection.md](component-selection.md) | 选型理由与边界，不是待执行预研队列 |
| [dependency-matrix.md](dependency-matrix.md) | 依赖选型基线与历史验证依据 |
| [tasks.md](tasks.md) | 历史实现计划及依赖关系，仅用于追溯 |

验收映射见 [docs/verification-matrix.md](../../../docs/verification-matrix.md)，编码方式见 [docs/standards](../../../docs/standards/README.md)。文档版本以各正文为准，不在导航重复维护。

P0/P1 标记保留需求来源；当前树中已有签到、活动、广告与双前端。`ad:position` 已有代码接线，不能因历史“P0占位”描述改回空实现。

规格不等于已验证实现。发现条款分歧或需要改变契约时，先写 [决策记录](../../../docs/decisions.md)，明确结论后同步需求、设计、测试与实现；不要静默让文档追认现有缺陷。
