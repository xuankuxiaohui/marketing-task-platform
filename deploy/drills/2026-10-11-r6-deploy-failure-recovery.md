# R6 first-release failure recovery + data retention — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `04fa5fd437d3d313eb5ff21f0b81f8eea921449b` (merge #139) |
| Branch | `docs/r6-restore-deploy-2026-10-11` |
| Executor | Grok Bot on bot box (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~15:55 CST |
| Evidence type | **Docs-only** (procedure bound to current scripts/templates; **no live failed-deploy** executed this slice) |
| Shared/prod DB touched | **No** |
| R6 overall | **已开 / 进行中** — **not** RC |

## Goal

Checklist row: *首次发布失败的恢复步骤与数据保留策略明确*. Bind recovery steps and retention policy to real repo paths so an operator can execute without inventing procedure. Rolling multi-version coexistence is explicitly out of scope until adopted.

## Bound artifacts (current tip)

| Concern | Path |
|---|---|
| Compose topology | [deploy/docker-compose.yml](../docker-compose.yml) (`name: mkt`; volumes `mysql-data`, `prometheus-data`) |
| Env template | [deploy/.env.example](../.env.example) → local `deploy/.env` (gitignored) |
| Empty-DB migrate + dual-app | [ci/deploy-smoke.sh](../../ci/deploy-smoke.sh); R6 evidence [2026-10-11-r6-empty-flyway-dual-app.md](2026-10-11-r6-empty-flyway-dual-app.md) |
| Full dump | [deploy/backup/backup.sh](../backup/backup.sh) |
| Continuous binlog | [deploy/backup/ship-binlog.sh](../backup/ship-binlog.sh) |
| PITR restore | [deploy/backup/restore.sh](../backup/restore.sh) + [RESTORE-DRILL.md](../backup/RESTORE-DRILL.md) |
| Isolated PITR (this R6 slice) | [../backup/drills/2026-10-11-r6-isolated-restore.md](../backup/drills/2026-10-11-r6-isolated-restore.md) |
| Migrations | `server/platform-db` Flyway only; admin enables Flyway, portal disables |
| Standing rules | [AGENTS.md](../../AGENTS.md): do **not** rewrite applied migrations; do **not** clear shared DBs |

## Data retention policy (first release)

1. **Named volumes are the data**: Compose `mysql-data` holds InnoDB + binlogs. Prefer `docker compose stop` / recreate **app** containers over `down -v`.
2. **`down -v` destroys data**: only use on throwaway projects (e.g. `COMPOSE_PROJECT_NAME=mkt-r6-restore`) or when an explicit wipe is authorized. Never `down -v` a shared/prod volume.
3. **Pre-risk backup**: before first cutover or any restore attempt, run `backup.sh` (and keep `ship-binlog.sh` archiving). Keep dump + `BINLOG_START` + archive **off git** (see `.gitignore` `deploy/backups/`).
4. **Restore is destructive**: `restore.sh` requires `RESTORE_I_ACCEPT_DATA_LOSS=YES` and must target an **isolated** DB. Refuse gate exits 2 without the flag.
5. **Migrations are append-only**: never edit/delete already-applied Flyway versions to “fix” a bad release. Ship a new forward migration or restore from backup to a known-good schema+data point.
6. **Secrets stay local**: `deploy/.env`, dump/binlog bytes, and real passwords are not committed.

## First-release failure recovery steps

### Scenario A — Compose / image build fails before traffic

1. Capture logs: `docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs --no-color > /tmp/mkt-first-release-fail.log`.
2. Leave volumes intact (`stop` apps; do **not** `down -v`).
3. Fix image/tag/config on a branch; rebuild only app services: `docker compose ... build admin-app portal-app-1 portal-app-2` then `up -d`.
4. Re-check readiness + `bash ci/deploy-smoke.sh` (or nginx `/healthz`) before exposing traffic.
5. If the failure was env-only, correct `deploy/.env` from `.env.example` keys; never paste production secrets into git.

### Scenario B — Flyway / empty-DB migrate fails mid-start

1. Admin owns migrations (`SPRING_FLYWAY_ENABLED=true`); portal must stay Flyway-off.
2. Inspect admin logs for the failed version; **do not** hand-edit `flyway_schema_history` or rewrite the failed SQL file that already partially applied on a kept volume.
3. Prefer: (a) fix forward with a new migration on a clean throwaway volume for validation, or (b) if the volume is known-corrupt from the failed first boot and **no retained data matters**, authorized `down -v` + empty re-migrate (same path as R6 empty-flyway drill).
4. If any business data already existed, take `backup.sh` output first; restore to isolated target per RESTORE-DRILL rather than patching production in place.

### Scenario C — Apps up but business smoke fails (journey / dual-instance)

1. Keep MySQL/Redis volumes; use [deploy-smoke](2026-10-11-r6-full-journey-deploy-smoke.md) / [dual-instance](2026-10-11-r6-dual-instance-fault-continuity.md) evidence paths as the pass bar.
2. Roll app containers back to last known-good image digest **without** wiping `mysql-data`.
3. If data corruption is suspected, stop writes, run `backup.sh`, restore into an isolated project (`COMPOSE_PROJECT_NAME=…`), verify markers/balances, then plan cutover — never restore onto shared DB without explicit isolation authorization.

### Scenario D — Need point-in-time undo after a bad write window

1. Choose full dump with `BINLOG_START` **before** the bad window + complete binlog archive covering the window.
2. On **isolated** MySQL: `RESTORE_I_ACCEPT_DATA_LOSS=YES STOP_DATETIME='…' ./deploy/backup/restore.sh <backup-dir>`.
3. Verify business invariants (balances, grants, instances) — see PITR evidence above.
4. Only after verification, plan replacement of the failed environment (document cutover separately; not automated in-repo).

## Honesty bar

| Item | Status |
|---|---|
| Procedure concrete + path-bound | **Yes** (this file) |
| Live failed-deploy exercised this slice | **No** (docs-only) |
| Live isolated PITR | **Yes** — [2026-10-11-r6-isolated-restore.md](../backup/drills/2026-10-11-r6-isolated-restore.md) (**PITR_OK**) |
| Checklist tick recommendation | Tick the deploy-failure-recovery row as **procedure complete**; do **not** imply a live failure was staged |

## Not done

- No merge / RC claim.
- No production cutover rehearsal.
- No rewrite of applied migrations.
