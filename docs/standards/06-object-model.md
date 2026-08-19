# 06 · 分层对象模型与转换规范

> 解决：哪一层用哪种对象、如何转换、谁允许持有谁。  
> 读者：后端开发者与 AI。违反本篇是跨层泄漏的主要来源。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Alibaba Java Coding Guidelines · Domain models | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ | DO / DTO / VO 分层思想；POJO 不用作类名；包装类型；禁止成员默认值掩盖空 |
| Alibaba Project Specification（应用分层） | 同上文档 Project Specification | 上层依赖下层；禁止跨层传持久化对象到展示层 |
| Jakarta Bean Validation 3.0 | https://jakarta.ee/specifications/bean-validation/3.0/ | 校验落在入参对象 |
| JEP 395 Records | https://openjdk.org/jeps/395 | 不可变数据载体 |
| Spring MVC 参数绑定 | https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods.html | `@RequestBody` / `@PathVariable` 绑定 Command/Query |
| design §2.2.3、§4.1 | 本仓库 | 端口 record、Result、列表投影 |

### 本项目覆盖

阿里手册用 `*DO`（表）、`*DTO`（传输）、`*VO`（视图）。本项目模块化单体 + 双应用，对象更细。**禁止**再引入 `BO` / `AO` / `PO` / `POJO` 后缀增加歧义。

| 阿里 | 本项目 |
|------|--------|
| `UserDO` | `AdminUserEntity` |
| `UserDTO` | 拆成 `Command` / `Query` / 端口 record |
| `UserVO` | `XxxResponse` / `XxxView` |
| Manager 层 | 不设；编排放 `application`，规则放 `domain` |

## 2. 对象族

单向流动：

```text
HTTP JSON
   ↓  Spring 绑定 + Bean Validation
Command / Query          （入，不可变 record）
   ↓  application 编排
Domain 参数 / 端口 record
   ↓
Entity                    （表行，可变，仅 mapper 层进出）
   ↓  convert
Response / View           （出，不可变 record）
   ↓
Result<T>                 （kernel 外壳）
   ↓
HTTP JSON
```

跨域：

```text
调用方 application  →  端口入参 record  →  提供方 application
提供方 Entity 不得穿出端口。端口只返回 record（GrantResult、UserAttributes、RiskVerdict）。
```

### 2.1 定义

| 类型 | 后缀 | 包 | 可变性 | 职责 |
|------|------|----|--------|------|
| Entity | `Entity` | `entity` | 可变 | 与一张表列一一对应 |
| Command | `Command` | `command` | 不可变 record | 写用例入参 |
| Query | `Query` | `query` | 不可变 record | 查用例入参（含分页） |
| Response | `Response` | `response` | 不可变 record | 单资源出参 |
| View | `View` | `response` | 不可变 record | 列表行/卡片等投影 |
| Port 契约 | 无强制后缀，用 design 已定名 | `com.mkt.contract` | record | 跨域同步 |
| Domain Event | 事件常量名已在 contract；载荷 `*Event` | contract 或域内 | record | Outbox 载荷 |
| Result | `Result` | kernel | record | 统一外壳，不是业务对象 |

### 2.2 命名正反例

| 正例 | 反例 | 原因 |
|------|------|------|
| `TaskDefinitionEntity` | `TaskDefinitionDO` / `TaskDefinition` | 与表或领域服务重名 |
| `TaskDefinitionSaveCommand` | `TaskDefinitionDTO` | DTO 方向不明 |
| `TaskInstanceQuery` | `TaskInstanceParam` | 含糊 |
| `PrizeCardView` | `PrizeVO` | 与阿里 VO「页面名」习惯冲突，且本项目统一 Response/View |
| `GrantResult` | `GrantResultDTO` | 端口类型以 design 为准，不加后缀 |
| `IdResponse` / `OkResponse` | `Map<String,Object>` | 缺省出参也要类型（design §4.1：`{id}` / `{ok:true}`） |

## 3. 各层允许持有的类型

