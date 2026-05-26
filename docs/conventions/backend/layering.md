# 后端分层架构与包结构

Controller → Service → Mapper 分层约定，以及缓存、UserContext、Configuration 类的组织方式。

## 包结构

```text
com.marketing.task
  TaskPlatformApplication          -- @SpringBootApplication 入口
  common/                          -- Result<T>, BusinessException, ErrorCode, GlobalExceptionHandler
  config/                          -- WebConfig, MybatisPlusConfig, JacksonConfig, CacheConfig 等
  context/                         -- UserContext, UserContextHolder
  interceptor/                     -- AdminAuthInterceptor, ClientAuthInterceptor
  controller/                      -- 共享端点，例如 CaptchaController
  controller/admin/                -- /api/admin/**
  controller/client/               -- /api/client/**
  controller/internal/             -- /api/internal/**
  domain/entity/                   -- 主业务 Entity
  domain/dto/                      -- 现有 DTO/Request/Response；新增对象按 api-rules 逐步收敛
  domain/enums/                    -- 枚举
  domain/reward/                   -- 奖励配置数据对象
  domain/vo/                       -- 视图对象
  mapper/                          -- 主业务 Mapper
  security/                        -- JWT、验证码、认证异常
  service/                         -- 通用服务
  service/auth/                    -- 登录注册
  service/cycle/                   -- 周期 key
  service/filter/                  -- 过滤器
  service/platform/                -- 平台适配
  service/reward/                  -- 任务奖励处理
  service/step/                    -- 步骤推进
  service/task/                    -- 任务定义和缓存
  prize/                           -- 奖品子域，内部同样遵守 Controller/Service/Mapper 分层
```

## Controller 层

- 类命名：`{Audience}{Resource}Controller`。
- 注解：`@RestController` + `@RequestMapping` + `@RequiredArgsConstructor`。
- 返回值统一使用 `Result<T>`。
- Controller 入参使用 `Request`，返回使用 `Response` 或 `VO`。
- 禁止接收或返回 Entity。
- 分页返回 `Result<IPage<ResponseOrVO>>` 或项目当前分页类型，不返回 `IPage<Entity>`。
- `@RequestBody` 参数使用 `@Valid`。
- 简单只读端点可以直接注入 Mapper，但仍必须转换 Entity。
- 写操作走 Service 层，由 Service 处理事务、幂等和缓存失效。

## Service 层

- 类命名优先 `{Domain}Service`，引擎、调度器、处理器按职责命名。
- 注解：`@Service` + `@RequiredArgsConstructor`，需要日志时加 `@Slf4j`。
- 写操作必须检查是否需要 `@Transactional`。
- 不继承 MyBatis-Plus 的 `IService` / `ServiceImpl`，沿用当前直接注入 Mapper 的模式。
- Service 方法体现业务语义，例如 `publish`、`offline`、`advanceStep`。
- 任务定义写路径必须触发缓存失效。

## Mapper 层

- 接口命名：`{Entity}Mapper extends BaseMapper<Entity>`。
- 标注 `@Mapper`。
- 查询优先使用 `LambdaQueryWrapper<T>` / `LambdaUpdateWrapper<T>`。
- 分页使用 MyBatis-Plus 分页插件。
- 复杂查询仍需避免字符串拼接 SQL。

## Entity 层

- Entity 与表结构对应，使用 `@TableName`。
- 主键 `Long id` 显式标注 `@TableId(type = IdType.AUTO)`。
- 字段使用包装类型表达数据库 null 语义。
- 枚举字段当前以 String 存储枚举名。
- Entity 不包含复杂业务流程，不直接作为 HTTP 契约。

## UserContext 注入

JWT 模式：

- 请求携带 `Authorization: Bearer <token>`。
- 拦截器验证 token 后构建 `UserContext`。
- 业务代码通过 `UserContextHolder.get()` 获取。
- 请求完成后清理 ThreadLocal。

Mock 模式：

- 开发和联调通过 `X-User-*`、`X-Platform` Header 透传。
- Header 字段见 `docs/conventions/api-rules.md`。

## cycle_key 规则

`user_task_instance` 通过 `(user_id, task_id, cycle_key)` 唯一键防止同周期重复创建。

| period_type | cycle_key |
|---|---|
| `ONCE` | 固定字符串 `ONCE` |
| `DAILY` | `yyyyMMdd` |
| `MONTHLY` | `yyyyMM` |
| `CRON` | 当前按分钟生成 `yyyyMMddHHmm` |
| `SPECIAL` | 使用 `task.special_cycle_key` |

并发保护：插入发生唯一键冲突时，重新查询已有实例。

## 缓存规范

- 缓存方案：Caffeine 本地缓存。
- 缓存层：`TaskDefinitionCacheService`。
- 读方法使用 `@Cacheable`。
- 失效方法使用 `@CacheEvict` 或封装的 evict 方法。

必须失效缓存的写路径：

- 创建或更新任务聚合。
- 发布或下线任务。
- 步骤、过滤器、端配置独立 CRUD。
- 其他会改变任务定义、展示、过滤或推进行为的写操作。

## Configuration 类

| 类 | 作用 |
|---|---|
| `WebConfig` | 注册拦截器，配置 CORS |
| `MybatisPlusConfig` | 注册分页拦截器 |
| `JacksonConfig` | Long 序列化为 String，避免 JS 精度丢失 |
| `CacheConfig` | 启用 Caffeine 缓存 |
| `PasswordEncoderConfig` | BCrypt PasswordEncoder |
| `AuthProperties` | JWT 密钥、过期时间、mock 开关 |

生产环境 CORS 必须限制为具体域名。
