---
name: task-execute
description: >
  测试阶段正规流。新工作从 origin/master 开 fix/<slug>，走 issue+PR。
  不要再叠 task/*，不要自动合 master，不要自动开下一题。
  阶段只看 PROJECT_STATUS.md。
---

# task-execute

当前阶段、打开的 issue、下一步只看仓库根 `PROJECT_STATUS.md`。硬停止读 `AGENTS.md` + `docs/standards/`。规格只认 `.kiro/specs/platform-v2/`。

权威链：requirements.md > design.md > 打开的 issue > docs/standards > verification-matrix > AGENTS.md。禁止发明表/错误码。禁止创建 `main`。**禁止 merge / push / force-push master**（人类点名除外）。

## 现在怎么做

1. 读 `PROJECT_STATUS.md`。不要按 `tasks.md` 下一空勾开新题，不要叠 `task/*`。
2. 从 `origin/master` 开 `fix/<slug>`（文档用 `docs/<slug>`）。
3. requirements > design。先测后审。issue + PR 到 master。不要自动合 master。
4. STATUS 是当前阶段指针。不要自动开下一题。

git author：`xuankuxiaohui` + GitHub `users.noreply`。禁止个人邮箱。

## 【卡点打断】

规格互斥、前置缺失、连续两轮测试失败且根因不明、密钥将进 git、准备削弱断言。
