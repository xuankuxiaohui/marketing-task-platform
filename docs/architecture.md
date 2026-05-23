# 自动化营销任务系统当前架构流程图

日期：2026-05-22

本文档基于当前代码整理，覆盖系统定位、核心流程图、已完成内容、当前限制和后续计划。

## 1. 系统定位

本系统是一个自动化营销任务平台，用于让运营配置任务，让 C 端用户按任务步骤完成行为，并在任务完成后触发奖励发放。

当前代码已经形成三个入口：

| 入口 | 使用方 | 主要职责 |
|---|---|---|
| `admin-web` + `/api/admin/**` | 运营后台 | 配置任务、步骤、过滤器、平台入口、发布/下线任务、查询用户实例 |
| `client-web` + `/api/client/**` | C 端用户 | 查看可参与任务、创建/查看任务实例、点击推进任务步骤 |
| `/api/internal/**` | 外部业务系统 | 回调 CALLBACK 步骤、上报 PROGRESS 进度 |

## 2. 总体架构流程图

```mermaid
flowchart LR
    AdminUser[运营人员] --> AdminWeb[admin-web<br/>Vue + Element Plus]
    ClientUser[C 端用户] --> ClientWeb[client-web<br/>Vue + Vant]
    BizSystem[外部业务系统] --> InternalApi[/Internal API/]

    AdminWeb --> AdminApi[/Admin API/]
    ClientWeb --> ClientApi[/Client API/]

    AdminApi --> TaskService[TaskService]
    ClientApi --> TaskService
    InternalApi --> StepAdvanceEngine[StepAdvanceEngine]
    ClientApi --> StepAdvanceEngine

    TaskService --> FilterEvaluator[FilterEvaluator<br/>QLExpress]
    TaskService --> CycleKeyResolver[CycleKeyResolver]
    TaskService --> DefinitionCache[TaskDefinitionCacheService<br/>Caffeine]
    StepAdvanceEngine --> DefinitionCache
    StepAdvanceEngine --> RewardService[RewardService]
    RewardService --> RewardHandlers[Point/Coupon/Badge Handler]

    TaskService --> DB[(MySQL)]
    DefinitionCache --> DB
    StepAdvanceEngine --> DB
    AdminApi --> DB
```

## 3. 核心领域模型

```mermaid
erDiagram
    TASK ||--o{ TASK_STEP : contains
    TASK ||--o{ TASK_FILTER : filters
    TASK ||--o{ TASK_PLATFORM : exposes
    TASK_STEP ||--o{ TASK_STEP_PLATFORM : adapts
    TASK ||--o{ USER_TASK_INSTANCE : creates
    USER_TASK_INSTANCE ||--o{ USER_TASK_STEP_PROGRESS : records
    TASK_STEP ||--o{ USER_TASK_STEP_PROGRESS : progresses

    TASK {
        bigint id
        string code
        string name
        string period_type
        string status
        int version
        string mutex_group_key
    }

    TASK_STEP {
        bigint id
        bigint task_id
        int seq
        string type
        int target_value
        string callback_event_key
        string reward_config_json
    }

    USER_TASK_INSTANCE {
        bigint id
        string user_id
        bigint task_id
        int task_version
        string cycle_key
        string status
        int current_step_seq
    }
```

## 4. Admin 配置与发布流程

```mermaid
sequenceDiagram
    actor Operator as 运营
    participant AdminWeb as admin-web
    participant AdminApi as AdminTaskController
    participant TaskService as TaskService
    participant Cache as TaskDefinitionCacheService
    participant DB as MySQL

    Operator->>AdminWeb: 编辑任务主体/步骤/过滤器/端配置
    AdminWeb->>AdminApi: POST /api/admin/task<br/>TaskAggregateDTO
    AdminApi->>TaskService: saveAggregate(dto)
    TaskService->>DB: insert/update task
    TaskService->>DB: delete + reinsert steps
    TaskService->>DB: delete + reinsert filters
    TaskService->>DB: delete + reinsert platforms
    TaskService->>Cache: evict(taskId)
    TaskService-->>AdminApi: saved task
    AdminApi-->>AdminWeb: Result.ok

    Operator->>AdminWeb: 发布任务
    AdminWeb->>AdminApi: POST /api/admin/task/{id}/publish
    AdminApi->>TaskService: publish(id)
    TaskService->>DB: status=PUBLISHED, version+1
    TaskService->>Cache: evict(taskId)
```

补充：当前还保留了步骤、过滤器、端配置的独立 CRUD API；这些写操作已补充缓存失效，避免 C 端读取旧定义。

