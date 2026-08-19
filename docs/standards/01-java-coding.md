# 01 · Java 编码规范

> 适用范围：`server/` 全部 Java 代码（含测试）。  
> 读者：后端开发者与 AI。  
> 运行时：JDK 26 + Spring Boot 4.1.x。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Alibaba Java Coding Guidelines | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ | 命名、常量、OOP、集合、并发、注释、安全；格式（4 空格 / 120 列） |
| Alibaba P3C（规则实现） | https://github.com/alibaba/p3c | IDE / CI 扫描规则集 |
| Google Java Style Guide | https://google.github.io/styleguide/javaguide.html | UTF-8、禁止通配 import、单顶级类、`@Override` 必写、禁止忽略捕获异常 |
| Spring Boot 系统要求 | https://docs.spring.io/spring-boot/system-requirements.html | Boot 4.1.0 支持 Java 17–26；本项目锁定 JDK 26 |
| Spring Boot JSON（Jackson 3） | https://docs.spring.io/spring-boot/reference/features/json.html | Jackson 3 为默认；包名 `tools.jackson` |
| Spring 官方 Jackson 3 说明 | https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring | 禁止再引入 Jackson 2 `ObjectMapper` 作为主路径 |
| Jakarta Bean Validation 3.0 | https://jakarta.ee/specifications/bean-validation/3.0/jakarta-bean-validation-spec-3.0.html | 入参校验 |
| MyBatis-Plus | https://baomidou.com/ | Mapper / `@TableName` / `@Version` / `ASSIGN_ID` |
| Sa-Token | https://sa-token.cc/ | `@SaCheckPermission`、双 `StpLogic` |
| OWASP Top 10 | https://owasp.org/www-project-top-ten/ | 注入、失效鉴权、敏感数据 |

### 本项目覆盖（禁止按外部指南改回去）

| 外部条款 | 本项目决定 | 原因 |
|----------|-----------|------|
| Google Style 2 空格 / 100 列 | **4 空格 / 120 列** | 与 Alibaba §Formatting 及国内 Java 生态默认一致，Spotless/P3C 可直接执行 |
| Alibaba 表映射类后缀 `*DO` | 后缀 **`Entity`** | 见 [06-object-model.md](06-object-model.md)；避免与领域「领域对象」混淆 |
| Alibaba `gmt_create` / `gmt_modified` | Java 字段 `createdAt` / `updatedAt` | 对齐 design §3.1 列名 `created_at` / `updated_at` |
| 手写 `new ObjectMapper()` | **禁止** | RL-11：只经 `com.mkt.kernel.json.JsonUtil` |

## 2. 源文件基线

1. **MUST** 文件编码 UTF-8，换行 LF（Alibaba Formatting #8；Google §2.2）。
2. **MUST** 一个源文件恰好一个顶级类型（Google §3.4.1）。`record` / `enum` / `@interface` 同此规则。
3. **MUST NOT** 使用通配 import，静态与非静态都不行（Google §3.3.1）。
4. **MUST NOT** 使用 JDK 25+ 的 `import module`（Google §3.3.1.1）。
5. **MUST** 包声明单独一行、不折行。
6. **MUST** import 分组：静态一组、非静态一组，组间空一行，组内 ASCII 序（Google §3.3.3）。
7. **MUST NOT** 使用 Tab 缩进（Alibaba / Google 一致）。

## 3. 命名

采纳 Alibaba Naming Conventions #1–#10、#13。

| 对象 | 规则 | 正例 | 反例 |
|------|------|------|------|
| 包 | 全小写、单数、一级一词 | `com.mkt.task.engine` | `com.mkt.Task`、`com.mkt.utils` |
| 类 / 接口 | UpperCamelCase 名词 | `StepEngine`、`RewardPort` | `stepEngine`、`HTMLDto` |
| 方法 / 字段 / 参数 | lowerCamelCase | `grantSource`、`lockAndGet` | `Grant_Source`、`lock_and_get` |
| 常量 | `UPPER_SNAKE_CASE`，语义完整 | `MAX_PAGE_SIZE` | `MAX`、`FLAG` |
| 抽象类 | `Abstract*` 或 `Base*` | `BaseIntegrationTest` | `CommonTest` |
| 异常 | 以 `Exception` 结尾 | `BusinessException` | `BizError` |
| 测试类 | 被测名 + 后缀，见 [11-testing.md](11-testing.md) | `CycleKeyResolverTest` | `Test1` |
| 布尔字段 | **禁止** `is` 前缀（Alibaba Naming #8） | `deleted`、`simulated` | `isDeleted` |
| 实现类 | 接口 + `Impl`（Alibaba #13） | `RewardPortImpl` | `RewardPortService` |
| 枚举类型 | 本项目用 `XxxStatus` / `XxxType`，**不强制** `*Enum` 后缀 | `GrantSource`、`AccountStatus` | `GrantSourceEnum`（可以但不鼓励，与 design 枚举名冲突） |
| 禁止 | 拼音、中英混拼、`$`/`_` 首尾、生僻缩写 | — | `renwu`、`_name`、`AbsSvc` |

