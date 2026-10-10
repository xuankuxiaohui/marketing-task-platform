# 当前功能清单

盘点日期：2026-10-10。对照 tip `44c995dd2c2c33b8fa4d9a3883537fb901bd5fb5`（Merge #122；含 F08 文档纠偏与 #121）。依据当前工作区的 Controller、应用服务、前端路由与页面。**这是当前实现清单，不表示全部需求已经完成，也不表示每项已通过真实环境验收。** 历史旅程核验以 [2026-10-07 报告](full-flow-verification-2026-10-07.md) 为准；本盘点纠偏 CrowdPort（#102）、DEC-004（#111）、F03（#105）、F08（#120）、DEC-003 关闭等已合事实，测试数量不替代当轮执行结果。

标记口径：**已存在**表示有对应实现入口；**部分实现**表示已发现明确缺口；**基础实现**表示已有运行代码或脚本，仍需部署、故障、容量或恢复验证。未列出的需求不能据此视为已实现。

## 1. 门户

门户页面入口见 [路由](../web/apps/client/src/router/index.ts)，两端账号与会话隔离。

| 功能 | 当前实现 | 状态与边界 |
|---|---|---|
| 游客浏览 | 首页、公开任务列表与详情、活动列表与详情、活动中心；受保护操作唤起登录浮层 | 已存在；私有数据和写操作要求登录。公开卡仍按可见性规则过滤 |
| 注册与登录 | 验证码、用户名可用性、注册、登录、退出、强制改密与失效提示 | 已存在；[门户认证 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/portal/PortalAuthController.java) |
| 个人资料与会话 | 查看资料、修改昵称、修改密码；登录、退出与跨标签页切换清除旧账号数据，拦截旧请求回写 | 已存在；[会话状态](../web/apps/client/src/store/session.ts) |
| 任务浏览与执行 | 分类列表、任务详情与步骤进度、领取、点击完成、继续任务与动作跳转、放弃 | 已存在；[任务 API](../server/domain-task/src/main/java/com/mkt/task/controller/portal/TaskPortalController.java)。回调与进度上报走 internal，门户不直接调用 |
| 我的任务 | 状态与分类筛选、分页、刷新、失败重试、任务来源导航与期限展示 | 已存在；[页面](../web/apps/client/src/views/mine/MineTasksPage.vue) |
| 我的奖品 | 状态筛选、分页、手动领取、详情、领取后重新加载；任务、活动或签到来源导航 | 已存在；[奖品 API](../server/domain-reward/src/main/java/com/mkt/reward/controller/portal/PrizePortalController.java)、[详情页](../web/apps/client/src/views/mine/PrizeDetailPage.vue)。第三方实际到账见第 4 节 |
| 我的积分 | 余额、按类型筛选的流水、分页、刷新与失败重试 | 已存在；[积分 API](../server/domain-reward/src/main/java/com/mkt/reward/controller/portal/PointsPortalController.java) |
| 签到 | 进行中的签到活动、月历、今日签到、补签扣积分、连续天数与梯度奖励、重复签到幂等 | 已存在；[签到 API](../server/domain-signin/src/main/java/com/mkt/signin/controller/portal/SigninPortalController.java)。扣分、记录与奖励的事务正确性需真实数据库验证 |
| 活动 | 富文本详情、子模块导航、参与状态、参与奖励；直接用户名单、新用户、用户每日/总次数、全局每日次数、地区限制、人群码（CrowdPort） | 部分实现；[活动 API](../server/domain-activity/src/main/java/com/mkt/activity/controller/portal/ActivityPortalController.java)。[DEC-003](decisions.md#dec-003跨域只读契约与人群能力已关闭) 已关闭；CrowdPort 含 `memberOfAll`/`memberOfAny`（有界 code 列表）；活动允许名单经 `memberOfAny`；匿名广告仍不筛人群 |
| 广告 | 轮播、单图、开屏、弹窗、浮标；素材跳转、时间窗、平台与登录灰度、每日频控、弹窗冷却、浮标关闭；登录用户 `crowdId` 筛选 | 部分实现；[投放服务](../server/domain-ad/src/main/java/com/mkt/ad/application/AdPortalAppService.java)。匿名使用设备标识且不筛人群（DEC-003 维持）；登录用户 `crowdId` 经 `memberOf`；F06 聚合例外已登记门禁 |
| 客户端埋点 | 页面和操作事件、广告曝光/点击等批量上报，携带设备与平台信息 | 已存在；[批量 API](../server/domain-tracking/src/main/java/com/mkt/tracking/controller/portal/TrackBatchController.java)。接收事件数与落库批次数分别计量 |

## 2. 管理后台

后台采用服务端动态菜单；页面实现见 [views](../web/apps/admin/src/views/)，路由装配见 [dynamic.ts](../web/apps/admin/src/router/dynamic.ts)。页面可达性取决于账号权限及菜单数据；API 存在不表示对应的独立管理页面也存在。

| 功能 | 当前实现 | 状态与边界 |
|---|---|---|
| 后台认证 | 验证码、登录/退出、资料、改密码、强制改密、动态菜单 | 已存在；[认证 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/AdminAuthController.java) |
| 管理员与角色 | 管理员查询、新增、编辑、启停、重置密码、逻辑删除；角色管理、角色权限查询与分配 | 已存在；[用户 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/UserAdminController.java)、[角色 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/RoleAdminController.java) |
| 权限与在线会话 | 权限树及节点增改删 API；在线会话查询与踢出 | 部分实现；[权限 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/PermissionAdminController.java)、[会话 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/SessionAdminController.java)。无独立权限节点编辑页面 |
| 门户用户 | 查询、跨域汇总详情、档案编辑、启停、重置密码、逻辑删除 | 已存在；[门户用户 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/PortalUserAdminController.java) |
| 内部调用方 | 登记、查询、启停、密钥轮换与旧密钥双活；密钥仅创建或轮换时返回 | 已存在；[调用方 API](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/InternalAppAdminController.java) |
| 系统维护 | 字典类型与条目、配置查询/创建/修改、缓存统计与指定失效、审计查询 | 已存在；[系统页面](../web/apps/admin/src/views/system/)。敏感配置掩码；缓存失效不能用于踢会话 |
| 任务编排 | 聚合编辑与画布；PASSIVE / CLICK / CALLBACK / PROGRESS / REWARD 五类步骤；条件迁移、动作与表达式校验 | 已存在；[任务编辑页](../web/apps/admin/src/views/task/definition/edit.vue)、[步骤类型](../server/domain-task/src/main/java/com/mkt/task/domain/StepTypes.java) |
| 任务投放与版本 | 一次性、日、月、CRON、特殊周期；时间窗、排序、灰度、人群与互斥；复制、发布、定时发布/取消、下线、修订重置、批量发布/下线、版本与差异、调度失败查询 | 已存在；[定义 API](../server/domain-task/src/main/java/com/mkt/task/controller/admin/TaskDefinitionAdminController.java)。旧实例绑定领取快照；下线重算期限、重发只影响新实例、到期拒绝推进等保护已实现，验收结果另记 |
| 人群、互斥与实例 | 人群包增改删及用户导入、互斥组管理、实例查询/详情/管理放弃 | 已存在；[任务后台 API](../server/domain-task/src/main/java/com/mkt/task/controller/admin/) |
| 奖品配置与库存 | 分类与奖品管理、类型参数、启停/删除、影响确认、库存补充与日志、日/总限领及地区/等级/标签限制 | 已存在；[奖品 API](../server/domain-reward/src/main/java/com/mkt/reward/controller/admin/PrizeAdminController.java)。库存条件更新、限领和幂等需实际库验证 |
| 发放与履约操作 | 成本汇总；按记录 ID 发放重试、履约确认/重试；人工补发及受控规则绕过 | 部分实现；[操作 API](../server/domain-reward/src/main/java/com/mkt/reward/controller/admin/GrantRecordAdminController.java)。没有细粒度逐笔发放记录列表/详情；第三方渠道为 stub |
| 积分管理 | 账户与流水查询、人工调整；平台内奖励入账、消费和过期处理 | 已存在；[积分 API](../server/domain-reward/src/main/java/com/mkt/reward/controller/admin/PointsAdminController.java)。[DEC-004](decisions.md#dec-004独立事务与失败审计语义) 已于 2026-10-10 关闭（#111） |
| 对账与补偿 | 批次创建、账单导入、匹配、差异明细、审核、执行补偿动作 | 已存在；[对账 API](../server/domain-reward/src/main/java/com/mkt/reward/controller/admin/ReconAdminController.java)。导入账单和状态操作不代表真实渠道接通 |
| 风控 | 用户/IP/设备黑白名单、导入/移除、有效期；R-a～R-f 六条内置规则配置；命中查询、加黑、解黑/转白、误判标记 | 已存在；[风控 API](../server/domain-risk/src/main/java/com/mkt/risk/controller/admin/)。Redis 计数、名单优先级与故障降级须动态验证 |
| 签到运营 | 活动及梯度奖配置、发布、定时发布、下线、签到记录查询 | 已存在；[签到 API](../server/domain-signin/src/main/java/com/mkt/signin/controller/admin/SigninAdminController.java) |
| 活动运营 | 富文本与子模块配置、参与奖励及限制、发布、定时发布、下线、参与记录与统计 | 部分实现；[活动 API](../server/domain-activity/src/main/java/com/mkt/activity/controller/admin/ActivityAdminController.java)。人群配置可保存；门户侧经 CrowdPort `memberOfAny` 求值允许人群码 |
| 广告运营 | 广告位与素材管理、绑定/解绑、时间窗、平台、灰度、人群配置 | 部分实现；[广告 API](../server/domain-ad/src/main/java/com/mkt/ad/controller/admin/AdAdminController.java)。人群配置可保存；登录投放已用 `crowdId`；F06 门禁已登记 |
| 埋点治理与调试 | 元数据增改删/启停、属性描述、所有者、客户端事件查询及接收策略 | 已存在；[埋点 API](../server/domain-tracking/src/main/java/com/mkt/tracking/controller/admin/)。属性描述存在不代表全部属性约束已强制执行 |
| 统计看板 | 任务漏斗、奖励成本、风控与广告统计，定时聚合 | 已存在；[指标 API](../server/admin-app/src/main/java/com/mkt/admin/controller/admin/MetricsAdminController.java)。聚合延迟、数值口径及容量需验证 |
| 任务模拟 | 按指定用户查任务、领取、点击、回调、进度、整流程与冲正 | 已存在；[模拟 API](../server/admin-app/src/main/java/com/mkt/admin/controller/admin/SimulateAdminController.java)。模拟带 `simulated` 标识，不能替代真实用户旅程和渠道验证 |

## 3. 内部集成与运行基础

| 能力 | 当前实现 | 验收边界 |
|---|---|---|
| internal 接口 | 任务回调、任务进度上报、奖励履约回调；HMAC、时间戳、nonce、防重放与限流 | 已存在；[任务接口](../server/domain-task/src/main/java/com/mkt/task/controller/internal/TaskInternalController.java)、[奖励接口](../server/domain-reward/src/main/java/com/mkt/reward/controller/internal/RewardInternalController.java)、[签名验证](../server/domain-identity/src/main/java/com/mkt/identity/application/InternalHmacVerifier.java)。`/internal/**` 仅内网 |
| 应用与数据 | 管理/门户双应用；MySQL、集中 Flyway 迁移；门户装配 task + reward，步骤与发奖同事务 | 基础实现；空库迁移、升级、装配及真实回滚需实测；未上线数据/契约替换默认见 [DEC-005](decisions.md#dec-005未上线阶段的数据与契约替换)（**推荐草案，仍开放**）；[后端入口](../server/README.md) |
| Redis 与缓存 | DB 2 会话、锁、限流、风控计数与广告频控；L1/L2 缓存、失效消息、故障降级 | 基础实现；[infra](../server/platform-infra/src/main/java/com/mkt/infra/)。F03（#105）已合 merge-load + stale-fill 防护；跨节点失效与故障降级的演练证据仍待 |
| 事务事件 | 业务同事务 Outbox、投递、重试退避、幂等消费者、服务端事件记录 | 基础实现；[OutboxRelay](../server/platform-infra/src/main/java/com/mkt/infra/outbox/OutboxRelay.java)。[DEC-002](decisions.md#dec-002outbox-消费容量已关闭) 已关闭；**F08 已落地 #120**（持锁多批/2s 预算/fixedDelay 5s/无 MQ）；容量数字门禁仍属 DEC-006 |
| 后台调度 | 任务/签到/活动定时发布、任务到期、发奖重试恢复、领取超时、奖品过期、履约重试/超时、积分过期、审计/进度/埋点清理与统计聚合 | 基础实现；一次旅程成功不证明所有时间边界、故障和多节点调度通过 |
| 运维入口 | 健康探针、Prometheus 接口与告警配置；Docker Compose、开发启动脚本、备份/连续 binlog 归档/恢复脚本 | 基础实现；[部署目录](../deploy/)、[backup README](../deploy/backup/README.md)、[开发脚本](../scripts/README.md)。网关静态/TLS **模板**已接线（F16）；连续 shipping 与隔离 PITR 取证见 `deploy/backup/drills/`；自签 TLS/静态 Host 浏览器取证见 `deploy/nginx/drills/`；`dev.ps1 stop` 归属为 Windows host-owned（bot box 运行时 N/A，静态清单见 `scripts/drills/`）；告警触发、真实 dist + 公网证书/Secure Cookie 验证仍需独立取证 |
| 工程验证 | 单元/属性/架构测试、真实 MySQL/Redis IT、前端组件和浏览器测试、OpenAPI 类型生成、性能脚本 | 验证入口已存在；[验证映射](verification-matrix.md)、[性能说明](../perf/README.md)。执行范围和结果按当轮报告；后端→OpenAPI JSON→TS 导出门禁已由 F12 链补齐（见 PR 栈 #100） |

## 4. 已知功能缺口

以下属于当前实现事实，不能通过补写“已完成”文档消除：

| 缺口 | 代码依据与影响 |
|---|---|
| 第三方真实发奖未接通 | [FulfillmentService](../server/domain-reward/src/main/java/com/mkt/reward/application/FulfillmentService.java) 仅生成 `adapter:stub-{recordId}`；支付宝红包、微信红包、话费等类别可配置，尚不能证明真实发送或到账。平台积分可独立验收 |
| （已收口）活动/广告人群组合与 admin 跨域 SQL | [DEC-003](decisions.md#dec-003跨域只读契约与人群能力已关闭) 已关闭并落地：`CrowdPort.memberOfAll`/`memberOfAny`；活动 `memberOfAny`；广告登录 `memberOf`、匿名不筛；F06 清单 [AdminAggregateSqlInventory](../server/admin-app/src/main/java/com/mkt/admin/arch/AdminAggregateSqlInventory.java) + [ArchAdminAggregateSqlTest](../server/admin-app/src/test/java/com/mkt/admin/ArchAdminAggregateSqlTest.java) |
| 后台缺逐笔发放查询 | [发放记录页](../web/apps/admin/src/views/reward/record/index.vue) 明确提示列表端点未装配；展示成本汇总，操作要求输入记录 ID。不能描述为完整发放记录查询管理 |
| 权限节点缺独立编辑页 | [PermissionAdminController](../server/domain-identity/src/main/java/com/mkt/identity/controller/admin/PermissionAdminController.java) 提供节点管理 API；当前后台只有角色授权等页面，没有独立权限节点编辑页面 |

其他正确性、查询、缓存、事务、观测和部署风险见 [重构方案](refactoring-blueprint.md)；待明确的设计边界见 [决策记录](decisions.md)。这些风险与功能入口是否存在分别记录，不能相互替代。
