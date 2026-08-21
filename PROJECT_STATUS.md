# PROJECT_STATUS
> 阶段：**v1 测试阶段**（尚未 preview） / 基线：master `6d03ef5` / 更新：2026-08-21

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**`）。`/internal/**` 不对外。

## 现在做到哪

- v1 已上 master `6d03ef5`。当前是**测试阶段**，不是 preview，不是 GA。
- 打开的 issue：[#69](https://github.com/xuankuxiaohui/marketing-task-platform/issues/69)、[#70](https://github.com/xuankuxiaohui/marketing-task-platform/issues/70) 为 P1，[#71](https://github.com/xuankuxiaohui/marketing-task-platform/issues/71) 为 P2。本 docs PR 之前 open PR = 0。远端只有 `master`。
- 进行中：领取 / 步骤 / 发奖 / 风控 常规测。
- 腾讯云 Lighthouse 演示箱另走，不挡 v1。

## 待修 P1（不是「记账不修」）

1. #69 互斥只扫已发布任务，OFFLINE 但仍 IN_PROGRESS 的组员拦不住新领取（R13.6 / 架构：互斥周期读已发布快照 vs R11.6 未删除定义全集）
2. #70 下线不重算在途实例 expire_at（R14.10）

## 也已知

- callback/progress 级联 GRANT 不带 IP/设备，这两条上 R-f 空（#71）
- CrowdPort / 看板直读他域表

## 下一步（最多 3 步）

1. 修 #69 / #70
2. 继续 v1 条款测
3. 演示箱另走，不挡本仓库 v1
