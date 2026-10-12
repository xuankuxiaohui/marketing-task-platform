# Project health checklist draft (2026-10-13) — post capacity Δ收口

**Status:** draft — **产品优先级已写入**（2026-10-13）；待架构对齐. **Not** a rewrite plan. **Not** RC. Capacity勾仍空.

**主线：** 整项目重规划；**默认不再排容量刀**。两勾仍空、非 RC。

**Trigger:** Team 2026-10-12 ~23:51 — after risk Δ收口, switch to **whole-project replan**; forbid big-bang rewrite; inventory first.

## 0. Capacity closure (context only)
- #195 `e5c3f4f` listAll Caffeine TTL3s merged.
- Stripe expand →5000; cold Δ risk-on **FAIL** http_fail≈0.27% with **exhausted=0** (pool OK).
- Evidence PR #196. **No further capacity knives by default.**
- Logs: `/workspace/r6-capacity-logs/stripe-expand-4500-after-195/`

## 1. Outdated / drift docs inventory (to verify)
| Area | Paths / notes | Risk |
|---|---|---|
| Decisions | `docs/decisions.md` (DEC-006 closed; confirm all DEC status vs code) | Spec drift |
| Capacity drills | `docs/capacity/*`, `deploy/drills/*`, R31 bullets — many FAIL evidence PRs; checklist勾仍空 | Noise vs living docs |
| Perf README | `perf/README.md` — gate table vs live `perf/*.js` thresholds | Drift |
| Deploy / R31 | `deploy/R31-go-live-checklist.md` — two capacity boxes still `[ ]` | Honest: not RC |
| OpenAPI / contracts | `docs` + generated clients vs `platform-contract` | API drift |
| README root | onboarding vs actual compose/`mkt` throwaway flow | Onboarding debt |

**Action:** mark each doc live / archive / rewrite-small; no big-bang docs rewrite.

## 2. Module boundaries (server)
| Module | Role (expected) | Boundary risks to review |
|---|---|---|
| `platform-kernel` | errors, clock, json | Leak of domain into kernel? |
| `platform-contract` | DTOs / ports | Risk/task ports churn |
| `platform-infra` | cache, redis, outbox | TwoLevel cache vs domain Caffeine L1 overlap |
| `platform-db` | schema/migrations | Migration discipline |
| `domain-task` | claim/list/advance/complete | Hot-path complexity (hold-lock, instances cache, skip-renew) |
| `domain-risk` | rules, grant checks | listAll cache just added; CntWindow / hit path readability |
| `domain-*` (other) | identity, admin, ads, … | Cross-domain calls / cycles |
| `portal-app` / `admin-app` | boot jars | Fat composition; config surface |

**Action:** dependency diagram + “who may call whom” rules; forbid drive-by cross-module features.

## 3. Readability / function / usability risks
| Theme | Examples | Notes |
|---|---|---|
| Hot-path density | `TaskPortalAppService`, `TaskStepAppService`, risk grant | Hard to reason; many capacity patches layered |
| Dual measurement + product | `perf/seed/*` hygiene vs product limits | Keep measurement scripts out of product semantics |
| Error / reject codes | claim daily-limit vs risk.blocked vs pool | Client messaging clarity |
| Observability | metrics aggregate gate, outbox lock waits | Ops story incomplete for RC |
| Test pyramid | domain unit vs IT/Testcontainers cost | CI server ~15m — keep focused |

## 4. UI debt — portal / login / claim (list only)
| Surface | Debt hypotheses (validate with 产品) |
|---|---|
| Login / register | Captcha UX; error copy; device-id headers opaque to users |
| Task list | Loading/empty/error states; gray/ratio tasks confusing |
| Claim / start | Daily-limit & risk rejects poorly explained |
| Progress / callback flows | Long cascade; user feedback lag |
| Admin rule config | Risk rules admin vs cache TTL expectations |
| Mobile / WEB parity | `X-Client-Platform` assumptions |

**Action:** product walkthrough + severity tags (P0/P1/P2); **no UI rewrite** in this draft.

## 5. 产品已定优先级（2026-10-13）— 请架构对齐

主线：整项目重规划；默认不再排容量刀。两勾仍空、非 RC。界面：美观简洁，优先门户/登录/领奖三条旅程；有界切片，不整站重写。

| 级 | 主题 | 范围 |
|---|---|---|
| **P0** | 旅程完整性 | 门户/登录/领奖：登录报错文案、领取被拒（日限/risk）说明、列表空态/加载/失败态。只列债分级，不整站重写。 |
| **P0** | 能力边界 | 文档与实现一致；过时 capacity FAIL 噪音归档；R31 勾空保持诚实；一份活的状态页。 |
| **P1** | 可读性/模块边界 | 跟架构体检清单，有界小切，禁大重构。 |
| **P2** | 选择性加固 / RC | 等产品+架构明确再开。 |

#196 证据勾空产品认；合入听架构+CI。

## 6. Proposed replan phases（按产品优先级重排）
1. **P0 UX debt backlog** — login / list / claim copy & states（产品主导 severity）。
2. **P0 Doc triage** — archive capacity FAIL noise；one living status page；R31 勾空诚实。
3. **P1 Boundary map** — 架构主导：freeze illegal deps；小缝合 only。
4. **P2 Selective hardening / RC** — 明确 DEC/R31 owner 后再开。

## 7. Explicit non-goals
- Big-bang rewrite / greenfield / 整站 UI 重写
- Lowering DEC-006 Δ or relaxing k6 gates
- Raising dailyLimit / 1536M / Tomcat / Redisson “to pass”
- More capacity knives unless 架构师 re-opens

## Ping
开发 → @架构师: 请对齐产品 §5 优先级并分配 P1 边界切片 owner。产品侧 P0 旅程债已认。
