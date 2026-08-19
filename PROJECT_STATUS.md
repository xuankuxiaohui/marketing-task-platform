# PROJECT_STATUS
> 阶段：**编码（编组 D 完成）** / 当前任务：**20 已合 master** / 更新：2026-08-19

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**` + `/internal/**`）。

## 现在做到哪

- 已勾选任务：**1–20**
- 进行中：无
- 下一步：任务 21（admin 认证与双账号会话），等人类说「继续」
- Git：唯一长期分支是 **master**（`602eb3c`）。不再使用 `main`

## 关键技术决策（本轮新发生的）

- 人类决定只认 `master`：GitHub 默认分支改为 `master`，CI 只跟 `master`，删除 `main`

## 改过的核心文件

- `.github/workflows/ci.yml`
- `docs/standards/08-git-workflow.md`
- `AGENTS.md`
- `.grok/skills/task-execute/SKILL.md`

## 测试与验证

- 本轮只改分支约定与 CI 触发，不改业务代码

## 已知问题（只写已证实）

- 任务 18 是风控路径，仍需人类评审，AI 不得宣称可合
- 会话（15）与迁移（13/14）与名单（17）仍待人类评审
- 本机无 Docker，`*IT` 只能在 CI 验证

## 尝试过但失败的方案

- 无（本轮）

## 明确禁止下一会话做的事

- 不要再创建或推送 `main`
- 不要做任务 21+，除非人类明确说「继续」
- 不要改 V1–V4
- 不要 evict `identity:session`、不要给 `ad:position` 写 L2
- 不要自审自合任务 18 / 资金 / 会话 / 风控 / 迁移
- 不要用 H2 / Embedded Redis 让 IT 本地变绿

## 下一步开发顺序（最多 3 步）

1. 任务 21：admin 认证与双账号会话（人类说「继续」后再做）
2. 人类评审任务 18（风控判定链）
3. 任务 22：RBAC 与权限树
