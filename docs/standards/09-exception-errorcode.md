# 09 · 异常与错误码规范

> 适用范围：kernel 异常模型、全局处理器、各域错误码。  
> 权威：requirements 附录 C；design §2.9、§3.9、§4.1。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Alibaba Java Coding Guidelines · Exception and Logs | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ | 不吞异常；不用于流程控制；捕获要小；禁止打印堆栈到输出流 |
| Google Java Style §6.2 | https://google.github.io/styleguide/javaguide.html#s6.2-caught-exceptions | 捕获后必须处理 |
| RFC 9110 | https://www.rfc-editor.org/rfc/rfc9110 | 状态码语义 |
| Zalando #151 / #177 / #153 | https://opensource.zalando.com/restful-api-guidelines/#151 | 文档化错误；禁止堆栈；429 + Retry-After |
| Jakarta Bean Validation | https://jakarta.ee/specifications/bean-validation/3.0/ | 校验异常映射 |
| Sa-Token 异常 | https://sa-token.cc/ | 未登录 / 无权限异常接入全局处理器 |

本项目**不采用** RFC 9457 Problem Details 作为对外形状。对外永远是附录 C 的 `Result`。

## 2. 异常分层

```text
Throwable
  ├─ BusinessException          // 可对用户展示，携带 ErrorCode
  │    ├─ RetryableGrantException      // 发放可重试，调用方整级联回滚（design §2.2.3）
  │    └─ PermanentGrantException      // PRIZE_DISABLED / PRIZE_DELETED / USER_INVALID
  ├─ 框架异常
  │    ├─ MethodArgumentNotValidException / ConstraintViolationException
  │    ├─ NotLoginException / NotPermissionException（Sa-Token）
  │    └─ MissingRequestHeaderException 等
  └─ 未预期
       └─ RuntimeException / Error → 500 common.server-error
```

1. **MUST** 业务拒绝抛 `BusinessException(ErrorCode)`（或子类），不要抛裸 `RuntimeException("互斥")`。
2. **MUST NOT** 用异常做正常分支（Alibaba）：例如用异常表示「幂等命中」。幂等命中返回已有 `GrantResult.hitIdempotent=true`。
3. **MUST NOT** 在领域服务捕获后空处理。要么上抛，要么转成规格中的结果对象（`RiskCheckPort` 用 `RiskVerdict`，**不抛业务异常**）。
4. **MUST NOT** 在 Controller 写 try/catch 拼 `Result.fail`。统一走 `@RestControllerAdvice`。
5. 检查异常：新代码 **SHOULD** 不声明 checked exception。第三方 checked 在边界转换成业务或包装。
6. `Error` / `InterruptedException`：后者 **MUST** 恢复中断标志再包装，禁止吞掉。

```java
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String messageOverride) {
        super(messageOverride);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.message(), cause);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String messageOverride, Throwable cause) {
        super(messageOverride, cause);
        this.errorCode = errorCode;
    }
}
```

`Result.message` **MUST** 只来自 `errorCode.message()` 或显式 `messageOverride`（面向用户的文案，如锁定剩余分钟）。  
**MUST NOT** 把 `cause.getMessage()` / `e.toString()` 当作 `messageOverride`。包装底层异常用 `(ErrorCode, Throwable)` 或三参形式，cause 只进日志。

## 3. ErrorCode

### 3.1 格式

```text
<域>.<场景>.<原因>
```

- 全小写，段内用连字符。
- 公共码两段：`common.<原因>`（design §3.9）。
- 正例：`task.claim.mutex-blocked`、`auth.login.locked`、`internal.sign.invalid-signature`。
- 反例：`TASK_CLAIM_MUTEX`、`10001`、`error.task.claim.mutex-blocked`。

### 3.2 分段权威

1. **域与场景** 以 design §3.9 表为准。
2. **原因值拼写** 以 design §4 逐端点清单为唯一权威（§3.9 原文）。不一致时听 §4。
3. 同一分段内新增原因 **可以** 不改 §3.9 表，但 **MUST** 写入该端点的 §4 清单或任务契约，并在枚举中登记。
4. **MUST NOT** 新增域前缀（禁止 `order.*`、`sys.*`）。P1 域 `ad` / `signin` / `activity` 已预留。

### 3.3 公共码（封闭）

| code | HTTP | 含义 |
|------|------|------|
| `common.param-invalid` | 400 | Bean Validation 或通用参数 |
| `common.permission-denied` | 403 | 后台无权限 |
| `common.not-found` | 404 | 资源不存在 |
| `common.rate-limited` | 429 | 限流 |
| `common.server-error` | 500 | 未预期 |

管理端通用错误另加 `auth.session.invalid`（401）。这些码适用于全部管理端点，不必在每个端点重复罗列（design §4.1）。

### 3.4 代码中如何声明

kernel 定义接口，各域枚举实现：

