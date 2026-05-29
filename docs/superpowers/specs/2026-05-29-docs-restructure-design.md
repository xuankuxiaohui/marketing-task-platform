# docs 目录重构设计

日期：2026-05-29

## 问题

1. `spec/current/` 持续增长 — 每个版本的 API/SQL 变更 append 进去，v0.3.x 历史 API 还在里面，越写越冗余
2. 同一功能在 release-notes、api.md、sql.md、roadmap 四处重复描述
3. `current-system-roadmap.md` 80%+ 是已完成的删除线，信息密度极低
4. 历史版本快照（v0.1.0~v0.5.0）保留不动

## 目标

- `spec/current/` 从按版本堆叠改为按模块组织
- 新增 `features.md` 作为系统能力的精简清单
- 清理 roadmap 中的已完成内容
- CLAUDE.md 中的文档引用路径同步更新

## 模块划分

| 模块 | 包含内容 |
|---|---|
| **task** | 任务 CRUD、步骤、过滤器、平台配置、条件分支、批量操作、定时发布、软删除、互斥组 |
| **prize** | 奖品管理、奖品记录、领奖、库存、过期 |
| **activity** | 活动配置、展示规则、灰度、参与规则、统计 |
| **signin** | 签到配置、签到记录、积分账户、积分流水 |
| **system** | 用户管理、鉴权(Sa-Token)、监控指标、模拟测试、审计日志 |

## 新目录结构

```
docs/spec/current/
├── features.md          # 功能清单（一个功能一行，按模块分组）
├── api/
│   ├── task.md          # 任务、步骤、过滤器、平台、分支、批量、定时
│   ├── prize.md         # 奖品管理、领奖、记录
│   ├── activity.md      # 活动、展示规则、参与
│   ├── signin.md        # 签到、积分
│   └── system.md        # 用户管理、鉴权、监控、模拟、审计
└── sql/
    ├── task.md          # V1, V2, V4, V5, V7~V16
    ├── prize.md         # V6, V20
    ├── activity.md      # V18, V19, V21
    ├── signin.md        # V17
    └── system.md        # V3, V10, V12, V13
```

## features.md 格式

每个功能点一行，不展开细节。详细 API/SQL 去对应模块文件看。

## api/ 文件格式

按接口分组，只写当前生效的 API，不按版本堆叠。

## sql/ 文件格式

迁移索引 + 关键 DDL + 字段说明。

## roadmap 清理

`current-system-roadmap.md` 改为：
- 删除所有已完成（删除线）内容
- 保留"当前限制"表格
- 保留"后续计划"（只写未完成的）

## CLAUDE.md 更新

"任务 → 必读文档"表格中 `docs/spec/current/` 行更新描述。

## 删除旧文件

- `docs/spec/current/api.md` → 拆分到 `api/task.md` 等
- `docs/spec/current/sql.md` → 拆分到 `sql/task.md` 等
- `docs/spec/current/release-notes.md` → 删除（内容已分散到各模块 api 文件和 roadmap）
