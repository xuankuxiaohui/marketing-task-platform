# Replan backlog (2026-10-13) — owners + checkboxes

**Status:** draft after #196 Δ收口. 勾空; **非 RC**. 主线整项目重规划；**默认不再排容量刀**. 禁大重构 / 整站 UI 重写.

**P0 order (架构批):** ① 文档分流 → ② task/risk/platform 边界图 → ③ 门户/登录/领奖界面债（产品标级）→ 再有界切片.

---

## P0-① 文档分流

| Done | ID | Item | Owner | Notes |
|:---:|:---|:---|:---|:---|
| [ ] | DOC-1 | 归档 capacity FAIL 噪音证据页（保留索引，不删历史 PR） | 开发 | `docs/capacity/*` 过时 FAIL 文 → archive 目录或 index |
| [ ] | DOC-2 | 一份活的状态页（容量/R6：两勾仍空、非 RC、Δ收口） | 开发 | 替代散落 STATUS；与 R31 诚实对齐 |
| [ ] | DOC-3 | `deploy/R31-go-live-checklist.md` 容量两勾保持 `[ ]`；加「另议」注 | 开发 | 禁自动勾 |
| [ ] | DOC-4 | `perf/README.md` 门禁表 vs 现网 `perf/*.js` 对齐 | 开发 | 只改文档/表，不改门禁值 |
| [ ] | DOC-5 | `docs/decisions.md` DEC 状态 vs 代码核对 | 架构师 | 开发协助举证 |
| [ ] | DOC-6 | 根 README onboarding vs 实际 compose/`mkt` 流程 | 开发 | 小改，不重写全书 |

## P0-② task / risk / platform 边界图

| Done | ID | Item | Owner | Notes |
|:---:|:---|:---|:---|:---|
| [ ] | BND-1 | 画出 `domain-task` / `domain-risk` / `platform-*` 允许依赖图 | 架构师 | 开发出一版草稿供批 |
| [ ] | BND-2 | 列出违规/过重交叉调用（hot-path） | 开发 | 跟 BND-1 |
| [ ] | BND-3 | 冻结「谁可调谁」规则写入 docs（短页） | 架构师 | 有界；不借机重构 |

## P0-③ 门户 / 登录 / 领奖界面债（产品已标级 2026-10-13）

旅程对应：
- **UX-1** → **登录**旅程（登录/注册报错）
- **UX-2** → **领奖/领取**旅程（日限 / risk.blocked 被拒说明）
- **UX-3** → **门户列表**旅程（空态 / 加载 / 失败态）

| Done | ID | Item | Owner | Severity | 切片顺序 | 验收 |
|:---:|:---|:---|:---|:---|:---|:---|
| [ ] | UX-2 | 领取被拒说明（日限 / risk.blocked） | 开发实现 → 产品验收文案 | **P0** | **先做（SL-2）** | 说清原因+何时可再领；禁技术码裸露 |
| [ ] | UX-1 | 登录/注册报错文案可读 | 开发实现 → 产品验收 | **P0** | 第二 | 账号/验证码/网络错误人话 |
| [ ] | UX-3 | 任务列表：空态 / 加载 / 失败态 | 开发实现 → 产品验收 | **P0**（失败）/ **P1**（空态·加载润色） | 第三 | 失败可重试；空态有引导 |
| [ ] | UX-4 | 美观简洁：少层级、少噪音（三旅程） | 产品主导 | **P1** | 后置 | 有界切，不整站重写 |
| [ ] | UX-5 | Admin risk 规则配置与缓存 TTL 预期文案 | 产品+开发 | **P1** | 后置 | — |

## P1 可读性 / 有界切片（架构批后才开刀）

| Done | ID | Item | Owner | Notes |
|:---:|:---|:---|:---|:---|
| [ ] | SL-1 | 从 BND-2 挑 1 个最小缝合切片 | 架构师定 → 开发做 | 禁大重构 |
| [ ] | SL-2 | **= UX-2** 领取被拒文案有界片 | 开发做 → 产品验收 | #196 合后先开；先测后合 |

## P2 选择性加固 / RC（明确再开）

| Done | ID | Item | Owner | Notes |
|:---:|:---|:---|:---|:---|
| [ ] | RC-1 | 是否续容量闭环 / CntWindow / run-full | 架构师+产品 | 默认 **不开** |
| [ ] | RC-2 | R31 勾与 RC 宣称 | 产品+架构师 | 两勾仍空直至另议 |

## Explicit non-goals
- 大重构 / greenfield / 整站 UI 重写
- 降 Δ、抬日限/1536M/频率、放宽 k6、Redisson/Tomcat 加码「为了过」
- 未批的容量刀

## Next
1. #196 CI 绿就合勾空（Δ收口证据+本 backlog）。
2. 开发开 DOC-1/DOC-2 草稿；架构审 BND-1；产品标 UX-1..3 severity。
