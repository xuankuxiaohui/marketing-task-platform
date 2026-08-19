# 04 · 代码评审规范

> 适用范围：所有 PR / 提交前自审 / AI 生成代码的复核。  
> 读者：作者、评审人、AI 代理。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Google Engineering Practices · Code Review | https://google.github.io/eng-practices/review/ | 评审目的、标准、作者义务、评审人义务 |
| Google Engineering Practices · The CL author's guide | https://google.github.io/eng-practices/review/developer/ | 小变更、描述、自审 |
| Conventional Commits | https://www.conventionalcommits.org/ | 提交说明结构 |
| OWASP Code Review Guide | https://owasp.org/www-project-code-review-guide/ | 安全审查维度 |
| 本仓库 design §2.8、§7 | 架构红线与测试门禁 | 机械检查项 |

Google 原文把评审标准概括为：评审人应优先保证代码**整体健康**，而不是追求个人偏好的完美。本项目沿用该原则。

## 2. 评审目的（按优先级）

来自 Google Code Review 的「What to look for」，加上本项目红线：

1. 行为正确，且能追溯到 requirements / design 条款。
2. 不破坏 RL-01~12 与附录 C。
3. 复杂度与设计匹配：不引入规格明确反选的框架。
4. 测试与实现同任务（tasks.md 纪律 ①）。
5. 命名、分层、错误码符合 01 / 06 / 07 / 09。
6. 安全：注入、鉴权、密钥、脱敏、`@Audited`、会话 evict（见 05）。
7. 可观测：关键路径有日志/指标，无敏感字段。

风格问题若已由 Spotless / P3C 覆盖，**MUST NOT** 在评审里重复争论缩进。

分支命名、PR 目标与 squash 见 [08-git-workflow.md](08-git-workflow.md)。本篇只管评审与提交说明。

## 3. 变更粒度

采纳 Google CL author's guide：

1. **SHOULD** 一个 PR 只做一件可描述的事（一个任务编号或一个缺陷）。
2. **MUST NOT** 把重构、功能、依赖升级混在同一 PR，除非不可分。
3. 编组 A 的 spike **MUST** 独立于 `server/` 业务 PR。
4. Flyway 脚本与使用该列的代码可以同 PR，但必须满足两段式兼容（02 §2.3）：先加列再读，或确认尚未发生产。

## 4. 提交说明

