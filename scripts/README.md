# 本机四进程（不走 Compose）

本机没有 Docker 时，用虚拟机上的 MySQL / Redis，在 Windows 上直接起：

| 进程 | 端口 | 说明 |
|------|------|------|
| `admin-app` | 8080 | 后台 API，`/admin/**` + `/actuator` |
| `portal-app` | 8081 | 门户 API，`/api/**`（`/internal/**` 不上公网） |
| admin Vite | 5173 | 管理端，代理 `/admin` → 8080 |
| client Vite | 5174 | C 端，代理 `/api` → 8081 |

**不改、不读 `deploy/`。** `deploy/.env` 是 Compose / 线上的（Docker 主机名 `mysql` / `redis`）。本机 JVM 只读仓库根 `.env.local`。

## 一次配置

`.env.local` 已被 gitignore，和 `deploy/.env` 不是同一份：

```powershell
.\scripts\dev.ps1 init
# 或：Copy-Item scripts\env.example .env.local
```

`.env.local` 里 JDBC / Redis 必须是虚拟机 IP（例如 `192.168.88.149:3308`），不能是 `mysql` / `redis`。`REDIS_DATABASE` 必须是 `2`。填好后就可以把 `deploy/.env` 还原成 `deploy/.env.example`。

JDK 用 **26**。本机 PATH 默认是 25，脚本会优先 `D:\develop\jdk\jdk-26.0.2`。也可设 `MKT_JAVA_HOME`。

## 日常命令

```powershell
.\scripts\dev.ps1 start              # 四个都起（缺 jar 会先 package）
.\scripts\dev.ps1 status
.\scripts\dev.ps1 restart            # 停再起，改完 Java 后用
.\scripts\dev.ps1 restart -Rebuild   # 重新 mvn package 再起后端
.\scripts\dev.ps1 stop
.\scripts\dev.ps1 logs               # 最近日志
.\scripts\dev.ps1 logs -Target admin -Follow
```

只动一部分：

```powershell
.\scripts\dev.ps1 restart -Target backend
.\scripts\dev.ps1 restart -Target frontend
.\scripts\dev.ps1 restart -Target admin
.\scripts\dev.ps1 restart -Target portal
.\scripts\dev.ps1 restart -Target admin-web
.\scripts\dev.ps1 restart -Target client
```

IDE 远程调试（JDWP，不 suspend）：

```powershell
.\scripts\dev.ps1 start -Target backend -DebugJvm
# admin 5005 / portal 5006
```

`scripts\dev.cmd` 是同一入口，给不想碰执行策略的终端用。

## 打开哪里

- 管理端：http://127.0.0.1:5173
- C 端：http://127.0.0.1:5174
- admin 健康：http://127.0.0.1:8080/actuator/health/readiness
- portal 健康：http://127.0.0.1:8081/actuator/health/readiness

日志和 pid 在 `.run/`（gitignore）。停不干净时 `status` 会列出端口占用，`stop` 会按 pid + 端口杀进程树。

## 不要做什么

- 不要为了本机调试去改 `deploy/docker-compose.yml` 或把 Compose 主机名改成虚拟机 IP（那是演示箱 / CI 的）。
- 不要用 Redis db0 / db1。
- 不要提交 `.env.local` / `.env` / `deploy/.env`。
- 不要把本机虚拟机地址写进 `deploy/.env`。
