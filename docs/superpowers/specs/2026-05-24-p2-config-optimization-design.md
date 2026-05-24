# P2 管理后台配置优化设计规格

日期: 2026-05-24 | 版本: v1.0 | 分支: main

## 1. 目标

重构管理后台三大配置痛点：互斥组不可视、步骤编辑体验差、端配置不完整（缺少步骤级配置和独立操作）。

## 2. 模块 1：互斥组实体化

### 2.1 数据库

```sql
CREATE TABLE mutex_group (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(256),
    scope       VARCHAR(32)  NOT NULL DEFAULT 'SAME_CYCLE',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- task 表
ALTER TABLE task ADD COLUMN mutex_group_id BIGINT;
ALTER TABLE task ADD INDEX idx_mutex_group (mutex_group_id);
-- 迁移: mutex_group_key → 创建 mutex_group + 设置 mutex_group_id
-- 迁移后: ALTER TABLE task DROP COLUMN mutex_group_key;
```

### 2.2 后端

- 新建 `MutexGroup` entity, mapper, service, controller
- API: CRUD `/admin/mutex-groups` + `GET /admin/mutex-groups/{id}/tasks`
- `TaskService.checkMutex()` 改为按 `mutex_group_id` 查询，支持 `SAME_CYCLE` / `FULL_LIFECYCLE` 两种 scope

### 2.3 前端

- 新建 `MutexGroupList.vue` (`/mutex-groups`): 表格(名称/描述/scope/任务数) + 新建/编辑弹窗
- 新建 `MutexGroupDetail.vue` (`/mutex-groups/:id`): 组信息 + 组内任务列表
- `BasicTab.vue`: `mutexGroupKey` 文本输入 → `<el-select>` 下拉(含"新建互斥组"入口)
- `TaskList.vue`: 增加"互斥组"列，可点击跳转详情

## 3. 模块 2：步骤配置重构

### 3.1 后端

新增端点:
- `GET /admin/task/{id}/steps/check-code?code=xxx&excludeStepId=xxx` → 返回 `{ valid: boolean }`
- `PUT /admin/task/{id}/steps/reorder` → body: `[{id, seq}, ...]`，批量更新排序

### 3.2 前端 StepsTab 改造

- 从 `<el-table>` 改为**卡片列表** (`vuedraggable` + `el-card`)
- 每张卡片: 序号、code、name、type 标签、编辑/删除按钮
- 拖拽排序后调 reorder API 持久化
- **编辑弹窗**: 垂直表单布局，包含:
  - code (带实时唯一性校验，红色提示)
  - name, type, description
  - type 动态表单: PROGRESS→targetValue, CALLBACK→callbackEventKey, REWARD→prizeId选择
  - **extraJson 结构化编辑器**: Key-Value 表格(预设常用 key + 自由输入 + 值类型切换)

## 4. 模块 3：端配置重构

### 4.1 数据库

```sql
ALTER TABLE task_step_platform
  ADD COLUMN action_type   VARCHAR(32) COMMENT 'NONE|CLAIM_REWARD|OPEN_URL|NATIVE_SCHEME|MINIAPP_PATH|SHARE',
  ADD COLUMN action_config VARCHAR(512);
```

### 4.2 操作枚举

| 操作 | 说明 | 参数 |
|------|------|------|
| NONE | 无额外操作 | — |
| CLAIM_REWARD | 手动领奖 | — |
| OPEN_URL | 打开链接 | `{"url":"..."}` |
| NATIVE_SCHEME | 唤起原生App | `{"scheme":"..."}` |
| MINIAPP_PATH | 小程序路径 | `{"path":"..."}` |
| SHARE | 分享 | `{"title":"...","image":"..."}` |

### 4.3 后端

- 扩展 `TaskStepPlatform` entity 增加 actionType/actionConfig
- `POST/PUT /admin/task/{id}/step-platforms` 批量保存步骤级平台配置
- C 端 `TaskStepVO.applyPlatformConfig()` 合并 action 字段
- 新增 `StepActionHandler` 策略: CLAIM_REWARD→调 PrizeService.grant()

### 4.4 前端

- 新建 `PlatformConfig.vue` (作为第五个 tab 或替代现有 PlatformsTab)
- 布局: 顶部平台 tabs(WEB/iOS/Android/小程序) + 任务级默认配置 + 步骤级覆盖卡片列表
- 步骤卡片: 按钮文案(input) + 主操作(select: NONE/CLAIM_REWARD/...) + 辅助按钮配置(可选跳转)
- 继承机制: 未覆盖字段灰色显示继承自任务级默认值，可点"覆盖"激活编辑

### 4.5 C 端渲染

- `TaskDetail.vue`: 步骤按钮点击时检查 actionType → CLAIM_REWARD 调发奖接口
- 辅助按钮: 若配置了 jumpType/jumpTarget，展示第二个按钮

## 5. 实施顺序

1. 模块 1: 互斥组实体化
2. 模块 2: 步骤配置重构
3. 模块 3: 端配置重构
