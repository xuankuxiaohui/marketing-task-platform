---
name: task-execute
description: >
  按 tasks.md 逐任务落地：开发会话全自动，开 PR 到 master 后做两轮分开的代码评审，
  有问题开 issue 并直接修。测试和运维每条新分支只做一轮，有问题只开 issue 不修。
  不要等「继续」或人类确认。Use when implementing the next platform-v2 task,
  opening a PR, or continuing from PROJECT_STATUS.md.
---

# task-execute

本仓库编码执行代理。当前任务以 `PROJECT_STATUS.md` 的「进行中 / 下一步」为准，只落地 `tasks.md` 该节仍未勾选项。硬停止、栈、模块名单、反选型只读根目录 `AGENTS.md`。

**运行方式（2026-08-19 起）**：开发会话全自动，不要等「继续」或任何人确认。人类会叫停。聊天只发摘要。

权威链禁止自行调和：

1. 行为 / 终态 / Schema / 红线 → `requirements.md` > `design.md`（先读总册 §0.2，再搜 `<!-- §x.y -->` 打开分册）
2. 本次做多远 → `tasks.md` 当前任务那一节（未勾选的不做；37/38 必须用子号）
3. 怎么写 → `docs/standards/`（按层打开，禁止通读 14 篇）
4. 验收对照 → `docs/verification-matrix.md`
5. 常驻硬停止 → `AGENTS.md`

条款互斥：立刻停笔，写清冲突双方与出处。禁止静默选边。禁止发明表 / 错误码。禁止再创建 `main`。PR 目标必须是 `master`。

## 循环（不要拆去等人）

1. 从最新 `origin/master` 开或沿用 `task/<n>-<slug>`，实现本节勾选项，跑 `cd server; mvn -q -DskipITs test`。
2. push 分支。立刻做 **一轮测试 + 一轮运维检查**。有问题只开 GitHub issue，**不要修、不要因此停下**。
3. 开 PR 到 `master`（已有则更新描述）。
4. **第一轮代码评审**（正确性 / 规格 / 鉴权会话）。有问题：开 issue → 立刻修 → commit + push。不要等。
5. **第二轮代码评审**（换清单：测试覆盖、`docs/standards/05-security.md`、部署风险）。不要复读第一轮。有问题：开 issue → 立刻修 → commit + push。
6. 两轮结束：勾选 `tasks.md`、整页重写 `PROJECT_STATUS.md`。CI 绿则 squash 合入 `master`。聊天一行摘要（任务号、PR、合没合）。
7. 立刻开下一未勾选任务。不要等「继续」。

主分支健康检查 **不要** 在本技能里做，交给每 3 小时的独立定时任务。不要在开发会话上挂 PR 事件评审 / CI 失败 / 半小时进度这类会抢轮的定时。

测试与运维失败：只记账（issue），不挡下一任务。代码评审问题：本轮必须修完再合。

## 1. 开工

读 STATUS、tasks 本节、design 锚点、矩阵该任务行、AGENTS 硬停止、git status。以代码和 git 为准。直接动手。

资金 / 会话 / 风控 / 迁移：照常实现、照常两轮评审后合入。不要因此等人。

## 2. 动手前读什么（禁止通读）

按 `AGENTS.md`「动手前读什么」那张表按层读。写路径 / 鉴权 / 审计 / 会话 / evict **只读 05**。打开本节 `_设计：_` 列出的分册锚点。

写代码前：已在 `task/<n>-<slug>` 则沿用；否则按 `docs/standards/08-git-workflow.md` 从 master 新建。禁止 `feat/`、`develop/`。工作区有未提交 diff 时禁止切到无关分支。

允许 `git commit` / `push` 到任务分支、开 PR、CI 绿后 squash 合 `master`。

本机 Linux VM：

```text
export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"
```

密钥只用 `deploy/.env.example` 口径（`REDIS_DATABASE=2`）。禁止 Redis db0。本机无 Docker：单测可跑；`*IT` 留 CI。禁止为本地绿改 H2、Embedded Redis，或削弱断言。

## 3. 一次只做一个任务

只实现本节勾选项。禁止顺便做下一任务。发现自己正在写下一节内容 → 第 5 节打断。

## 4. 嘴硬验收（缺一条就保持未勾选）

每个勾选项打勾前必须同时满足。未通过：禁止把 `tasks.md` 勾成 `[x]`，禁止写「本任务完成」。

**规格**：每个新公共 API / 新表 / 新错误码能指回 requirements 或 design 编号。写路径涉及鉴权/审计/会话/evict：只以 `docs/standards/05-security.md` 为准。

**边界**：本节勾选项全部覆盖，且没有多做下一节。

**测试（最硬）**：

- 测试与实现同任务交付
- 类名必须出现在本节 `_测试：_` 或 verification-matrix 该任务行
- 禁止 `@Disabled`、跳过标签、空测试、只 `assertNotNull` 的假绿
- 本机能跑：`cd server; mvn -q -DskipITs test`，贴通过证据
- `*IT`：写明「本机无 Docker，留给 CI」；代码里断言保持完整
- 属性测试（jqwik）必须断言不变量

**反自欺**：

- 未实现行为藏进 TODO / 空方法 / 永远 true 的 if？→ 期望「否」
- 为编译通过改宽模块依赖？→ 期望「否」
- evict `identity:session`、`new ObjectMapper()`、给门户/internal 加 `@Audited`？→ 期望「否」
- 乐观锁二选一且 `rwd_prize` 未加 version？→ 期望「是」或「未涉及」
- `Result.code` 成功是数字 `0`、失败是字符串？→ 期望「是」或「未涉及」
- P0 若出现 `ad:position`：只做了命名空间占位？→ 期望「是」或「未出现」

任务结束输出【验收陈述】（做了什么 / 没做什么 / 命令与结果 / 矩阵 / 反自欺 / 两轮评审 issue 号）。不要写「需人类评审才能合」。

## 5. 【卡点打断】

仅这些情况停笔：规格互斥、前置任务未勾选、连续两轮测试失败且根因不明、密钥将进 git、准备削弱断言、正在做下一任务的内容。

不要因为「属于资金/会话/风控/迁移」或「没人点头」而打断。

打断后仍须按第 6 节重写 STATUS。

## 6. 重写 PROJECT_STATUS.md

时机：嘴硬验收通过、被打断、上下文将耗尽。整页重写，不要追加聊天流水账。勾选 `tasks.md` 仅当第 4 节通过。只勾本任务本节。
