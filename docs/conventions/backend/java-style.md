# 后端 Java / Spring Boot 编码规范

> 基于[阿里巴巴 Java 开发手册](https://github.com/alibaba/p3c)（v1.4+）。各节标注了对应的手册章节号。

## 目录

1. [命名规范](#命名规范-manual-1)
2. [代码格式](#代码格式-manual-15)
3. [POJO 设计](#pojo-设计-manual-41)
4. [常量定义](#常量定义-manual-13)
5. [注释规范](#注释规范-manual-16)
6. [依赖注入](#依赖注入)
7. [异常处理](#异常处理-manual-51)
8. [日志规范](#日志规范-manual-53)
9. [集合处理](#集合处理-manual-42)
10. [校验规范](#校验规范)
11. [测试规范](#测试规范)
12. [合规检查清单](#合规检查清单)

---

## 命名规范 (Manual §1)

**类命名**（UpperCamelCase）：
- Controller: `{Audience}{Resource}Controller`（如 `AdminTaskController`）
- Service: `{Domain}Service`（如 `TaskService`）；引擎类例外 `StepAdvanceEngine`
- Mapper: `{Entity}Mapper`（如 `TaskMapper`）
- DTO: 必须加后缀 `DTO`、`Request`、`Response`、`VO`，如 `TaskAggregateDTO`、`CallbackRequest`
- Entity: 与表名对应（`Task`、`TaskStep`），不加后缀，通过 `domain/entity/` 包区分

**方法命名**（lowerCamelCase）：
- 获取单个: `getXxx` / `requireXxx`（不存在时抛异常）
- 列表查询: `listXxx`
- 保存/更新: `saveXxx` / `updateXxx`
- 删除: `deleteXxx`
- 布尔判断: `isXxx` / `hasXxx` / `matchXxx`
- 状态变更: `markXxx`（如 `markInProgress`、`markCompleted`）

**包命名**：全小写，无下划线。`com.marketing.task.controller.admin` 符合规范。

**当前合规情况**：命名整体良好。Entity 主键缺少 `@TableId` 显式声明（应补上）。

## 代码格式 (Manual §1.5)

| 规则 | 要求 |
|---|---|
| 缩进 | 4 空格，禁止 Tab |
| 行宽 | 建议不超过 120 字符 |
| 大括号 | Egyptian 风格（左大括号同行，右大括号独占一行） |
| 空行 | 不同逻辑块之间空一行 |
| 空格 | 运算符两侧、逗号后、`if`/`for`/`while` 后加空格 |

**import 顺序**：`static import` → `第三方包`（按字母序）→ `项目内包`

## POJO 设计 (Manual §4.1)

**Entity vs DTO/VO 分离**：

Controller 层只接收 DTO/Request、只返回 VO/Response。Entity 不得越过 Service 层暴露到 Controller 层。

| 对象类型 | 职责 | 所在层 | 示例 |
|---|---|---|---|
| Entity | 数据库映射 | Service / Mapper | `Task`, `UserTaskInstance` |
| DTO | 接口输入（请求体） | Controller 入参 | `TaskAggregateDTO`, `CallbackRequest` |
| VO | 接口输出（响应体） | Controller 返回值 | `TaskAdminVO`, `TaskClientVO` |

**为什么禁止 Controller 返回 Entity**：
1. **安全**：Entity 的内部字段不应暴露给客户端
2. **解耦**：数据库 Schema 变化不应直接破坏 API 契约
3. **数据塑形**：前端需要的数据结构往往与数据库结构不同
4. **JSON 序列化风险**：JPA/MyBatis 关联代理序列化时可能触发意外 SQL 或循环引用

**当前状态**：**已修复（2026-05-23）**。所有 Controller 已改为返回/接收 VO，DTO 已不再持有 Entity。详见 `domain/vo/` 包和 `backend/layering.md`。

**Lombok 使用原则**：
- Entity：推荐 `@Getter` + `@Setter` 而非 `@Data`。`@Data` 生成的 `equals()`/`hashCode()` 包含所有字段，Entity 放入集合时可能导致意外行为。
- DTO/VO：可用 `@Data`，因为通常只做传输，不进入长生命周期集合。
- 当前所有 Entity 均使用了 `@Data`，建议逐步替换。

**数据类 vs 业务类**：
- 数据类（POJO）不应包含业务逻辑。当前违反：`RewardConfig` 既是 `@Data` POJO 又包含 `ObjectMapper` 实例和 `parse()` 解析逻辑。建议将解析逻辑抽到独立工具类。

## 常量定义 (Manual §1.3)

- **禁止魔法值**：任何硬编码的数字和字符串（除 0、1、-1 等公认值外）都应定义为命名常量。
- 当前违反：
  - `Result.ok()` 中 `code = 0` 和 `message = "ok"` 是魔法值
  - 错误码 `400`、`401`、`404`、`500` 在多处重复出现
  - `FilterExpressionEngine` 中超时 `100` 毫秒是魔法数字
- 建议：
  - 创建 `ErrorCode` 枚举统一管理错误码
  - 创建 `Constants` 类存放公共常量（超时、长度限制等）

## 注释规范 (Manual §1.6)

- **所有 public 类、接口、枚举、public 方法都必须有 Javadoc**（当前：整个代码库零 Javadoc，严重不符合）
- 注释应解释 **WHY**（为什么这样做），而非 WHAT（做了什么）
- TODO 注释需要标注负责人和日期：`// TODO(tanhh, 2026-06-01): 对接真实积分系统 API`
- 类注释包含：作者、创建日期、职责说明
- 推荐用 `@Schema(description = "...")` 给 API 字段补充文档（当前已有部分使用）

## 依赖注入

- **强制使用构造器注入**（Manual §4.3 推荐），禁止 `@Autowired` 字段注入
- 使用 Lombok `@RequiredArgsConstructor` 简化构造器，字段声明为 `private final`
- 当前：**100% 合规**，零 `@Autowired` 字段注入

## 异常处理 (Manual §5.1)

`BusinessException`：
- 继承 `RuntimeException`，含 `int code` 和 `String message`
- 单参构造默认 code=400

`GlobalExceptionHandler`（`@RestControllerAdvice`）：
1. `BusinessException` → `Result.fail(ex.getCode(), ex.getMessage())`
2. `MethodArgumentNotValidException` → `Result.fail(400, 第一条校验错误)`
3. `Exception` → `Result.fail(500, ex.getMessage())`（兜底）

**关键规则**：
- **禁止 `catch (Exception)`** 在业务代码中捕获过于宽泛的异常。当前违反：
  - `FilterExpressionEngine.execute()` catch 所有异常返回 false，会掩盖 NPE 等严重 bug
- **禁止吞异常**：catch 块必须至少打日志。当前违反：
  - `RewardConfig.parse()` catch `JsonProcessingException` 未打日志
- 错误码当前为硬编码整数。P0 计划引入 `ErrorCode` 枚举。

## 日志规范 (Manual §5.3)

**框架**：SLF4J + Logback，通过 Lombok `@Slf4j` 注入。

**日志级别**：
| 级别 | 场景 |
|---|---|
| ERROR | 系统错误，需要人工介入 |
| WARN | 可恢复的异常，需要关注 |
| INFO | 关键业务操作（任务发布、实例创建、发奖） |
| DEBUG | 开发调试信息（缓存命中、步骤进入/退出） |

**规则**：
- 使用 `{}` 占位符，禁止字符串拼接（当前合规）
- **禁止打印敏感数据**：当前 `PointRewardHandler`、`CouponRewardHandler`、`BadgeRewardHandler`、`LogRewardService` 均直接 log 了 `step.getRewardConfigJson()`，应移除或脱敏
- 打印异常时必须传 Throwable 对象：`log.error("msg", exception)`

## 集合处理 (Manual §4.2)

- 判断空集合用 `isEmpty()`，不用 `size() == 0`
- 空返回用 `Collections.emptyList()` 而非 `new ArrayList<>()`
- 遍历用 `forEach` / `stream()` 而非原始 for 循环
- `stream().map().filter().collect()` 链式调用优于多步骤临时变量
- `Collectors.toMap()` 必须提供 merge 函数处理 key 重复

## 校验规范

- Jakarta `@Valid`：用于 Controller `@RequestBody` 参数
- DTO 字段使用 `@NotBlank` / `@NotNull` 等注解
- 编程式校验：Service 层手动 null/blank 检查，抛 `BusinessException`
- 复杂业务校验应抽取为独立方法（`checkMutex()` 模式）

## 测试规范

- 框架：JUnit 5 + Mockito（`@ExtendWith(MockitoExtension.class)`）
- 测试类可见性：package-private（不加 `public`）
- 依赖注入：`@BeforeEach` 中手动 `new ServiceUnderTest(mock1, mock2, ...)`，不使用 `@InjectMocks`
- Mock 注解：`@Mock` 声明依赖，`@Captor` / `ArgumentCaptor` 捕获参数
- MyBatis-Plus 表元信息必须在 `@BeforeAll` 中初始化：

```java
@BeforeAll
static void initMybatisPlus() {
    MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
    TableInfoHelper.initTableInfo(assistant, Task.class);
}
```

- 方法命名：描述性英文（如 `callback_shouldBeIdempotent`）
- 断言风格：JUnit 5 `assertThrows` / `assertEquals` / `assertTrue`
- Mock 验证：`verify(mock, times(n)).method()` / `verify(mock, never()).method()`
- 测试组织：一个测试类对应一个 Service/组件，同包路径放在 `src/test/java` 下

---

## 合规检查清单

| 阿里手册章节 | 规则 | 当前状态 | 行动 |
|---|---|---|---|
| §1.1 类命名 | UpperCamelCase，DTO/Request 后缀 | 合规 | — |
| §1.2 方法命名 | lowerCamelCase，动词前缀 | 合规 | — |
| §1.3 常量命名 | UPPER_CASE + 下划线，无魔法值 | 合规 | ErrorCode 枚举已引入（2026-05-24） |
| §1.5 代码格式 | 4 空格、120 字符行宽、括号风格 | 合规 | — |
| §1.6 注释 | public 类和方法要有 Javadoc | **部分合规** | 补齐 auth 模块 public 方法 Javadoc |
| §4.1 POJO | Entity 用 @Getter/@Setter 非 @Data | 合规 | AdminUser、ClientUser 已改为 @Getter/@Setter（2026-05-24） |
| §4.1 POJO | DTO 不直接持有 Entity | **已修复** | — |
| §4.1 POJO | Controller 不返回/接收 Entity | **已修复** | — |
| §4.1 POJO | 数据类不混入业务逻辑 | **违反** | 拆分 RewardConfig |
| §4.2 集合 | isEmpty()，stream()，空集合返回 | 合规 | — |
| §4.3 DI | 构造器注入 | **完全合规** | — |
| §5.1 异常 | 禁止 catch Exception，禁止吞异常 | **违反** | FilterExpressionEngine、RewardConfig |
| §5.3 日志 | SLF4J + {}，不打印敏感数据 | 部分合规 | 移除 reward config JSON 日志 |
| §6 数据库 | snake_case，idx_/uk_，禁止 SQL 拼接 | 合规 | 新 Entity 已有 @TableId |

## 已知待改进

| 项目 | 建议 | 状态 |
|---|---|---|
| 错误码 | 引入 `ErrorCode` 枚举，划分 4xx/5xx 段 | 已完成（2026-05-24） |
| Auth 服务包结构 | AdminAuthService / ClientAuthService 移至 `service/auth/` | 已完成（2026-05-24） |
| Javadoc | 至少为 Controller 和 public Service 方法补齐 | 进行中 |
| RewardConfig | 将解析逻辑抽到独立工具类 | 待处理 |
| Entity @Data | 新 Entity（AdminUser、ClientUser）已用 @Getter/@Setter | 已完成 |
| 异常吞没 | FilterExpressionEngine、RewardConfig 补日志 + 缩小 catch 范围 | 待处理 |
| 敏感日志 | 4 个 handler 移除或脱敏 rewardConfigJson | 待处理 |
| 集成测试 | 增加 `@SpringBootTest` 端到端测试 | 待处理 |
| Auth 模块测试 | AdminAuthService / ClientAuthService / CaptchaService 单测 | 待补充 |

## 相关文档

- `backend/layering.md` — 包结构、分层架构
- `backend/database.md` — 数据库规范、实体映射
- `backend/filter-safety.md` — 过滤器安全规则
- `backend/step-advancement.md` — 步骤推进规则
- `api-rules.md` — API 响应格式、错误码
