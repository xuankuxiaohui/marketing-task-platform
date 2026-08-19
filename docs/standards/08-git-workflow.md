# 08 · Git 与分支

> 适用范围：本仓库所有提交与 PR。  
> 提交说明与评审清单见 [04-code-review.md](04-code-review.md)，本篇只管分支、PR、保护规则。  
> 流程取 [GitHub flow](https://docs.github.com/en/get-started/using-github/github-flow)（短命分支 → PR → `master`），提交说明取 [Conventional Commits](https://www.conventionalcommits.org/)。

## 1. 长期分支

| 分支 | 角色 |
|------|------|
| `master` | 唯一长期分支。随时可构建。禁止直接 `push`（有远端保护后） |

**MUST NOT** 再建 `develop` / `release` / 按人命名的长期分支。P0 / P1 用任务号区分，不靠长期分支隔离。

## 2. 短命分支

从最新 `master` 拉出，合入后删除。

```text
task/<n>-<slug>     对应 tasks.md 任务号，如 task/28-claim-start、task/37.1-admin-system
spike/<n>-<slug>    编组 A 冒烟，如 spike/1-redisson（代码只进 spike/）
fix/<slug>          无任务号的缺陷
docs/<slug>         仅规格或 docs/standards
```

1. **MUST** 一个分支只对应一件事（通常一个任务号）。编组 J（任务 44–49）**MUST NOT** 在任务 43 验收前从 `master` 开功能分支合入。
2. `slug` 小写 + 连字符，不超过 40 字符。
3. **MUST NOT** 用 `feat/xxx`、`thanh/xxx`、中文分支名。
4. 需要基于未合入的前置任务时：在 PR 描述写清依赖，**不要**把两个任务揉进同一分支。

## 3. 提交

规则全文在 04 §4。这里只强调：

- 格式：`<type>(<scope>): <summary>`，正文 `Refs: task-<n>, R<x.y>`
- **MUST NOT** 把无关文件、密钥、`spike/*/target`、`node_modules` 提交上去
- 本地整理 **可以** rebase 自己的短命分支；**MUST NOT** `push --force` 到 `master`

## 4. PR

1. 目标分支 **MUST** 是 `master`。
2. 标题与 squash 后的提交说明同一套 Conventional Commits。
3. 描述里写：任务号、触及的需求/设计条款、测试怎么跑。
4. 合入方式：**squash merge**，保持 `master` 线性。
5. 门禁见 04 §10。资金 / 会话 / 风控 / 迁移路径 **MUST** 有人类签字，AI 评审不能单独放行。
6. 契约变更（Controller / Command / Response / ErrorCode）**MUST** 同 PR 提交 `packages/shared` 生成物。

## 5. 保护与 CODEOWNERS

远端（GitHub）对 `master` 开启：禁止直推、PR 必审、状态检查通过才能合。

`.github/CODEOWNERS` 在有协作者账号后启用，路径按模块，不要按人散落。建议：

```text
# 先填真实 GitHub handle，再提交此文件
.kiro/specs/                        @owner
docs/standards/                     @owner
server/platform-db/                 @owner
server/domain-reward/               @owner
server/domain-identity/             @owner
server/domain-risk/                 @owner
deploy/                             @owner
```

没有 CODEOWNERS 时，04 §10 的人类签字规则仍然生效。

## 6. AI 检查清单

- [ ] 分支名符合 §2，能指到 `task-<n>` 或说明为何是 `fix`/`docs`
- [ ] 未把 spike 工程拷进 `server/`
- [ ] 提交说明有 `Refs:`
- [ ] 未 force-push `master`，未提交密钥
- [ ] 资金/鉴权路径未自审自合
