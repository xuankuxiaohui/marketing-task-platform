# Project health checklist draft (2026-10-13) — post capacity Δ收口

**Status:** draft for 架构师/产品. **Not** a rewrite plan. **Not** RC. Capacity勾仍空.

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

## 5. Proposed replan phases (for 架构师/产品 to edit)
1. **Doc triage** — archive capacity FAIL noise; one living capacity status page.
2. **Boundary map** — freeze illegal dependencies; small seams only.
3. **UX debt backlog** — login/list/claim copy & states (product-owned).
4. **Selective hardening** — only if product prioritizes (not capacity soak by default).
5. **RC gate** — reopen only with explicit DEC/R31 owner.

## 6. Explicit non-goals
- Big-bang rewrite / greenfield
- Lowering DEC-006 Δ or relaxing k6 gates
- Raising dailyLimit / 1536M / Tomcat / Redisson “to pass”
- More capacity knives unless 架构师 re-opens

## Ping
开发 → 架构师/产品: please review this draft, prioritize §1–4, assign owners.
