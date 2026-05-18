# marketing-task-platform

营销自动化任务平台。

## 文档

当前版本文档：[`docs/v0.1.0/`](./docs/v0.1.0/)

| 文档 | 内容 |
|---|---|
| [设计文档](./docs/v0.1.0/design.md) | 系统架构、领域模型、核心引擎、MVP 范围 |
| [API 文档](./docs/v0.1.0/api.md) | Admin / Client / Internal API 参考和示例 |
| [SQL 文档](./docs/v0.1.0/sql.md) | 表结构、索引、迁移文件索引 |
| [版本说明](./docs/v0.1.0/release-notes.md) | v0.1.0 新增内容、验证结果、已知限制 |

## 快速验证

```bash
mvn -f backend/pom.xml -DskipTests compile -q
npm --prefix admin-web run build
```
