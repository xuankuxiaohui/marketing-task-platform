# 依赖选型基线

本表记录项目已选组件与版本意图。实际构建由 [server/pom.xml](../../../server/pom.xml)、前端 `package.json` 和 [pnpm-lock.yaml](../../../web/pnpm-lock.yaml) 决定；变更时同步本表与构建文件，差异必须核实，不能静默以其中一份覆盖另一份。

以下是仓库基线，不是最新版本推荐。本轮只核对声明与历史记录，没有解析全部依赖树、联网验证兼容性或重新运行 Spike。

## 后端

| 组件 | 项目基线 | 说明 |
|---|---|---|
| JDK | 26；本机约定路径 `D:\develop\jdk\jdk-26.0.2` | POM enforcer 限制 `[26,27)`；不要使用默认 PATH 的 25 |
| Spring Boot | 4.1.0 | 父 POM；JSON 使用 Jackson 3 `tools.jackson` |
| Redisson | 4.6.1 | 手动装配；Lua StringCodec 注意事项见历史报告 |
| Sa-Token | 1.45.0 | boot4 starter + redis-template；双 StpLogic |
| MyBatis-Plus | 3.5.17 | boot4 starter |
| Spring Cache / Caffeine / Redis 客户端 | Boot BOM | 项目封装 PlatformCache |
| springdoc-openapi | 3.1.0 | 两应用命名空间内导出三分组契约 |
| AviatorScript | 5.4.3 | 表达式白名单约束见设计 §5.10 |
| Hutool core / crypto | 5.8.47 | 禁用 JSON/HTTP/DB/all 模块 |
| easy-captcha | 1.6.2 | 图形验证码 |
| jqwik | 1.9.3 | 属性测试 |
| ArchUnit | 1.5.0 | 模块与架构检查 |
| logstash-logback-encoder | 8.1 | JSON 日志 |
| Flyway / MySQL driver / Micrometer | Boot BOM | Flyway 仅 platform-db；Prometheus 内网抓取 |
| Testcontainers | Boot BOM | kernel 测试依赖；真实 MySQL/Redis IT，不是可被 H2 替代的占位 |

## 前端与环境

- pnpm 基线见 [web/package.json](../../../web/package.json) 的 `packageManager`（当前为 9.15.9），Node 约束见同文件 `engines`。
- 管理端 Vue 3 + Ant Design Vue，门户 Vue 3 + Vant 4；确切组件版本以各包清单及锁文件为准。
- 共享开发环境约定为 MySQL 8.0.25、Redis 6.0.8 / DB 2；这不是已上线环境。本轮未连接验证服务版本，也不升级共享实例。
- Compose / IT 使用 MySQL 8 与 Redis 7 镜像。环境差异需在兼容验证中记录，不能用测试镜像替代部署验收。
- 本机无 Docker；单元测试本地执行，Testcontainers IT 留给具备 Docker 的 CI。无需为本轮文档整理安装 Docker 或执行实验。

## 历史验证资料

2026-08-18 的组件验证记录如下，结论仅对当时环境与代码有效。报告用于解释选型和装配注意事项，不代表当前应用、集成或容量验收通过。

| 组件 | 历史报告 |
|---|---|
| Redisson | [spike/1-redisson](../../../spike/1-redisson/REPORT.md) |
| Sa-Token | [spike/2-sa-token](../../../spike/2-sa-token/REPORT.md) |
| MyBatis-Plus | [spike/3-mybatis-plus](../../../spike/3-mybatis-plus/REPORT.md) |
| 两级缓存 | [spike/4-cache](../../../spike/4-cache/REPORT.md) |
| springdoc | [spike/5-springdoc](../../../spike/5-springdoc/REPORT.md) |
| AviatorScript | [spike/6-aviator](../../../spike/6-aviator/REPORT.md) |
| Hutool | [spike/7-hutool](../../../spike/7-hutool/REPORT.md) |
| 其他测试与日志组件 | [spike/8-misc](../../../spike/8-misc/REPORT.md) |

版本升级单独说明原因、影响与验证结果，不借业务重构顺手升级。组件选择理由见 [component-selection.md](component-selection.md)。
