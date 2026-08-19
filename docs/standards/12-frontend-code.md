# 12 · 前端编码规范

> 适用范围：`web/apps/admin`（Vue 3 + Element Plus + Pinia）、`web/apps/client`（Vue 3 + Vant 4）。  
> 工程化（构建、类型生成、lint）见 [13-frontend-engineering.md](13-frontend-engineering.md)。

## 1. 依据（官方）

| 来源 | 链接 | 本篇采纳 |
|------|------|----------|
| Vue.js Style Guide | https://vuejs.org/style-guide/ | Priority A 全部；Priority B 作为本项目 MUST |
| Vue Style Guide · Essential | https://vuejs.org/style-guide/rules-essential.html | 多词组件名、详细 props、`v-for` 的 key、禁止 v-if+v-for、scoped 样式 |
| Vue Style Guide · Strongly Recommended | https://vuejs.org/style-guide/rules-strongly-recommended.html | 组件文件名、单文件组件顺序、自闭合组件 |
| Vue `<script setup>` | https://vuejs.org/api/sfc-script-setup.html | 默认写法 |
| Vue Composition API FAQ | https://vuejs.org/guide/extras/composition-api-faq.html | 组合式，不用 Options 新代码 |
| Pinia | https://pinia.vuejs.org/core-concepts/ | 状态模块 |
| TypeScript Handbook | https://www.typescriptlang.org/docs/handbook/intro.html | 严格类型 |
| Element Plus | https://element-plus.org/ | 管理端组件 |
| Vant 4 | https://vant-ui.github.io/vant/#/zh-CN | 门户组件 |
| vue-pure-admin | https://pure-admin.cn/ | 管理端骨架：动态路由、权限指令 |
| Vue Flow | https://vueflow.dev/ | 任务画布（任务 37.2） |

## 2. 两端边界

| | admin | client |
|--|-------|--------|
| 用户 | 运营 / 管理员 | C 端用户 |
| UI 库 | Element Plus | Vant 4 |
| 鉴权 | Cookie（浏览器自动带）+ CSRF 头 | `Authorization: Bearer` |
| 路由 | 动态路由，权限码 = 附录 B | 静态路由 + 登录守卫 |
| 视口 | 桌面优先 | 移动端 H5 / WebView（模块 I） |

1. **MUST NOT** 在一端引入另一端的 UI 库。
2. **MUST NOT** 共用「带 Element/Vant 的业务组件」。共享只限无 UI 的 TS 函数与生成类型。
3. 管理端 **MUST** 基于 vue-pure-admin-thin 的权限指令与多标签，不要自研第二套 RBAC 前端。

## 3. Vue 强制规则（Priority A）

官方 Essential，本项目全部 MUST：

1. 组件名多词：`TaskCard`、`PrizeStatusTag`。禁止 `<Item>`、`<Task>`。根 `App` 除外。
2. props 必须有类型（`defineProps<{ status: PrizeStatus }>()` 或运行时详细对象）。禁止 `defineProps(['status'])`。
3. `v-for` **必须** `:key`，且 key 为稳定 id，禁止数组下标（列表会排序/删除时）。
4. **禁止** 同一元素同时 `v-if` 与 `v-for`。过滤用 `computed`；或 `v-for` 放在 `<template>`。
5. 业务组件样式 **必须** `scoped`（或 CSS modules）。布局/App 可全局。覆盖 Element/Vant 用官方推荐的类或 `:deep()`，禁止改 node_modules。

Priority B 本项目升级为 MUST：

- 组件文件名 PascalCase：`TaskCard.vue`。
- SFC 顺序：`<script setup>` → `<template>` → `<style scoped>`（与 Vue 强推荐及多数脚手架一致；一旦选定不改）。
- 基础组件在模板中自闭合：`<MyInput />`。
- 单文件组件不超过「一屏职责」：容器组件管数据，展示组件管 UI。

## 4. TypeScript

1. **MUST** `strict: true`。禁止新的 `any`。第三方边界用 `unknown` 再收窄。
2. API 请求/响应类型 **MUST** 来自 `packages/shared` 的 OpenAPI 生成物。禁止手写平行 `interface LoginResp`。
3. 组件 props / emit **MUST** 显式类型。
4. 枚举字面量与后端封闭值一致（`IN_PROGRESS`），不要在前端映射成另一套英文。
5. **MUST NOT** `// @ts-ignore` 掩盖错误。`@ts-expect-error` 仅允许一行且注释原因。

## 5. Composition 与状态