## 5. C 端任务参与流程

```mermaid
sequenceDiagram
    actor User as C 端用户
    participant ClientWeb as client-web
    participant ClientApi as ClientTaskController
    participant TaskService as TaskService
    participant Filter as FilterEvaluator
    participant Cycle as CycleKeyResolver
    participant StepEngine as StepAdvanceEngine
    participant DB as MySQL

    User->>ClientWeb: 打开任务列表
    ClientWeb->>ClientApi: GET /api/client/task/list<br/>X-User-* headers
    ClientApi->>TaskService: listPublished(userContext)
    TaskService->>DB: 查询 PUBLISHED 且在时间窗口内的任务
    TaskService->>Filter: 逐个执行 enabled filter
    Filter-->>TaskService: true/false
    TaskService-->>ClientApi: 可见任务列表
    ClientApi-->>ClientWeb: tasks

    User->>ClientWeb: 进入任务详情或开始任务
    ClientWeb->>ClientApi: GET /api/client/task/{taskId}
    ClientApi->>TaskService: requireTask(taskId)
    ClientApi->>TaskService: getOrCreateInstance(task, userContext)
    TaskService->>TaskService: 互斥组校验
    TaskService->>Cycle: 生成 cycle_key
    TaskService->>DB: 查询或创建 user_task_instance
    TaskService->>StepEngine: enter(instance)
    StepEngine->>DB: 自动级联 PASSIVE/REWARD
    ClientApi-->>ClientWeb: instance + steps + stepPlatforms
```

## 6. 步骤推进流程

```mermaid
flowchart TD
    Start(["实例进入 StepAdvanceEngine"]) --> LoadSteps["从缓存加载 task steps"]
    LoadSteps --> FindCurrent{"找到 currentStepSeq?"}
    FindCurrent -->|"否"| Completed["标记实例 COMPLETED"]
    FindCurrent -->|"是"| Type{"当前步骤类型"}

    Type -->|"PASSIVE"| CompletePassive["完成当前步骤"]
    CompletePassive --> NextSeq["进入下一 seq"]
    NextSeq --> FindCurrent

    Type -->|"REWARD"| Reward["调用 RewardService"]
    Reward --> RewardOk{"有匹配奖励处理器?"}
    RewardOk -->|"否"| RewardFail["抛业务异常，事务回滚"]
    RewardOk -->|"是"| CompleteReward["完成奖励步骤"]
    CompleteReward --> Rewarded["标记实例 REWARDED"]

    Type -->|"CLICK"| WaitClick["标记 IN_PROGRESS，等待 C 端点击"]
    WaitClick --> ClickApi["POST /api/client/task/:taskId/step/:stepId/click"]
    ClickApi --> CompleteClick["完成 CLICK 步骤"]
    CompleteClick --> FindCurrent

    Type -->|"CALLBACK"| WaitCallback["标记 IN_PROGRESS，等待 internal callback"]
    WaitCallback --> CallbackApi["POST /api/internal/task/callback"]
    CallbackApi --> CompleteCallback["校验 eventKey 并完成"]
    CompleteCallback --> FindCurrent

    Type -->|"PROGRESS"| WaitProgress["标记 IN_PROGRESS，等待 progress 上报"]
    WaitProgress --> ProgressApi["POST /api/internal/task/progress"]
    ProgressApi --> ReachTarget{"进度是否达到目标值?"}
    ReachTarget -->|"否"| KeepProgress["更新进度并保持 IN_PROGRESS"]
    ReachTarget -->|"是"| CompleteProgress["完成 PROGRESS 步骤"]
    CompleteProgress --> FindCurrent
```

## 7. 内部回调与进度上报流程

```mermaid
sequenceDiagram
    participant Biz as 外部业务系统
    participant Internal as InternalCallbackController
    participant DB as MySQL
    participant Engine as StepAdvanceEngine

    Biz->>Internal: POST /api/internal/task/callback<br/>instanceId 或 userId+taskId+cycleKey
    Internal->>DB: 定位 user_task_instance
    Internal->>Engine: callback(instance, callbackEventKey)
    Engine->>DB: 校验当前步骤为 CALLBACK 且 eventKey 匹配
    Engine->>DB: 写 user_task_step_progress
    Engine->>Engine: 继续级联后续步骤

    Biz->>Internal: POST /api/internal/task/progress
    Internal->>DB: 定位 user_task_instance
    Internal->>Engine: progress(instance, stepId, progressValue)
    Engine->>DB: 写入或更新进度
    Engine->>Engine: 达标后完成步骤并继续级联
```