方法动词前缀（Alibaba Naming #15，本项目强制用于 Mapper / 仓储）：

| 前缀 | 语义 |
|------|------|
| `get` | 单条 |
| `list` | 多条 |
| `count` | 计数 |
| `insert` / `save` | 写入（Mapper 用 `insert`） |
| `update` | 更新 |
| `delete` / `remove` | 删除（逻辑删除用 `markDeleted`） |

领域服务方法用业务动词：`startInstance`、`advanceStep`、`grant`、`check`。不要把业务方法都叫 `process` / `handle` / `doXxx`。

## 4. 格式（Spotless 将按此执行）

来自 Alibaba Formatting #1–#7：

- K&R 大括号：`{` 不换行；`else` 与 `}` 同行。
- `if` / `for` / `while` / `switch` 与 `(` 之间一个空格；`(` 后、`)` 前无空格。
- 运算符两侧空格。
- 缩进 4 空格。
- 列宽 120；折行时运算符随下一行；`.` 随方法名换行；参数在逗号后断。
- `if` / `else` / `for` / `while` / `do` **即使单行也必须加大括号**（与 Google §4.1.1 一致，严于部分阿里示例）。

```java
// 正例
if (flag == 0) {
    stepEngine.enter(instance);
} else {
    return Result.fail(ErrorCodes.TASK_STEP_STATE_MISMATCH);
}
```

## 5. 类型、空值、集合

1. **MUST** 重写方法加 `@Override`（Alibaba OOP #2；Google §6.1）。
2. **MUST NOT** 忽略捕获的异常；至少记日志或换业务异常（Google §6.2；Alibaba Exception）。空 `catch` 禁止。
3. **MUST** 常量或确定非空对象调用 `equals`；优先 `Objects.equals`（Alibaba OOP #6）。
4. **MUST** 包装类型比较用 `equals`，不用 `==`（Alibaba OOP #7）。
5. **MUST NOT** 用 `==` / `equals` 比较浮点（Alibaba OOP #8）。金额用 `long` 分（design 成本字段 `costFen`）。
6. **MUST** 持久化对象与跨边界 DTO 的字段用包装类型；局部计数可用原语（Alibaba OOP #9）。
7. **MUST NOT** 给 Entity / Command / Response 字段赋业务默认值掩盖「未赋值」（Alibaba OOP #10）。框架或 DB 默认值除外（`deleted` 默认 0 由列默认值负责）。
8. **MUST** 禁止魔法值。状态、权限码、缓存命名空间、错误码走枚举或常量类（Alibaba Constant #1）。
9. **MUST** 长整型字面量用 `L` 不大写 `l`（Alibaba Constant #2）：`12L`。
10. **MUST** `ArrayList` / `HashMap` 指定初始容量当大小可预估。
11. **MUST NOT** 在 `foreach` 中 `remove`；用迭代器或 `removeIf`。
12. **MUST NOT** 把 `null` 塞进 `List.of` / `Map.of`（它们拒绝 null）。

## 6. 并发与事务

本项目并发策略见 design P4 / §5：第一道防线是 **DB 唯一约束 + 行级乐观锁**；分布式锁只用于无唯一约束可依托的场景。

