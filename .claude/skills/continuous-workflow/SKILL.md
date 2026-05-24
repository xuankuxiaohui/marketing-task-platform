---
name: continuous-workflow
description: "Pure process engine for multi-session development. Zero project content — reads state from docs/, conventions from conventions/, executes, syncs back. Use when the user asks to continue, push forward, or work autonomously."
---

# Continuous Workflow

纯进程引擎。不包含任何项目内容（任务卡片、版本号、文件路径、测试数），所有数据从 `docs/` 和代码库实时读取。

## 1. 会话启动

### 1.1 环境快照

```bash
git branch --show-current && git log --oneline -3 && git status --short
```

### 1.2 状态同步

| 读什么 | 从哪读 | 取什么 |
|---|---|---|
| 进度全貌 | `docs/current-system-roadmap.md` | 已完成/限制/计划、P0-P3 优先级、下一个未完成任务 |
| 规格文档 | `docs/spec/current/` | API 契约、SQL 表结构、版本发布记录 |
| 项目规范 | `docs/conventions/` | 分层、编码、命名、数据库、API、步骤推进、过滤安全、前端规则 |
| 设计文档 | `docs/superpowers/specs/` | 复杂功能的前置设计 spec（如有） |
| 上次会话 | `memory_search` (Ruflo) | 最近完成的任务、遗留的阻塞上下文 |

### 1.3 测试基线

```bash
mvn -f backend/pom.xml test -q 2>&1 | grep -E "Tests run:|BUILD"
```

## 2. 执行循环

```
读 roadmap → 取下一个未完成任务
              │
              ▼
         复杂度判断
         ╱         ╲
      简单          复杂
   （单文件/       （多文件/
    CRUD 模式）     新模块/跨层）
      │              │
      ▼              ▼
   直接实现     进入 brainstorming
                → 写 spec → 用户确认
      │              │
      ▼              ▼
         实现 → 验证 → 提交 → 更新文档 → memory_store 记录进度
                                            │
                            ┌───────────────┘
                            ▼
                    取下一个任务（循环）
```

### 2.1 复杂度判断

用以下启发式规则判断是否需要先写 spec：

**直接实现**（跳过 brainstorming）：
- 单文件改动，遵循现有模式（如新增一个 Limiter、一个 Controller endpoint）
- 表结构参考现有同类型（如新增配置表，参考 prize 表模式）
- 前端组件参考现有同类（如新增列表页，参考 PrizeList.vue）

**先 brainstorming 写 spec**：
- 新模块/新包（跨 3+ 文件，涉及新的服务边界）
- 新步骤类型或新的推进行为
- 影响现有幂等/事务/缓存逻辑
- 用户明确要求"先设计"

### 2.2 Ruflo 分流

| 复杂度 | 执行方式 | 工具 |
|---|---|---|
| 简单（≤2 文件） | 当前会话直接写 | — |
| 中等（3-5 文件，前后端分离） | 并行 spawn 两个 agent | `agent_spawn`: backend-agent + frontend-agent |
| 复杂（新模块/跨 5+ 文件） | brainstorming → spec → swarm | `swarm_init` → 多 agent 并行 |

**中/复杂任务 spawn 示例**：
```
agent_spawn(backend-agent): 读 conventions → 实现 Controller/Service/Mapper/Test/Flyway
agent_spawn(frontend-agent): 读 conventions → 实现 API 层/页面组件/路由
共享约束：API 契约（从 spec 或约定推导）
```

### 2.3 质量门禁

全部通过才可提交：
- [ ] `mvn -f backend/pom.xml compile`
- [ ] `mvn -f backend/pom.xml test`（全过，新增代码有测试）
- [ ] `npm --prefix admin-web run build`
- [ ] `npm --prefix client-web run build`

### 2.4 提交

一个任务一个提交，不混合。格式遵循 `docs/conventions/git.md`。

### 2.5 文档同步

每完成任务后更新：

| 文件 | 更新 |
|---|---|
| `docs/current-system-roadmap.md` | 标记完成、更新限制表、更新 test count |
| `docs/spec/current/release-notes.md` | 新增版本条目 |
| `docs/spec/current/api.md` | 新端点 |
| `docs/spec/current/sql.md` | 新表/迁移 |

每个文档顶部维护 `最后更新：YYYY-MM-DD`。

### 2.6 记录进度 (Ruflo)

```text
memory_store(key="last-task", value="{taskId, summary, testCount, timestamp}")
```

## 3. 阻塞处理

- 在 roadmap 限制表记录阻塞原因
- 跳到下一个非阻塞任务
- 不反复重试同一阻塞任务

## 4. 禁止事项

- 跳过质量门禁
- force push / 修改 git config
- 删除或修改 `.claude/settings.local.json`
- 提交构建产物
- 在设计未确认时大规模重构
- 引入未经项目使用的第三方库
- 修改已执行的 Flyway 迁移（只新增）
- **在本文档内记录任何项目状态**（测试数、版本号、文件路径、任务卡片）——这些属于 `docs/`
