# Spike 3 · MyBatis-Plus

- **结论：采用** `com.baomidou:mybatis-plus-spring-boot4-starter:3.5.17` + generator 同版本
- **库：** `192.168.88.149:3308` / `mkt_platform`（临时表 `spike_mp_demo`，测完删除）
- **生成器：** `enableLombok(boolean)` 在 3.5.17 已变签名，不要传 boolean

## 断言

CRUD、ASSIGN_ID 雪花、乐观锁第二更 0 行、生成器产出 `.java`。

## 复现

```
set JAVA_HOME=D:\develop\jdk\jdk-26.0.2
mvn -f spike/3-mybatis-plus/pom.xml test
```