1. **MUST** 领取、推进、发奖、积分入账的事务隔离级别为 **READ COMMITTED**（design §5.7）。在 kernel 事务模板统一声明，业务方法不要自行改隔离级别。
2. **MUST** 事务边界放在应用服务（`@Transactional`），Controller 禁止开事务。
3. **MUST NOT** 在 `@Transactional` 方法内发起 HTTP / RPC / 消息中间件直发（RL-07）。异步只写 Outbox。
4. **MUST** 乐观锁更新带 `version` 条件；更新计数 ≠ 1 视为冲突，按规格重读或失败，禁止静默覆盖。
5. **MUST** Redisson 锁：`tryLock(0, …)` 失败即跳过或返回冲突；`try/finally` 解锁。看门狗默认 30s（design §6.8）。
6. **MUST NOT** 用 `SimpleDateFormat` 做共享字段（线程不安全，Alibaba Concurrency）。时间用 `java.time`（`Instant` / `OffsetDateTime` / `LocalDate`）。
7. **MUST NOT** 使用 `finalize()`（Google §6.4）。
8. 虚拟线程：Boot 4 可开启。**MUST NOT** 在虚拟线程里做长时间占用的 `synchronized` 锁 IO；锁用 `ReentrantLock` 或 Redisson。不要把线程池当默认并发模型去「优化」虚拟线程。

## 7. Spring Boot 4 / 本栈专项

### 7.1 JSON

