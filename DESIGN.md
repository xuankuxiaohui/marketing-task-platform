# Design System — 营销任务平台

## Product Context

- **What this is:** 运营编排任务，C 端按步骤完成并发奖。两端账号隔离。
- **Who it's for:** 后台 = 运营；门户 = C 端用户。
- **Project type:** 后台是桌面运营台；门户是移动端 H5 / WebView。
- **UI libraries:** admin = Element Plus；client = Vant 4。禁止第二套 CSS 框架。

## Aesthetic Direction

- **Portal:** 积分俱乐部（App UI，不是大促落地页）。第一眼是余额、签到进度、今日可做。
- **Admin:** 冷静运营台。第一眼是今日 KPI 和待办。
- **Decoration:** 克制。门户已有开屏 / 弹窗 / 浮标 / 轮播，禁止再加装饰广告。
- **Do not:** 紫色渐变、三列图标格、Vant 默认蓝 `#1989fa`、整页 Empty 吞掉其他入口。

## Color

门户（`web/apps/client/src/styles.css`）：

- Primary `#0f766e` / deep `#115e59` / warm `#0d9488`
- Accent `#c2410c`（积分奖励、倒计时、可领）
- Surface `#fffdf8` / bg `#f3eee4` / ink `#1c1917` / muted `#78716c`

后台（`web/apps/admin/src/styles.css`）：

- Primary 同源青绿；内容区冷灰 `#f4f4f5`，不要跟门户抢米色
- 主色只用于主按钮和当前态

## Typography

- 中文系统字体：苹方 / 微软雅黑 / Segoe UI
- 门户正文 ≥ 15px；后台表格 13px
- 数字用 `tabular-nums`（积分、KPI）

## Layout

- 门户 Tab：**首页 / 任务 / 奖品 / 我的**（旧路径 `/mine/tasks`、`/mine/prizes` 保留）
- 首页第一屏：积分 → 签到 7 日 → 今日可做（≤5）。活动卡第二屏。零活动不整页 Empty。
- 进度条只用已有数据（积分数字、连签、任务状态）。禁止编造「再得 N 分兑券」。
- 后台 Dashboard：4 KPI（数字 + 单位）+ 待办。列表统一 toolbar / Tag / 空错加载。

## Empty / Error

- 空态必须带下一步（去签到 / 去任务 / 去登录）
- 门户不展示风控内部原因（R34.6）
- 加载 / 空 / 错三态都要有

## Motion

- 仅按钮按压、下拉刷新、领取成功
- 时长 150–250ms
