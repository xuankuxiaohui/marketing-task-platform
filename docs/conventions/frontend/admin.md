# Admin 前端规范

管理后台 (admin-web) 特有规则。Element Plus 组件模式、主题覆盖、路由策略。

## Element Plus 组件模式

- Element Plus 全局注册（`app.use(ElementPlus)`），无需按需导入
- 表格编辑模式：`el-table` 内嵌 `el-input` / `el-select`（如 StepsTab）
- 卡片布局：`el-card` + `template #header` 作为页面容器
- 对话框：`el-dialog` 用于过滤器校验等弹窗场景
- 标签页：`el-tabs` + `el-tab-pane` 实现分步表单（TaskEdit）

### tab 子组件 expose 模式

StepsTab / FiltersTab / PlatformsTab 通过 `defineExpose()` 暴露 `getXxx()` / `setXxx()` 给父组件 TaskEdit：

```typescript
// tab 子组件
const steps = ref<Step[]>([])
function setSteps(data: Step[]) { steps.value = data || [] }
defineExpose({ getSteps: () => steps.value, setSteps })
```

长期看应升级为 props down + events up，更符合 Vue 单向数据流原则。

## 主题与样式

- 全局 `styles/theme.css` 覆盖 Element Plus CSS 变量
- 主题色 `#2563eb`，侧边栏深紫 `#2d1b69`
- 无 CSS 预处理器
- `!important`：当前 40+ 处使用（主要在 theme.css），应逐步迁移到 CSS 变量体系

## 路由

- `createWebHistory()`（HTML5 History API）
- 静态 import（非懒加载）
- 路由路径用复数：`/tasks`
- 新建和编辑复用同一组件，通过 `route.params.id` 是否存在区分模式

## 环境配置

- dev server 端口 5173
- proxy `/api` → `localhost:8080`
- 路径别名 `@` → `./src`
