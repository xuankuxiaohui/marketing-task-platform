# server/ — 后端多模块骨架（任务 9）

> 任务 9 当时的骨架说明。v1 已上 master `6d03ef5`（**测试阶段**，尚未 preview）；P1 域与 `web/` 已在树上。现状见 [PROJECT_STATUS.md](../PROJECT_STATUS.md)。

`groupId=com.mkt`。目录名 = `artifactId`。父 POM：`platform-parent` `0.1.0-SNAPSHOT`。

版本唯一来源：[dependency-matrix.md](../.kiro/specs/platform-v2/dependency-matrix.md)（任务 1–8）。

## 模块清单

| artifactId | 包根 | 层 | 本任务装配 |
|------------|------|----|------------|
| platform-kernel | `com.mkt.kernel` | 基础 | 空壳（组件在任务 11） |
| platform-contract | `com.mkt.contract` | 基础 | 空壳，依赖 kernel（端口在任务 12） |
| platform-db | `com.mkt.db` | 基础 | 空壳，依赖 kernel（Flyway 在任务 13） |
| platform-infra | `com.mkt.infra` | 基础设施 | 空壳，依赖 kernel（缓存/Outbox 在任务 15–16） |
| domain-identity | `com.mkt.identity` | 域 | 空壳，依赖 contract + infra |
| domain-task | `com.mkt.task` | 域 | 空壳，依赖 contract + infra |
| domain-reward | `com.mkt.reward` | 域 | 空壳，依赖 contract + infra |
| domain-risk | `com.mkt.risk` | 域 | 空壳，依赖 contract + infra |
| domain-tracking | `com.mkt.tracking` | 域 | 空壳，依赖 contract + infra |
| admin-app | `com.mkt.admin` | 应用 | 启动类 + 命名空间守卫；依赖 infra + db；**未**依赖任何 domain-* |
| portal-app | `com.mkt.portal` | 应用 | 同上；**未**装配 task/reward（RL-05 终态，本任务按「暂不装配」） |

域之间无 Maven 依赖。无 `domain-points`、无 P1 域、无 `web/`。

## 与 dependency-matrix 锁定版本对照

| 组件 | 矩阵锁定 | 父 POM |
|------|----------|--------|
| JDK | 26.0.2 | `java.version=26`；enforcer `[26,27)` |
| Spring Boot | 4.1.0 | `spring-boot-starter-parent` 4.1.0 |
| Redisson | 4.6.1 | `redisson.version`（本任务未引用） |
| Sa-Token | 1.45.0 | `sa-token.version`（本任务未引用） |
| MyBatis-Plus | 3.5.17 | `mybatis-plus.version`（本任务未引用） |
| Spring Cache + Caffeine + Redis | Boot BOM | 本任务未引用 |
| springdoc-openapi | 3.1.0 | `springdoc.version`（本任务未引用） |
| AviatorScript | 5.4.3 | `aviator.version`（本任务未引用） |
| Hutool core + crypto | 5.8.47 | `hutool.version`（本任务未引用） |
| easy-captcha | 1.6.2 | `easy-captcha.version`（本任务未引用） |
| jqwik | 1.9.3 | `jqwik.version`（本任务未引用） |
| ArchUnit | 1.5.0 | `archunit.version`（本任务未引用） |
| logstash-logback-encoder | 8.1 | `logstash-logback-encoder.version`（本任务未引用） |
| Testcontainers | 备选、无锁定版本 | 父 POM 未登记 |

enforcer：直接依赖必须是上表已登记坐标、`org.springframework.boot:*` 或 `com.mkt:*`；并禁 H2 / Hutool JSON·HTTP·DB / XXL-Job / Drools / Spring Cloud / RuoYi。

## 命名空间守卫（RL-08）

| 应用 | 允许前缀 | 越界 |
|------|----------|------|
| admin-app | `/admin`、`/actuator` | 404 |
| portal-app | `/api`、`/internal`、`/actuator` | 404 |

启动时 `ApplicationRunner` 扫描 `RequestMappingHandlerMapping`，路径不在前缀集合则 fail-fast。已排除 `ErrorMvcAutoConfiguration`（避免默认 `/error` 踩红线）。

## 验收命令

```text
$env:JAVA_HOME="D:\develop\jdk\jdk-26.0.2"
cd server
mvn -q -DskipTests compile
```
