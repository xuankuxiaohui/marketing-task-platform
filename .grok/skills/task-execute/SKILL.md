---
name: task-execute
description: >
  按 tasks.md 逐任务落地：实现、开 PR、一轮评审、测试与运维记账，然后开下一任务。
  不要合 master（早上人类确认）。上下文超过 260k 清会话再继续。
  Use when implementing the next platform-v2 task or continuing from PROJECT_STATUS.md.
---

# task-execute

本仓库编码执行代理。当前任务以 `PROJECT_STATUS.md` 为准，只落地 `tasks.md` 该节未勾选项。硬停止只读 `AGENTS.md`。

**夜间运行（2026-08-20 起）**：不要合 `master`。早上由人类确认才合。每个任务做完 = 实现 + **一轮**代码评审 + 测试 + 运维。然后立刻开下一任务。新开任务前若会话/grok 上下文超过 **260k**，清掉再用本技能开新会话。一直做到 `tasks.md` 勾完或人类叫停。不要等「继续」。

权威链：requirements.md > design.md > tasks.md 本节 > docs/standards > verification-matrix > AGENTS.md。条款互斥才【卡点打断】。禁止发明表/错误码。禁止创建 `main`。PR 目标必须是 `master`，**禁止 squash/merge 进 master**。

## 循环

1. 新开任务前检查上下文；≥260k 则清会话后重新执行本技能。
2. 从最新 `origin/master` 开 `task/<n>-<slug>`。不要从上一任务未合分支叠上去。
3. 实现本节勾选项；`cd server; mvn -q -DskipITs test`。
4. push。做一轮测试 + 一轮运维。有问题只开 GitHub issue，**不要修、不要因此停下**。
5. 开 PR 到 master。**不要 merge。**
6. **一轮**代码评审。有问题：开 issue → 立刻修 → commit + push。不要等。
7. 勾选 `tasks.md`、整页重写 `PROJECT_STATUS.md`。聊天一行摘要（任务号、PR 链接、不合）。
8. 立刻开下一未勾选任务（先做 260k 检查）。

主分支健康检查交给每 3 小时定时，不挡开发。开发续跑发现没在写就接下一个未完成任务，仍然不合 master。

## 开工

读 STATUS、tasks 本节、design 锚点、矩阵该任务行、AGENTS、git status。直接动手。

资金 / 会话 / 风控 / 迁移：照常实现，仍然不合 master。

## 动手前读什么

按 `AGENTS.md` 那张表按层读。写路径/鉴权/审计/会话/evict **只读 05**。允许 commit / push / 开 PR。**禁止合 master。**

本机 Linux VM：

```text
export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"
```

Redis DB 2。无 Docker：`*IT` 留 CI。禁止 H2 / Embedded Redis 假绿。
git author：`xuankuxiaohui` + GitHub `users.noreply`。禁止写入带手机号的个人邮箱。

## 一次只做一个任务

只实现本节勾选项。

## 嘴硬验收

规格、边界、测试、反自欺同原规则。通过才勾选。不要写「请立刻合」；早上人类自己看打开的 PR。

## 【卡点打断】

规格互斥、前置缺失、连续两轮测试失败且根因不明、密钥将进 git、准备削弱断言。不要因为「没人点头合 master」而停下一任务。
