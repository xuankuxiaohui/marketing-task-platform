# 设计文档：营销任务平台 v2

> 版本：**v2.15**　需求：[requirements.md](requirements.md) v3.10　选型：[component-selection.md](component-selection.md)
> 步骤引擎：[feasibility-step-engine.md](feasibility-step-engine.md)　对账：[feasibility-recon.md](feasibility-recon.md)　风控：[feasibility-risk.md](feasibility-risk.md)
> 验收矩阵：[../../docs/verification-matrix.md](../../docs/verification-matrix.md)　历史差异：[CHANGELOG-design.md](CHANGELOG-design.md)
>
> v2.15 相对 v2.14：§4.2 补角色当前权限集 GET。v2.14 相对 v2.13：§4.9.0 匿名清单补活动中心 GET 与绑卡任务 GET（R32.1 / #91）。v2.13 相对 v2.12：**按章拆册**。§ 编号不变；引用仍写 `design §x.y`；跳转搜 `<!-- §x.y -->`（不行号）。`ad:position` 明确为 P0 预留名 / 任务 48 接线。

<!-- §0 -->
## 0. 分册与章节索引

代理**不要通读**本文件或任一超长分册。按改动打开下表对应文件，用 §0.2 锚点跳转。

<!-- §0.1 -->
### 0.1 怎么读

| 改什么 | 打开 | 章 |
|--------|------|----|
| 模块、红线、部署、会话、缓存、Outbox、调度、降级 | [design-architecture.md](design-architecture.md) | §2 / §6 / §3.11 |
| 表、不变量、错误码分段、Redis 键 | [design-schema.md](design-schema.md) | §3.1–§3.10 |
| HTTP 端点、匿名清单、菜单种子 | [design-api.md](design-api.md) | §4 |
| 算法伪代码、测试映射、场景落地 | [design-algorithm.md](design-algorithm.md) | §5 / §7 |
| 概述、需求↔章节、口径、决策号 | **本文件** | §1 / §8 |

各分册 ≤ 5 万字符。§3.11（P1 表骨架）挂在架构分册，避免 Schema 分册超限。

<!-- §0.2 -->
### 0.2 章节索引（§ → 文件 + 锚点）

各分册标题上一行有 HTML 注释 `<!-- §x.y -->`。跳转：打开文件，搜索该注释。**不要记行号**，增删段落后锚点仍有效。

