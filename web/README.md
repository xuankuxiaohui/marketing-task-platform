# 前端开发

pnpm workspace 包含 `apps/admin`（Vue + Ant Design Vue）、`apps/client`（Vue + Vant）和 `packages/shared`（无 UI 的工具及 OpenAPI 类型）。两端各自维护路由、会话和 HTTP 适配器。仓库约束见 [AGENTS.md](../AGENTS.md)，格式和类型规则由 ESLint / TypeScript 配置执行，体验基线见 [DESIGN.md](../DESIGN.md)。

## 常用命令

在 `web/` 执行；Node/pnpm 范围以 [package.json](package.json) 为准：

```text
pnpm install --frozen-lockfile
pnpm --filter admin dev
pnpm --filter client dev
pnpm lint
pnpm test
pnpm --filter admin build
pnpm --filter client build
```

`pnpm lint` 已包含两端与 shared 的类型检查；`pnpm test` 运行 workspace 的 Vitest。应用开发默认端口为 5173/5174，API 代理指向本地两应用；四进程启动见 [scripts/README.md](../scripts/README.md)。启动前端不代表后端可用。

## API 类型

```text
pnpm --filter @mkt/shared gen:api:fetch
pnpm --filter @mkt/shared gen:api
```

`gen:api:fetch` 从正在运行且可访问 springdoc 的两应用导出 admin/portal/internal JSON，再生成 TypeScript；服务地址可配置 `ADMIN_OPENAPI_BASE` / `PORTAL_OPENAPI_BASE`。`gen:api` 只从本地 JSON 生成，不访问后端。

JSON 位于 `packages/shared/openapi/`，生成类型位于 `packages/shared/src/openapi/`，生成入口为 [gen-api.mjs](packages/shared/scripts/gen-api.mjs)。不要手改生成类型；变更契约时连同生成源和调用方一起检查。当前 CI 的 [check-openapi-types.sh](../ci/check-openapi-types.sh) 只检查 JSON→TS 一致，尚不能发现后端→JSON 的漂移；这是后续重构要补的验证链路。

## 测试边界

- Vitest 覆盖纯函数与组件等测试；存在测试文件不等于已通过，也不等于交互完整。
- Playwright 入口是 `web/` 下的 `pnpm test:e2e`，不在 `admin` 包中。配置见 [playwright.config.ts](playwright.config.ts)，CI 使用 [e2e-compose.sh](../ci/e2e-compose.sh) 准备真实后端与浏览器。
- E2E 当前使用 Vite 开发服务器；生产构建产物、TLS、反代路由仍需独立验收。
- 个人中心分页、错误重试、领奖后的分页重载及跨标签页切换有独立浏览器回归：`pnpm exec playwright test -c playwright.personal-lists.config.ts`。它使用真实门户 UI 和测试 API 响应，独立启动并清理本地 Vite；默认端口 4185，可用 `PLAYWRIGHT_PERSONAL_LISTS_PORT` 指定。真实后端业务旅程仍使用上面的 Compose E2E 入口。
- 分页应验证真实组件先更新 loading 再触发 load 的顺序；筛选/会话切换需覆盖响应乱序，网络异常后应可继续操作。
- 真实浏览器检查负责视口、滚动、弹层、焦点和图片等视觉交互；不能仅靠组件测试判断。

远端独立测试栈可以设置 `E2E_ENV_FILE` 指向本轮被忽略的环境文件；相对路径从仓库根解析，文件必须存在。该文件提供 `MKT_INIT_ADMIN_PASSWORD` 和 `REDIS_PASSWORD`，无需覆盖已有 `deploy/.env`。`E2E_API_BASE` 指向后端网关的本机 SSH 隧道地址，两端 Vite 的代理通过 `PORTAL_PROXY_TARGET` / `ADMIN_PROXY_TARGET` 指向同一地址。

验证码读取可通过另一个本机 SSH TCP 隧道：同时设置 `E2E_REDIS_HOST=127.0.0.1` 和 `E2E_REDIS_PORT=<独立隧道端口>`。只允许 loopback 地址及非 `6379` 端口，隧道目标必须是本轮独立测试 Redis；helper 只执行认证、选择 DB 2 和读取 `captcha:admin:*` / `captcha:portal:*`，无需本机 Docker。未配置这两个变量时沿用本地 Docker Compose 读取方式；独立 Compose 项目需设置 `COMPOSE_PROJECT_NAME`。环境文件、E2E 状态文件和会话凭据不要放进测试报告。

个人中心列表与会话隔离已完成一批代码重构，包含任务分类、积分类型、分页/刷新/重试、奖品领取与详情，以及旧请求隔离。回归入口见 [验证映射](../docs/verification-matrix.md)，后续候选工作见 [重构方案](../docs/refactoring-blueprint.md)；历史完成记录不代替本次执行结果。
