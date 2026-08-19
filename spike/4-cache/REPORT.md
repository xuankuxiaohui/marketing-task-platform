# Spike 4 · 两级缓存

- **结论：采用** Spring Cache + Redis L2（Boot BOM）+ Caffeine 在 classpath；广播用 Redis `PUBLISH cache:evict`
- **中间件：** Redis DB 2
- **说明：** 本 spike 验证 L2 回源与 pub/sub 跨连接可达。任务 15 再封装 `PlatformCache`（Caffeine L1 + afterCommit evict）

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/4-cache/pom.xml test
```