| § | 标题 | 文件 | 锚点 |
|---|------|------|------|
| 1 | 概述 | [design.md](design.md) | `<!-- §1 -->` |
| 1.1 | 设计目标 | [design.md](design.md) | `<!-- §1.1 -->` |
| 1.2 | 设计原则 | [design.md](design.md) | `<!-- §1.2 -->` |
| 1.3 | 代码仓库布局 | [design.md](design.md) | `<!-- §1.3 -->` |
| 2 | 架构总览与架构红线 | [design-architecture.md](design-architecture.md) | `<!-- §2 -->` |
| 2.1 | 系统上下文 | [design-architecture.md](design-architecture.md) | `<!-- §2.1 -->` |
| 2.2 | Maven 模块与依赖 / 三端口 | [design-architecture.md](design-architecture.md) | `<!-- §2.2 -->` |
| 2.3 | API 命名空间与网关 | [design-architecture.md](design-architecture.md) | `<!-- §2.3 -->` |
| 2.4 | 应用组装与运行形态 | [design-architecture.md](design-architecture.md) | `<!-- §2.4 -->` |
| 2.5 | 关键时序 | [design-architecture.md](design-architecture.md) | `<!-- §2.5 -->` |
| 2.6 | 部署视图 | [design-architecture.md](design-architecture.md) | `<!-- §2.6 -->` |
| 2.7 | 技术栈与版本策略 | [design-architecture.md](design-architecture.md) | `<!-- §2.7 -->` |
| 2.8 | 架构红线 RL-01~12 | [design-architecture.md](design-architecture.md) | `<!-- §2.8 -->` |
| 2.9 | 全局设计约定 | [design-architecture.md](design-architecture.md) | `<!-- §2.9 -->` |
| 3 | 领域模型与 Schema | [design-schema.md](design-schema.md) | `<!-- §3 -->` |
| 3.1 | 建模总约定 | [design-schema.md](design-schema.md) | `<!-- §3.1 -->` |
| 3.2 | identity / 系统管理 `sys_` | [design-schema.md](design-schema.md) | `<!-- §3.2 -->` |
| 3.3 | task 域 | [design-schema.md](design-schema.md) | `<!-- §3.3 -->` |
| 3.4 | reward 域 | [design-schema.md](design-schema.md) | `<!-- §3.4 -->` |
| 3.5 | points 域 | [design-schema.md](design-schema.md) | `<!-- §3.5 -->` |
| 3.6 | risk 域 | [design-schema.md](design-schema.md) | `<!-- §3.6 -->` |
| 3.7 | tracking 域 | [design-schema.md](design-schema.md) | `<!-- §3.7 -->` |
| 3.8 | DB 层不变量 | [design-schema.md](design-schema.md) | `<!-- §3.8 -->` |
| 3.9 | 错误码分段 | [design-schema.md](design-schema.md) | `<!-- §3.9 -->` |
| 3.10 | Redis 数据结构索引 | [design-schema.md](design-schema.md) | `<!-- §3.10 -->` |
| 3.11 | P1 域骨架 | [design-architecture.md](design-architecture.md) | `<!-- §3.11 -->` |
| 4 | API 契约 | [design-api.md](design-api.md) | `<!-- §4 -->` |
| 4.1 | 全局契约细则 | [design-api.md](design-api.md) | `<!-- §4.1 -->` |
| 4.2 | admin 认证与会话 | [design-api.md](design-api.md) | `<!-- §4.2 -->` |
| 4.3 | admin 系统管理 | [design-api.md](design-api.md) | `<!-- §4.3 -->` |
| 4.4 | admin 任务域 | [design-api.md](design-api.md) | `<!-- §4.4 -->` |
| 4.5 | admin 奖励与积分 | [design-api.md](design-api.md) | `<!-- §4.5 -->` |
| 4.6 | admin 风控 | [design-api.md](design-api.md) | `<!-- §4.6 -->` |
| 4.7 | admin 埋点元数据 | [design-api.md](design-api.md) | `<!-- §4.7 -->` |
| 4.8 | internal 回调 | [design-api.md](design-api.md) | `<!-- §4.8 -->` |
| 4.9 | 门户 common | [design-api.md](design-api.md) | `<!-- §4.9 -->` |
| 4.10 | admin 前端页面 / 菜单种子 | [design-api.md](design-api.md) | `<!-- §4.10 -->` |
| 5 | 核心算法 | [design-algorithm.md](design-algorithm.md) | `<!-- §5 -->` |
| 5.1 | 步骤推进引擎 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.1 -->` |
| 5.2 | 分支求值 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.2 -->` |
| 5.3 | 可见性与灰度 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.3 -->` |
| 5.4 | CycleKeyResolver | [design-algorithm.md](design-algorithm.md) | `<!-- §5.4 -->` |
| 5.5 | 领取 startInstance | [design-algorithm.md](design-algorithm.md) | `<!-- §5.5 -->` |
| 5.6 | 发放状态机 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.6 -->` |
| 5.7 | 库存原子扣减 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.7 -->` |
| 5.8 | 积分原子变动 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.8 -->` |
| 5.9 | 风控判定链 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.9 -->` |
| 5.10 | 表达式 AST 白名单 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.10 -->` |
| 5.11 | 对账匹配与补发门禁 | [design-algorithm.md](design-algorithm.md) | `<!-- §5.11 -->` |
| 6 | 横切机制 | [design-architecture.md](design-architecture.md) | `<!-- §6 -->` |
| 6.1 | 双账号会话 | [design-architecture.md](design-architecture.md) | `<!-- §6.1 -->` |
| 6.2 | 两级缓存（含 `ad:position` 预留） | [design-architecture.md](design-architecture.md) | `<!-- §6.2 -->` |
| 6.3 | 限流 | [design-architecture.md](design-architecture.md) | `<!-- §6.3 -->` |
| 6.4 | Outbox | [design-architecture.md](design-architecture.md) | `<!-- §6.4 -->` |
| 6.5 | 审计 AOP | [design-architecture.md](design-architecture.md) | `<!-- §6.5 -->` |
| 6.6 | traceId / 日志 | [design-architecture.md](design-architecture.md) | `<!-- §6.6 -->` |
| 6.7 | 调度治理 | [design-architecture.md](design-architecture.md) | `<!-- §6.7 -->` |
| 6.8 | Redis 降级矩阵 | [design-architecture.md](design-architecture.md) | `<!-- §6.8 -->` |
| 6.9 | Flyway 策略 | [design-architecture.md](design-architecture.md) | `<!-- §6.9 -->` |
| 7 | 测试策略 | [design-algorithm.md](design-algorithm.md) | `<!-- §7 -->` |
| 7.1 | 分层与门禁 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.1 -->` |
| 7.2 | 测试基础设施 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.2 -->` |
| 7.3 | 66 条属性 → 测试类 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.3 -->` |
| 7.4 | 并发不变量 C-1~C-12 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.4 -->` |
| 7.5 | 步骤引擎 24 场景 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.5 -->` |
| 7.6 | 架构红线测试 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.6 -->` |
| 7.7 | 恶意样本库 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.7 -->` |
| 7.8 | 性能门槛 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.8 -->` |
| 7.9 | 前端测试 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.9 -->` |
| 7.10 | 覆盖率门禁 | [design-algorithm.md](design-algorithm.md) | `<!-- §7.10 -->` |
| 8 | 需求追溯与终检 | [design.md](design.md) | `<!-- §8 -->` |
| 8.1 | 需求 ↔ 章节 | [design.md](design.md) | `<!-- §8.1 -->` |
| 8.2 | 口径 | [design.md](design.md) | `<!-- §8.2 -->` |
| 8.3 | 设计决策 | [design.md](design.md) | `<!-- §8.3 -->` |
| 8.4 | 任务编排输入 | [design.md](design.md) | `<!-- §8.4 -->` |

---

<!-- §1 -->
## 1. 概述

<!-- §1.1 -->
### 1.1 设计目标

将 requirements.md v3.9 落实为可实施、可验证的技术方案。业务语义自建；通用能力使用开源组件。运行时：**JDK 26 + Spring Boot 4**。组件坐标由任务 1 写入 `dependency-matrix.md`；无官方 starter 时手动装配。

<!-- §1.2 -->
### 1.2 设计原则（全部源于需求，编号可追溯）

| # | 原则 | 需求依据 |
|---|------|---------|
| P1 | **模块化单体内核 + 双应用部署**：业务领域以 Maven 模块物理隔离，admin-app（管理面）与 portal-app（用户面）独立部署、独立扩容、独立发布；portal-app 无状态可水平扩展 | NFR 可扩展性 3 |
| P2 | **依赖单向 + 跨域仅两种形式**：领域之间禁止互访数据表；同步调用走 `platform-contract` 端口接口（同库同事务），异步通知走领域事件（Outbox） | NFR 可扩展性 2 |
| P3 | **同事务发放（架构红线）**：portal-app 必须装配 task 与 reward（积分在 reward 内），保证 REWARD 发放与步骤推进同 JVM 同事务；拆分为独立进程属重大架构变更，需先行评审 Outbox/Saga 替代方案 | NFR 可扩展性 5、R14.5、R18.1、feasibility §3.4 |
| P4 | **幂等优先**：一切用户侧并发路径（领取、推进、发奖、领取奖品、签到）以数据库唯一约束 + 幂等响应为第一道防线，行级乐观锁收敛并发，分布式锁仅用于无唯一约束可依托的场景（奖品领取、调度恰一） | R13.7、R14.3、R14.7、R18.4、NFR 可维护性 4 |
| P5 | **快照隔离**：任务/签到配置发布即固化不可变版本快照，实例绑定快照执行，后续修改只影响新版本 | R12.1–R12.6、R21.1 |
| P6 | **缓存与调度全节点一致**：两级缓存（Caffeine L1 + Redis L2）+ 广播失效；变更后全实例 1 秒内一致；全部定时任务分布式锁恰一执行 | R7.4、R8.3、NFR 可用性 3 |
| P7 | **异步边界经 Outbox**：审计、服务端埋点事件与业务同事务写 `sys_outbox` 后异步投递，业务回滚则事件不投递；埋点链路故障不影响任何业务功能 | R10.3、R28.8、R28.9 |
| P8 | **辅助链路可降级且留痕**：Redis 故障时会话拒绝（不静默放行）；限流器故障放行并告警；缓存故障回源；风控异常按配置降级（默认放行 + 降级事件 + 告警） | NFR 可用性 4、R26.3 |
| P9 | **全局契约统一**：统一响应体、HTTP 状态码映射、分页契约、时间基线（UTC 存储 / ISO-8601 输出 / 业务自然日 UTC+8）、业务错误码格式 `<域>.<场景>.<原因>` 一律按 requirements 附录 C 执行，本设计不重复定义、不得偏离 | 附录 C |
| P10 | **安全无后门**：不存在可关闭鉴权的全局开关；密钥仅环境变量注入；生产 profile 编译期排除调试免登与 mock 能力 | NFR 安全 2/3、R31.6 |

<!-- §1.3 -->
### 1.3 代码仓库布局

```text
marketing-task-platform/
├── server/                          # 后端 Maven 多模块（JDK 26 + Spring Boot 4）
│   ├── pom.xml
│   ├── platform-kernel/
│   ├── platform-contract/
│   ├── platform-db/
│   ├── platform-infra/              # Redis 会话/缓存/锁/限流 + Outbox
│   ├── domain-identity/
│   ├── domain-task/
│   ├── domain-reward/               # 奖品、发放、积分
│   ├── domain-risk/
│   ├── domain-tracking/
│   ├── admin-app/
│   └── portal-app/
│   # P1：domain-signin / domain-activity / domain-ad
├── web/                             # 前端 pnpm workspace
│   ├── apps/admin/                  # 管理后台（vue-pure-admin-thin 骨架 + Ant Design Vue）
│   ├── apps/client/                 # 门户 H5（Vant 4）
│   └── packages/shared/             # 两端共享（类型生成产物/工具）
└── .kiro/specs/platform-v2/         # 规格文档（本设计所在）
```

---

<!-- §8 -->
## 8. 附录：需求追溯与终检

> 需求权威 = [requirements.md](requirements.md) v3.9；实现权威 = 本设计（总册 + 分册）。条款互斥时停笔写冲突，禁止静默跟 design。

<!-- §8.1 -->
### 8.1 需求编号 ↔ 设计章节映射表

> 落点列出该需求的**主设计位置**（Schema/契约/算法/横切/测试五类各取最强落点）。文件见 §0.2。

| 需求 | Schema（§3） | API 契约（§4） | 算法（§5） | 横切/其他 | 测试（§7） |
|------|-------------|---------------|-----------|----------|-----------|
| R1 后台登录 | §3.2 `sys_admin_user` | §4.2 登录/验证码/改密 | — | §6.1 会话、§6.3 限流 | §7.3 R1.1–R1.3 |
| R2 RBAC | §3.2 RBAC 五表 | §4.2 角色/权限/菜单树 | — | §6.2 `rbac:permission` 缓存、RL-10 | §7.3 R2.1–R2.2 |
| R3 后台用户管理 | §3.2 `sys_admin_user` | §4.2 用户 CRUD/重置 | — | — | §7.3 R3.1 |
| R4 门户注册登录 | §3.2 `sys_portal_user` | §4.9 auth 端点组 | — | §6.1 双 StpLogic | §7.3 R4.1、§7.9 |
| R5 门户用户管理 | §3.2 `sys_portal_user` | §4.2 portal-user 端点组 | — | §2.2.3 UserAttributePort（含 accountStatus） | §7.3 R5.1 |
| R6 在线会话 | §3.10 Sa-Token 会话结构 | §4.2 会话列表/踢下线 | — | §6.1 | §7.3 R6.1（双实例） |
| R7 字典 | §3.2 `sys_dict_*` | §4.3 + §4.9 dict 只读 | — | §6.2 缓存 | §7.3 R7.1 |
| R8 系统配置 | §3.2 `sys_config` | §4.3 | — | §6.2、RL-11 | §7.3 R8.1 |
| R9 缓存管理 | §3.10 命名空间结构 | §4.3 概况/清理（session 禁 evict） | — | §6.2（`ad:position` P0 仅占位） | §7.3 R9.1 |
| R10 审计 | §3.2 `sys_audit_log`（operator_id 可空） | §4.3 审计查询 | — | §6.5 AOP（仅 `/admin/**`）、§6.4 Outbox、RL-12 | §7.3 R10.1 |
| R11 任务定义 | §3.3 定义/步骤/分支/动作/互斥/人群包 | §4.4 聚合保存/表达式校验/复制 | §5.2 分支、§5.3 灰度、§5.10 表达式 | — | §7.3 R11.1–R11.2、§7.7 |
| R12 发布与快照 | §3.3 `task_version_snapshot`（D-01 修订草稿） | §4.4 发布/批量/版本对比 | — | §6.7 调度 1 | §7.3 R12.1–R12.3 |
| R13 可见性与领取 | §3.3 `task_instance` | §4.9 列表/详情/start | §5.3、§5.5 领取（lockAndGet） | §6.2 `task:published-index`、§2.2.3 D-12 | §7.3 R13.1–R13.4、§7.4 C-1 |
| R14 步骤引擎 | §3.3 实例/步骤/进度去重表 | §4.8、§4.9 推进入口 | §5.1 四入口/级联 | §6.7 调度 2 | §7.3 R14.1–R14.4、§7.4 C-2/C-3、§7.5 |
| R15 internal 回调 | §3.2.8 调用方登记 + §3.10 `nonce:` 防重放 | §4.8 HMAC 全规范 + §4.2 internal-apps 管理 | §5.1 callback/progress 入口 | §6.3 appId 限流 | §7.3 R15.1、§7.7 攻击样本 |
| R16 平台动作 | §3.3 动作两表 | §4.9 详情合并渲染 | 回退链见 §4.9.2 注记（纯函数，无独立 §5 伪代码） | §2.9 `X-Client-Platform` | §7.3 R16.1–16.4 |
| R17 奖品与库存 | §3.4 `rwd_prize_category`/`rwd_prize`/`rwd_stock_log` | §4.5 分类/奖品 CRUD | §5.7 原子扣减/限领、§5.6.3 成本快照 | — | §7.3 R17.1–R17.3、§7.4 C-4/C-5 |
| R18 发放 | §3.4 `rwd_grant_record` | §4.5 记录查询/重试/履约、§4.8 履约回调 | §5.6 领取七态 + §5.6.3 履约 | §6.7 调度 3/10 | §7.3 R18.1–R18.2、§7.4 C-6 |
| R19 领取与过期 | §3.4 | §4.9 我的奖品/领取 | §5.6 CLAIMING 流转 | §6.7 调度 4/5、§6.8 锁退化 | §7.3 R19.1–R19.2、§7.4 C-7 |
| R20 积分 | §3.5 `pnt_*` | §4.5 调整、§4.9 余额/流水 | §5.8 原子变动/过期 | §6.7 调度 6 | §7.3 R20.1–R20.2、§7.4 C-8 |
| R21 签到（P1） | §3.11 `sgn_*` | P1 扩 §4.9 signin 端点组 | 连签/补签/梯度见 §3.11；发放复用 §5.6（SIGNIN_DAY） | — | §7.3 R21.1、§7.4 C-9 |
| R22 活动（P1） | §3.11 `act_*` | P1 扩 §4 活动管理 | 限量 CAS 见 §3.11；**不**复用 §5.5（那是任务领取链） | — | §7.3 R22.1、§7.4 C-10 |
| R23 看板（P1） | §3.11 `mtr_*` 聚合表 | P1 扩 §4.4 指标端点 | 按日幂等 upsert 见 §3.11 | §6.7 增聚合调度 | §7.3 R23.1 |
| R24 模拟（P1） | §3.11 模拟器 + 各表 `simulated` | P1 扩 §4 `/admin/simulate/**` | §3.11 进程内 + `GrantContext.simulated=true` | §2.4.1 admin-app 全域装配 | §7.3 R24.1 |
| R25 名单 | §3.6 `risk_list_item` | §4.6 名单管理/导入 | §5.9 判定链（黑优先） | §3.10 `risk:list:` 投影；[feasibility-risk.md](feasibility-risk.md) | §7.3 R25.1–R25.2、§7.4 C-12 |
| R26 规则引擎 | §3.6 `risk_rule_config` | §4.6 规则配置 | §5.9 R-a~R-f（R-e = GRANT+elapsedSeconds，非滑窗、不写 risk:cnt，D-09） | §3.10 `risk:cnt:`；[feasibility-risk.md](feasibility-risk.md) | §7.3 R26.1–R26.4 |
| R27 命中与处置 | §3.6 `risk_hit_log`/`risk_handle_log` | §4.6 命中查询/处置 | — | RL-12 | §7.3 R27.1 |
| R28 事件模型 | §3.7 `evt_event_log` | §4.9 track 批量上报 | — | §6.4 Outbox、RL-12 | §7.3 R28.1–R28.3 |
| R29 元数据与调试 | §3.7.2 evt_event_metadata（元数据种子=附录 D） | §4.7 元数据/调试查询 | — | — | §7.3 R29.1 |
| R30 广告位（P1） | §3.11 `ad_*` | P1 扩 §4.9 `/api/common/ad/**` | 频控/输出见 §3.11（R30.5） | §6.2 `ad:position` **P0 占位 / 任务 48 接线** | §7.3 R30.1–R30.2、§7.4 C-11 |
| R31 部署基线 | §6.9 Flyway（P0）；P1 表骨架 §3.11 | — | — | §2.6 部署视图、§2.7 版本策略 | §7.8 R31.1 CI 冒烟 |
| R32 门户会话体验 | — | §4.9 匿名边界封闭清单 | — | §6.1 401 原因码（D-02） | §7.3 R32.1、§7.9 E2E |
| R33 个人中心 | — | §4.9 档案/退出 | — | — | §7.3 R33.1、§7.9 |
| R34 任务浏览体验 | — | §4.9 列表/详情 + 交互注记表 | — | — | §7.3 R34.1、§7.9 |
| R35 奖品积分体验 | — | §4.9 + 状态-按钮映射注记 | — | — | §7.3 R35.1（前端）、§7.9 |
| R36 签到体验（P1） | P1 扩 §3 | P1 扩 §4.9 | 连签计算（P1 细化，规则见 R21.2） | — | §7.3 R36.1（前端） |
| R37 成本与对账 | §3.4.4 `rwd_recon_*` + `recon_action_policy` | §4.5 spend/recon/review | §5.11 匹配 + 门禁 + 补发关原单（D-10） | [feasibility-recon.md](feasibility-recon.md)；附录 A 自动补发闸 | §7.3 R37.1–R37.3 |

NFR 与附录落点：

| 条目 | 设计落点 |
|------|---------|
| NFR 性能 1–8 | §7.8 逐条 k6 门槛映射；容量假设另见 §2.7 与 feasibility-step-engine §3.7 |
| NFR 可用性 1–4 | §2.4.2 运行形态、§6.7 调度恰一、§6.8 降级矩阵 |
| NFR 可观测性 1–4 | §6.6 traceId/JSON 日志、§6.2 缓存指标、§6.7 任务指标、§2.7 Micrometer 栈 |
| NFR 安全 1–8 | §6.1 Cookie/CSRF、RL-10 无鉴权后门、§4.8 HMAC、§6.5 脱敏、§7.7 恶意样本 |
| NFR 可扩展性 1–5 | §2.2 模块与依赖规则、RL-01~05、§2.3 命名空间 |
| NFR 可维护性 1–4 | RL-09 迁移、RL-11 配置收口、§7 全章（属性/并发/架构测试） |
| NFR 数据与合规 | §6.7 调度 8/9（保留清理）、§6.5 脱敏、R34.6 空态不暴露风控 |
| 附录 A 配置键（52） | §3.2 `sys_config` 种子（V1 全量预置）、§6.2 缓存、各消费点 |
| 附录 B 权限码 | §4 各 admin 端点权限码列（与附录 B 对齐，含 category / recon / stock-replenish / prize:enable / record:fulfill） |
| 附录 C 全局契约 | §2.9 全局约定、§4.1 契约细则 |
| 附录 D 事件编码 | §3.7 元数据种子、§6.4 路由表、§4.9 track、§7.9 E2E 埋点断言 |

<!-- §8.2 -->
### 8.2 口径

需求定义做什么，本设计定义怎么做。

| 项 | 值 |
|----|-----|
| 表 | 39（sys 12 + task 12 + rwd 7 + pnt 2 + risk 4 + evt 2） |
| 正确性属性 | 66（§7.3）；任务级对照见 [verification-matrix.md](../../docs/verification-matrix.md) |
| 场景矩阵 | 步骤 24 + 对账 28 + 风控 30（三份 feasibility） |
| 配置键 | 附录 A 52 键 |
| 服务端事件 | 附录 D 与 §6.4 路由表 |
| 缓存命名空间 | R9.1 与 §6.2；`ad:position` P0 占位、任务 48 接线 |
| 跨域端口 | RewardPort / UserAttributePort / RiskCheckPort |
| 发放记录 | 领取七态 + 履约四态 + 成本快照；分类可扩展 |
| 对账 | 分类+账单日批次；MATCHED / PLATFORM_ONLY / CHANNEL_ONLY / AMOUNT_MISMATCH；补发门禁 D-10 |

<!-- §8.3 -->
### 8.3 设计决策

D-01 修订草稿存储、D-02 401 原因码、D-03 时钟注入、D-04 覆盖率门禁、D-05 事件路由表、D-06 `identity:user-attr`、D-07 `sys_internal_app` + `evt_event_metadata`、D-08 `track.disabled-event-policy`、D-09 R-e elapsedSeconds + 同请求新建跳过、D-10 对账补发门禁（核渠 + `recon_action_policy` + 补发永不自动 + **成功关原单**）、D-11 Outbox 按 `producer` 分 Relay（声明了消费方向则空消费者不得 DELETE）、D-12 `UserAttributePort.lockAndGet` + `accountStatus`（领取/发放禁止直查用户表）、D-13 R5.6 只读门面（`RewardPort.userSummary` / `RiskCheckPort.userSummary` / `TaskReadPort.instanceCounts`；写路径仍三端口）。

<!-- §8.4 -->
### 8.4 任务编排输入

requirements.md v3.9 + 本设计 §2–§7 + component-selection.md §6 + 三份 feasibility §2 + [verification-matrix.md](../../docs/verification-matrix.md)。

1. 每个任务对应模块、需求编号、设计锚点；验收 = 需求验收标准 + §7 测试类（一表见 verification-matrix）。
2. 顺序：Spike → 骨架 → kernel/contract/db → 风控埋点 → identity → task → reward → 双前端（37/38 已拆号）→ 集成/部署 → P1。
3. 测试与实现同一任务交付。
4. P1 编组独立于 P0。`ad:position` 在 P0 只占位。
