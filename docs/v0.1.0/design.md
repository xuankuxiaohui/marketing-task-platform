# v0.1.0 设计文档

## 背景

营销自动化任务平台用于让运营配置任务，让用户在 C 端完成任务并领取奖励。v0.1.0 是 Sprint 1 MVP 骨架，重点不是做完整运营系统，而是把最小闭环跑通：

```text
运营配置任务 → 发布任务 → 用户列表可见 → 创建用户实例 → 完成步骤 → 发奖记录
```

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17, Spring Boot 3.5.18 |
| ORM | MyBatis-Plus 3.5.7 |
| 数据库 | MySQL 8 |
| 迁移 | Flyway |
| 表达式引擎 | QLExpress 3.3.2 |
| API 文档 | springdoc-openapi 2.6.0 |
| Admin 前端 | Vue 3, TypeScript, Vite, Element-Plus, Pinia, Vue Router |
| Monorepo | pnpm workspace，当前机器验证使用 npm 兜底 |

## 模块边界

```text
backend/
  controller/admin      管理后台 API
  controller/client     C 端用户 API
  controller/internal   业务系统回调 API，占位到 Sprint 2
  service/filter        QLExpress 过滤器安全边界
  service/cycle         周期到 cycle_key 的解析
  service/step          步骤推进与自动级联
  service/reward        发奖接口，v0.1.0 使用日志实现
  service/task          任务查询、发布、实例创建
  mapper                MyBatis-Plus Mapper
  domain                实体、枚举、DTO

admin-web/
  api                   axios 封装和 API 调用
  router                页面路由
  stores                Mock UserContext
  views                 任务、实例、Mock 用户上下文页面
```

## 领域模型

```text
task ──┬── task_step ── task_step_platform
       ├── task_filter
       └── task_platform

user_task_instance ── user_task_step_progress
```

### 核心对象

| 对象 | 作用 |
|---|---|
| `task` | 任务主体，保存名称、周期、状态、版本、互斥组等配置 |
| `task_step` | 任务步骤，支持 CLICK、CALLBACK、PROGRESS、REWARD、PASSIVE |
| `task_filter` | 任务展示过滤器，保存 QLExpress 表达式 |
| `task_platform` | 任务级端入口配置 |
| `task_step_platform` | 步骤级端特化配置，例如按钮文字和跳转目标 |
| `user_task_instance` | 用户在某个周期下的任务实例，按 `(user_id, task_id, cycle_key)` 唯一 |
| `user_task_step_progress` | 用户实例中每个步骤的完成进度 |

## API 命名空间

| 命名空间 | 使用方 | 当前状态 |
|---|---|---|
| `/api/admin/**` | 管理后台 | 已提供任务分页、任务保存、发布、下线、实例分页、表达式校验 |
| `/api/client/**` | C 端 | 已提供任务列表、详情/创建实例、显式开始、CLICK 推进 |
| `/api/internal/**` | 业务系统 | 已提供回调占位接口，返回 501 |

## UserContext

v0.1.0 不建设用户中心。本系统通过 Header 接收外部用户上下文，由 `UserContextInterceptor` 解析后写入 `UserContextHolder`。

| Header | 含义 |
|---|---|
| `X-User-Id` | 外部用户 ID |
| `X-User-Province` | 省份，例如 BJ |
| `X-User-Role` | 用户角色 |
| `X-User-Tags` | 逗号分隔标签 |
| `X-User-Org-Id` | 组织 ID |
| `X-User-Level` | 用户等级，整数 |
| `X-Platform` | 平台，默认 WEB |

生产环境应替换为 JWT 解析、网关注入或 RPC 查询外部用户中心。前端可写 Header 只用于 MVP 联调。

## 任务过滤器

`FilterExpressionEngine` 是过滤器唯一入口。表达式必须经过安全校验和白名单函数执行。

### 安全边界

| 规则 | 当前实现 |
|---|---|
| 长度限制 | 最大 1024 字符 |
| 禁用关键字 | `System`、`Runtime`、`Process`、`Thread`、`Class.forName`、`import`、`new`、`exec`、`eval` |
| 执行超时 | 100ms，超时返回 false |
| 上下文暴露 | 使用 ThreadLocal 保存 `UserContext`，表达式只能调用白名单函数 |

