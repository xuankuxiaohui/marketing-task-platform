# 11 · 测试规范

> 适用范围：后端与前端自动化测试、CI 门禁。  
> 权威：design §7；requirements 66 条正确性属性；NFR 可维护性 3–4。  
> 任务号 → 属性 → 测试类一表：[verification-matrix.md](../verification-matrix.md)。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| JUnit User Guide | https://docs.junit.org/ | Jupiter 编程模型、生命周期、扩展 |
| Spring Boot Testing | https://docs.spring.io/spring-boot/reference/testing/index.html | `@SpringBootTest`、切片测试、Testcontainers 集成 |
| Testcontainers | https://java.testcontainers.org/ | MySQL / Redis 模块；测试不依赖手工环境 |
| jqwik User Guide | https://jqwik.net/docs/current/user-guide.html | 属性测试、生成器、种子复现 |
| ArchUnit User Guide | https://www.archunit.org/userguide/html/000_Index.html | 包依赖、分层 |
| k6 文档 | https://grafana.com/docs/k6/latest/ | 压测脚本与阈值 |
| Playwright | https://playwright.dev/docs/intro | E2E |
| Vitest | https://vitest.dev/guide/ | 前端组件测试 |
| Alibaba 测试命名 | https://alibaba.github.io/Alibaba-Java-Coding-Guidelines/ | 测试类 = 被测名 + `Test` |

本项目测试分层以 design §7.1 为唯一门禁表，不另发明金字塔比例。

## 2. 分层与门禁

| 层 | 工具 | 命名 | Maven | 门禁 |
|----|------|------|-------|------|
| 单元 | JUnit 5 + Mockito + **AssertJ**，不起 Spring | `*Test` | surefire | 阻断 |
| 属性 | jqwik | `*PropertyTest` | surefire | 阻断 |
| 架构 | ArchUnit | `*ArchTest` | surefire | 阻断 |
| 集成 | Spring Boot Test + Testcontainers | `*IT` | failsafe | 阻断 |
| 双实例 | 两上下文 | `*IT`（基类 `TwoPortalAppIT` / `TwoAdminAppIT`） | failsafe | 阻断 |
| 前端组件 | Vitest | `*.spec.ts` | pnpm | 阻断 |
| E2E | Playwright | `*.spec.ts` | 编组 I | 阻断（**任务 43**） |
| 压测 | k6 | `perf/*.js` | **任务 43** 跑 P0 子集（NFR 性能 1–5、7）；**任务 49** 为 P1 全量 + 容量复验（`perf/run-full.sh`） | 43 = P0 发布签署；49 = P1 签署。**5 分钟 k6 不进例行 PR CI** |

1. **MUST** 测试与实现同任务交付（tasks.md 纪律 ①）。
2. **MUST NOT** 用 H2 / Embedded Redis 替代 MySQL 8 / Redis。JSON、CHECK、分区、`utf8mb4_0900_ai_ci` 行为不同。
3. 本机无 Docker：集成测试在 CI 跑。开发者 **MUST NOT** 为了本地绿而改用内存库，也 **MUST NOT** 削弱 `*IT` 断言好让本机跳过。
4. IT 容器镜像：`mysql:8.0` + `redis:7-alpine`（与矩阵一致）。现网共享 Redis 为 **6.0.8 / DB 2**（见 [14-deployment.md](14-deployment.md)）。P0 所用 SET/ZSET/pub-sub/Lua 在 6.0.8 足够。**MUST NOT** 为「和 IT 镜像一致」去升级共享实例；正式升 7 另排。
5. flaky：失败即阻断；同轮至多重跑 1 次且必须留日志/线程转储；连续两轮重跑判实败（design §7.10）。

## 3. 命名与结构

