# 过滤器表达式安全规则

## 概述

过滤器表达式由运营在管理后台配置，运行时由 `FilterExpressionEngine` 对 `UserContext` 评估。

- 引擎：QLExpress 3.3，内嵌运行，严格沙箱限制
- 必须通过 `FilterExpressionEngine` 评估，禁止直接调用 QLExpress `ExpressRunner`
- 参见 `backend/src/main/java/com/marketing/task/service/filter/FilterExpressionEngine.java`

## 安全设计 (OWASP 视角)

过滤器表达式本质上是**用户输入的可执行代码**，属于 OWASP A03:2021（Injection）风险域。安全策略采用**纵深防御**：

1. **主防线 —— QLExpress 沙箱**：`new ExpressRunner(false, false)` 创建时关闭了类型加载和脚本模式，表达式只能调用已注册的函数和运算符
2. **辅助防线 —— 禁用关键字**：关键字的正则匹配阻止常见的反射和代码注入路径
3. **纵深防线 —— 资源限制**：长度限制和超时控制防止 DoS 攻击

**关键安全原则**：
- 最小权限：只注册表达式必需的函数到白名单，不暴露多余能力
- 默认拒绝：未在白名单中的函数一律不可用
- 安全失败：超时或异常时返回 `false`（拒绝访问而非放行）

## 白名单函数

| 函数 | 用途 | 参数 | 示例 |
|---|---|---|---|
| `inProvince` | 检查省份是否在列表中 | 省份列表(数组或单值) | `inProvince(['BJ', 'SH'])` |
| `hasTag` | 检查是否有指定标签 | 标签(字符串) | `hasTag('vip')` |
| `hasAnyTag` | 检查是否有任一标签 | 标签列表(数组) | `hasAnyTag(['vip', 'new_user'])` |
| `roleEquals` | 检查角色精确匹配 | 角色(字符串) | `roleEquals('admin')` |
| `roleIn` | 检查角色是否在列表中 | 角色列表(数组) | `roleIn(['admin', 'operator'])` |
| `inAllowlist` | 检查是否在白名单中 | ID 列表(数组) | `inAllowlist(['uid1', 'uid2'])` |
| `notInDenylist` | 检查是否不在黑名单中 | ID 列表(数组) | `notInDenylist(['uid3'])` |
| `orgEquals` | 检查组织精确匹配 | 组织(字符串) | `orgEquals('org_001')` |
| `orgIn` | 检查组织是否在列表中 | 组织列表(数组) | `orgIn(['org_001', 'org_002'])` |
| `levelGte` | 检查等级 >= 阈值 | 阈值(数字) | `levelGte(5)` |
| `levelEq` | 检查等级精确匹配 | 等级(数字) | `levelEq(10)` |

`inAllowlist` 和 `notInDenylist` 当前抛 `BusinessException("函数暂未实现")`，名单数据源尚未接入（P1 计划）。

## 新增函数规则

1. 必须在 `FilterExpressionEngine.registerFunctions()` 中显式注册：`runner.addFunction("name", new Operator() { ... })`
2. 通过 `current()` 获取 ThreadLocal 中的 `UserContext`（使用 ThreadLocal 是为了在 QLExpress 的静态 Operator 中获取上下文）
3. 通过 `arg(list, index)` 安全提取参数（处理越界和类型）
4. 集合成员判断使用 `containsArg()` / `intersects()` 辅助方法
5. 必须补充单元测试（至少 1 个正例 + 1 个反例），见 `FilterExpressionEngineTest`
6. 更新本文档的白名单函数表
7. 新函数不能引入任何外部 I/O（HTTP 调用、文件读写、数据库查询均禁止）——过滤器必须在 100ms 内纯内存计算完成

## 禁用关键字

`FilterExpressionEngine` 的 `FORBIDDEN` 正则：

```
System|Runtime|Process|Thread|Class\.forName|import\s|new\s|exec|eval
```

| 关键字 | 阻止的攻击向量 |
|---|---|
| `System` | `System.exit()` / `System.getProperty()` |
| `Runtime` | `Runtime.getRuntime().exec()` 命令执行 |
| `Process` | Process 对象操作 |
| `Thread` | 线程创建和操作 |
| `Class.forName` | 反射加载任意类 |
| `import\s` | 导入恶意类 |
| `new\s` | 实例化任意对象 |
| `exec` | 命令执行 |
| `eval` | QLExpress 内部的二次 eval |

匹配时（大小写不敏感）抛 `BusinessException("过滤表达式包含禁用关键字")`。

## 长度和超时限制

| 限制 | 值 | 违反行为 |
|---|---|---|
| 最大长度 | 1024 字符 | 抛 `BusinessException("过滤表达式长度不能超过1024字符")` |
| 执行超时 | 100 毫秒 | 返回 `false`（安全失败，不阻塞调用方） |

超时通过 `CompletableFuture.supplyAsync(...).get(100, TimeUnit.MILLISECONDS)` 实现：
- 内部使用 ForkJoinPool.commonPool() 执行，超时后 `TimeoutException` 被捕获并返回 `false`
- 注意：`CompletableFuture.get()` 超时不会取消正在执行的任务（任务继续执行但不影响结果）
- 生产化建议：使用 `ExecutorService` + `Future.cancel(true)` 以支持中断

## 校验流程

1. **保存时校验**（Admin API）：`FilterExpressionEngine.validate(expression)` → 检查非空 → 长度 → 禁用关键字 → 不通过抛 `BusinessException`。此时**不会**真正执行表达式（不需要 UserContext）。
2. **运行时评估**（C 端任务列表）：`FilterExpressionEngine.evaluate(expression, userContext)` → validate → `ExpressRunner.execute()` → 返回 boolean
3. 运行时意外错误（非预期的 parse error、NPE 等）返回 `false`，不抛异常。**但必须记录日志**——当前 `execute()` 的 `catch (Exception)` 覆盖过宽且未打日志，应缩小 catch 范围或增加 `log.warn`。

## 表达式缓存建议

当前每次过滤评估都调用 `runner.execute()` 重新解析表达式。对于生产环境中大量用户同时查询任务列表的场景，建议：

- 在任务**发布时**预编译表达式：`runner.getInstructionSet(expression)` 返回指令集缓存
- 运行时直接执行指令集，跳过 parse 阶段
- 缓存 key 为 `taskId`（表达式修改后失效）
- 预期收益：减少 CPU 消耗，降低过滤评估延迟

## 审计日志建议

为了排查运营配置错误和安全事件，建议增加审计日志：

- 保存过滤器时：`log.info("Filter saved: taskId={}, expression={}", taskId, expression)`
- 表达式校验失败时：`log.warn("Filter validation failed: taskId={}, reason={}", taskId, ex.getMessage())`
- 运行时评估失败时（当前静默返回 false）：`log.warn("Filter evaluation failed: expression={}, userId={}", expression, userContext.getUserId())`

## 测试要求

- 每个白名单函数必须有正例和反例测试
- 组合表达式测试（多个函数 AND 组合）
- 每个禁用关键字的拒绝测试
- 边界情况：null、空字符串、纯空格、恰好 1024 字符、1025 字符
- 超时测试（构造一个死循环或极长计算来验证 100ms 超时）
- 安全回归测试：每新增一个禁用关键字时，补对应测试
- 测试类：`FilterExpressionEngineTest`

## 相关文档

- `backend/layering.md` — 缓存配置、Configuration 类
- `backend/java-style.md` — 异常处理（禁止吞异常）、日志规范
- `api-rules.md` — API 响应格式、错误码
