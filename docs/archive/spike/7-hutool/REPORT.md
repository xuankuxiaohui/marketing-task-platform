# Spike 7 · Hutool

- **结论：采用** `hutool-core:5.8.47` + `hutool-crypto:5.8.47`（HMAC 在 crypto，不在 core）
- **禁止** hutool-json；JSON 用 Boot 4 `tools.jackson`（`spring-boot-starter-json`）
- **断言：** Jackson 序列化、手机号脱敏、HMAC-SHA256 小写 hex

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/7-hutool/pom.xml test
```
