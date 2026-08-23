# marketing-task-platform

营销任务平台：运营编排任务，C 端按步骤完成，平台发奖。

当前是**测试阶段**（preview 之前，版本未到 0.0.1）。规格仍以 [`.kiro/specs/platform-v2`](.kiro/specs/platform-v2/README.md) 为准。进度看 [PROJECT_STATUS.md](PROJECT_STATUS.md)。编码代理先读 [AGENTS.md](AGENTS.md)。编码规范在 [`docs/standards/`](docs/standards/README.md)。

## 怎么跑

两个进程：后台 `admin-app` 走 `/admin/**`，门户 `portal-app` 走 `/api/**`。`/internal/**` 不上公网。

JDK 26 / Spring Boot 4.1。域模块之间不互相依赖。

### Compose（有 Docker 时）

```bash
cp deploy/.env.example deploy/.env          # 改占位符，不要提交真实密钥
cd server && mvn -DskipTests -DskipITs package
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
```

本机入口默认 `http://127.0.0.1:18080`。边缘只暴露 `/admin`、`/api`，不转发 `/internal`。

### 本机四进程（无 Docker，连虚拟机 MySQL / Redis）

不要改 `deploy/`。本机连接写仓库根 `.env.local`（gitignore），和线上的 `deploy/.env` 分开：

```powershell
.\scripts\dev.ps1 init                 # 第一次：生成 .env.local（不会读/写 deploy/.env）
.\scripts\dev.ps1 start
.\scripts\dev.ps1 status
.\scripts\dev.ps1 restart              # 改完代码后
.\scripts\dev.ps1 restart -Rebuild     # 重新 package 后端再起（跳过测试和覆盖率）
.\scripts\dev.ps1 stop
```

管理端 http://127.0.0.1:5173 ，C 端 http://127.0.0.1:5174 。说明见 [scripts/README.md](scripts/README.md)。

## Git

唯一长期分支是 `master`。新工作从 `origin/master` 开短命分支，PR 回去。
