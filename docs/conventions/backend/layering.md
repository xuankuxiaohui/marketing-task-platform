# 后端分层架构与包结构

Controller → Service → Mapper 分层约定，以及缓存、UserContext、Configuration 类的组织方式。

## 目录

1. [包结构](#包结构)
2. [Controller 层](#controller-层)
3. [Service 层](#service-层)
4. [Mapper 层](#mapper-层)
5. [Entity 层](#entity-层)
6. [UserContext 注入](#usercontext-注入)
7. [cycle_key 规则](#cycle_key-规则)
8. [缓存规范](#缓存规范)
9. [Configuration 类](#configuration-类)

---

## 包结构

```
com.marketing.task
  TaskPlatformApplication          -- @SpringBootApplication 入口
  common/                          -- Result<T>, BusinessException, ErrorCode, GlobalExceptionHandler
  config/                          -- @Configuration 类 (WebConfig, MybatisPlusConfig, JacksonConfig, CacheConfig, PasswordEncoderConfig, AuthProperties)
  context/                         -- UserContext, UserContextHolder (ThreadLocal)
  interceptor/                     -- AdminAuthInterceptor, ClientAuthInterceptor (HandlerInterceptor)
  controller/admin/                -- Admin*Controller, /api/admin/**
  controller/client/               -- Client*Controller, /api/client/**
  controller/internal/             -- Internal*Controller, /api/internal/**
  controller/                      -- CaptchaController (共享端点 /api/captcha)
  domain/entity/                   -- @TableName 实体 (Task, TaskStep, TaskFilter, AdminUser, ClientUser 等)
  domain/dto/                      -- 请求/响应 DTO (LoginRequest, LoginResponse, RegisterRequest 等)
  domain/enums/                    -- 枚举类 (TaskStatus, StepType, PeriodType 等)
  domain/reward/                   -- RewardConfig (JSON 解析 + 数据对象)
  domain/vo/                       -- 视图对象 (TaskAdminVO, TaskClientVO, TaskStepVO 等)
  mapper/                          -- {Entity}Mapper extends BaseMapper<Entity>
  security/                        -- JWT 签发/验证 (AdminJwtProvider, ClientJwtProvider)、CaptchaService、AuthenticationException
  service/task/                    -- TaskService, TaskDefinitionCacheService
  service/auth/                    -- AdminAuthService, ClientAuthService
  service/step/                    -- StepAdvanceEngine, StepHandler 接口及实现
  service/filter/                  -- FilterExpressionEngine, FilterEvaluator
  service/cycle/                   -- CycleKeyResolver
  service/platform/                -- PlatformAdapter 接口及实现, PlatformAdapterRegistry
  service/reward/                  -- RewardService, RewardHandler 接口及实现
```

## Controller 层

- 类命名：`{Audience}{Resource}Controller`
- 注解：`@RestController` + `@RequestMapping` + `@RequiredArgsConstructor`
- 依赖注入：构造器注入，字段声明为 `private final`
- 返回值：统一使用 `Result<T>`
- **返回类型必须是 VO/DTO，严禁返回 Entity**。分页场景使用 `Result<IPage<TaskAdminVO>>` 而非 `Result<IPage<Task>>`
- 校验：`@RequestBody` 参数使用 `@Valid`（参数也必须是 DTO/VO，不能是 Entity）
- 简单只读端点可直接注入 Mapper（但仍需做 Entity → VO 转换），写操作走 Service 层

```java
@RestController
@RequestMapping("/api/admin/task")
@RequiredArgsConstructor
public class AdminTaskController {
    private final TaskService taskService;
    private final TaskMapper taskMapper;

    @GetMapping
    public Result<IPage<TaskAdminVO>> list(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        IPage<Task> entityPage = taskMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<>());
        return Result.ok(entityPage.convert(TaskAdminVO::from));
    }
}
```

## Service 层

- 类命名：`{Domain}Service`（`StepAdvanceEngine` 是例外）
- 注解：`@Service` + `@RequiredArgsConstructor` + `@Slf4j`
- 写操作必须加 `@Transactional`
- 不继承 MyBatis-Plus 的 `IService` / `ServiceImpl`，直接注入 Mapper
- 避免一个 Service 过于膨胀 — 按领域功能拆分
- Service 方法应体现业务语义（`publish` 而非 `updateStatus`）

## Mapper 层

- 接口：`{Entity}Mapper extends BaseMapper<Entity>`，标注 `@Mapper`
- 所有查询使用 `LambdaQueryWrapper<T>` / `LambdaUpdateWrapper<T>`（类型安全）
- 分页：`taskMapper.selectPage(Page.of(page, size), wrapper)` 返回 `IPage<T>`
- 分页插件在 `MybatisPlusConfig` 中注册（`PaginationInnerInterceptor(DbType.MYSQL)`）
- 无 XML 映射文件，复杂查询用注解或 QueryWrapper 条件构造
- `Collectors.toMap()` 必须提供 merge 函数

## Entity 层

- 注解：`@Getter` + `@Setter` + `@TableName("table_name")`
- 主键 `Long id`，必须显式标注 `@TableId(type = IdType.AUTO)`。当前多数 Entity 缺少，靠 MyBatis-Plus 默认推断，**应补上**
- 时间字段 `LocalDateTime createdAt` / `LocalDateTime updatedAt`，由代码手动设置
- 枚举字段以 `String` 存储枚举名（如 `TaskStatus.PUBLISHED.name()`）
- 所有字段使用包装类型（`Long` 而非 `long`），避免基本类型默认值干扰数据库 null 语义

## UserContext 注入

两种模式通过 `app.auth.mock-enabled` 配置切换：

**JWT 模式**（`mock-enabled: false`，生产）：
- 请求 → `AuthInterceptor.preHandle()` 提取 `Authorization: Bearer <token>` → JWT 验证 → 构建 UserContext → `UserContextHolder.set(ctx)` → `afterCompletion()` 清理
- 无有效 token 时返回 401

**Mock 模式**（`mock-enabled: true`，开发）：
- 通过 HTTP Header 透传（`X-User-Id`、`X-User-Province` 等），与旧版 `UserContextInterceptor` 兼容

Header 字段和详情见 `api-rules.md`。

## cycle_key 规则

`user_task_instance` 通过 `(user_id, task_id, cycle_key)` 唯一键防止同周期重复创建。

| period_type | cycle_key |
|---|---|
| `ONCE` | 固定字符串 `ONCE` |
| `DAILY` | `yyyyMMdd` |
| `MONTHLY` | `yyyyMM` |
| `CRON` | `yyyyMMddHHmm`（当前按分钟生成，还没有真实调度器） |
| `SPECIAL` | 使用 `task.special_cycle_key` |

并发保护：`getOrCreateInstance()` 插入时 catch `DuplicateKeyException`，发生冲突后重新查询已有实例。

## 缓存规范

- 缓存方案：Caffeine 本地缓存（进程内）
- 配置：30 分钟过期（expireAfterWrite），最大 500 条，开启统计
- 缓存层：`TaskDefinitionCacheService`，封装 `@Cacheable` / `@CacheEvict`
- 读方法使用 `@Cacheable(value = "cacheName", key = "#param")`
- 失效方法使用 `@CacheEvict(value = {"cache1", "cache2", ...}, key = "#taskId")`

**缓存失效触发点**：
- 聚合保存（创建/更新任务）后
- 发布 / 下线任务后
- 步骤 / 过滤器 / 端配置独立 CRUD 后

规则：任何修改任务定义的写路径，都必须调用 `cacheService.evict(taskId)`。

## Configuration 类

| 类 | 作用 |
|---|---|
| `WebConfig` | 实现 `WebMvcConfigurer`，注册 `AdminAuthInterceptor` / `ClientAuthInterceptor`，配置 CORS |
| `MybatisPlusConfig` | `@Configuration` + `@Bean MybatisPlusInterceptor`，注册分页拦截器 |
| `JacksonConfig` | 自定义 `ObjectMapper`，`Long` / `long` 序列化为 String 防止 JS 精度丢失 |
| `CacheConfig` | `@EnableCaching` + `@Bean CacheManager`（Caffeine 30min TTL） |
| `PasswordEncoderConfig` | `@Bean PasswordEncoder`（BCrypt） |
| `AuthProperties` | `@ConfigurationProperties(prefix = "app.auth")`，JWT 密钥、过期时间、mock 开关 |

注意：`WebConfig` 中 CORS 配置 `allowedOriginPatterns("*")` 当前用于 MVP 联调，生产环境必须限制为具体域名。

## 相关文档

- `backend/java-style.md` — 编码风格、POJO 设计、异常、日志
- `backend/database.md` — 数据库规范、实体映射
- `backend/filter-safety.md` — 过滤器安全规则
- `backend/step-advancement.md` — 步骤推进规则
- `api-rules.md` — API 响应格式、UserContext Header
