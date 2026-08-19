# Spike 2 · Sa-Token

- **结论：采用** `cn.dev33:sa-token-spring-boot4-starter:1.45.0` + `sa-token-redis-template:1.45.0`
- **要点：** 双账号用两个 `new StpLogic("admin"|"client")`，**不要**注册两个 `StpLogic` Spring Bean（`SaBeanInject` 只收一个）
- **测试：** `SaTokenContextMockUtil.setMockContext()` 才能在无 HTTP 请求下 `login`
- **会话：** Redis DB 2；新 `StpLogic` 实例能读到已登录 token

## 断言

双体系 token 不互通、重启等价新 Logic 会话仍在、并发上限挤最早、logout 后旧 token 失效。

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/2-sa-token/pom.xml test
```
