# Git 规范

## 提交类型

| 类型 | 说明 |
|---|---|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不改变外部行为） |
| `docs` | 文档变更 |
| `test` | 测试 |
| `chore` | 构建/工具/依赖变更 |

## 提交消息格式

```
<type>: <subject>

[可选的 body 说明]

Co-Authored-By: Claude <noreply@anthropic.com>
```

- subject 不超过 70 字符，中文或英文均可
- 描述改了什么，以及**为什么**改
- 不要写 `fix bug` 或 `update code` 这类无信息量的消息

## 分支命名

| 类型 | 格式 | 示例 |
|---|---|---|
| 功能分支 | `feature/<name>` | `feature/reward-service` |
| 修复分支 | `fix/<name>` | `fix/sql-injection` |
| 发布分支 | `release/vX.Y.Z` | `release/v0.3.0` |
| 热修复 | `hotfix/<name>` | `hotfix/cache-evict` |

## 不纳入版本管理的文件

- `.env` 及含密钥的配置文件
- `node_modules/`
- `tsconfig.tsbuildinfo` 等构建产物
- IDE 配置文件（`.vscode/`、`.idea/`）

## PR 规范

- 标题：`<type>: <简短描述>`，与 commit 风格一致
- 描述：包含变更摘要（1-3 条要点）和测试计划
- 合并前必须通过 CI（如有）