| 层 | 可依赖 | 禁止 |
|----|--------|------|
| `controller.*` | Command、Query、Response、View、`Result`、应用服务 | Entity、Mapper、端口实现细节、JsonUtil 之外的 mapper |
| `application` | Command、Query、Entity、端口、领域服务、Mapper、Convert | Servlet API、HTTP 头解析（应在过滤器/控制器完成） |
| `domain` | 本域纯类型、枚举、kernel 基础类型；**可以只读** Entity 字段做状态机 | Spring Web、Mapper、MyBatis 注解语义；禁止调用 Entity setter / 写回；写库只在 application + Mapper |
| `mapper` | Entity | Command、Response |
| `convert` | Entity ↔ Command/Response/View | 访问 Mapper、开事务 |
| 端口实现 | 本域应用/领域、Entity（内部） | 把 Entity 当作返回类型 |
| `platform-contract` | JDK + kernel | Entity、Spring Web、MyBatis |

**MUST NOT** 把 Entity 放入 `Result.data`。  
**MUST NOT** 把 `Map` / `JSONObject` 当作跨层契约。  
**MUST NOT** 让前端依赖的字段只存在于 Entity 而不存在于 Response。

`domain` 层可执行口径：方法可以接收 Entity（或从 Entity 抽出的不可变快照），只读 getter 做规则。**MUST NOT** 在 domain 调用 `setXxx`、`updateById`、打开事务。需要改状态时返回新值或领域结果，由 application 写回 Mapper。

## 4. Entity 规则

1. **MUST** 一类一表。字段与列同序尽量对齐 DDL，便于 diff。
2. **MUST** 用包装类型（Alibaba OOP #9）。
3. **MUST NOT** 在 Entity 写业务方法（发奖、推进）。状态迁移在领域服务。
4. **MUST NOT** 在 Entity 上堆 `@JsonIgnore` 来「防止泄漏」——泄漏应在类型上杜绝。
5. 乐观锁字段 `version`、逻辑删除 `deleted` 仅出现在 design 规定的表。
6. 密码哈希、secret 列 **MUST** 仅存 Entity / Mapper，Response 不得有对应字段（design §4.1 缺省规则 ①）。
7. **SHOULD** 使用 Lombok `@Getter` `@Setter` 或手写；若用 record 当 Entity，必须确认 MyBatis-Plus 对该表的兼容已在编组 A 冒烟覆盖。默认用 class。

## 5. Command / Query 规则

1. **MUST** 为 record，字段 camelCase，与 JSON 一致（附录 C / Jackson 默认）。
2. **MUST** 在字段上标注校验：`@NotBlank` `@Size` `@Min` 等。复杂跨字段校验用自定义注解或应用服务显式校验，错误码仍是 `common.param-invalid`，除非规格给了专用码。
3. **MUST NOT** 把路径变量重复塞进 body 又不校验一致性。路径 `id` 以路径为准。
4. Query **MUST** 包含 `page` / `pageSize`（若该端点分页）。默认值在绑定层处理：`page≥1`，`pageSize` 默认 20、上限 100 截断（附录 C）。
5. **MUST NOT** Command 内嵌 Entity。
6. 管理端更新 **SHOULD** 用完整 Command 或明确的 patch 字段集合；禁止半更新却把 null 解释成「清空」而不在规格里声明。

```java
public record TaskDefinitionSaveCommand(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull Instant windowStart,
        @NotNull Instant windowEnd
) {}
```

时间字段在 Java 侧用 `Instant` 或 `OffsetDateTime`。Jackson 按 ISO-8601 带偏移解析，入库转 UTC（见 07）。

## 6. Response / View 规则

1. **MUST** 为 record。集合字段复数名词：`records`、`permissions`、`steps`。
2. 列表端点 `data` **MUST** 为：

```json
{ "total": 0, "records": [ { ... } ] }
```

3. 写端点缺省：创建 `{ "id": 1 }` → `IdResponse`；更新/删除/状态流转 `{ "ok": true }` → `OkResponse`（design §4.1）。
4. **MUST NOT** 返回内部原因给 C 端（灰度未命中、具体风控规则编号）。用规格规定的用户文案（R34.6）。
5. 管理端 View 可以比 C 端多诊断字段，但仍不得含哈希与密钥。
6. `null` 与缺省：业务上「没有」用 `null` 或缺省省略；**MUST NOT** 用空字符串表示未设置的金额或时间。布尔 **MUST NOT** 为 null（Zalando #122 精神 + 本项目 TINYINT）。

## 7. 端口对象

三端口签名以 design §2.2.3 为唯一权威。**MUST NOT** 改名或拆字段而不先改规格。

