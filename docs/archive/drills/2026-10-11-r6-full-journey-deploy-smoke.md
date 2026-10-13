# R6 journey slice — deploy-smoke business flow (登录 / 领取 / 完成 / 领奖) — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `e281f6086cecca18f35aa94bd8ece00a5c1d3e29` (merge #135; R6 open + empty Flyway dual-app) |
| Branch | `docs/evidence-r6-journey-deploy-smoke` |
| Executor | Grok Bot on bot box (no CloudAgent; user forced box-local clone/edit/push) |
| Date (Asia/Shanghai) | 2026-10-11 ~13:29–13:30 CST |
| Isolation | Compose project `mkt`; existing throwaway volume from R6 first slice (not prod) |
| Shared/prod DB touched | **No** |
| R6 overall | **已开 / 进行中** — this file is **journey slice** evidence only; **not** RC pass |

## Goal

Architect next slice after #135: **全量旅程取证** bound to [R31-go-live-checklist.md](../R31-go-live-checklist.md):

1. Run `ci/deploy-smoke.sh` (repeat compose start + business idempotency).
2. Evidence for API business flow: **登录 → 领取(任务 start) → 完成 → 领奖(积分发放)**.
3. Keep R6 **进行中**; do **not** claim RC pass.

**Out of scope this PR** (later R6 slices): 双实例故障注入、容量(DEC-006)、恢复/部署取证. Do **not** claim RC pass. Real-browser static/SPA refresh remains a separate checklist row.

## Environment

| Item | Detail |
|---|---|
| Host | Debian bot box; Docker Engine 26.1.5; dockerd `--iptables=true` |
| JDK / Maven | Temurin 26.0.2+10; Maven 3.9.9 under `/home/box/.local/` (jars already packaged at tip) |
| Compose | `deploy/docker-compose.yml` + `deploy/.env` |
| Script | [`ci/deploy-smoke.sh`](../../ci/deploy-smoke.sh) — R31.1: compose up ×2 → health → Flyway → admin/portal login → prize/task → register-claim-grant ×2 identical fingerprints |
| Base URL | `http://127.0.0.1:18080` (nginx) |

## Commands run

```bash
git fetch origin && git checkout docs/evidence-r6-journey-deploy-smoke
# HEAD == origin/master == e281f6086cecca18f35aa94bd8ece00a5c1d3e29

export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"

# Fail-stop: script uses set -euo pipefail; non-zero aborts this slice
bash ci/deploy-smoke.sh
# → exit 0; terminal log archived under /tmp/r6-journey/deploy-smoke.log on the box
```

## Outcomes

| Check | Result |
|---|---|
| First `compose up -d --build` + wait | admin / portal-1 / portal-2 readiness **UP**; nginx `/healthz` → `ok` |
| Second `compose up -d` + wait | stack stays healthy (repeat start) |
| Flyway history | `1 2 3 4 5 6 6.1 6.2 7` (success rows present; versions 1 and 4 asserted by script) |
| Admin 登录 | captcha + `/admin/auth/login` (init password / unlocked / optional change-password path) → CSRF session |
| Prize + task setup | prize `smoke_pts` (POINTS, 10) enabled; task `smoke_task` saved + **PUBLISHED** with REWARD step |
| Portal 注册/登录 | user `smoke_p0` register-or-login via `/api/common/auth/*` + captcha |
| 领取 | `POST /api/common/task/{taskId}/start` → `instanceId=1` |
| 完成 / 领奖 | task instance **COMPLETED**; grant **GRANTED**; points balance **10** |
| Idempotent second flow | `flow1` == `flow2`: `{"taskId": 1, "prizeId": 1, "instanceId": 1, "balance": 10}` |
| SQL fingerprints | users=**1**, instances=**1**, grants=**1** for `smoke_p0` |
| Prometheus | targets include `admin-app` + `portal-app` (both `up`) |
| Script verdict | **`deploy-smoke ok`** (exit **0**) |
| RC / full R6 | **not claimed** |

### Flow log excerpt

```text
flyway: 1 2 3 4 5 6 6.1 6.2 7 
flow1 {"taskId": 1, "prizeId": 1, "instanceId": 1, "balance": 10}
flow2 {"taskId": 1, "prizeId": 1, "instanceId": 1, "balance": 10}
deploy-smoke ok
EXIT:0
# wall clock ~13:29:43 → 13:30:12 CST
```

### Post-run SQL / health (spot check)

```text
nginx /healthz → ok
admin readiness → {"status":"UP"}
portal-1 / portal-2 readiness → {"status":"UP"}
task_instance (smoke_p0): id=1 status=COMPLETED
rwd_grant_record (smoke_p0): id=1 status=GRANTED
Prometheus activeTargets jobs: admin-app, portal-app, portal-app (health up)
```

## Gaps / explicit non-claims

1. **Not** real-browser static assets / SPA refresh-route (checklist browser row stays open; this slice is API deploy-smoke 业务流).
2. **Not** portal single-node kill / dual-instance fault continuity.
3. **Not** DEC-006 capacity report; **not** backup/PITR / TLS-public re-run.
4. Volume was not wiped this run (journey on post–first-slice schema); empty-DB migrate already evidenced in [2026-10-11-r6-empty-flyway-dual-app.md](2026-10-11-r6-empty-flyway-dual-app.md).

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §构建、迁移与发布 — `deploy-smoke.sh` 重复启动与业务幂等；登录 / 领取 / 完成 / 领奖 API 旅程指向本文件。