### 白名单函数

| 函数 | 作用 | 当前实现 |
|---|---|---|
| `inProvince(valueOrList)` | 省份匹配 | 已实现 |
| `hasTag(tag)` | 标签匹配 | 已实现 |
| `hasAnyTag(tags)` | 标签交集匹配 | 已实现 |
| `roleEquals(role)` | 角色相等 | 已实现 |
| `roleIn(roles)` | 角色列表匹配 | 已实现 |
| `inAllowlist(name)` | 名单白名单 | v0.1.0 返回 true，占位 |
| `notInDenylist(name)` | 名单黑名单 | v0.1.0 返回 true，占位 |
| `orgEquals(orgId)` | 组织相等 | 已实现 |
| `orgIn(orgIds)` | 组织列表匹配 | 已实现 |
| `levelGte(level)` | 等级大于等于 | 已实现 |
| `levelEq(level)` | 等级相等 | 已实现 |

## 周期与 cycle_key

`CycleKeyResolver` 将任务周期转换为用户实例唯一键的一部分。

| period_type | cycle_key |
|---|---|
| `ONCE` | 固定 `ONCE` |
| `DAILY` | 当前时间 `yyyyMMdd` |
| `MONTHLY` | 当前时间 `yyyyMM` |
| `CRON` | 当前时间 `yyyyMMddHHmm`，v0.1.0 为占位策略 |
| `SPECIAL` | `task.special_cycle_key` |

`user_task_instance` 使用唯一键 `(user_id, task_id, cycle_key)` 防止同周期重复创建。

## 步骤推进

`StepAdvanceEngine` 负责实例进入、CLICK 推进和 PASSIVE / REWARD 自动级联。

```text
创建实例 current_step_seq=1
  ↓
如果当前步骤是 PASSIVE：自动完成，进入下一步
  ↓
如果当前步骤是 CLICK：等待用户 click API
  ↓
CLICK 完成后继续级联
  ↓
如果下一步是 REWARD：调用 RewardService，实例状态变 REWARDED
```

### 当前支持

| StepType | 状态 |
|---|---|
| PASSIVE | 自动完成 |
| CLICK | `POST /api/client/task/{taskId}/step/{stepId}/click` 推进 |
| REWARD | 自动调用 `LogRewardService` |
| CALLBACK | Sprint 2 实现 |
| PROGRESS | Sprint 2 实现 |

## 任务版本

`TaskService.publish()` 发布时将 `task.status` 设置为 `PUBLISHED`，并将 `task.version + 1`。创建用户实例时，`user_task_instance.task_version` 会记录当时的版本。

当前 v0.1.0 已存储版本快照，但还没有实现“按历史版本读取旧步骤配置”的完整机制。后续需要增加任务配置快照表，或在 `task_step` 中引入版本字段。

## MVP 流程

```text
1. Admin 创建 task 并保存为 DRAFT
2. Admin 发布 task，状态变 PUBLISHED，version + 1
3. Client list 读取已发布任务，按时间窗和过滤器筛选
4. Client detail/start 创建 user_task_instance
5. StepAdvanceEngine 自动推进 PASSIVE / REWARD
6. Client click 推进 CLICK 步骤
7. LogRewardService 记录发奖日志
8. Admin instance 查询实例状态
```

## 已知限制

| 限制 | 原因 | 后续方向 |
|---|---|---|
| Admin 保存任务只保存 `task` 主体 | v0.1.0 是骨架，步骤/过滤器/端配置 API 尚未补齐 | 增加聚合保存 DTO 和子表写入 |
| CALLBACK / PROGRESS 未实现 | Sprint 1 只跑 CLICK/PASSIVE/REWARD | Sprint 2 实现 internal callback |
| allowlist / denylist 返回 true | 名单数据源未接入 | 增加名单表或 Redis 查询服务 |
| 没有真实鉴权 | MVP 使用 Header Mock UserContext | 接入网关/JWT/外部用户中心 |
| 没有配置版本快照表 | 当前只保存 instance.task_version | 增加 task_config_snapshot 或子表 version |
