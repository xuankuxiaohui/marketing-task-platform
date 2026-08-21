# 营销任务平台 · 规格

本目录是产品规格。实现以需求、设计、任务三份为准。

实现进度与当前产品阶段（v1 已上 master `6d03ef5`，**测试阶段**，尚未 preview）见仓库根 [PROJECT_STATUS.md](../../../PROJECT_STATUS.md)。本目录不是施工进度。

| 文档 | 内容 | 版本 |
|------|------|------|
| [requirements.md](requirements.md) | 用户故事、验收标准、正确性属性、附录 A–D | v3.9 |
| [design.md](design.md) | **总册**：§0 索引（锚点 `<!-- §x.y -->`）+ §1 概述 + §8 追溯。正文在分册 | v2.13 |
| [design-architecture.md](design-architecture.md) | §2 架构 / §6 横切 / §3.11 P1 骨架 | 随 v2.13 |
| [design-schema.md](design-schema.md) | §3.1–§3.10 Schema | 随 v2.13 |
| [design-api.md](design-api.md) | §4 API | 随 v2.13 |
| [design-algorithm.md](design-algorithm.md) | §5 算法 / §7 测试 | 随 v2.13 |
| [tasks.md](tasks.md) | 53 个可追踪任务（编组 A–J；37/38 已拆号） | v2.9 |

| 文档 | 用途 |
|------|------|
| [component-selection.md](component-selection.md) | 组件选型与 Spike 清单 |
| [dependency-matrix.md](dependency-matrix.md) | 编组 A 版本矩阵（**冒烟已绿**，2026-08-18） |
| [feasibility-step-engine.md](feasibility-step-engine.md) | 步骤引擎 24 场景 |
| [feasibility-recon.md](feasibility-recon.md) | 对账 / 补发门禁 28 场景 |
| [feasibility-risk.md](feasibility-risk.md) | 风控名单 + 判定链 30 场景 |
| [CHANGELOG-requirements.md](CHANGELOG-requirements.md) | 需求版本差异（代理不必读） |
| [CHANGELOG-design.md](CHANGELOG-design.md) | 设计版本差异（代理不必读） |

验收对照（任务 → 属性 → 测试类）：仓库 [`docs/verification-matrix.md`](../../docs/verification-matrix.md)。

写作约定以本目录规格为准。编码层规范见仓库 [`docs/standards/`](../../../docs/standards/README.md)，不替代本目录规格。

代理打开 design：**先读 [design.md](design.md) §0.2 索引**，在分册里搜索 `<!-- §x.y -->`，不要通读、不要记行号。

## 决策

| 决策点 | 结论 |
|--------|------|
| 部署 | 模块化单体内核 + admin-app / portal-app |
| API | `/admin/**`、`/api/common/<模块>/**`、`/internal/**`（网关不向公网暴露 internal） |
| 领域 | identity / task / reward（含积分）/ risk / tracking；P1：signin / activity / ad |
| 运行时 | JDK 26 + Spring Boot 4 |
| 数据 | 共库，表按域前缀；UTC 存储 / ISO-8601 输出 / 业务自然日 UTC+8 |
| 缓存 | Spring Cache + Caffeine + Redis。`ad:position` P0 只占位，任务 48 接线 |
| 表达式 | AviatorScript + AST 白名单 |
| 异步 | Outbox（按 producer 分 Relay） |
| 管理端 | vue-pure-admin-thin（`web/apps/admin`）；门户 Vant 4（`web/apps/client`） |

## 范围

- **P0**：身份权限、系统管理、任务引擎、奖励积分、风控、埋点、部署、门户体验、双前端核心页
- **P1**：签到、活动、看板、模拟器、广告位、压测

## 口径

39 张表 · 66 条正确性属性 · 步骤 24 + 对账 28 + 风控 30 场景 · 附录 A 52 键 · 跨域写端口 Reward / UserAttribute / RiskCheck · 只读门面 TaskReadPort（D-13）
