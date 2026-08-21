# PROJECT_STATUS
> 阶段：**测试阶段**（preview 之前，版本未到 0.0.1） / 基线：master `6d03ef5` / 更新：2026-08-21

## 项目一句话

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。两端账号隔离。双应用：admin-app（`/admin/**`）、portal-app（`/api/**`）。`/internal/**` 不对外。

## 现在做到哪

- 已走出 0→1 叠链口径。任务 29–49 **已在** master `6d03ef5`，不是「29–49 未合」。
- 主干 `6d03ef5` 是 0→1 第一刀，还没过测审。当前是**测试阶段**（preview 之前，版本未到 0.0.1）。
- 打开的 issue：[#69](https://github.com/xuankuxiaohui/marketing-task-platform/issues/69)、[#70](https://github.com/xuankuxiaohui/marketing-task-platform/issues/70) 为 P1 待修，[#71](https://github.com/xuankuxiaohui/marketing-task-platform/issues/71) 为 P2。远端只有 `master`。新工作从 `origin/master` 开 `fix/<slug>`，先测后审；除非点名，不要合 master。修完再接下一条。
- 进行中：领取 / 步骤 / 发奖 / 风控 常规测。
- 腾讯云 Lighthouse 演示箱另走，还没起来，不挡本仓库测试。

## 待修 P1（不是「记账不修」）

1. #69 互斥扫不到 OFFLINE 在途：互斥只扫已发布任务，OFFLINE 但仍 IN_PROGRESS 的组员拦不住新领取（R13.6）
2. #70 下线不重算 expire_at：任务下线后在途实例 expire_at 仍按领取窗口（R14.10）

## 也已知

- callback/progress 级联 GRANT 不带 IP/设备，这两条上 R-f 空（#71，P2）
- CrowdPort / 看板直读他域表

## 下一步（最多 3 步）

1. 从 `origin/master` 开 `fix/<slug>` 修 #69 / #70
2. 继续领取 / 步骤 / 发奖 / 风控 常规测
3. 演示箱另走，不挡测试
