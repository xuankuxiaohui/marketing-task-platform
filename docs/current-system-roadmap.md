最后更新：2026-05-29

## 1. 当前限制

| 限制 | 当前状态 | 风险 |
|---|---|---|
| 真实鉴权 | Sa-Token 多账号 JWT 已实现，SSO/OAuth2 扩展点已预留 | 尚未对接外部用户中心/单点登录 |
| 真实发奖 | reward_record 表 + 幂等 + 失败重试已实现，handler 暂为模拟（无外部API） | 外部奖励系统对接后再替换 handler 实现 |
| CRON 调度 | `TaskCycleScheduler` 每 5 分钟扫描并激活新周期 | 尚未实现批量预创建用户实例 |
| 平台适配器 | Adapter 已注册，detail() 已通过 PlatformAdapterRegistry 合并 step + stepPlatform | IOS/Android/Miniapp adapter 均为默认实现 |

## 2. 后续计划

暂无待完成计划