```text
src/test/java/com/mkt/task/engine/CycleKeyResolverTest.java
src/test/java/com/mkt/task/engine/CycleKeyResolverPropertyTest.java
src/test/java/com/mkt/admin/ArchLayerRuleTest.java
src/test/java/com/mkt/task/ScenarioMatrixIT.java
```

1. 方法名 **MUST** 表达场景：`shouldRejectWhenMutexOccupied` 或中文场景名 `scenario01_首步骤PASSIVE自动级联`（矩阵测试强制中文场景名，design §7.5）。
2. **MUST NOT** `test1` / `testOk`。
3. 单测类与被测类同包不同源集，便于测包可见性。
4. 新测试优先放被测模块；需要完整装配的放 app 模块。
5. 不启 Testcontainers、不启两应用的 HTTP 冒烟（`Result` 外壳、traceId、OpenAPI 三分组隔离）放 `platform-kernel` 的 `*IT`，用 `KernelTestApplication`（仍起嵌入式 Tomcat，走 failsafe）。**MUST NOT** 为此起 admin-app / portal-app。任务 36 / `gen:api` 的 JSON **MUST** 来自两应用命名空间路径，见 [07-api-design.md](07-api-design.md) §2，**MUST NOT** 用 kernel 冒烟 JSON。

## 4. 单元测试

覆盖 design §5 纯函数：分桶、CycleKeyResolver、分支求值、动作合并、限制链顺序、脱敏截断、默认昵称。

1. **MUST** 不启动 Spring。时钟注入 `Clock.fixed` 或 `MutableClock`。
2. **MUST NOT** 测 getter/setter、框架本身。
3. Mockito **SHOULD** 只 mock 跨边界端口，不 mock 被测类半个内部方法。
4. 断言 **MUST** 用 AssertJ（`assertThat`）。禁止再引入 Hamcrest / 各域混用 JUnit `Assertions` 作为主风格（JUnit 的 `assertThrows` 可保留）。
5. 恶意表达式样本：`domain-task/src/test/resources/expression/malicious-*.txt`，运行器逐行断言（§7.7）。扩样本只加文件。

## 5. 属性测试（jqwik）

1. 66 条属性中可随机生成的子集 **MUST** 按 design §7.3 表建 `*PropertyTest`。
2. **MUST** 把生成器种子写入日志，保证失败可复现（jqwik 官方：记录 `random` seed；design §7.3）。
3. **MUST** 尝试次数与规模在注解中显式给出，避免默认值过小漏掉不变量。
4. 库存不变量等 **MUST** 断言 DB 或纯模型恒等式，例如 `成功发放数 + remaining = total`。

```java
@Property(tries = 200)
void stockConserved(@ForAll("grantTraces") List<GrantOp> ops) {
    StockModel model = StockModel.seed(100);
    ops.forEach(model::apply);
    Assertions.assertThat(model.granted() + model.remaining()).isEqualTo(100);
}
```

## 6. 集成测试

### 6.1 拓扑 A（默认）

design §7.2：

- 基类 `BaseIntegrationTest` 在 kernel test-jar。
- Testcontainers：`mysql:8.0` + `redis:7-alpine`（矩阵）。
- 每测试类：`flyway clean` + 全量迁移。迁移失败即失败。
- 类间 `TRUNCATE` 全业务表（逆序，无物理 FK 仍按依赖序）。
- **MUST NOT** 靠测试事务回滚做隔离（Outbox 线程会脱离）。用随机后缀数据。
- 提供 `awaitOutboxDrain()`：轮询待投递 = 0 且目标可见，超时 5s 失败。
- portal 测试默认装配全部 P0 域（RL-05）。admin 接口用 `AdminBaseIT`。

### 6.2 拓扑 B

同一 JVM 两个上下文，端口 18081/18082，共享容器。用于会话踢下线跨实例、缓存 1s 一致、调度恰一。

### 6.3 时钟与故障

