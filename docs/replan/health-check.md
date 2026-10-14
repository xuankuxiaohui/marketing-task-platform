# HC-1 全项目体检（2026-10-13）

**Status:** 只出文档，**不改代码**。非 RC；容量冻结；R31 两勾仍空。
**Base:** master `60d5de7`（#211 UX-5 合后）。
**Scope（架构+产品批，2026-10-13 ~12:35 CST）：** 测试缺口 / 死代码 / 依赖 / 可读性 + 产品 4 条 UX 核实。每项给严重度 P1–P3 与改动量 S/M/L，末尾 Top5（UX ≤ 2）。合入后由架构从 Top5 挑下一刀。

方法：仓库内静态盘点（文件/行数、类名引用 grep、视图代码走读）。没有跑覆盖率工具，数字是结构性信号，不是覆盖率百分比。

## 1. 测试缺口

服务端 main/test 规模（文件数/行数）：

| 模块 | main | test | 备注 |
|---|---|---|---|
| domain-task | 187 / 11545 | 68 / 9183 | 热路径最重 |
| domain-identity | 188 / 9671 | 86 / 7016 | |
| domain-reward | 144 / 8078 | 58 / 6075 | |
| domain-risk | 75 / 3623 | 38 / 3213 | |
| domain-activity | 56 / 2958 | 16 / 1466 | 测试偏薄 |
| domain-ad | 53 / 2446 | 7 / 917 | **测试最薄**（7 个测试文件） |
| domain-signin | 56 / 2232 | 15 / 1470 | |
| domain-tracking | 55 / 2163 | 25 / 1940 | |
| platform-infra | 44 / 2483 | 32 / 1566 | |
| platform-kernel | 24 / 768 | 19 / 921 | |
| platform-contract | 29 / 449 | 3 / 267 | DTO 为主，可接受 |
| admin-app / portal-app | 43 / 1696 · 5 / 206 | 64 / 3402 · 13 / 1806 | 组装层，覆盖足 |

| # | 发现 | 证据 | 级 | 量 |
|---|---|---|---|---|
| T1 | 6 个 Controller 无任何测试引用 | `AdAdminController`(144 行)、`ReconAdminController`(107)、`DictEntryAdminController`(59)、`RiskRuleAdminController`(52)、`AdPortalController`(50)、`SpendAdminController`(37) | P2 | M |
| T2 | domain-ad 测试密度最低（main 2446 行 / 7 个测试文件） | 广告后台 CRUD + 门户投放仅少量用例 | P2 | M |
| T3 | `FulfillmentService`(400 行) 无专属单测，仅经 9 处间接引用覆盖 | 发放履约是资金相关路径 | P2 | S |
| T4 | 其余前 12 大服务类均有专属 `*Test` | TaskPublish/TaskPortal/StepEngine/Grant/… | — | — |
| T5 | 前端：client 80 源文件 / 52 spec，admin 89 / 52，e2e 4 条旅程 spec | 覆盖面合理；见 UX 节缺的失败态用例 | P3 | S |

## 2. 死代码 / 仓库杂物

| # | 发现 | 证据 | 级 | 量 |
|---|---|---|---|---|
| D1 | 服务端未发现「类名零引用」的 main 类（排除 Controller/Config/Listener/Job 等框架入口后） | 全量类名 grep | — | — |
| D2 | 前端 client/admin/shared 未发现零引用源文件 | 文件名 import grep | — | — |
| D3 | 根目录 `spike/`（8 个技术验证子目录，46 个跟踪文件）自 2026-08-19 后未动，不参与构建 | `git log -- spike` | P3 | S | **done 2026-10-14** → `docs/archive/spike/`；根留 `spike/README.md` 指针 |
| D4 | 根目录 `com/` 里有本地编译残留 `.class`（未跟踪，已被 `*.class` 忽略） | 仅本机杂物，不进仓库 | P3 | S |
| D5 | `docs/capacity`(35) + `deploy/drills`(56) 共 90+ 份容量/演练 md，多为 FAIL 证据 | **2026-10-13 Top4 已归档** → `docs/archive/`；活页 `docs/capacity/STATUS.md` + `ARCHIVE-INDEX.md` | P2 | M（归档，非删） **done** |

## 3. 依赖

| # | 发现 | 证据 | 级 | 量 |
|---|---|---|---|---|
| DP1 | 主栈版本新：Spring Boot 4.1.0、Java 26、Redisson 4.6.1、Sa-Token 1.45.0、MyBatis-Plus 3.5.17、springdoc 3.1.0；前端 Vue 3.5 / Vite 7 / Vitest 3 / TS 5.9 | `server/pom.xml`、`web/*/package.json` | — | — |
| DP2 | Hutool（core+crypto）只在 domain-identity 的 2 个 main 文件里用 | 可换 JDK 自带实现，减一个大依赖 | P3 | S |
| DP3 | Aviator 只在 domain-task 1 个文件里用（表达式引擎） | 用法集中，保留合理；记一笔版本跟踪 | P3 | — |
| DP4 | 未发现重复的同类库 | — | — | — |

