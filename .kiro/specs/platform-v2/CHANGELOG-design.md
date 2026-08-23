# 设计文档版本差异

> 代理只读 [design.md](design.md) 终态（总册 + 分册），不必读本文件。本文件只记相对上一版的差异。

| 版本 | 相对 | 差异 |
|------|------|------|
| v2.16 | v2.15 | §4.9.3 `PrizeCardView` 补 `obtainedAt`（获得时间；取 grant `createdAt`，无则 `grantedAt`）。 |
| v2.15 | v2.14 | §4.2 补 `GET /admin/identity/roles/{id}/permissions`（`identity:role:query`，出参 `{permissionIds}`），供分配权限回填当前权限集。 |
| v2.14 | v2.13 | §4.9.0 匿名清单补活动 list/detail GET 与任务 list/detail GET（可选登录，公开项；灰度/过滤对访客隐藏）；领取/参与/start 仍登录。C 端 `/home` `/activity` 可逛。 |
| v2.13 | v2.12 | 按章拆为 `design-architecture` / `design-schema` / `design-api` / `design-algorithm`；总册 §0.2 用 `<!-- §x.y -->` 锚点（不行号）。`ad:position` 明确 P0 占位、任务 48 接线。对账/风控补 feasibility。 |
| v2.12 | v2.11 | SCHEDULED 手动提前发布（R12.1）；§5.9 注册/登录不跑 R-a–R-f；灰度排除包；重复 click/callback 200 幂等；progress processing=400；R5.6 只读门面 D-13；403 拦截器补审计；`portal_route` 8 值；现网 Redis 6.0.8 注记。 |
| v2.11 | v2.10 | §2.3.3 actuator 暴露改为 Boot 实际键（`probes.enabled` + `include=health,prometheus`）；就绪是 health group，不是独立 endpoint。 |
| v2.10 | v2.9 | D-11 Outbox 按 `producer` 分 Relay、声明了消费方向的事件空消费者不得 DELETE；D-12 `UserAttributePort.lockAndGet` + `accountStatus`，领取禁止直查用户表；补发关原单；§6.4 补 R-b/R-c/R-d 计数消费；审计仅 `/admin/**`；会话禁整空间 evict。 |
| v2.9 | v2.8 | 对账补发门禁（D-10）：分类/奖品 `recon_action_policy`、差异项核渠、`TIMEOUT`/`SENDING` 强制人工；`MANUAL_GRANT` 永不自动；匹配平台集含 `FULFILL_FAILED`；履约失败原因封闭。 |
| v2.8 | v2.7 | R-e 改为 GRANT+elapsedSeconds（同请求新建跳过，不写 risk:cnt）；RiskSubject/GrantContext 扩字段；RL-02 删除独立 points 模块例外；P1 域骨架 §3.11；匿名边界测试口径修正。 |
| v2.7 | v2.6 | D-02 门户 401 四码与 R4.3 对齐（废 `forced-offline`，补 `missing`）；后台 401 统一 `auth.session.invalid`；CSRF Cookie + data 同值；领取链 / 互斥 SQL / 级联发奖分支 / 对账 PENDING 时机与需求对齐。 |