```java
public interface ErrorCode {
    String code();       // task.claim.mutex-blocked
    int httpStatus();    // 400
    String message();    // 默认可展示文案
}

public enum TaskErrorCodes implements ErrorCode {
    CLAIM_MUTEX_BLOCKED("task.claim.mutex-blocked", 400, "当前有进行中的互斥任务"),
    CLAIM_DAILY_LIMIT("task.claim.daily-limit", 400, "今日领取次数已达上限");
}
```

1. **MUST** 枚举常量与 code 字符串同时存在，禁止只在 throw 处写字面量。
2. **MUST** HTTP 状态与附录 C 表一致。
3. C 端文案 **MUST** 面向用户；**MUST NOT** 包含规则编号、灰度桶、SQL、类名（R34.6）。
4. 管理端文案可以更具体，但仍禁止堆栈与密钥。
5. `auth.login.locked` 的 message **MUST** 含剩余分钟数（R1.3）。用 `messageOverride` 构造。

## 4. 全局处理器

`platform-kernel` 提供唯一 `@RestControllerAdvice`。

| 异常 | 映射 |
|------|------|
| `BusinessException` | `errorCode.httpStatus` + `errorCode.code` + message |
| `MethodArgumentNotValidException` / `BindException` / `ConstraintViolationException` | 400 `common.param-invalid`，message 含字段级信息（design §4.1） |
| `NotLoginException` | 后台 401 `auth.session.invalid`；门户按踢下线原因四码 |
| `NotPermissionException` | 403 `common.permission-denied` |
| `NoHandlerFoundException` / 命名空间守卫 | 404，可无业务体或 `common.not-found` |
| 限流拦截 | 429 + `Retry-After` + `common.rate-limited` 或更具体码 |
| 其它 | 500 `common.server-error`，message 统一「服务异常，请稍后重试」，日志打完整堆栈 + traceId |

1. **MUST** 所有分支写入 `traceId`。
2. **MUST** 500 路径记 ERROR 日志；业务拒绝记 WARN 或 INFO，不打完整堆栈（除非需要排障的编程错误）。
3. **MUST NOT** 把 `e.getMessage()` 原样给 C 端，若 message 来自底层 JDBC/Redis。
4. 生产 **MUST NOT** 开启 Spring `include-stacktrace`。

## 5. 端口与异常

| 端口 | 异常策略 |
|------|----------|
| `RewardPort.grant` | 可重试 → `RetryableGrantException`（调用方回滚级联）；永久 → `PermanentGrantException`（封闭枚举）；规则链业务异常原样上抛 |
| `RiskCheckPort.check` | **不抛业务异常**；`REJECT` / `SILENT_REJECT` / `MARK` / `PASS` |
| `UserAttributePort` | 用户不存在不抛；`accountStatus=NOT_FOUND` |

调用方 **MUST** 按上表处理，禁止把 `RiskVerdict.REJECT` 再包成未登记错误码。C 端映射用规格给定的 `risk.blocked.generic` 等，不暴露具体规则。

## 6. 校验失败细节

Bean Validation 失败 message 格式：

```text
fieldName: 约束说明; otherField: 约束说明
```

1. 字段名用 JSON camelCase。
2. **MUST NOT** 把对象整个 `toString` 塞进 message。
3. 文件导入、批量接口按规格返回「结果报告」，不要对第一行失败就只给一个 `param-invalid` 而无行号——若规格要求报告。

## 7. 日志与异常（与 10 篇衔接）

Alibaba Exception 要点落地：

1. 捕获后 **MUST** 留下现场：`log.warn("grant failed, prizeId={}, userId={}, traceId={}", …, e)`。
2. **MUST NOT** 既打日志又抛出时丢失 cause：需要包装时用 `throw new BusinessException(code, e)`（`(ErrorCode, Throwable)`）。业务异常的 `message` 仍是错误码文案，**不要**把 `e.getMessage()` 暴露给客户端；日志打完整 cause。
3. **MUST NOT** `e.printStackTrace()`。
4. 跨线程（虚拟线程 / 调度）：未捕获异常处理器 **MUST** 记 ERROR + traceId（调度无请求时用任务名）。

## 8. 新增错误码流程

1. 确认 §3.9 已有域.场景；只加原因值。
2. 写入该端点的 design §4 清单（或任务里的契约补丁，随后合回 design）。
3. 在域枚举登记 code、HTTP、默认文案。
4. 集成测试断言该端点失败分支的 HTTP + code。
5. OpenAPI `@ApiResponse` 更新。
6. **MUST NOT** 复用其它域的 code 字面量。

## 9. AI 检查清单

- [ ] 抛出的是 `BusinessException` + 已登记 `ErrorCode`
- [ ] code 格式与 §3.9 / §4 一致
- [ ] HTTP 映射符合附录 C
- [ ] C 端文案无内部原因
- [ ] Controller 无本地 try/catch 转 Result
- [ ] `RiskCheckPort` 路径没有用异常表示拒绝
- [ ] 500 不泄漏异常原文
- [ ] 429 带 Retry-After
