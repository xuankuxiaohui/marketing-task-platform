# Spike 6 · AviatorScript

- **结论：采用** `com.googlecode.aviator:aviator:5.4.3`
- **能力：** 自定义函数求值、Lexer token 可枚举、`EVAL_TIMEOUT_MS` 打断 `while(true)`、缓存求值 P99 < 1ms、long 域前置校验
- **基准：** 见 `perf/expression-benchmark.md`（Java 侧 1 万次，非 JS）

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/6-aviator/pom.xml test
```
