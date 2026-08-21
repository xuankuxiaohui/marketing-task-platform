# marketing-task-platform

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。

当前是**测试阶段**（preview 之前，版本未到 0.0.1）。规格仍以 [`.kiro/specs/platform-v2`](.kiro/specs/platform-v2/README.md) 为准。进度看 [PROJECT_STATUS.md](PROJECT_STATUS.md)。编码代理先读 [AGENTS.md](AGENTS.md)。编码规范在 [`docs/standards/`](docs/standards/README.md)。

## 怎么跑

两个进程：后台 `admin-app` 走 `/admin/**`，门户 `portal-app` 走 `/api/**`。`/internal/**` 不上公网。

JDK 26 / Spring Boot 4.1。域模块之间不互相依赖。

```bash
cp deploy/.env.example deploy/.env          # 改占位符，不要提交真实密钥
cd server && mvn -DskipTests -DskipITs package
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

本机入口默认 `http://127.0.0.1:18080`。边缘只暴露 `/admin`、`/api`，不转发 `/internal`。

## Git

唯一长期分支是 `master`。新工作从 `origin/master` 开短命分支，PR 回去。