1. 新代码 **MUST** 用 `<script setup lang="ts">` + Composition API。禁止新写 Options API。
2. 可复用逻辑抽 `composables/useXxx.ts`，命名 `use` 前缀（Vue 官方 composable 约定）。
3. Pinia store **MUST** 按领域拆：`useSessionStore`、`useTaskStore`。禁止巨型 `useMainStore`。
4. store 只放跨页状态。一次性页面筛选留在页面 `ref`。
5. **MUST NOT** 在 store 持久化令牌到 `localStorage` 的同时再复制一份到内存却不同步。门户令牌存哪以 R32 为准；后台令牌在 Cookie，前端 **MUST NOT** 再存一份会话 JWT。
6. 异步请求：统一封装（见 13）。页面 **MUST** 处理 loading / 空 / 错三态。

```vue
<script setup lang="ts">
import { computed } from "vue";
import type { TaskCard } from "@mkt/shared/openapi";

const props = defineProps<{
  task: TaskCard;
}>();

const rewardPreview = computed(() => props.task.rewardPreview ?? "");
</script>

<template>
  <article class="task-card">
    <h3>{{ task.name }}</h3>
    <p>{{ rewardPreview }}</p>
  </article>
</template>

<style scoped>
.task-card { /* ... */ }
</style>
```

## 6. 与后端契约

1. 成功：用 `@mkt/shared` 的 `isOk(result)` 取 `data`。**MUST NOT** 在未收窄的 `result.code` 上同时当 number 和 string 用（附录 C 异构，见 07 §6.1）。
2. 失败：`isFail(result)` 后展示 `message`；管理端提供复制 `traceId`。
3. 门户 401 四码按 R32.5 文案与跳转，**MUST NOT** 把四码合成一个「请重新登录」。
4. 后台 401 统一登出到登录页。
5. 429：尊重 `Retry-After`，避免死循环重试。
6. 分页组件绑定 `page` / `pageSize` / `total` / `records`，禁止改成 `list`。
7. 时间展示：用共享工具把 ISO-8601 转为 UTC+8 业务日或本地格式；**MUST NOT** 把带偏移的串当本地无时区 `Date` 解析两次。

## 7. 权限与安全

1. 管理端按钮 / 路由 **MUST** 用纯后台下发的权限码（附录 B）。前端隐藏不是安全边界，只是体验。
2. 写操作 **MUST** 带 `X-CSRF-Token`（从 Cookie 或登录 `data.csrfToken` 读取，二者同值）。
3. **MUST NOT** 用 `v-html` 渲染未消毒内容。活动富文本仅渲染后端已消毒字段（R22）。
4. **MUST NOT** 在 URL query 放令牌。
5. 门户 **MUST NOT** 展示风控内部原因、灰度未命中原因（R34.6）。空列表用通用空态。
6. 验证码错误按 R32.2 自动刷新，不把答案记入日志。

## 8. 体验基线（模块 I）

门户必须满足 R32–R36 的交互，而不是「有接口就行」：

- 任务卡片奖励预览公式见 R34.1。
- 步骤时间线：已完成 / 当前 / 未激活。
- 进度类步骤展示 `x/N`；停留页不自动轮询（R34.4）。
- 奖品按钮状态 = 领取七态 × 履约四态（R35.2），映射单测锁定。
- 下拉刷新、触底分页。

管理端页面清单对齐 design §4.10，禁止私自加顶级菜单。

## 9. 埋点

1. 列表曝光由**客户端**报 `task.card.exposure`，服务端列表接口不写该事件（design §2.5.2）。
2. 上报器批量 + sendBeacon + 本地补发。失败 **MUST NOT** 打断主流程（R28.8）。
3. 事件编码只使用附录 D / 元数据已登记值。

## 10. 无障碍与 i18n

1. 图标按钮 **SHOULD** 有 `aria-label`。
2. 文案 P0 用中文写死在 i18n 资源或常量，**SHOULD** 集中 `locales/zh-CN.ts`，避免散落魔法句。
3. 颜色对比跟随 Element / Vant 默认主题，不要在卡片上用过浅灰字。

## 11. 禁止

| 禁止 | 改法 |
|------|------|
| jQuery / 直接操作 DOM 改列表 | Vue 数据驱动 |
| 复制粘贴 axios 实例到每个页面 | 共享 `http` 客户端 |
| `window.location` 硬跳管理路由 | vue-router |
| 在客户端「再实现一遍」领取校验链 | 以后端结果为准 |
| 为了好看引入第二套 CSS 框架 | 只用当前端 UI 库 |

## 12. AI 检查清单

- [ ] SFC 顺序与多词名
- [ ] 类型来自 OpenAPI
- [ ] 无 v-if+v-for、有 key
- [ ] 样式 scoped
- [ ] 401/403/429 按规格；成功失败用 `isOk` / `isFail`
- [ ] CSRF（admin 写）或 Bearer（portal）
- [ ] 未从 admin 生成物 import 到 portal（反之业务类型亦然）
- [ ] C 端无内部原因文案
- [ ] 奖品/任务状态映射有单测或复用共享函数
