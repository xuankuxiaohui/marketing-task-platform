# Windows 本地开发

[dev.ps1](dev.ps1) 在 Windows 上管理两个 JVM 和两个 Vite 进程，连接已配置的 MySQL/Redis，不启动 Docker。以下描述依据脚本静态检查；本轮未启动应用。

| 目标 | 默认端口 | 浏览器入口或用途 |
|---|---|---|
| `admin` | 8080 | 后台 API `/admin/**` |
| `portal` | 8081 | 门户 API `/api/**`；`/internal/**` 仅内部调用 |
| `admin-web` | 5173 | [管理端](http://127.0.0.1:5173) |
| `client` | 5174 | [门户](http://127.0.0.1:5174) |

## 环境准备

- 安装 JDK 26、Maven、pnpm；前端版本范围见 [web/package.json](../web/package.json)。
- 默认配置为仓库根 `.env.local`，可通过 `MKT_ENV_FILE` 指定另一个本地配置文件。变量模板见 [env.example](env.example)。配置文件不能提交到 Git。
- JDBC/Redis 地址须能被本机解析，不使用 Compose 专用的 `mysql` / `redis` 主机名；共享环境只使用 `mkt_platform` 与 Redis DB 2。
- JDK 查找顺序：`MKT_JAVA_HOME`、`D:\develop\jdk\jdk-26.0.2`、`JAVA_HOME`，均验证为 JDK 26。

在仓库根执行：

```powershell
.\scripts\dev.ps1 init
```

`init` 在目标配置已存在时不覆盖。若仓库根存在非 Compose 配置的旧 `.env`，当前实现会将其复制到目标配置并删除旧 `.env`；否则复制模板。填写本地配置不需要修改或还原 `deploy/.env`。

## 日常操作

```powershell
.\scripts\dev.ps1 start
.\scripts\dev.ps1 status
.\scripts\dev.ps1 logs -Target admin -Follow
.\scripts\dev.ps1 restart -Target backend -Rebuild
.\scripts\dev.ps1 stop
```

`-Target` 支持 `all`、`backend`、`frontend` 和表格中的单个目标。[dev.cmd](dev.cmd) 是同一入口的包装。

- `start` / `restart` 仅在缺少 JAR 或指定 `-Rebuild` 时打包。**修改 Java 后仅执行 `restart` 会继续使用旧 JAR。**
- 打包命令跳过测试和 JaCoCo，仅用于开发启动，不能作为测试通过的证据。
- 前端只在 `web/node_modules` 不存在时自动安装依赖；修改依赖后应自行执行 `pnpm install --frozen-lockfile`。
- 当前 `start -Target frontend` 也会检查本地配置、中间件和 JDK；仅需运行前端时可使用 [web/README.md](../web/README.md) 的命令。
- 日志和 PID 位于 `.run/`，`logs -Follow` 只能指定单个目标。
- **停止归属（F16）**：`stop` / `restart` 优先终止 `.run/<name>.pid` 保存的进程树；若端口仍被占用，仅当能验证该监听进程归属本项目时才清理——判定顺序为 (1) 与保存 PID 相同 (2) 保存 PID 的子进程树 (3) 命令行匹配本项目 jar（`admin-app-*.jar` / `portal-app-*-exec.jar`）或对应 Vite/pnpm filter。无法验证归属时**跳过端口强杀**并告警；确认是误占且可丢弃时显式加 `-Force`。本轮未在共享环境执行 stop。

调试命令：

```powershell
.\scripts\dev.ps1 start -Target backend -DebugJvm
```

JDWP 默认不暂停，admin/portal 分别使用 5005/5006；当前监听地址为 `*`，仅在受控本地网络启用。

测试命令与范围见 [后端说明](../server/README.md) 和 [前端说明](../web/README.md)。本机没有 Docker 时，真实 MySQL/Redis 集成测试及部署冒烟留到具备条件的 CI/测试环境。