1. **MUST** 业务时间走可替换 `Clock`（D-03）。过期/锁定测试推进 `MutableClock`，禁止 `Thread.sleep` 等真实 15 分钟。
2. Redis 故障：`RedisPauseSupport` 或 Testcontainers pause。断言 design §6.8 矩阵。
3. **MUST NOT** `Thread.sleep(2000)` 等待异步。用 Awaitility 或 `awaitOutboxDrain()`。

### 6.4 Fixtures

每域 `Fixtures.task()` / `portalUser()` / `prize()` 链式构造，随机后缀。种子词典走测试 classpath Repeatable 迁移，不进生产。

## 7. 架构测试

`ArchSuiteTest` 在 admin-app（类路径完整）。规则与 RL 编号一一对应（§7.6）。新增红线先改 design §2.8 再加测试。

AT-C01 及其它自有规则以 design 为准。

故意违规类放 `src/test/java/.../arch/fixture` 并在测试中证明规则会失败；不得留在 `main`。

## 8. 并发测试

NFR 可维护性 4 的 9 条路径 + 签到补全：design §7.4。

1. 线程数与断言以该表为准（例如库存 64 线程）。
2. **MUST** 最终态直连容器 `JdbcTemplate` 查询，不经可能有缓存的应用查询。
3. 错误码要能区分限领原因（C-5）。

## 9. 场景矩阵

`ScenarioMatrixIT`：24 用例与 feasibility §2 **一一对应**。方法名 `scenarioNN_...`。新增场景必须先改 feasibility 再加测试。

## 10. 前端测试

design §7.9：

1. Vitest：按钮状态（R35.1）、表单校验、纯函数。
2. Playwright：登录、领取、推进、奖品领取主路径。
3. **MUST** mock（MSW）数据源来自 springdoc 导出的 OpenAPI JSON。契约变则测试红。
4. CI：**MUST** 对触及契约或 `web/` 的变更跑 `pnpm --filter @mkt/shared gen:api` 并 `git diff --exit-code`（与 04 §10、13 §4 同一条门禁）。生成物只许出现在 `packages/shared`。

## 11. 压测

- 脚本在 `perf/`，环境 staging compose，portal-app ×2。
- 稳定加压 ≥ 5 分钟取 P95。
- 门槛 = NFR 性能 1–5 与 7。报告 `perf/reports/<日期>/`。
- 种子规模对齐 NFR 性能 8。

## 12. 覆盖率（D-04）

以 design §7.10 写死的阈值为准（实现任务 10 落地）。新增资金路径 **MUST** 有测试，禁止用 `@Generated` 大面积排除业务类。

## 13. 禁止

| 禁止 | 原因 |
|------|------|
| `@Disabled` 长期挂起 | 等于降低门禁 |
| 测试顺序依赖 `MethodOrderer` 且共享可变静态 | flaky |
| 测真实外网 | 不可重复 |
| 把生产库当 IT | 安全 |
| 断言完整 JSON 字符串含动态时间 | 脆弱；断言字段 |
| 复制 200 行 Fixture 不抽 | 维护成本 |
| 为测 OpenAPI 分组起整应用再维护 AutoConfiguration exclude | 每加一个域配置就红；隔离测用 kernel `OpenApiGroupsIT`，路径用各 app `SpringdocNamespacePathTest`；`gen:api` 仍打两应用 |

## 14. AI 检查清单

- [ ] 新行为能指到 §7.3 / §7.4 / §7.5 某一行，或已补充规格
- [ ] 纯函数有 `*Test`；不变量有 `*PropertyTest` 或 IT
- [ ] 无 H2、无任意 sleep
- [ ] 断言为 AssertJ
- [ ] 时间用 MutableClock
- [ ] Outbox 用 `awaitOutboxDrain`
- [ ] 架构规则未因「方便」削弱
- [ ] 前端未手写与 OpenAPI 冲突的类型
- [ ] 新增 `*AutoConfiguration` 未去改 OpenAPI 测试 exclude 名单
