# 营销任务平台

运营在管理端编排任务，用户在门户按步骤参与，平台发放奖品和积分。后台账号与门户账号相互隔离。

**项目尚未上线。** 当前范围、重构进展与验证状态见 [PROJECT_STATUS.md](PROJECT_STATUS.md)。

## 从这里开始

| 需要了解 | 入口 |
|---|---|
| 当前工作、已知缺口、下一步 | [PROJECT_STATUS.md](PROJECT_STATUS.md) |
| 当前有哪些功能、哪些仍有缺口 | [当前功能清单](docs/current-features.md) |
| 真实环境全流程验证结果 | [2026-10-07 核验报告](docs/full-flow-verification-2026-10-07.md) |
| 文档各管什么、按问题查资料 | [文档导航](docs/README.md) |
| 项目诊断、目标结构、重构与验收 | [重构方案](docs/refactoring-blueprint.md) |
| 尚未解决的规格和方案分歧 | [决策记录](docs/decisions.md) |
| 本地启动 | [开发脚本说明](scripts/README.md) |
| 后端 / 前端开发 | [server](server/README.md) / [web](web/README.md) |
| 编码代理 | [AGENTS.md](AGENTS.md) |

## 仓库结构

| 目录 | 职责 |
|---|---|
| `server/` | Maven 多模块：4 个平台模块、8 个领域模块、2 个应用 |
| `web/apps/admin/` | Vue 管理端，Ant Design Vue |
| `web/apps/client/` | Vue 移动门户，Vant 4 |
| `web/packages/shared/` | 两端共享的契约类型与纯工具 |
| `.kiro/specs/platform-v2/` | 需求、设计分册、业务场景与选型依据 |
| `docs/` | 导航、重构计划、决策、验收映射与编码规范 |
| `scripts/`、`deploy/`、`ci/`、`perf/` | 本地开发、部署模板、CI、性能验证 |
| `spike/` | 历史组件验证实验，不是正式业务实现 |

后端采用模块化单体内核，部署为 `admin-app` 与 `portal-app`。管理应用处理 `/admin/**`；门户应用处理 `/api/**` 和内部回调 `/internal/**`。内部回调及监控端点不向公网开放。任务推进与奖励发放保持同进程、同数据库事务。

## 本地启动

需要 JDK 26、Maven、Node.js、pnpm，以及可连接的 MySQL / Redis。确切依赖见 [依赖矩阵](.kiro/specs/platform-v2/dependency-matrix.md) 和构建文件；共享开发 Redis 使用 DB 2。

```powershell
.\scripts\dev.ps1 init
# 在被 Git 忽略的 .env.local 中填写本地连接信息
.\scripts\dev.ps1 start
.\scripts\dev.ps1 status
```

管理端默认访问 `http://127.0.0.1:5173`，门户访问 `http://127.0.0.1:5174`。首次 init 的环境文件迁移行为、重建、停止和日志见 [脚本说明](scripts/README.md)。容器部署见 [部署检查单](deploy/R31-go-live-checklist.md)。

存在启动脚本、测试或部署模板，不代表已经通过验收或具备上线条件。检查结果必须对应具体代码版本和运行记录。
