# 后端 Java / Spring Boot 编码规范

本文件只写稳定规范，不记录当前代码是否合规。

## 命名规范

| 类型 | 规则 | 示例 |
|---|---|---|
| Controller | `{Audience}{Resource}Controller` | `AdminTaskController` |
| Service | `{Domain}Service`，引擎类可按职责命名 | `TaskService`, `StepAdvanceEngine` |
| Mapper | `{Entity}Mapper` | `TaskMapper` |
| Entity | 与表名对应，不加后缀 | `Task`, `UserTaskInstance` |
| Request | HTTP 请求体 | `LoginRequest`, `TaskCreateRequest` |
| Response | 稳定 HTTP 响应契约 | `LoginResponse` |
| VO | 页面视图聚合或展示模型 | `TaskAdminVO`, `TaskClientVO` |
| DTO | 内部跨层传输、快照、批处理 | `TaskSnapshotDTO` |

方法命名：

- 获取单个：`getXxx` / `requireXxx`。
- 列表查询：`listXxx` / `pageXxx`。
- 保存和更新：`saveXxx` / `updateXxx`。
- 删除：`deleteXxx`。
- 布尔判断：`isXxx` / `hasXxx` / `matchXxx`。
- 状态变更：用业务动作命名，例如 `publish`、`offline`、`markCompleted`。

包名全小写，不使用下划线。

## 代码格式

- 缩进 4 空格，禁止 Tab。
- 建议行宽不超过 120 字符。
- 左大括号同行，右大括号独占一行。
- 不同逻辑块之间空一行。
- 运算符两侧、逗号后、`if` / `for` / `while` 后加空格。
- import 保持 IDE 默认有序，不引入无用 import。

## POJO 设计

- Entity 只做数据库映射，不暴露到 Controller 入参或返回值。
- Entity 推荐 `@Getter` + `@Setter`，避免 `@Data` 生成全字段 `equals()` / `hashCode()`。
- Request/Response/VO/DTO 可使用 Lombok 简化样板代码。
- 数据对象不混入复杂业务流程；解析、计算、外部调用放到 Service 或专用组件。
- 时间字段使用 `LocalDateTime`。
- 数据库可空字段使用包装类型。

接口对象边界以 `docs/conventions/api-rules.md` 为准。

## 常量

- 重复出现或有业务语义的字符串、数字应提取为常量、枚举或配置。
- `0`、`1`、`-1` 等公认值可以直接使用。
- 错误码优先使用项目已有 `ErrorCode` 体系；不要在新代码里散落硬编码错误码。
- QLExpress 超时、长度限制、白名单函数名等安全相关值必须集中管理。

## 注释

- 注释解释为什么这样做、业务约束是什么，不重复代码。
- public 类和复杂 public 方法应有简短 Javadoc 或注释说明职责。
- TODO 必须说明原因和后续动作，避免只写 `TODO`。
- API 字段说明优先使用 `@Schema(description = "...")`。

## 依赖注入

- 使用构造器注入。
- Lombok 场景使用 `@RequiredArgsConstructor`，依赖字段声明为 `private final`。
- 禁止新增字段注入 `@Autowired`。

## 异常处理

- 业务错误使用 `BusinessException` 或明确的领域异常。
- 不在业务代码中静默吞异常。
- `catch (Exception)` 只允许在边界层或确实需要兜底时使用，并必须记录必要上下文。
- 不把内部异常栈、SQL、token、验证码等敏感信息直接返回给前端。

## 日志

- 使用 SLF4J 占位符 `{}`，不要字符串拼接。
- `INFO` 记录关键业务动作，例如任务发布、实例创建、发奖。
- `WARN` 记录可恢复但需要关注的问题。
- `ERROR` 记录需要人工介入或会影响业务的问题。
- 打印异常时传 Throwable 对象。
- 禁止打印 token、验证码、密码、完整用户敏感信息、奖励配置原文。

## 集合与 Stream

- 判断空集合用 `isEmpty()`。
- 空返回优先使用 `Collections.emptyList()` 或 `List.of()`。
- `Collectors.toMap()` 必须提供 merge 函数，除非已证明 key 唯一。
- Stream 链过长或包含复杂分支时，改用清晰循环或拆方法。

## 校验

- Controller `@RequestBody` 使用 `@Valid`。
- Request 字段使用 Jakarta Validation 注解。
- Service 层补充业务校验并抛出明确异常。
- 复杂校验抽为私有方法或专用校验组件。

## 测试

- 框架：JUnit 5 + Mockito。
- 测试类默认 package-private。
- Service 单测中手动构造被测对象，避免过度依赖 Spring 容器。
- 断言使用 JUnit 5 或项目现有断言风格，保持一致。
- 重点覆盖步骤推进、过滤器、奖励发放、任务定义缓存、鉴权和 API 契约。

MyBatis-Plus 表元信息相关单测可按现有测试模式初始化 `TableInfoHelper`。
