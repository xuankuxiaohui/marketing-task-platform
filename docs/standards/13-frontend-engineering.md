# 13 · 前端工程规范

> 适用范围：`web/` 的构建、依赖、类型生成、质量门禁、目录约定。  
> 编码风格见 [12-frontend-code.md](12-frontend-code.md)。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| pnpm Workspaces | https://pnpm.io/workspaces | monorepo 包管理 |
| pnpm Catalog / 锁文件 | https://pnpm.io/npmrc | 锁文件入库 |
| Vite | https://vite.dev/guide/ | 开发与构建 |
| Vue SFC 规范 | https://vuejs.org/api/sfc-spec.html | 单文件组件 |
| vue-tsc | https://github.com/vuejs/language-tools | 类型检查 |
| ESLint | https://eslint.org/docs/latest/use/configure/ | 静态检查 |
| typescript-eslint | https://typescript-eslint.io/ | TS 规则 |
| eslint-plugin-vue | https://eslint.vuejs.org/ | Vue 规则，含 Style Guide |
| Prettier | https://prettier.io/docs/en/ | 格式（若启用则与 ESLint 分工） |
| EditorConfig | https://editorconfig.org/ | 跨编辑器空白符 |
| openapi-typescript | https://openapi-ts.dev/ | 从 OpenAPI 生成类型 |
| unplugin-auto-import | https://github.com/unplugin/unplugin-auto-import | 门户按需 API（Vant） |
| Playwright CI | https://playwright.dev/docs/ci | E2E 在 CI |
| Twelve-Factor II / III | https://12factor.net/dependencies · https://12factor.net/config | 依赖显式声明；环境配置 |

## 2. Workspace

```yaml
# web/pnpm-workspace.yaml
packages:
  - "apps/*"
  - "packages/*"
```

1. **MUST** 使用 pnpm，禁止在 `web/` 用 npm/yarn 另锁一份。
2. **MUST** 提交 `pnpm-lock.yaml`。CI `pnpm install --frozen-lockfile`。
3. **MUST** 内部包用 `workspace:` 协议引用 `@mkt/shared`。
4. 依赖版本 **SHOULD** 在 workspace 根统一。应用不得偷偷引入不同大版本的 `vue` / `typescript`。
5. **MUST NOT** 提交 `node_modules`、`dist`、覆盖率缓存。

## 3. 应用脚手架

| 应用 | 基础 | 构建 |
|------|------|------|
| `apps/admin` | vue-pure-admin-thin | Vite |
| `apps/client` | Vue 3 + Vant 4 + unplugin-auto-import | Vite |

1. 开发代理：admin → `/admin`，client → `/api`。**MUST NOT** 把生产后端地址写死在源码。
2. 环境变量仅 `VITE_` 前缀可进客户端包。**MUST NOT** 把服务端密钥放进 `VITE_*`。
3. 生产 API 基路径走部署反代同源（design §2.3.3），默认不开 CORS。
4. 构建产物：admin 静态资源由 Nginx 托管；client 同样。文件名带 content hash。

## 4. OpenAPI 类型生成（NFR 可维护性 2）

流水线：

```text
springdoc 导出
  admin.json / portal.json / internal.json
        ↓ openapi-typescript
packages/shared/src/openapi/*.ts    （生成物）
        ↓
apps/admin、apps/client 只 import 生成类型
```

1. **MUST** 生成命令写入 `package.json`：`pnpm --filter @mkt/shared gen:api`。JSON **MUST** 来自两应用命名空间（`/admin/v3/api-docs/{group}`、`/api/v3/api-docs/{group}`），**MUST NOT** 用 `KernelTestApplication` 冒烟导出。任务 36 落地 `web/` 之前，本命令不是当前 PR 门禁。
2. **MUST NOT** 手改生成文件。改契约先改后端 + 规格，再生成。
3. CI **MUST** 在下列变更后跑 `gen:api` 并 `git diff --exit-code`（与 04 §10、11 §10 同一条）：
   - 后端 `controller` / `command` / `query` / `response` / `ErrorCode` / springdoc 分组
   - 任何 `web/` 源码
4. MSW handler **MUST** 按生成类型或 OpenAPI 示例构造，禁止随意字段。
5. 类型隔离：**门户包 MUST NOT** import `admin.json` 生成物；**管理端默认 MUST NOT** 把 `internal.json` 打进生产包。调试 internal 须显式、单独入口，不得进 admin 主包。
6. `Result.code` 的 OpenAPI `oneOf` 与 `isOk` / `isFail` 守卫见 [07-api-design.md](07-api-design.md) §6.1。守卫实现放 `packages/shared`，禁止各页手写。

## 5. 静态检查

最低集（任务 10 / 前端骨架落地时一次配齐）：

| 工具 | 作用 |
|------|------|
| `vue-tsc --noEmit` | 类型 |
| ESLint + eslint-plugin-vue + typescript-eslint | Vue Style Guide + TS |
| Prettier 或 ESLint stylistic | 格式 |
| EditorConfig | UTF-8、LF、缩进 2（前端生态默认；**与后端 Java 4 空格不同**） |

1. 前端缩进 **MUST** 为 2 空格（Vue/Prettier 默认）。不要用 Java 的 4 空格套前端。
2. **MUST** 开启 `vue/multi-word-component-names`、`vue/no-v-for-v-if-on-the-same-element`、`vue/require-v-for-key`。
3. **MUST** 禁止 `eslint-disable` 大段；单行 disable 要写原因。
4. CI 对 `web/`：`lint` + `typecheck` + `test` 阻断合并。

## 6. HTTP 客户端

1. 每端一个封装：admin 带 CSRF 拦截器；client 带 Bearer 拦截器。
2. 401 处理见 12 §6，集中在拦截器，页面不各自 `if (code===401)`。
3. 超时明确配置。上传/导入可单独更长超时。
4. **MUST NOT** 在页面直接 `fetch` 散落 URL 字符串。URL 常量与生成的 path 对齐。

## 7. 路由与代码分割

1. 管理端路由与菜单种子（design §4.10）同步。权限码字符串不得在前端发明。
2. 页面 **SHOULD** 路由级懒加载 `() => import('./views/...')`。
3. 画布（vue-flow）、ECharts（P1）**MUST** 异步加载，避免进入登录页就下载。

## 8. 测试命令

```text
pnpm --filter admin test        # Vitest
pnpm --filter client test
pnpm --filter admin test:e2e    # Playwright，CI 用
```

Playwright 按官方 CI 文档使用浏览器缓存。E2E 基地址来自环境变量，禁止写死个人 IP。

## 9. 性能预算（与 NFR 对齐的前端侧）

1. 门户首屏 **SHOULD** 避免巨大预加载。Vant 按需引入。
2. 图片用规格中的 URL，平台不提供上传（R30.2）；**MUST** 限制渲染尺寸，防原图撑爆。
3. 埋点批量，不每点击打一个请求。

## 10. AI 检查清单

- [ ] 改动在正确 app / shared 包
- [ ] 未手写 OpenAPI 已有类型；契约变更已重生生成物
- [ ] 未把 admin 类型引进 portal / 未把 internal 打进 admin 生产包
- [ ] 未新增 VITE_ 密钥
- [ ] lint/typecheck 规则能过
- [ ] 管理端新页有权限码且菜单可追溯 §4.10 或规格新增
- [ ] 锁文件已更新
