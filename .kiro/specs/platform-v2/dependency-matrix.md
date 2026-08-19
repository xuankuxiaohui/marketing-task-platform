# 依赖版本矩阵

> 父 POM 唯一版本来源。编组 A 冒烟已绿（2026-08-18），结论见下表。
> 运行时：**JDK 26.0.2** + **Spring Boot 4.1.0**

## 口径（预研，冒烟前可改）

| 项 | 候选 | 依据 | 未决 |
|----|------|------|------|
| JDK | **26.0.2**（已核实） | 项目约束；路径 `D:\develop\jdk\jdk-26.0.2` | 默认 `java` 仍是 25.0.4，spike 须显式设 `JAVA_HOME` |
| Spring Boot | **4.1.0**（优先） | [系统要求](https://docs.spring.io/spring-boot/system-requirements.html)：4.1.0 支持 Java 17–26；4.0.x 只到 Java 25 | 规格写「Boot 4」，未钉小版本。要用 JDK 26 必须走 4.1 |
| Maven | 3.9.5（本机已有） | — | 可用 |
| Testcontainers | Redis 7 + MySQL 8 | 任务 1/3/4/8 | 本机 **PATH 无 docker**，容器冒烟会红 |

八项冒烟已绿。本机无 Docker：Redis/MySQL 冒烟打 `192.168.88.149`（DB 2 / `mkt_platform`）。Testcontainers 标备选，不阻塞任务 9。

## 矩阵

| 组件 | 锁定版本 | 验证任务 | 结论 | 冒烟证据 | 备注 |
|------|----------|----------|------|----------|------|
| JDK | 26.0.2 | 1–8 共用 | 采用 | — | `D:\develop\jdk\jdk-26.0.2`。默认 PATH 仍是 25 |
| Spring Boot | 4.1.0 | 1–8 共用 | 采用 | — | JSON = `tools.jackson` |
| Redisson | 4.6.1（手动 `RedissonClient`） | 1 | 采用 | spike/1-redisson/REPORT.md | Lua 必须 `StringCodec`。starter 可解析，任务 15 继续手动装配 |
| Sa-Token | spring-boot4-starter + redis-template **1.45.0** | 2 | 采用 | spike/2-sa-token/REPORT.md | 双 StpLogic 不要注册成两个 Bean |
| MyBatis-Plus | spring-boot4-starter **3.5.17** | 3 | 采用 | spike/3-mybatis-plus/REPORT.md | 对 LAN MySQL 8.0.25 |
| Spring Cache + Caffeine + Redis | Boot BOM | 4 | 采用 | spike/4-cache/REPORT.md | `PUBLISH cache:evict` 跨连接可达 |
| springdoc-openapi | starter-webmvc-ui **3.1.0** | 5 | 采用 | spike/5-springdoc/REPORT.md | Boot 4 用 RestClient 测分组 |
| AviatorScript | **5.4.3** | 6 | 采用 | spike/6-aviator/REPORT.md | P99<1ms 通过 |
| Hutool | core + crypto **5.8.47** | 7 | 采用 | spike/7-hutool/REPORT.md | HMAC 在 crypto；禁 hutool-json |
| easy-captcha | **1.6.2** | 8 | 采用 | spike/8-misc/REPORT.md | — |
| jqwik | **1.9.3** | 8 | 采用 | spike/8-misc/REPORT.md | 与 JUnit 5 并存 |
| Testcontainers | — | 8 | 备选 | spike/8-misc/REPORT.md | 本机无 Docker；LAN 连通替代 |
| ArchUnit | **1.5.0** | 8 | 采用 | spike/8-misc/REPORT.md | 1.4.1 不支持 class file 70 |
| logstash-logback-encoder | **8.1** | 8 | 采用 | spike/8-misc/REPORT.md | JSON 含 MDC `traceId` |
| Flyway | Boot BOM starter-flyway + `flyway-mysql` | 13 | 采用 | — | 脚本只在 platform-db；仅 admin-app 执行 |
| MySQL driver | Boot BOM `mysql-connector-j` | 13 | 采用 | — | 现网 MySQL 8.0.25 |
| Testcontainers | Boot BOM 2.0.5 `testcontainers-junit-jupiter` + `testcontainers-mysql` | 13 | 备选本机 / CI 采用 | — | `FlywayV1IT`；本机无 Docker 走 `-DskipITs` |

结论枚举：`待冒烟` / `采用` / `备选` / `阻塞`。

## 本机环境（2026-08-18 探测）

| 项 | 实测 | 编组 A 要求 |
|----|------|-------------|
| `java -version`（默认 PATH） | Oracle JDK **25.0.4** LTS（`D:\develop\jdk\jdk-25.0.4`） | 不要用这个跑 spike |
| JDK 26 | Oracle **26.0.2**（`D:\develop\jdk\jdk-26.0.2`，已 `java -version` 核实） | 编组 A 必须用这个 |
| Maven | 3.9.5 | 有即可 |
| Docker | 本机无；虚拟机 19.03.13 | Testcontainers 备选；冒烟用 LAN |

## 执行纪律（仍不写业务代码）

1. JDK 26 已就绪：`D:\develop\jdk\jdk-26.0.2`。跑 spike 时设 `JAVA_HOME=D:\develop\jdk\jdk-26.0.2`，不要用 PATH 里的 25。
2. 先装 Docker Desktop（或等价引擎），`docker version` 能连上 daemon。仍未装，任务 1/3/4/8 的 Testcontainers 会红。
3. spike 工程放仓库根 `spike/<任务号>-<名称>/`，不进 `server/`。
4. 每项绿了再改本表对应行；任一项阻塞则停在编组 A，不进任务 9。
5. 冒烟与正式编码仍需单独授权。