| 端口 | 关键类型 | 注意 |
|------|----------|------|
| `RewardPort` | `GrantResult`、`GrantContext`、`GrantSource` | `GrantContext.simulated` 默认 false；`bypassRules` 仅 `MANUAL_GRANT` |
| `UserAttributePort` | `UserAttributes`、`accountStatus` | `lockAndGet` 不走缓存；禁止返回 Entity |
| `RiskCheckPort` | `RiskScene`、`RiskSubject`、`RiskVerdict` | **不抛业务异常**，拒绝由 verdict 表达 |

转换：

- 调用方用本域 Command 组装端口入参。
- 提供方把 Entity 转成端口 record 再返回。
- **MUST NOT** 为了省事让端口返回 `Map` 或 JSON 字符串。

## 8. 转换器

1. **MUST** 转换集中在 `convert/*Converter`（或 `*Convert`），静态方法或 Spring 无状态组件。
2. **MUST** 单向方法，命名：

| 方法 | 方向 |
|------|------|
| `toEntity(Command)` | 新建 |
| `apply(Command, Entity)` | 更新可变字段 |
| `toResponse(Entity)` | 出站 |
| `toView(Entity)` | 列表投影 |
| `toGrantContext(Command)` | 端口入参 |

3. **MUST NOT** Entity 上提供 `toResponse()`（把出站形状焊死在表模型）。
4. **MUST NOT** 在 Converter 访问数据库或 Redis。
5. 嵌套对象逐层转。禁止 `JsonUtil.convertValue(entity, Response.class)` 靠同名偷懒——密码字段同名即泄漏，且列名演进不可见。
6. 不强制 MapStruct。若引入，必须：只生成 Converter、配置放一处、禁止 Entity 与 Response 循环引用。未写入 dependency-matrix 前 **MUST NOT** 私自加依赖。
7. 集合转换用显式循环或 `stream`，保持空列表不是 `null`。

```java
public final class PrizeConverter {

    private PrizeConverter() {}

    public static PrizeCardView toView(PrizeEntity e) {
        return new PrizeCardView(
                e.getId(),
                e.getName(),
                e.getStatus(),
                e.getExpireAt()
        );
    }
}
```

## 9. 枚举与值对象

1. 封闭枚举 **MUST** 与表 CHECK / design 列注释同字面量（`ENABLED`、`IN_PROGRESS`）。
2. Java 枚举名 UpperCamelCase，常量 `UPPER_SNAKE_CASE`。JSON 默认枚举名，**MUST** 与库值一致，不要再做一套小写映射，除非规格写了。
3. 跨域枚举（`GrantSource`、`RiskScene`）只定义在 contract，域内 **MUST NOT** 复制。
4. 值对象（如周期键 `cycleKey`）用 `String` 亦可；若封装，必须不可变且校验长度 ≤40。

## 10. 分页与通用外壳

kernel 提供：

```text
Result<T>          code + message + data + traceId
PageResult<T>      total + records   // 作为 Result.data
PageQuery          page + pageSize
IdResponse         id
OkResponse         ok
```

Controller：

```java
return Result.ok(new PageResult<>(total, views));
```

**MUST NOT** 各域自己再造一套 `R` / `AjaxResult` / `CommonResult`。

## 11. 典型违规

```java
// 反例 1：Entity 出站
return Result.ok(userMapper.selectById(id));

// 反例 2：Controller 拼 Map
return Result.ok(Map.of("id", e.getId(), "pwd", e.getPasswordHash()));

// 反例 3：端口传 Entity
GrantResult grant(PrizeEntity prize, PortalUserEntity user);

// 反例 4：双向转换搅在 Entity
public PrizeResponse toResponse() { ... }
```

## 12. AI 检查清单

- [ ] 新对象落在正确包并使用正确后缀
- [ ] Controller 签名只有 Command/Query/Response/Result
- [ ] domain 只读 Entity 字段，无 setter / 无写库
- [ ] 转换单向且不访问 IO
- [ ] 端口进出都是 contract record
- [ ] 列表是 `{total, records}`，敏感列已剔除
- [ ] 未使用 `*DTO`/`*VO`/`*DO`/`*POJO` 类名
- [ ] 未用 `Map`/`JsonNode` 当业务契约
