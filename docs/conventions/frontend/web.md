# C 端前端规范

门户/客户端 (client-web) 特有规则。Vant 4 组件模式、懒加载路由、用户状态持久化。

## Vant 4 组件模式

- Vant 通过 `unplugin-auto-import` + `VantResolver` 按需自动导入，无需手动 import
- 页面容器：`van-nav-bar` 导航 + 内容区
- 列表模式：`van-pull-refresh` + `van-cell-group` / `van-cell`（TaskList）
- 步骤展示：`van-steps` + `van-step` 垂直方向展示任务进度（TaskDetail）
- 操作按钮：`van-button` type="primary" block round
- 反馈：`showToast()` 成功/失败提示

### 标签类型约束

`van-tag` 的 `type` prop 只接受 `'primary' | 'success' | 'danger' | 'warning' | 'default'`，不可传入任意字符串。

## 主题与样式

- 依赖 Vant 默认主题，无全局 CSS 覆盖
- 页面背景 `#f7f8fa`
- 无 CSS 预处理器

## 路由

- `createWebHistory()`（HTML5 History API）
- 动态 import（代码分割）：`() => import('../views/task/TaskList.vue')`
- 路由路径用单数：`/task/:id`（建议后续与 admin 统一）

## Pinia 状态持久化

client-web 用户 mock 状态需要在会话间保持：

```typescript
// MockLogin.vue 模式：先 reactive 拷贝，再 $patch 提交
const local = reactive({ ...store.$state })
function save() { store.$patch(local) }
```

推荐后续使用 `pinia-plugin-persistedstate` 做 localStorage 持久化。

## MockLogin 模式

MockLogin.vue 提供表单编辑 user store 字段，用于联调切换用户身份。

## 环境配置

- dev server 端口 5174
- proxy `/api` → `localhost:8080`
- 路径别名 `@` → `./src`
- Vant 按需自动导入（unplugin-vue-components）

## 相关文档

- `frontend/shared.md` — Vue 3 / TypeScript / Pinia 共享规则
- `api-rules.md` — API 响应格式、拦截器契约
