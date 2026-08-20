---
name: task-execute
description: >
  0→1 施工循环：每题一个 task/<n>-<slug> 分支，从上一题 tip（已合则从 origin/master）开出。
  禁止合/推/强推 master。编组 F 做到任务 31 停。任务 29 起一题一分支。
  Use when implementing the next platform-v2 task or continuing from PROJECT_STATUS.md.
---

# task-execute

0→1 施工，不是迭代交付。规格只认仓库文件。当前任务以当前分支 `PROJECT_STATUS.md` 为准，只落地 `.kiro/specs/platform-v2/tasks.md` 该节未勾选项。硬停止只读 `AGENTS.md` + `docs/standards/`。

权威链：requirements.md > design.md > tasks.md 本节 > docs/standards > verification-matrix > AGENTS.md。条款互斥才【卡点打断】。禁止发明表/错误码。禁止创建 `main`。

**禁止 merge master。禁止 push master。禁止 force-push master。** 人类点名合入除外。

## 当前链（以 git 为准）

- 唯一长期分支是 `master`。任务 22–28 已合（#36 → `d7a02eb`）。
- **任务 29 起：一题一个新分支 `task/<n>-<slug>`。**
- 上一题未合：从上一题分支 tip 开（叠链，方便带上未合代码）。
- 上一题已合 master：从 `origin/master` 开，不要复用旧分支接着写。
- 不要回到 `task/27-*` / `task/28-*` 上写 29+。不要为 22–26 再推旧 PR。
- 编组 F 最后一题是任务 31。做完 31 停，等「继续」。不要做 32+。

## 循环

1. 新开本任务分支 `task/<n>-<slug>`（基线见上）。只做 `tasks.md` 该节未勾选项。
2. 实现后跑：`cd server && mvn -q -DskipITs test`。红了就修到绿。禁止 H2 / Embedded Redis 假绿。
3. 勾选 `tasks.md` 该节，整页重写 `PROJECT_STATUS.md`（当前题、分支名、已叠哪些、未合 master）。
4. commit + push 本任务分支。开 **本任务** 的 PR 到 master（不合）。不要把下一题写进本分支。
5. 立刻开下一题的新分支，叠在本任务 tip 上（本任务未合时）。
6. 禁止 merge / push / force-push `master`。
7. 不要每题做两轮代码评审。测试/运维只开 GitHub issue，不修、不挡下一题。编译/单测红必须修到绿。
8. 做到任务 31 停。`PROJECT_STATUS` 写成「编组 F 待验收」。不要做 32+。
9. 会话/grok 上下文 ≥260k：清会话再执行本技能。

聊天一行摘要：当前任务号、分支、单测绿/红、停没停。

## 开工顺序（现在）

- 29：`task/29-step-engine`，从 `origin/master`（已含 28）。
- 30：`task/30-internal-callback`，从 29 tip。
- 31：`task/31-instance-admin`，从 30 tip。然后停。

## 动手前读什么

读当前分支 STATUS、tasks 本节、design 锚点、矩阵该任务行、`AGENTS.md`。写路径/鉴权/审计/会话/evict **只读 05-security**。允许 commit / push / 开本任务 PR。**禁止合 master。**

本机 Linux VM：

```text
export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"
```

Redis DB 2。无 Docker：`*IT` 留 CI。禁止 H2 / Embedded Redis 假绿。
git author：`xuankuxiaohui` + GitHub `users.noreply`。禁止写入带手机号的个人邮箱。
本机 Docker MySQL `127.0.0.1:3308`、Redis 6379、admin 8080 / portal 8081 若在跑：不要杀。

## 一次只做一个任务

只实现本节勾选项。做完再开下一题的新分支。

## 嘴硬验收

规格、边界、测试、反自欺同原规则。通过才勾选。不要写「请立刻合」。

## 【卡点打断】

规格互斥、前置缺失、连续两轮测试失败且根因不明、密钥将进 git、准备削弱断言。编组 F 做完 31 必须停。
