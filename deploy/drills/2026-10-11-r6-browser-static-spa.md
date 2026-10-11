# R6 browser / static SPA evidence — 真实浏览器静态资源与旅程 — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `cfcd77def75f26b2bf8f2ac56c6fa6525d9d75f3` (merge #140; R6 restore/deploy) |
| Branch | `docs/r6-browser-spa-2026-10-11` |
| Executor | Grok Bot on bot box (no CloudAgent; user forced box-local clone/edit/push) |
| Date (Asia/Shanghai) | 2026-10-11 ~16:12–16:16 CST |
| Isolation | Compose project `mkt` (existing throwaway volume; **not** prod); nginx recreated with real dist override only |
| Shared/prod DB touched | **No** |
| R6 overall | **已开 / 进行中** — this file is **browser/static SPA slice** evidence only; **not** RC pass |
| Capacity (DEC-006) | Still **FAIL** / checklist unchecked (unchanged) |

## Goal

Architect next slice after #140: checklist row in [R31-go-live-checklist.md](../R31-go-live-checklist.md):

> 真实浏览器验证静态资源、登录、刷新路由、领取、完成任务和领奖。

Bind to **real Vite production dist** served by nginx (not placeholders; not Vite dev). Prior F16 TLS drill ([nginx/drills/2026-10-11-f16-tls-static-browser.md](../nginx/drills/2026-10-11-f16-tls-static-browser.md)) used placeholders only and does **not** satisfy this row. API journey via `deploy-smoke` remains a separate checked row.

## Environment

| Item | Detail |
|---|---|
| Host | Debian bot box; Docker Engine; google-chrome `/usr/bin/google-chrome`; Playwright 1.62 (Chromium) |
| Node / pnpm | Node v20.19.2; pnpm 9.15.9 |
| Compose | `deploy/docker-compose.yml` + `deploy/.env` + override [`docker-compose.r6-browser-dist.yml`](../docker-compose.r6-browser-dist.yml) |
| Base URL | `http://127.0.0.1:18080` (nginx; portal default server) |
| Dist | `web/apps/client/dist`, `web/apps/admin/dist` (`VITE_BASE=/console/`) — gitignored; rebuilt at tip |
| Harness | [`web/playwright.nginx-dist.config.ts`](../../web/playwright.nginx-dist.config.ts) + [`web/e2e/r6-browser-static-spa.spec.ts`](../../web/e2e/r6-browser-static-spa.spec.ts); reuses e2e `global-setup` (admin seed prize/task/activity) |
| Screenshots | [r6-browser-artifacts/screenshots/](r6-browser-artifacts/screenshots/) (small PNGs) |

## Commands run

```bash
git fetch origin && git checkout -B master origin/master
# HEAD == origin/master == cfcd77def75f26b2bf8f2ac56c6fa6525d9d75f3
git checkout -b docs/r6-browser-spa-2026-10-11

cd web
pnpm --filter client build
VITE_BASE=/console/ pnpm --filter admin build

cd ..
docker compose --env-file deploy/.env -f deploy/docker-compose.yml \
  -f deploy/docker-compose.r6-browser-dist.yml -p mkt \
  up -d --force-recreate --no-deps nginx
# mounts: client/dist → html/client; admin/dist → html/admin/console

# curl spot-check (also covered in Playwright)
curl -sS http://127.0.0.1:18080/ | head   # real SPA shell, title 营销任务平台
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18080/assets/index-*.js
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:18080/mine/prizes  # try_files → index

cd web
export E2E_API_BASE=http://127.0.0.1:18080 PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080
pnpm exec playwright test -c playwright.nginx-dist.config.ts
# → 5 passed (~19s)
```

## Outcomes

| Check | Result |
|---|---|
| Real client dist mounted (not `html-placeholders`) | **PASS** — index title `营销任务平台`; `/assets/*.js|css` **200** |
| Real admin dist under `/console/` (`Host: admin.local`) | **PASS** — `/` → 302 `/console/`; console shell + asset **200**; deep `/console/system/users` serves SPA index |
| SPA `try_files` / hard refresh `/mine/prizes` | **PASS** — HTML shell + `#app`; reload still 200 |
| Portal register (UI + captcha) | **PASS** |
| Portal overlay login on `/mine` | **PASS** (`e2e_login1` from globalSetup) |
| Task claim (`POST .../start`) → IN_PROGRESS | **PASS** |
| Step click-complete → COMPLETED + reward feedback | **PASS** |
| Prize claim on 我的奖品 → GRANTED / ARRIVED / 已到账 | **PASS** |
| Points balance shows **10** | **PASS** |
| Stock `journey-core` without ad dismiss | **FAIL (expected / documented)** — leftover capacity-seed `home_popup` (`perf-home_popup`) intercepts clicks; dedicated R6 spec dismisses via Vant close icon |
| Compose `mkt` left UP | **Yes** (nginx on real dist; other services untouched) |
| RC / full R6 | **not claimed** |
| Capacity checkbox | **still empty / FAIL** |

### Playwright verdict

```text
5 passed (19.3s)
  ✓ static shell + asset 200 from real dist
  ✓ SPA try_files: hard refresh on deep client route still serves index
  ✓ register + login overlay path on real dist
  ✓ claim / complete / prize claim via UI
  ✓ optional admin Host static smoke
```

Box log: `/workspace/r6-browser-logs/playwright-r6-browser.log` (not committed).

### Screenshots

| File | Step |
|---|---|
| [01-static-shell.png](r6-browser-artifacts/screenshots/01-static-shell.png) | Portal home shell from nginx dist |
| [02-spa-refresh-mine-prizes.png](r6-browser-artifacts/screenshots/02-spa-refresh-mine-prizes.png) | Deep link / hard refresh |
| [03-register-home.png](r6-browser-artifacts/screenshots/03-register-home.png) | Post-register home |
| [04-overlay-login-mine.png](r6-browser-artifacts/screenshots/04-overlay-login-mine.png) | Overlay login |
| [05-activity-list.png](r6-browser-artifacts/screenshots/05-activity-list.png) | Activity / task cards |
| [06-task-detail.png](r6-browser-artifacts/screenshots/06-task-detail.png) | Task detail before claim |
| [07-task-completed.png](r6-browser-artifacts/screenshots/07-task-completed.png) | 任务完成 sheet |
| [08-prize-claimed.png](r6-browser-artifacts/screenshots/08-prize-claimed.png) | 已到账 |
| [09-points-balance.png](r6-browser-artifacts/screenshots/09-points-balance.png) | Balance 10 |

## Gaps / explicit non-claims

- **Not RC / not “R6 通过”.** Capacity P0 remains overall FAIL; scheduling cron still sample-only; production CA / Secure Cookie full-stack still open elsewhere.
- Does **not** replace API `deploy-smoke` row (already checked).
- F16 TLS placeholder drill remains separate and insufficient for this row.
- Leftover perf-seed ads on shared `mkt` DB require dismiss in UI for honest browser clicks; no `down -v` performed.
- Admin evidence is Host/static smoke only (binding row is portal journey).

## Reproduce

1. Build client + admin dist (see [nginx/README.md](../nginx/README.md)).
2. Apply [`docker-compose.r6-browser-dist.yml`](../docker-compose.r6-browser-dist.yml); recreate nginx only.
3. `cd web && E2E_API_BASE=http://127.0.0.1:18080 PLAYWRIGHT_BASE_URL=http://127.0.0.1:18080 pnpm exec playwright test -c playwright.nginx-dist.config.ts`