## 4. 可读性

| # | 发现 | 证据 | 级 | 量 |
|---|---|---|---|---|
| R1 | domain-task 应用服务过胖：`TaskPublishAppService` 848 行、`TaskPortalAppService` 830、`StepEngine` 677、`TaskDefinitionAppService` 563 | 容量补丁层层叠在门户热路径（hold-lock、instances cache、skip-renew） | P1 | L（只能按方法簇小切） |
| R2 | domain-reward：`GrantAppService` 677、`ReconAppService` 505、`PrizeAppService` 418、`FulfillmentService` 400 | 发放/对账混在应用层 | P2 | L |
| R3 | `ActivityAdminAppService` 546、`AdAdminAppService` 389 | 后台 CRUD + 校验混写 | P3 | M |
| R4 | 模块边界见 `docs/replan/module-boundaries.md`（BND-2 观察中） | 不重复 | — | — |

## 5. 产品 4 条 UX 核实

| # | 产品假设 | 核实结论 | 证据 | 级 | 量 |
|---|---|---|---|---|---|
| U1 | 任务/奖品列表失败态（P1） | **部分成立。** 门户「我的任务/我的奖品」、首页活动、活动中心已有失败态+重试。**缺口 1：** 活动详情页（任务列表所在）`fetchActivityDetail` 失败时先弹 `response.message` 原文，再落到「暂无进行中的活动」空态，失败看起来像没活动，且没有重试。**缺口 2：** Admin 任务定义/奖品等列表失败只出顶部提示条，表格不清空、空态仍显示「暂无数据」，`runWithLoading` 无 catch，网络异常不会落成页面提示 | `client/src/views/activity/index.vue:94-98,138`；`admin/src/views/reward/prize/index.vue:80-84`；`admin/src/composables/runWithLoading.ts` | P1 | S（门户）/ M（Admin 通用） |
| U2 | Admin 风控页英文码（P2S） | **成立。** 规则表列头写死 `code`；「动作」列和编辑下拉直接显示 `REJECT / SILENT_REJECT / MARK`；风控案例页 `actionResult`、名单页 `listType` 也是原值 | `admin/src/views/risk/rule/index.vue:99,116,151`；`risk/case/index.vue:169`；`risk/list-item/index.vue:218` | P2 | S |
| U3 | 门户 toast 技术码（P1） | **基本已收口，剩 1 处。** UX-1/UX-2 的 `portal-error.ts` 已把登录/领取拒绝码映射成中文，未知码兜底成网络错误。漏网：活动详情页直接 `showFailToast(response.message)`，绕过映射，后端回码时会露原文 | `client/src/utils/portal-error.ts`；`client/src/views/activity/index.vue:96` | P1 | S（与 U1 门户缺口同一处） |
| U4 | 空态引导（P3） | **部分成立。** 「我的任务/我的奖品」空态已有「去做任务」按钮；首页活动空态、活动详情空态只有一句文字，没有下一步 | `client/src/views/home/index.vue:378-382`；`client/src/views/activity/index.vue:138` | P3 | S |

## 6. Top5（建议顺序，架构挑刀）

| 序 | 项 | 级 | 量 | 为什么 |
|---|---|---|---|---|
| 1 | **U1+U3 门户活动详情失败态**：失败走 `showPortalFail` 映射，失败与空态分开，加重试按钮，补 spec | P1 | S | 一处修两条产品项，门户主旅程 |
| 2 | **U2 Admin 风控页中文化**：列头 + 动作/案例结果/名单类型加中文标签（复用 `status-label.ts` 模式） | P2 | S | 产品点名、改动小、无后端变更 |
| 3 | **T1+T3 补测**：6 个 Controller 的 WebMvc 切片测试 + `FulfillmentService` 单测 | P2 | M | 零行为改动，给后续可读性小切打底 |
| 4 | **D5 容量文档归档**：`docs/capacity`、`deploy/drills` 的 FAIL 证据挪进 `docs/archive/`，留一份活状态页 | P2 | M | **2026-10-13 Top4 done**（本刀）；与 replan P0 文档分诊合并，只动文档 |
| 5 | **R1 第一刀**：从 `TaskPortalAppService` 抽一个方法簇（只搬不改语义，前后测试不变） | P1 | L→S 切 | 热路径可读性最大债；须在 #3 之后、架构定切口 |

UX 项在 Top5 中占 2 条（#1、#2），符合「Top5 UX ≤ 2」。

## 7. 明确不做
- 不改代码（本 PR 只有本文件）
- 不排容量刀、不放宽 DEC-006 Δ 或 k6 门槛
- 不整站 UI 重写、不大重构
