# Spike 8 · 杂项

| 组件 | 锁定版本 | 结论 |
|------|----------|------|
| easy-captcha | `com.github.whvcse:easy-captcha:1.6.2` | 采用 |
| jqwik | `net.jqwik:jqwik:1.9.3` | 采用（与 JUnit 5 并存） |
| ArchUnit | `archunit-junit5:1.5.0` | 采用（1.4.1 读不了 JDK 26 class file 70） |
| logstash-logback-encoder | `8.1` | 采用（JSON 含 MDC `traceId`） |
| Testcontainers | — | **备选**。本机无 Docker。已用 LAN MySQL 8.0.25 + Redis 6.0.8 并行连通冒烟替代 |

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/8-misc/pom.xml test
```