格式采用 [Conventional Commits](https://www.conventionalcommits.org/) 1.0.0：

```text
<type>(<scope>): <summary>

<body>

Refs: task-<n>, R<x.y>, RL-<nn>
```

| type | 用途 |
|------|------|
| `feat` | 用户可见能力 |
| `fix` | 缺陷 |
| `test` | 只改测试 |
| `refactor` | 行为不变 |
| `chore` | 构建、依赖矩阵、规范文档 |
| `docs` | 仅文档 |
| `perf` | 性能 |

`scope` 用模块：`kernel` / `identity` / `task` / `reward` / `risk` / `tracking` / `admin` / `portal` / `web-admin` / `web-client` / `db` / `infra`。

摘要 **MUST** 用祈使句、不超过 72 字符。正文写清行为变化与规格编号。

```text
feat(task): add startInstance mutex check

Reject concurrent claims with task.claim.mutex-blocked when
uk(user, task, cycleKey) hits.

Refs: task-28, R13.7, design §5.5
```

## 5. 作者自审（提交前 MUST）

Google 要求作者先自审 diff。本项目清单：

- [ ] 描述写了任务号与需求号
- [ ] 本地 / CI 相关测试已跑（至少单元 + 被改模块 IT）
- [ ] 无密钥、无本地绝对路径、无 `TODO` 无主
- [ ] 新 API 已进 OpenAPI 分组，错误码已在 design §4 或走缺省三件套
- [ ] 契约变更已跑 `pnpm --filter @mkt/shared gen:api`，生成物无手工改、无未提交 diff
- [ ] 后台非 GET 已标 `@Audited`；门户 / internal 未标
- [ ] 新表/列只出现在新的 Flyway 版本
- [ ] 未跨域 import
- [ ] 未引入反选型依赖；未发明缓存命名空间
- [ ] 前端类型来自生成物，或本 PR 同时提交 `packages/shared` 生成物

## 6. 评审人检查单

### 6.1 正确性

- 实现与 design 伪代码 / 状态机是否同序？（尤其 §5.5 领取链、§5.6 发放、§5.9 风控）
- 失败路径是否回到规格中的错误码与 HTTP 状态？
- 并发：是否依赖唯一约束或 CAS，而不是「先查后写」？
- 幂等键是否与 design 一致？

### 6.2 架构

- RL-01~12 有无开口？新依赖方向？
- portal-app 是否仍装配 task + reward？
- `@Transactional` 内有无远程调用？
- 有无直读配置表 / 自建 `ObjectMapper`？
- 后台非 GET 是否 `@Audited`？门户 / internal 是否误标？
- 是否对 `identity:session` 走了 cache evict（必须走 R6 踢下线）？

### 6.3 API 与对象

- 响应是否包在 `Result`？Entity 是否泄漏？
- 时间是否 ISO-8601 带偏移？分页是否 `page`/`pageSize`/`total`/`records`？
- 后台写是否 CSRF + 权限码 + `@Audited`？门户 401 是否四码之一？

### 6.4 数据

- 已应用迁移是否被改？
- CHECK / UNIQUE / 字符集是否齐全？
- 不可变表有无 update/delete？

### 6.5 测试

- 规格写了测试策略的条款，本 PR 是否有对应测试？
- 属性测试生成器是否可复现（种子入日志）？
- 有无用 `Thread.sleep` 等待异步？

### 6.6 安全（OWASP）

- 注入（SQL、SpEL、表达式、HTML）
- 鉴权缺口、越权（IDOR：只凭路径 id 不校验归属）
- 敏感日志
- CSRF / HMAC / 验证码绕过

### 6.7 前端

- 是否手写重复类型？
- 按钮状态是否按 R35 映射？
- 是否把风控内部原因展示给 C 端（R34.6 禁止）？

## 7. 评审意见怎么写

采纳 Google：评论针对代码，说明原则与依据，给出可执行改法。

```text
# 正例
这里领取链少了 lockAndGet。design §5.5 顺序是
账号状态 → 幂等 → 可见性 → 风控 → 互斥 → 每日上限。
请改为 UserAttributePort.lockAndGet，不要查 sys_portal_user。

# 反例
这段写得不好，改一下。
```

分级：

| 标记 | 含义 |
|------|------|
| `blocking` | 必须改，否则不合并 |
| `should` | 应当改；作者可辩驳 |
| `nit` | 品味，不阻断 |
| `question` | 需要作者解释 |

评审人 **MUST NOT** 因命名口味（在规范允许范围内）阻断。作者对 `blocking` **MUST** 处理或引用规格说明为何已满足。

## 8. AI 作为作者时

1. **MUST** 在 PR / 提交说明列出触及的规范条款。
2. **MUST** 不做规格外的「顺手重构」。
3. **MUST** 对每个新公共符号给出归属模块。
4. 若发现规格冲突：停止发挥，把冲突写进说明，等待人类。禁止静默选边。
5. 禁止用生成注释吹嘘（「巧妙地」「完美解决」）。

## 9. AI 作为评审人时

按第 6 节输出结构化报告：

```markdown
## 结论
APPROVE | REQUEST_CHANGES

## blocking
- file:line — 违反 RL-03 / R13.6 — 改法

## should
- ...

## 已核对
- [ ] 附录 C 响应体
- [ ] Flyway 未改历史
- [ ] `@Audited` 机械规则
- [ ] OpenAPI 生成物
- [ ] 测试与任务测试行对应
```

**MUST** 引用文件与条款，禁止空泛「LGTM」。未读 design 对应节时，结论只能是「未完整评审」。

## 10. 合并门禁

与 design §7.1 / §7.10 对齐：

| 检查 | 级别 |
|------|------|
| 单元 + 属性 + ArchUnit | 阻断 |
| 集成测试 `*IT` | 阻断 |
| 覆盖率阈值 D-04 | 阻断 |
| Spotless 格式化 | 阻断 |
| OpenAPI 生成物无 diff | 阻断（触及 Controller / Command / Query / Response / ErrorCode / springdoc 的 PR，以及任何 `web/` PR）。命令：`pnpm --filter @mkt/shared gen:api`；产物只提交 `packages/shared`，禁止手改 |
| 开发会话两轮分开的代码评审（有问题开 issue 并直接修，不等人类点头） | 所有 PR |

资金与并发路径（领取、发奖、库存、积分、HMAC）**MUST** 走两轮分开的代码评审并修完 issue；不要等人签字。人类会叫停。