1. **MUST** 业务代码只通过 `com.mkt.kernel.json.JsonUtil` 序列化（RL-11）。
2. **MUST NOT** `new ObjectMapper()` / `new JsonMapper()` / Hutool `JSONUtil`。
3. Jackson 3 API 在 `tools.jackson.*`；注解仍在 `com.fasterxml.jackson.annotation.*`（Spring 官方迁移说明）。**MUST NOT** 混用 Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper` 作为运行时 mapper。

### 7.2 配置与时钟

1. **MUST** 业务配置只经 `ConfigService.getTyped`，禁止直读配置表（RL-11）。
2. **MUST** 时间比较与「现在」只注入 `Clock` Bean（design D-03）。测试用 `MutableClock`。禁止 `Instant.now()` / `System.currentTimeMillis()` 出现在业务语义路径。
3. **MUST** 密钥、数据源、Redis、会话、HMAC secret 只来自环境变量（NFR 安全 2；[14-deployment.md](14-deployment.md)）。

### 7.3 Web 层

1. **MUST** Controller 按 `controller.admin` / `controller.portal` / `controller.internal` 分包（RL-04）。
2. **MUST** 后台写接口具备 `@SaCheckPermission("<code>")` 或显式白名单（RL-10）。权限码只能来自 requirements 附录 B。
3. **MUST** 入参用 Jakarta Validation 注解；失败由全局处理器映射为 `common.param-invalid`（附录 C）。
4. **MUST** Controller 返回 `Result<T>`，不返回 Entity（见 06）。
5. **MUST NOT** Controller 内写业务规则、SQL、缓存、锁。只做：鉴权注解、参数接收、调用应用服务、返回。
6. **MUST** 全部非 GET 的 `/admin/**` 标注 `@Audited(module, action)`（design §6.5）。`module`/`action` 取对应域，不要自造模块名。GET **MUST NOT** 标。`/api/common/**` 与 `/internal/**` **MUST NOT** 标（R10.1）。失败登录无操作人时由 AOP 写 `operator_id=NULL`，不要在控制器里特判。

```java
@RestController
@RequestMapping("/admin/task/definitions")
@RequiredArgsConstructor
public class TaskDefinitionAdminController {

    private final TaskDefinitionAppService appService;

    @SaCheckPermission("task:definition:update")
    @Audited(module = "task", action = "definition-update")
    @PostMapping("/{id}")
    public Result<IdResponse> update(
            @PathVariable long id,
            @Valid @RequestBody TaskDefinitionSaveCommand command) {
        return Result.ok(new IdResponse(appService.save(id, command)));
    }
}
```

### 7.4 MyBatis-Plus

1. **MUST** 实体带 `@TableName("task_definition")`，字段 `@TableField` 仅在驼峰无法默认映射时使用。
2. **MUST** 高写入表 `evt_event_log`、`task_progress_report` 使用 `ASSIGN_ID`；其余默认 DB 自增（design §3.1）。
3. **MUST** 先分清列语义，再决定是否走乐观锁：
   - **发布/快照版本号**（`task_definition.version`、实例绑定的发布版本）**不是**乐观锁列，禁止加 `@Version`、禁止在 UPDATE 里 `version + 1` 当 CAS。
   - **行级乐观锁列**（目前仅 `task_instance_step.version`，design §5.1.2）只走下面两条之一，禁止混用导致 SET 里 `version` 加两次：
     - **默认**：实体 `@Version` + MyBatis-Plus 乐观锁插件。`updateById` / `UpdateWrapper` **MUST NOT** 再手写 `version = version + 1`。
     - **原子 CAS 例外**（步骤完成等条件更新）：只在 Mapper XML 写 `SET version = version + 1 WHERE … AND version = #{version} AND status = 'ACTIVE'`。入参 **MUST** 为 `@Param` 基本类型，**MUST NOT** 传入带 `@Version` 的 Entity。
   - **库存扣减**（`rwd_prize`）**没有** `version` 列。SQL 以 design §5.7 为准：`remaining_stock = remaining_stock - ? WHERE id=? AND remaining_stock >= ?`。详见 [02-sql.md](02-sql.md) §8。
4. **MUST** XML / 注解 SQL 全部参数化。禁止字符串拼接 SQL。
5. **MUST NOT** 在域模块放 `*.sql` 资源（RL-09）。
6. **MUST NOT** 跨域 import 另一域的 Mapper / Entity（RL-03）。
7. **MUST** 逻辑删除仅用于 design 指定的四张表：`sys_admin_user`、`sys_portal_user`、`task_definition`、`rwd_prize`。其它表禁止加 `@TableLogic`。
8. **SHOULD** 简单 CRUD 用 BaseMapper；复杂条件、条件更新、批量、分区 SQL 写在 Mapper XML，便于审查执行计划。

### 7.5 依赖与工具库

1. **MUST** 版本只出现在父 POM / `dependency-matrix.md`。子模块禁止私自写版本号。
2. Hutool **只允许** `hutool-core` + `hutool-crypto`（脱敏、HMAC、日期辅助；矩阵已锁定）。**MUST NOT** 引入 `hutool-json`、`hutool-http`、`hutool-db`。
3. HMAC 用 Hutool `SecureUtil.hmacSha256`（在 `hutool-crypto`）或 JDK `Mac`，输出小写 hex（R15.2）。比较 **MUST** 用常量时间（`MessageDigest.isEqual`）。
4. 密码 **MUST** 用 `BCryptPasswordEncoder`，cost ≥ 12（R1.5）。禁止 MD5/SHA 存密码。
5. **MUST NOT** 引入 Spring Security 完整过滤器链；只保留 `spring-security-crypto`。

### 7.6 缓存命名空间与 Redis 键

权威：design §6.2 / §6.3 / §6.7；R9.1。业务只经 `PlatformCache`。**MUST NOT** 发明新命名空间或键前缀。

非 Cache 的 Redis 事实键（验证码 / 限流 / 名单投影 / nonce / 踢人原因）**封闭全集 = design §3.10**，本表不重复。需要新前缀必须先改 §3.10。

封闭缓存命名空间（新增必须先改 R9.1）：

| 命名空间 | 谁管 | 注意 |
|----------|------|------|
| `dict` / `config` | PlatformCache | 变更即失效 |
| `rbac:permission` | PlatformCache | |
| `task:snapshot` / `task:published-index` / `task:crowd` | PlatformCache | `task:crowd` 的 L2 是 Redis SET |
| `risk:rule` | PlatformCache | |
| `identity:user-attr` | PlatformCache | `lockAndGet` 不走缓存 |
| `ad:position` | PlatformCache | **P0 占位 / P1 接线**。任务 15 只登记 ns；禁止广告读写。任务 48 才写 L2 与 evict |
| `identity:session` | **Sa-Token 自管** | **禁止**经 `PlatformCache.evict` / `/admin/system/cache/evict` 清理（R9.2）。踢下线走 R6 |

Cache / 锁以外、实现时必须按 §3.10 原样使用的键（禁止改拼写）：

| 前缀 | 用途 |
|------|------|
| `<namespace>:<业务键>` | Cache L2 |
| `captcha:{id}` | 验证码 |
| `rl:{dim}:{key}` | 限流滑窗 |
| `lock:` / `sched:` / `outbox:relay:admin` / `outbox:relay:portal` | 锁；领取 = `lock:rwd-claim:{recordId}` |
| `risk:list:{dim}:{type}:{value}` | 名单投影 |
| `risk:cnt:` | 风控滑窗 ZSET；**R-e 禁止写入** |
| `nonce:{appId}:{nonce}` | internal 防重放 |
| `session:kick-reason:{loginType}:{token}` | 踢下线原因（D-02） |
| `cache:evict` | L1 失效广播频道 |

## 8. 注释与 Javadoc

采纳 Alibaba Code Comments + Google §7，收紧为：

1. **MUST** 公共 API（`platform-contract` 端口、kernel 对外类型、跨模块服务接口）写 Javadoc：一句话摘要 + 参数/返回/抛出。
2. **MUST** 注释写 *为什么* 与规格编号（`R14.5`、`RL-07`、`D-11`），不写「给 i 加 1」。
3. **MUST NOT** 用注释关闭检查或留下 `TODO` 而不建任务。允许的标记：`TODO(task-N):` 指向 `tasks.md` 编号。
4. **MUST NOT** 大段注释掉的死代码入库。
5. 中文注释允许，且本项目 **SHOULD** 用中文写业务意图（团队与规格语言一致）。标识符必须英文。

```java
/**
 * 领取事务内锁定门户用户行并返回画像。不读不写缓存。
 * @see design §2.2.3 UserAttributePort.lockAndGet
 */
