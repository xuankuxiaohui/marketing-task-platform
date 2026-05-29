# docs 目录重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 `docs/spec/current/` 从按版本堆叠改为按模块组织，新增功能清单，清理 roadmap

**Architecture:** 纯文档重构，不涉及代码变更。按 task/prize/activity/signin/system 五个模块拆分 API 和 SQL 文档，新增 features.md 作为精简功能清单，清理 roadmap 中的已完成内容。

**Tech Stack:** Markdown

---

## 文件结构总览

```
docs/spec/current/
├── features.md              # 新建：功能清单
├── api/                     # 新建目录
│   ├── task.md              # 任务模块 API
│   ├── prize.md             # 奖品模块 API
│   ├── activity.md          # 活动模块 API
│   ├── signin.md            # 签到模块 API
│   └── system.md            # 系统模块 API
└── sql/                     # 新建目录
    ├── task.md              # 任务模块 SQL
    ├── prize.md             # 奖品模块 SQL
    ├── activity.md          # 活动模块 SQL
    ├── signin.md            # 签到模块 SQL
    └── system.md            # 系统模块 SQL
```

删除：`api.md`、`sql.md`、`release-notes.md`
修改：`current-system-roadmap.md`、`CLAUDE.md`

---

### Task 1: 创建 features.md

**Files:**
- Create: `docs/spec/current/features.md`

- [ ] **Step 1: 创建 features.md**

每个功能点一行，按模块分组，不展开细节。

- [ ] **Step 2: 提交**

---

### Task 2: 创建 api/task.md

**Files:**
- Create: `docs/spec/current/api/task.md`

- [ ] **Step 1: 创建 api/task.md**

从 `api.md` 提取任务相关 + 代码中未覆盖的接口：
- 任务 CRUD（分页、详情、聚合保存、发布、下线、软删除）
- 批量发布/下线、定时发布/取消、复制、版本历史
- 步骤 CRUD + 拖拽排序 + code 校验
- 过滤器 CRUD + 表达式校验
- 平台配置 CRUD + 步骤平台配置
- 条件分支（transitions）
- 互斥组 CRUD + 移除关联
- C 端：列表、详情、开始、点击
- Internal：回调、进度
- DTO 结构

- [ ] **Step 2: 提交**

---

### Task 3: 创建 api/prize.md

**Files:**
- Create: `docs/spec/current/api/prize.md`

- [ ] **Step 1: 创建 api/prize.md**

- Admin：列表、创建、更新、启用/禁用、详情、记录（按奖品/全局）、补发
- Client：记录列表、领取、记录详情

- [ ] **Step 2: 提交**

---

### Task 4: 创建 api/activity.md

**Files:**
- Create: `docs/spec/current/api/activity.md`

- [ ] **Step 1: 创建 api/activity.md**

- Admin：列表、详情、创建、更新、删除、展示规则、发布、下线、退回草稿、子模块
- Client：列表、详情、展示规则、参与

- [ ] **Step 2: 提交**

---

### Task 5: 创建 api/signin.md

**Files:**
- Create: `docs/spec/current/api/signin.md`

- [ ] **Step 1: 创建 api/signin.md**

- Admin：配置 CRUD、发布/下线、统计、记录、积分流水、手动发放
- Client：配置列表、签到、补签、日历、状态、余额、流水

- [ ] **Step 2: 提交**

---

### Task 6: 创建 api/system.md

**Files:**
- Create: `docs/spec/current/api/system.md`

- [ ] **Step 1: 创建 api/system.md**

- 鉴权：Admin 登录、Client 注册/登录、Mock 模式
- 用户管理：Admin/Client 用户 CRUD
- 监控：dashboard、任务/活动维度 summary/daily
- 模拟测试：全部端点
- 实例查询 + 事件日志
- 审计日志
- 验证码

- [ ] **Step 2: 提交**

---

### Task 7: 创建 sql/task.md

**Files:**
- Create: `docs/spec/current/sql/task.md`

- [ ] **Step 1: 创建 sql/task.md**

迁移：V1, V2, V4, V5, V7, V8, V9, V11, V12, V14, V15, V16
内容：迁移索引 + 关键 DDL + 字段说明

- [ ] **Step 2: 提交**

---

### Task 8: 创建其余 sql/ 文件

**Files:**
- Create: `docs/spec/current/sql/prize.md`
- Create: `docs/spec/current/sql/activity.md`
- Create: `docs/spec/current/sql/signin.md`
- Create: `docs/spec/current/sql/system.md`

- [ ] **Step 1: sql/prize.md** — V6, V20
- [ ] **Step 2: sql/activity.md** — V18, V19, V21
- [ ] **Step 3: sql/signin.md** — V17
- [ ] **Step 4: sql/system.md** — V3, V10, V13
- [ ] **Step 5: 提交**

---

### Task 9: 删除旧文件

**Files:**
- Delete: `docs/spec/current/api.md`
- Delete: `docs/spec/current/sql.md`
- Delete: `docs/spec/current/release-notes.md`

- [ ] **Step 1: 删除**
- [ ] **Step 2: 提交**

---

### Task 10: 清理 roadmap

**Files:**
- Modify: `docs/current-system-roadmap.md`

- [ ] **Step 1: 删除所有已完成（删除线 ✅）内容，保留当前限制表格和未完成计划**
- [ ] **Step 2: 提交**

---

### Task 11: 更新 CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

- [ ] **Step 1: 更新"任务 → 必读文档"表格中 spec/current 描述**
- [ ] **Step 2: 提交**

---

### Task 12: 验证

- [ ] **Step 1: 检查目录结构** — `find docs/spec/current/ -type f | sort`
- [ ] **Step 2: 确认无残留旧文件**
