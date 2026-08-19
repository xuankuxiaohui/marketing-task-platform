# 05 · 安全检查单

> 适用范围：所有后端写路径、鉴权、密钥、审计、门户展示。  
> 权威：NFR 安全 1–8；RL-10；R1 / R4 / R6 / R9.2 / R10 / R15 / R34.6。  
> 本篇是**机械清单**，不重新定义规格。细节仍以 requirements / design 为准。  
> **编码层唯一细则**：审计 Outbox、`identity:session` 禁 evict、`@Audited` 面。AGENTS / standards README 只保留一行指针，改这里即可。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| OWASP Top 10 | https://owasp.org/www-project-top-ten/ | 注入、失效鉴权、敏感数据、越权 |
| OWASP Cheat Sheet · Secrets | https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html | 密钥不入库 |
| 本仓库 01 / 02 / 07 / 09 / 10 / 12 / 14 | 本目录 | 落地条款 |

## 2. 鉴权

1. **MUST NOT** 存在可关闭鉴权的环境变量或配置开关（RL-10）。`local` 免登不得打进生产构建。
2. 后台写：**MUST** `@SaCheckPermission`（或显式白名单）+ `X-CSRF-Token`。
3. 门户：Bearer `client:`。交叉令牌 **MUST** 401（R4 属性 1）。
4. 门户 401 四码不得合并：`auth.session.missing` / `expired` / `kicked-concurrent` / `kicked-admin`。后台 401 只有 `auth.session.invalid`。
5. 匿名端点封闭清单以 design §4.9.0 为准。新增必须先改规格。越界命名空间 **MUST** 404，不进鉴权（RL-08）。
6. 权限码只能来自附录 B。前端隐藏不是安全边界。

## 3. 审计

design §6.5 机械规则，编码层再写一次：

| 面 | `@Audited` |
|----|------------|
| 非 GET `/admin/**` | **MUST** 标，`module`/`action` 取域 |
| GET `/admin/**` | **MUST NOT** |
| `/api/common/**`、`/internal/**` | **MUST NOT** |

失败登录：`operator_id` 可空。摘要：JsonUtil → 脱敏 → 截断 2000。不得把密码、令牌、HMAC 原文写入摘要。CSRF 缺失/不一致 → **403**。

落库与事务（R10.3 / design §6.5）：

1. **MUST** 经 `EventPublisher.append("audit.log", …)` 与业务同事务写入 Outbox，Relay 异步落 `sys_audit_log`。**MUST NOT** 在请求线程里直接 insert 审计表。
2. 成功、业务失败、异常抛出都记（R10 属性 1）。事务回滚则无审计行。
3. Sa-Token 拦截器阶段的 403（R2.3）**MUST** 仍能落审计：用过滤器 / 拦截器后置补 `audit.log`，不能只靠方法上的 `@Audited`。
4. `REQUIRES_NEW` 只允许这两处：design §5.6.2 发放可重试失败留痕；design §5.9 命中 `risk_hit_log` + `risk.hit.recorded`（主事务回滚不丢命中）。评审不得误杀，也不得在别处滥用。

## 4. 会话与缓存

1. 踢下线只走 R6（`StpLogic.logout`）。
2. **MUST NOT** 经 `PlatformCache.evict` 或 `/admin/system/cache/evict` 清理 `identity:session`（R9.2）。命中该空间 → `system.cache.session-forbidden`。
3. 缓存命名空间封闭清单见 [01-java-coding.md](01-java-coding.md) §7.6。禁止发明 `user:info` 一类键。

## 5. 注入与表达式

1. SQL 全部 `#{}`。动态列名白名单。禁止用户输入进 `ORDER BY` / `LIMIT`。
2. 表达式只走 AviatorScript AST 白名单（design §5.10）。禁止 `eval`、SpEL、反射执行用户字符串。
3. 活动富文本（P1）服务端 HTML 白名单后再落库（R22）。前端 **MUST NOT** `v-html` 未消毒字段。

## 6. 越权（IDOR）

1. 门户读/写实例、奖品记录、积分流水 **MUST** 校验归属 `userId == 当前登录用户`，禁止只信路径 id。
2. 管理端按权限码，不按「知道 id」。
3. internal 只认 HMAC 四头 + 登记的 `appId`，不认伪造的用户头。

## 7. 密钥与内部接口

1. 密钥只来自环境变量。仓库只提交示例。生产日志 **MUST NOT** 打印环境变量全集。
2. internal：`X-App-Id` `X-Timestamp` `X-Nonce` `X-Sign`。`X-Trace-Id` 透传但 **MUST NOT** 进签名串。
3. nonce 窗口内重复 **MUST** 拒绝且不改业务状态。Redis 故障时防重放 **拒绝**（不可 fail-open）。
4. 密钥轮换支持双密钥窗口，禁止「先删后加」。
5. Nginx **MUST NOT** 把 `/internal` 暴露到公网。`actuator` / `prometheus` 仅内网。
6. Redis **MUST** 用 DB 2。默认 db0 会打到共享机上其它应用的会话。

## 8. 敏感数据

禁止出现在日志、审计摘要、埋点属性、Response：

- 密码、验证码答案、BCrypt 哈希
- 会话令牌、CSRF 明文（可记「已校验」）
- HMAC secret、签名原文
- 银行卡、证件号；手机/邮箱按 `DesensitizedUtil` + R10.6

C 端 **MUST NOT** 展示风控规则编号、灰度未命中原因（R34.6）。用规格给定的用户文案。

## 9. 限流与验证码

1. 登录/注册/验证码/用户名可用性：IP + 账号桶。超限 429 + `Retry-After`。
2. 限流 Redis 故障 fail-open，但必须打 `mkt.degrade` + 告警（10 §8）。
3. 验证码错误按 R32.2 刷新，答案不进日志。

## 10. AI / 评审检查清单

- [ ] 无鉴权开关；后台写 = 权限 + CSRF + `@Audited`
- [ ] 门户/internal 未标 `@Audited`；审计走 Outbox，未同步 insert
- [ ] 拦截器 403 仍有审计行（R2.3）
- [ ] 未 evict `identity:session`
- [ ] 门户写路径校验资源归属
- [ ] SQL / 表达式 / HTML 无注入面
- [ ] 新密钥在 `.env.example`，不在仓库
- [ ] Redis DB = 2；`/internal` 不进公网 Nginx
- [ ] C 端文案无内部原因；日志无秘密
- [ ] 匿名端点仍在 design §4.9.0 清单内
