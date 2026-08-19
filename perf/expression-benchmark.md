# Aviator 求值基准（Spike 6）

任务写 `perf/expression-benchmark.js`。本轮在 JDK 26 上用 JUnit 采样，不跑 Node。

- 表达式：`province() == 'GD' && userLevel() >= 3`（编译缓存后）
- 次数：10_000
- 门槛：P99 < 1ms
- 结果：**通过**（`AviatorSmokeTest.cachedEvalP99UnderOneMs`）
