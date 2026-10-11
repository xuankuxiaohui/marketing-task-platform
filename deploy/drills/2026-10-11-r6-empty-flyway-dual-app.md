# R6 first slice — empty-DB Flyway + dual-app assembly — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `7f7385623ed79730166a73c1d06efe02ed3ffc01` (merge #134; DEC-006 + DEC-008 closed) |
| Branch | `docs/evidence-r6-open-empty-flyway-dual-app` |
| Executor | Grok Bot on bot box (no CloudAgent; user forced box-local clone/edit/push) |
| Date (Asia/Shanghai) | 2026-10-11 ~12:49–13:00 CST |
| Isolation | Throwaway Compose project `mkt`; `docker compose … down -v` before up → fresh `mkt_mysql-data` |
| Shared/prod DB touched | **No** |
| R6 overall | **已开 / 进行中** — this file is **first-slice** evidence only; **not** RC pass |

## Goal

Architect first-slice for R6 发布候选验收:

1. Mark R6 **已开** (opened ≠ passed).
2. Evidence for **空库 Flyway migrate + 双应用装配** (admin migrates; portal Flyway off; both apps healthy).
3. Bind evidence into [R31-go-live-checklist.md](../R31-go-live-checklist.md).

**Out of scope this PR** (later R6 slices): 全量旅程、双实例故障注入、容量(DEC-006)、恢复/部署取证. Do **not** claim RC pass.

## Environment

| Item | Detail |
|---|---|
| Host | Debian bot box; Docker Engine 26.1.5; dockerd restarted with `--iptables=true` (earlier `--iptables=false` blocked bridge Redis→app; documented below) |
| JDK / Maven (host package only) | Temurin 26.0.2+10; Maven 3.9.9 under `/home/box/.local/` |
| Compose | `deploy/docker-compose.yml` + `deploy/.env` (gitignored placeholders) |
| Images | `mysql:8.0`, `redis:7-alpine`, `eclipse-temurin:26-jre-noble` (app runtime), `nginx:1.27-alpine`, `prom/prometheus:v2.55.1` |
| Apps | `admin-app` ×1 (Flyway **on**), `portal-app-1` + `portal-app-2` (Flyway **off**), nginx, prometheus |

## Commands run

```bash
git fetch origin && git checkout master && git reset --hard origin/master   # -> 7f73856
git checkout -b docs/evidence-r6-open-empty-flyway-dual-app

export JAVA_HOME=/home/box/.local/jdk/jdk-26.0.2+10
export PATH="$JAVA_HOME/bin:/home/box/.local/maven/apache-maven-3.9.9/bin:$PATH"
(cd server && mvn -DskipTests -DskipITs package -q)   # exit 0; jars present

# First attempt failed: dockerd --iptables=false → admin Redis ConnectTimeout
# Fix (bot box only; not a product change):
#   sudo kill <old-dockerd>; sudo dockerd --host=unix:///var/run/docker.sock --iptables=true

COMPOSE=(docker compose --env-file deploy/.env -f deploy/docker-compose.yml)
"${COMPOSE[@]}" down -v
"${COMPOSE[@]}" up -d --build    # exit 0; empty volume; admin healthy then portals
"${COMPOSE[@]}" up -d            # second up; stack stays healthy (idempotent start)
```

## Outcomes

| Check | Result |
|---|---|
| Empty MySQL volume | `down -v` then new `mkt_mysql-data`; initdb created `mkt_platform` + flyway user |
| Admin Flyway on empty schema | `Current version … << Empty Schema >>` → applied **9** migrations → **v7** |
| `flyway_schema_history` | versions `1,2,3,4,5,6,6.1,6.2,7` all `success=1` |
| Business tables (excl. history) | **51** |
| Portal Flyway | compose `SPRING_FLYWAY_ENABLED=false` / `MKT_FLYWAY_ENABLED=false`; process env confirmed; **0** Flyway log lines on portal-1/2 |
| Dual-app assembly / readiness | admin `/actuator/health/readiness` **UP**; portal-1 **UP**; portal-2 **UP**; nginx `/healthz` → `ok` |
| Second `compose up -d` | healthy; history still 9 rows / max version 7 (no empty-schema re-migrate) |
| Verdict (this slice) | **PASS** for 空库迁移+启动 + admin-migrate/portal-Flyway-off + dual-app ready |
| RC / full R6 | **not claimed** |

### Admin Flyway log excerpt (empty → v7)

```text
Schema history table `mkt_platform`.`flyway_schema_history` does not exist yet
Successfully validated 9 migrations
Current version of schema `mkt_platform`: << Empty Schema >>
Migrating schema `mkt_platform` to version "1 - sys baseline"
… (2..6, 6.1, 6.2) …
Migrating schema `mkt_platform` to version "7 - ad position"
Successfully applied 9 migrations to schema `mkt_platform`, now at version v7
Started AdminApplication in 8.018 seconds
```

### Portal start excerpt (no Flyway)

```text
Starting PortalApplication … profile "prod"
Started PortalApplication in ~5.6 seconds
# grep -ci flyway on portal-app-1 / portal-app-2 logs → 0
```

### flyway_schema_history (query)

```text
1	1	sys baseline	1
2	2	task core	1
3	3	reward points	1
4	4	risk tracking	1
5	5	sgn signin	1
6	6	act activity	1
7	6.1	mtr metrics	1
8	6.2	simulate permissions	1
9	7	ad position	1
```

## Infrastructure note (bot box)

Initial `compose up` with dockerd `--iptables=false` left bridge FORWARD broken: admin could not open `redis:6379` (`ConnectTimeoutException`). Redis/MySQL healthchecks still passed (in-container). After restarting dockerd with `--iptables=true`, inter-container Redis worked and the empty-DB stack came up healthy. This is a **host Docker networking** constraint on this box, not an application defect. Operators on normal Docker Desktop / CI with default iptables should not need the restart.

## Gaps / explicit non-claims

1. **Not** full journey / login-claim-grant smoke (`ci/deploy-smoke.sh` business flow not run this PR).
2. **Not** portal single-node kill / dual-instance fault continuity.
3. **Not** DEC-006 capacity report; **not** backup/PITR / TLS-public re-run.
4. Stock-DB upgrade path (non-empty migrate) still separate.
5. Compose stack torn down after evidence capture is operator choice; evidence above is from the live run at tip `7f73856`.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §构建、迁移与发布 — items for empty migrate+start and admin-Flyway / portal-Flyway-off point here.
