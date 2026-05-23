# 前端共享规范

admin-web (Element Plus) 和 client-web (Vant 4) 两个 Vue 项目共同遵循的规则。

## 目录

1. [项目结构](#项目结构)
2. [技术选型对比](#技术选型对比)
3. [API 层组织](#api-层组织)
4. [状态管理 (Pinia)](#状态管理-pinia)
5. [Mock 用户上下文](#mock-用户上下文)
6. [组件规范](#组件规范)
7. [TypeScript 规范](#typescript-规范)
8. [错误处理](#错误处理)
9. [常量管理](#常量管理)
10. [Composables（组合式函数）](#composables组合式函数)
11. [可访问性 (A11y)](#可访问性-a11y)
12. [生命周期与清理](#生命周期与清理)
13. [性能](#性能)
14. [环境变量与构建配置](#环境变量与构建配置)
15. [已知待改进](#已知待改进)

---

## 项目结构

```
admin-web/src/                    client-web/src/
  api/                              api/
    http.ts       -- Axios 实例       http.ts       -- Axios 实例
    task.ts       -- 任务 API         task.ts       -- 任务 API (所有端点)
    step.ts       -- 步骤 API
    filter.ts     -- 过滤器 API
    platform.ts   -- 端配置 API
    instance.ts   -- 实例 API
  router/                           router/
    index.ts      -- 路由配置         index.ts      -- 路由配置 (懒加载)
  stores/                           stores/
    user.ts       -- Pinia 用户状态   user.ts       -- Pinia 用户状态
  views/                            views/
    login/                           login/
    task/          -- 任务页          task/          -- 任务页
      tabs/        -- 编辑子页
    instance/      -- 实例页
  styles/                           App.vue
    theme.css     -- 全局主题        main.ts
  App.vue           -- 布局壳子
  main.ts           -- 入口
```

建议新增目录：
```
  constants/        -- 常量定义（枚举值、标签映射、API 路径）
  composables/      -- 可复用组合式函数
  types/            -- 共享 TypeScript 类型
```

## 技术选型对比

| 方面 | admin-web | client-web |
|---|---|---|
| 框架 | Vue 3 + Composition API | Vue 3 + Composition API |
| UI 库 | Element Plus（全局注册） | Vant 4（按需自动导入） |
| 状态管理 | Pinia (options-api) | Pinia (options-api) |
| 构建 | Vite | Vite |
| 路由加载 | 静态 import | 动态 import (代码分割) |

## API 层组织

**http.ts（Axios 实例）**：
- `baseURL: '/api'`, `timeout: 10000`
- 请求拦截器：从 Pinia `useUserStore()` 读取用户上下文，注入 `X-User-*` 和 `X-Platform` Header
- 响应拦截器：统一处理 401 → 跳转登录、500 → 全局提示

**API 文件范式**：
```typescript
import { http } from './http'

export interface Task { id?: string; code: string; name: string; /* ... */ }

export function listTasks(params: Record<string, any>) {
  return http.get('/admin/task', { params })
}
```

- TypeScript 接口与 API 函数同文件定义；接口被多处引用时提取到 `types/`
- 响应需解包两层：`response.data`（Axios）→ `data.data`（Result 包装）
- 建议封装 `unwrapResponse<T>(response): T` 工具函数

## 状态管理 (Pinia)

模式：options-api 风格 `defineStore`

```typescript
export const useUserStore = defineStore('user', {
  state: () => ({
    userId: 'u_demo', province: 'BJ', role: 'vip',
    tags: 'vip,active', orgId: 'org_001', level: 5, platform: 'WEB',
  }),
})
```

**规范**：
- 组件中修改 store 应通过 `store.$patch()` 或 actions，避免直接 `v-model="store.xxx"`
- client-web `MockLogin.vue` 的做法更好：先 `reactive()` 拷贝，再 `$patch` 提交
- 每个 store 职责单一

## Mock 用户上下文

**流程**：Pinia `useUserStore` 持有用户属性 → Axios 请求拦截器读取 store 并设置 HTTP Header → 后端 `UserContextInterceptor` 解析为 `UserContext`

- admin-web 拦截器：无条件设置所有 Header
- client-web 拦截器：条件设置（`if (user.userId)` 等），`platform` 默认为 `'WEB'`
- Mock UI：`MockLogin.vue` 提供表单编辑 store 字段，用于切换用户身份

## 组件规范

- 所有组件使用 `<script setup lang="ts">`（Composition API）
- Props 使用 `defineProps<T>()` 泛型声明
- 双向绑定使用 `defineModel<T>()`（Vue 3.4+）
- `defineExpose()` 模式应考虑升级为 props down + events up
- 状态使用 `ref()` / `reactive()`，派生值使用 `computed()`，副作用使用 `watch()` / `watchEffect()`
- 初始化逻辑放在 `onMounted()` 中
- 每个组件使用 `<style scoped>` 隔离样式
- 组件文件行数建议不超过 300 行

## TypeScript 规范

- 保持 `"strict": true`（当前已开启，不要降级）
- 建议开启 `"noUnusedLocals": true` 和 `"noUnusedParameters": true`
- 模块解析保持 `"moduleResolution": "bundler"`（Vite 推荐）
- **禁止 `any`**：当前 7 处使用了 `any`
  - `catch (e: any)` → `catch (e: unknown)` + 类型守卫
  - `defineModel<any>()` → 具体泛型类型
- 模板引用类型：`const stepsTabRef = ref<InstanceType<typeof StepsTab>>()`
- 类型导入：使用 `import type { ... }` 明确类型导入

## 错误处理

**响应拦截器**（在两个 http.ts 中）：
```typescript
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) router.push('/login')
    else if (error.response?.status >= 500) ElMessage.error('服务器异常，请稍后重试')
    return Promise.reject(error)
  }
)
```

**组件级**：每个 API 调用必须用 try/catch 包裹。建议封装 `useRequest()` composable 统一管理 loading/error/data 状态。

## 常量管理

以下字符串在多个组件中重复硬编码，建议提取到 `constants/index.ts`：

| 类别 | 重复值 |
|---|---|
| 周期类型 | `'ONCE'`, `'DAILY'`, `'MONTHLY'`, `'CRON'`, `'SPECIAL'` |
| 步骤类型 | `'CLICK'`, `'CALLBACK'`, `'PROGRESS'`, `'REWARD'`, `'PASSIVE'` |
| 任务状态 | `'DRAFT'`, `'PUBLISHED'`, `'OFFLINE'` |
| 实例状态 | `'PENDING'`, `'IN_PROGRESS'`, `'COMPLETED'`, `'REWARDED'` |
| 平台 | `'WEB'`, `'ADMIN'`, `'IOS'`, `'ANDROID'`, `'MINIAPP'` |

```typescript
// constants/index.ts
export const PeriodType = {
  ONCE: 'ONCE', DAILY: 'DAILY', MONTHLY: 'MONTHLY',
  CRON: 'CRON', SPECIAL: 'SPECIAL',
} as const
export const PeriodTypeLabel: Record<string, string> = { /* ... */ }
```

API 路径也应提取为常量：`constants/api.ts`。

## Composables（组合式函数）

当前项目没有 `composables/` 目录，以下逻辑适合提取：

| 候选 Composable | 当前状态 |
|---|---|
| `useStatusLabel()` | 标签映射在 InstanceList / TaskDetail 中重复 |
| `usePeriodLabel()` | 标签映射在 TaskList、BasicTab 中重复 |
| `useRequest()` | loading/error/data 模式每个组件都在手工做 |
| `useUserContext()` | 用户上下文读写散落在 http.ts 和 MockLogin |

命名约定：以 `use` 开头，返回 refs。

```typescript
export function useRequest<T>(fn: () => Promise<T>) {
  const loading = ref(false); const error = ref<Error | null>(null); const data = ref<T>()
  async function execute() {
    loading.value = true; error.value = null
    try { data.value = await fn() } catch (e) { error.value = e as Error }
    finally { loading.value = false }
  }
  return { loading, error, data, execute }
}
```

## 可访问性 (A11y)

达到 WCAG 2.1 Level A 基线：

- **装饰性 SVG**：添加 `aria-hidden="true"`
- **语义化 HTML**：使用 `<nav>`、`<main>`、`<section>` 而非全是 `<div>`
- **表单**：每个 input 应有对应的 `<label>` 或 `aria-label`
- **颜色对比度**：主题色 `#2563eb` 在白色背景上为 4.6:1（勉强 AA）
- **键盘导航**：确保 Tab 键能遍历所有可操作元素

## 生命周期与清理

创建资源的代码旁边就应写好对应的清理代码：

| 资源 | 清理方式 |
|---|---|
| `setInterval` / `setTimeout` | `onUnmounted(() => clearInterval(id))` |
| `addEventListener` | `onUnmounted(() => removeEventListener(...))` |
| `watch` / `watchEffect` | 自动清理（`<script setup>` 自动绑定生命周期） |
| Axios 请求取消 | `const controller = new AbortController()` → `onUnmounted(() => controller.abort())` |
| WebSocket / SSE | `onUnmounted(() => connection.close())` |

## 性能

- **computed vs method**：派生值用 `computed()`（有缓存），不用方法
- **懒加载路由**：大型应用用动态 import（client-web 已做）
- **列表虚拟化**：列表超过 100 条时考虑虚拟列表
- **避免 `v-if` 和 `v-for` 同时使用**：Vue 官方明确禁止

## 环境变量与构建配置

- 创建 `.env.development`：`VITE_API_BASE_URL=http://localhost:8080`
- 创建 `.env.production`：`VITE_API_BASE_URL=https://api.example.com`
- 禁止将敏感信息放入 `VITE_*` 变量（会被打包进客户端代码）
- admin-web：dev server 端口 5173
- client-web：dev server 端口 5174

## 已知待改进

| 项目 | 建议 |
|---|---|
| ESLint | 添加 `@vue/eslint-config-typescript` 推荐规则 |
| Prettier | 添加 `.prettierrc`：2 空格、单引号、无分号 |
| .editorconfig | 添加：`indent_size = 2`、`end_of_line = lf` |
| TypeScript `any` | 替换为 `unknown` 或具体类型 |
| 响应拦截器 | 添加统一错误处理 |
| try/catch | 包裹所有 API 调用 |
| Constants | 提取 magic strings 到 `constants/` |
| Composables | 提取 `useRequest()`、标签映射 composables |
| CSS `!important` | 迁移到 CSS 变量体系 |
| A11y | 装饰性 SVG 加 `aria-hidden` |
| API 路径 | 提取到 `constants/api.ts` |
| `tsconfig.tsbuildinfo` | 加入 `.gitignore` |

## 相关文档

- `frontend/admin.md` — 管理后台 (Element Plus) 特有规则
- `frontend/web.md` — C 端 (Vant 4) 特有规则
- `api-rules.md` — API 响应格式、拦截器契约
