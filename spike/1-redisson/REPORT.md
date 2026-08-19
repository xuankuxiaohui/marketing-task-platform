# Spike 1 · Redisson

- **结论：采用** `org.redisson:redisson:4.6.1`（手动装配 `RedissonClient`）
- **运行时：** JDK 26.0.2 + Spring Boot 4.1.0
- **中间件：** 虚拟机 Redis 6.0.8 `192.168.88.149:6379` DB 2（本机无 Docker，未用 Testcontainers）
- **starter：** `redisson-spring-boot-starter:4.6.1` 可解析；冒烟用手动 `Redisson.create`，避开 auto-config 与默认 Kryo 编解码
- **Lua：** 必须 `StringCodec`，数字参数传字符串，否则 Redis `tonumber` 为 nil

## 断言

| 项 | 结果 |
|----|------|
| 双线程 `tryLock(0)` 恰一获得 | 通过 |
| 持锁 32s 看门狗仍持有 | 通过 |
| Lua 滑窗 10 次恰 5 次通过 | 通过 |

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/1-redisson/pom.xml test
```
