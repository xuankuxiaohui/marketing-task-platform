# R6 dual-instance fault Continuity — portal 单节点退出仍可服务 — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `e9b90cab3f50cb10fbd051a794a533374d60a006` (merge #136; R6 journey deploy-smoke) |
| Branch | `docs/evidence-r6-dual-instance-fault-continuity` |
| Executor | Grok Bot on bot box (no CloudAgent; user forced box-local clone/edit/push) |
| Date (Asia/Shanghai) | 2026-10-11 ~14:07–14:10 CST |
| Isolation | Compose project `mkt`; existing throwaway volume from prior R6 slices (not prod) |
| Shared/prod DB touched | **No** |
| R6 overall | **已开 / 进行中** — this file is **dual-instance fault Continuity** evidence only; **not** RC pass |

## Goal

Architect next slice after #136: **双实例故障 Continuity** bound to [R31-go-live-checklist.md](../R31-go-live-checklist.md):

1. Confirm portal×2 readiness UP and nginx `/healthz` ok.
2. Stop **one** portal instance (`portal-app-1`).
3. Through **nginx**, critical portal paths must still **read and write** successfully while the remaining instance serves traffic.
4. Keep R6 **进行中**; do **not** claim RC pass.

**Out of scope this PR** (later R6 slices): 容量(DEC-006)、恢复/部署取证、真实浏览器静态/SPA。Do **not** claim RC pass.

## Environment

| Item | Detail |
|---|---|
| Host | Debian bot box; Docker Engine 26.1.5; dockerd `--iptables=true` |
| JDK / Maven | Temurin 26.0.2+10; Maven 3.9.9 under `/home/box/.local/` (jars/images reused from prior R6; no rebuild) |
| Compose | `deploy/docker-compose.yml` + `deploy/.env`; project `mkt` |
| Upstream | nginx `portal_app`: `portal-app-1:8081` + `portal-app-2:8081` |
| Base URL | `http://127.0.0.1:18080` (nginx) |

## Commands run

```bash
git fetch origin && git checkout -b docs/evidence-r6-dual-instance-fault-continuity
# HEAD == origin/master == e9b90cab3f50cb10fbd051a794a533374d60a006

COMPOSE=(docker compose --env-file deploy/.env -f deploy/docker-compose.yml -p mkt)
BASE=http://127.0.0.1:18080

# Baseline: stack already Up from prior R6 journey (reused; no --build)
"${COMPOSE[@]}" ps
curl -fsS "$BASE/healthz"   # → ok
"${COMPOSE[@]}" exec -T portal-app-1 curl -fsS http://127.0.0.1:8081/actuator/health/readiness  # UP
"${COMPOSE[@]}" exec -T portal-app-2 curl -fsS http://127.0.0.1:8081/actuator/health/readiness  # UP
# Portal API via nginx: captcha → login smoke_p0 → points/balance → 10

# Fault inject
"${COMPOSE[@]}" stop portal-app-1
# portal-app-1 Exited (143); portal-app-2 still healthy UP

# Continuity (fail-stop): nginx healthz + critical R/W via remaining portal
for i in 1..5; do curl -fsS "$BASE/healthz"; done   # all HTTP 200 ok
# WRITE: portal login smoke_p0 (captcha + /api/common/auth/login)
# READ:  /api/common/points/balance → 10
# WRITE: /api/common/task/1/start → instanceId=1 (idempotent)
# ×5 login+balance → failures=0/5; reconfirm R/W (login+balance+task start) PASS

# Optional recover
"${COMPOSE[@]}" start portal-app-1   # both portals UP again
```

## Outcomes

| Check | Result |
|---|---|
| Baseline both portals | portal-app-1 / portal-app-2 readiness **UP**; admin UP; nginx `/healthz` → `ok` |
| Baseline portal API via nginx | login `smoke_p0` + `/api/common/points/balance` → balance **10** |
| Fault inject | `docker compose … stop portal-app-1` → status **Exited (143)**; portal-app-2 remains **UP** |
| Continuity `/healthz` | 5/5 HTTP **200** `ok` while portal-app-1 down |
| Continuity WRITE via nginx | portal login `smoke_p0` **PASS**; `POST /api/common/task/1/start` → `instanceId=1` **PASS** |
| Continuity READ via nginx | `/api/common/points/balance` → **10** **PASS** (5/5 login+balance failures=**0**/5) |
| Remaining instance | portal-app-2 readiness **UP** throughout fault window |
| Optional recover | `start portal-app-1` → both readiness **UP** again |
| Slice verdict | **PASS** (dual-instance fault Continuity only) |
| RC / full R6 | **not claimed** |

### Continuity log excerpt

```text
# FAULT 14:08:16 CST — stop portal-app-1 → Exited (143); portal-app-2 UP
# CONTINUITY 14:08:19 CST
healthz try1..5 HTTP 200 body=ok
try1..5 PASS avail=False balance=10
CONTINUITY_PORTAL_API failures=0/5
CONTINUITY_PORTAL_API: PASS
# OPTIONAL RECOVER 14:08:48 CST — portal-app-1 UP again; both healthy
# RECONFIRM 14:09:02–14:09:52 CST — stop portal-app-1 again; nginx R/W:
#   WRITE login token present; WRITE task/1/start instanceId=1;
#   READ points balance=10; AFTER_STOP_PORTAL_1 CRITICAL R/W PASS
#   DRILL_VERDICT PASS (exit 0)
```

## Gaps / explicit non-claims

1. **Not** DEC-006 capacity report / k6 / perf seeds.
2. **Not** backup/PITR / TLS-public re-run / restore-deploy evidence.
3. **Not** real-browser static assets / SPA refresh-route (checklist browser row stays open).
4. Continuity proved via nginx failover to the remaining portal instance; no claim about sticky sessions or long-lived in-memory state across nodes.
5. Volume was not wiped this run (fault drill on post–journey schema).

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §构建、迁移与发布 —「portal 双实例及就绪检查有效，单节点退出时服务可继续使用」指向本文件。