UserAttributes lockAndGet(long userId);
```

## 9. 安全编码（OWASP + Alibaba Security + NFR 安全）

1. **MUST** 所有外部输入视为不可信：HTTP body/query/header、internal HMAC 正文、埋点 payload、导入文件。
2. **MUST** SQL 参数化（OWASP A03 Injection）。
3. **MUST** 表达式引擎只走 AviatorScript AST 白名单（design §5.10）；禁止 `eval`、SpEL、反射执行用户字符串。
4. **MUST** 后台写操作校验 CSRF 头 `X-CSRF-Token`（R1.13）。
5. **MUST NOT** 日志、审计摘要、事件属性输出密码、令牌、secret、银行卡明文。脱敏用 Hutool `DesensitizedUtil`，规则见 R10.6。
6. **MUST NOT** 存在可关闭鉴权的配置开关（RL-10）。`local` profile 免登不得打进生产构建。
7. **MUST** 生产关闭 springdoc UI（design §2.3.3）。
8. 活动富文本（P1）**MUST** 服务端 HTML 白名单消毒后再落库（R22）。
9. 写路径评审清单见 [05-security.md](05-security.md)，不要只读本节。

## 10. 禁止清单（AI 高频失误）

| 禁止 | 正确做法 |
|------|----------|
| `System.out.println` | 结构化日志（见 10） |
| `e.printStackTrace()` | 全局处理器 + 日志 |
| `catch (Exception e) {}` | 记录或转换为 `BusinessException` |
| 吞掉乐观锁失败 | 返回规格中的冲突/重试错误码 |
| 新造 `ObjectMapper` | `JsonUtil` |
| 域间互相 `@Autowired` 对方 Service | 走 Port 或 Outbox |
| `Thread.sleep` 等待 Outbox | 测试用 `awaitOutboxDrain()` |
| 在实体上暴露 `passwordHash` 到 API | 投影到 Response，敏感列排除（design §4.1） |
| 使用 `Date` / `Calendar` | `java.time` + `Clock` |
| 为「方便」引入 Guava / Apache Commons 重复能力 | 先 JDK，再 hutool-core / hutool-crypto，再新增依赖评审 |

## 11. 执行

| 机制 | 覆盖 |
|------|------|
| **Spotless**（Alibaba / 120 列 + 4 空格 + 禁通配 import） | 格式、import。**不**并行再上 Checkstyle 同一规则 |
| P3C IDEA 插件（开发期） | 命名、OOP、集合 |
| ArchUnit RL-01~12 | 分层、鉴权、SQL 位置、JsonUtil、`@Audited` 存在性可作补充 |
| 编译器 `-Xlint`（可选） | `@Override`、忽略异常 |
| CI：Spotless + 单测/属性/ArchUnit 红则阻断 | NFR 可维护性 3 |

## 12. AI 检查清单

生成或修改 Java 文件后，逐项自检：

- [ ] 包名属于正确 Maven 模块，未跨域 import Mapper/Entity
- [ ] 无通配 import、无 Tab、列宽 ≤ 120
- [ ] 新类型遵循 06 的后缀与转换方向
- [ ] 未 `new ObjectMapper` / 未用 Hutool JSON
- [ ] 时间来自 `Clock`，金额为分（`long`）
- [ ] 写路径有事务边界且无远程调用
- [ ] 后台写接口有权限注解；非 GET `/admin/**` 有 `@Audited`；门户/internal 无
- [ ] 错误码已在域枚举登记，能指回 design §4 该端点；同分段新原因走 09 §8，未新造域前缀
- [ ] 缓存命名空间 / Redis 键来自 §7.6 封闭清单；未 evict `identity:session`
- [ ] 乐观锁未混用插件与 XML +1
- [ ] 日志无密钥，异常未空 catch
