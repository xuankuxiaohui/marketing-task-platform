---
name: task-execute
description: >
  0→1 施工循环：组内每题一个 task/<n>-<slug> 分支。一组做完写交接、提交、清会话，
  再执行本技能开下一组（先建新分支）。禁止合/推/强推 master。不要等人说继续。
  Use when implementing the next platform-v2 task or continuing from PROJECT_STATUS.md.
---

# task-execute

0→1 施工，不是迭代交付。规格只认仓库文件。当前任务以当前分支 `PROJECT_STATUS.md` 为准，只落地 `.kiro/specs/platform-v2/tasks.md` 该节未勾选项。硬停止只读 `AGENTS.md` + `docs/standards/`。

权威链：requirements.md > design.md > tasks.md 本节 > docs/standards > verification-matrix > AGENTS.md。条款互斥才【卡点打断】。禁止发明表/错误码。禁止创建 `main`。

**禁止 merge master。禁止 push master。禁止 force-push master。** 人类点名合入除外。

交接文档就是当前分支的 `PROJECT_STATUS.md`（整页重写，不要另起一份）。

## 编组（tasks.md）

| 编组 | 任务 | 停组后下一组 |
|------|------|--------------|
| A Spike | 1–8 | B |
| B 骨架 | 9–11 | C |
| C 契约/库/基建 | 12–16 | D |
| D 风控+埋点 | 17–20 | E |
| E identity | 21–25 | F |
| F task | 26–31 | G |
| G reward+points | 32–35 | H |
| H 双前端 | 36–39 | I |
| I 闭环/E2E | 见 tasks.md | J |
| J 其余 | 见 tasks.md | 勾完停 |

## 组内：一题一分支

1. 新开 `task/<n>-<slug>`。上一题未合：从上一题 tip 开。上一题已合 master：从 `origin/master` 开。
2. 只做本节未勾选项。实现后：`cd server && mvn -q -DskipITs test`，红了修到绿。禁止 H2 / Embedded Redis 假绿。
3. 勾选 `tasks.md` 该节，整页重写 `PROJECT_STATUS.md`（当前题、分支、已叠哪些、未合 master）。
4. commit + push 本任务分支。开 **本任务** PR 到 master（不合）。不要把下一题写进本分支。
5. 组内还有下一题：立刻从本 tip 开下一题新分支，继续做。不要等人。
6. 不要每题做两轮代码评审。测试/运维只开 GitHub issue，不修、不挡下一题。编译/单测红必须修。
7. 禁止 merge / push / force-push `master`。

## 一组做完：交接 → 提交 → 清上下文 → 再跑本技能

当前编组最后一题勾完并 push/PR 之后，**不要停等「继续」**：

1. **更新交接文档**：整页重写 `PROJECT_STATUS.md`：阶段写成「编组 X 待验收 / 下一组 Y 第 N 题」；写清已勾任务、链尖分支、未合 master 的 PR、硬停止、下一组第一题。
2. **提交代码**：把交接文档和本组收尾一并 commit + push 当前链尖。
3. **清空上下文**：不要 `--continue`。关掉本 grok 会话，新开会话。
4. **重新执行本技能**：新会话第一件事读本文件 + `PROJECT_STATUS.md`。
5. **下一组开发前创建新分支**：`task/<下一组第一题号>-<slug>`，从本组链尖 tip 开（本组未合时）。然后做下一组第一题，组内循环回到上面。

一直做到 `tasks.md` 勾完。会话/grok 上下文 ≥260k 也按第 3–4 步清会话再执行本技能（即使还在组内）。

聊天一行摘要：当前任务号、分支、单测绿/红、停没停。

## 现在（以 git 为准）

- master 含 22–28（#36）。29–32 在未合链上（#38–#42）。
- 当前编组 **G（32–35）**。32 已勾。组内继续 33→34→35（各开新分支）。
- 35 做完：交接写成编组 G 待验收 / 下一组 H 任务 36，commit，清会话，再跑本技能，开 `task/36-*` 做编组 H。不要在 35 上停等。

## 动手前读什么

读当前分支 STATUS（交接）、tasks 本节、design 锚点、矩阵该任务行、`AGENTS.md`。写路径/鉴权/审计/会话/evict **只读 05-security**。允许 commit / push / 开本任务 PR。**禁止合 master。**

本机 Linux VM：

```text
export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"
```

Redis DB 2。无 Docker：`*IT` 留 CI。禁止 H2 / Embedded Redis 假绿。
git author：`xuankuxiaohui` + GitHub `users.noreply`。禁止写入带手机号的个人邮箱。
本机 Docker MySQL `127.0.0.1:3308`、Redis 6379、admin 8080 / portal 8081 若在跑：不要杀。

## 一次只做一个任务

只实现本节勾选项。组内做完立刻下一题新分支。组界按「一组做完」五步。

## 嘴硬验收

规格、边界、测试、反自欺同原规则。通过才勾选。不要写「请立刻合」。

## 【卡点打断】

规格互斥、前置缺失、连续两轮测试失败且根因不明、密钥将进 git、准备削弱断言。不要因为「没人点头合 master」或「编组做完」而停下一组。
