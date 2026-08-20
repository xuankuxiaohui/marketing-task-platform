---
name: task-execute
description: >
  0→1 施工循环：在当前任务链分支上逐题落地 tasks.md，勾选并重写 PROJECT_STATUS，立刻叠下一题。
  禁止合/推/强推 master。编组 F 做到任务 31 停。已到任务 28 则从 task/28 分支继续。
  Use when implementing the next platform-v2 task or continuing from PROJECT_STATUS.md.
---

# task-execute

0→1 施工，不是迭代交付。规格只认仓库文件。当前任务以当前分支 `PROJECT_STATUS.md` 为准，只落地 `.kiro/specs/platform-v2/tasks.md` 该节未勾选项。硬停止只读 `AGENTS.md` + `docs/standards/`。

权威链：requirements.md > design.md > tasks.md 本节 > docs/standards > verification-matrix > AGENTS.md。条款互斥才【卡点打断】。禁止发明表/错误码。禁止创建 `main`。

**禁止 merge master。禁止 push master。禁止 force-push master。**

## 当前链（以 git 为准）

- 唯一长期分支是 `master`（任务 21 已合）。
- 施工链：已叠 22–28 的代码在 `task/28-visibility-claim`（叠过 `task/27-publish-schedule`）。**已经做到任务 28，从 `task/28-visibility-claim` 继续，不要回到 27，不要从 origin/master 平行新开。**
- 没有任务链时，才从 `origin/master` 开 `task/<n>-<slug>`。
- 施工期最多保留**链尖一个 PR**（目标 master，现为 #36）。不要再给 22–26 的旧 stacked PR 推提交，不要为每题新开打向 master 的 PR。
- 编组 F 最后一题是任务 31。做完 31 停，等人类说「继续」。不要做 32+。

## 循环

1. 只在**当前任务链分支**上做 `tasks.md` 本节未勾选项。已有链就接着写，不要每题从 master 新开分支，不要每题新开 `task/<n>-*`。
2. 实现后跑：`cd server && mvn -q -DskipITs test`。红了就修到绿。禁止 H2 / Embedded Redis 假绿。
3. 勾选 `tasks.md` 该节，整页重写 `PROJECT_STATUS.md`（当前题、分支名、已叠哪些、未合 master）。
4. 立刻做下一题，叠在同一条链上（同一分支继续 commit + push 链尖）。
5. 禁止 merge / push / force-push `master`。
6. 不要每题做两轮代码评审。不要每题从 master 新开分支。不要为 22–26 再更新/新开一堆打向 master 的 PR。
7. 测试/运维发现问题：只开 GitHub issue，不修、不挡下一题。编译/单测红除外，必须修到绿。
8. 施工期最多保留链尖一个 PR。旧 stacked PR 不要再推提交。
9. 做到当前编组最后一题就停，等「继续」。现在编组 F 最后一题是任务 31。不要做 32+。
10. 会话/grok 上下文 ≥260k：清会话再执行本技能。不要等人说「继续」才做 29–31。

做完任务 31：勾选、`PROJECT_STATUS` 写成「编组 F 待验收」，链尖 PR 描述写清 22–31 未合 master，然后停。

聊天一行摘要：当前任务号、分支、单测绿/红、停没停。

## 开工顺序（现在）

1. 27 未完成 → 先完成 27。
2. 已完成（现状）→ 在 `task/28-visibility-claim` 上做 28 未勾选项；28 已勾完则 29 → 30 → 31。
3. 不要把 28 再 merge 回 `task/27-publish-schedule`。不要从 origin/master 平行新开。

## 动手前读什么

读当前分支 STATUS、tasks 本节、design 锚点、矩阵该任务行、`AGENTS.md`。写路径/鉴权/审计/会话/evict **只读 05-security**。允许 commit / push 链尖。**禁止合 master。**

本机 Linux VM：

```text
export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"
```

Redis DB 2。无 Docker：`*IT` 留 CI。禁止 H2 / Embedded Redis 假绿。
git author：`xuankuxiaohui` + GitHub `users.noreply`。禁止写入带手机号的个人邮箱。
本机 Docker MySQL `127.0.0.1:3308`、Redis 6379、admin 8080 / portal 8081 若在跑：不要杀。

## 一次只做一个任务

只实现本节勾选项。做完立刻下一题，仍在同一分支。

## 嘴硬验收

规格、边界、测试、反自欺同原规则。通过才勾选。不要写「请立刻合」。

## 【卡点打断】

规格互斥、前置缺失、连续两轮测试失败且根因不明、密钥将进 git、准备削弱断言。不要因为「没人点头合 master」而停 29–31。编组 F 做完 31 必须停。
