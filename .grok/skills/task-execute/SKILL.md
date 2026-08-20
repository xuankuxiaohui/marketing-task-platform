---
name: task-execute
description: >
  跑本技能做一题、提交、清上下文，再跑本技能，直到当前编组做完。
  组内每题新分支；下一组开发前再开新分支。禁止合 master。
  Use when implementing the next platform-v2 task or continuing from PROJECT_STATUS.md.
---

# task-execute

0→1 施工。规格只认仓库文件。当前任务以 `PROJECT_STATUS.md` 为准，只落地 `.kiro/specs/platform-v2/tasks.md` 该节未勾选项。硬停止只读 `AGENTS.md` + `docs/standards/`。

权威链：requirements.md > design.md > tasks.md 本节 > docs/standards > verification-matrix > AGENTS.md。禁止发明表/错误码。禁止创建 `main`。**禁止 merge / push / force-push master**（人类点名除外）。

交接文档 = 当前分支 `PROJECT_STATUS.md`（整页重写）。

## 循环（就这一套）

1. **执行本技能**：读 STATUS + 本节未勾选项，实现。`cd server && mvn -q -DskipITs test`，红了修到绿。
2. **提交**：勾选 tasks.md，整页重写 PROJECT_STATUS，commit + push 本任务分支，开本任务 PR 到 master（不合）。
3. **清空上下文**：不要 `--continue`。关掉本会话。
4. **再执行本技能**：新会话读本文件 + STATUS，做下一题。
5. 重复 1–4，**直到当前编组最后一题勾完**。

组内每一题：新开 `task/<n>-<slug>`（未合则从上一题 tip，已合则从 origin/master）。不要把下一题写进本分支。不要每题两轮评审。测试/运维只开 issue 不修、不挡（编译/单测红必须修）。

## 编组做完

最后一题提交之后：

1. PROJECT_STATUS 写成「编组 X 待验收 / 下一组第一题」（交接）。
2. commit + push。
3. 清上下文，再执行本技能。
4. **下一组开发前**开新分支 `task/<下一组第一题>-<slug>`，从本组链尖 tip 开，然后继续上面的循环。

不要等人说「继续」。`tasks.md` 勾完才停。上下文 ≥260k 也按「清上下文再跑本技能」。

聊天一行：任务号、分支、单测绿/红、停没停。

## 编组

A 1–8 · B 9–11 · C 12–16 · D 17–20 · E 21–25 · F 26–31 · G 32–35 · H 36–39 · 其后见 tasks.md。

**现在**：编组 G（32–35）。32 已勾。继续 33→34→35，每题新分支 + 每题提交后清会话再跑本技能。35 完后交接、清会话、开编组 H 新分支。

## 动手前

读 STATUS、tasks 本节、design 锚点、矩阵、AGENTS。写路径只读 05-security。

```text
export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"
```

Redis DB 2。无 Docker：`*IT` 留 CI。禁止 H2 / Embedded Redis。
git author：`xuankuxiaohui` + GitHub `users.noreply`。禁止个人邮箱。
本机 MySQL 3308 / Redis / admin 8080 / portal 8081 若在跑：不要杀。

## 【卡点打断】

规格互斥、前置缺失、连续两轮测试失败且根因不明、密钥将进 git、准备削弱断言。
