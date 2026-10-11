# R6 isolated restore (PITR) evidence — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (branch base) | `04fa5fd437d3d313eb5ff21f0b81f8eea921449b` (Merge #139 capacity) |
| Branch | `docs/r6-restore-deploy-2026-10-11` |
| Executor | Grok Bot on bot box (**no** CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~15:53–15:57 CST |
| Isolation | Throwaway Compose project **`mkt-r6-restore`** (override `name:` + `COMPOSE_PROJECT_NAME`); DB `mkt_platform_r6_restore`; volume removed after drill |
| Shared/prod DB touched | **No** (shared Compose project `mkt` left running untouched) |
| R6 status | **已开 / 进行中**；本片恢复 **PASS**；**非** RC；**不**声称容量/浏览器 PASS |

## Goal

R6 bind of restore/deploy evidence per [RESTORE-DRILL.md](../RESTORE-DRILL.md).

Primary path this slice: **full → continuous `ship-binlog.sh` → A/B → PITR via `BINLOG_ARCHIVE_DIR`** (same pattern as F16 continuous ship; reusable `backup.sh` / `ship-binlog.sh` / `restore.sh`). Business check: A present, B absent, ledger=150. An earlier same-day companion-binlog-only pass (`20261011T075420Z`) also reached PITR_OK; this report records the fuller re-run.

Deploy-side R6 slices already bound elsewhere (this PR only **points**; does not re-run):

- Empty Flyway + dual-app: [../../drills/2026-10-11-r6-empty-flyway-dual-app.md](../../drills/2026-10-11-r6-empty-flyway-dual-app.md)
- Journey / deploy-smoke: [../../drills/2026-10-11-r6-full-journey-deploy-smoke.md](../../drills/2026-10-11-r6-full-journey-deploy-smoke.md)
- Dual-instance Continuity: [../../drills/2026-10-11-r6-dual-instance-fault-continuity.md](../../drills/2026-10-11-r6-dual-instance-fault-continuity.md)
- Capacity P0 (overall FAIL): [../../drills/2026-10-11-r6-capacity-dec006.md](../../drills/2026-10-11-r6-capacity-dec006.md)

Prior F16 drills reused for scripts/patterns: [2026-10-10-f16-isolated-restore.md](2026-10-10-f16-isolated-restore.md), [2026-10-10-f16-continuous-binlog-ship.md](2026-10-10-f16-continuous-binlog-ship.md).

## Environment

| Item | Detail |
|---|---|
| Host | Debian bot box; Docker Engine 26.1.5; `dockerd` already running |
| Compose | `deploy/docker-compose.yml` service `mysql` only; project **`mkt-r6-restore`** (never `mkt`) |
| Env file | `/workspace/r6-restore-drill/env` (throwaway credentials; **outside repo**; not committed) |
| MySQL | 8.0.46; `log_bin=ON`; `binlog_format=ROW`; `server-id=1`; TZ `+00:00` |
| Host `mysqlbinlog` | MySQL 8.0.46 (`mysqlbinlog Ver 8.0.46`) |

## Script SHAs (unchanged this PR; scripts already on master)

| Path | SHA256 |
|---|---|
| `deploy/backup/backup.sh` | `76c7f0bda75d0b2c4913cddfd7d0fef536e27eebc9739c96e6ba0239c2bb00cc` |
| `deploy/backup/ship-binlog.sh` | `98faab30e12b00d2ecd160fc200fc16557c21e56048a814ee75fb01372118848` |
| `deploy/backup/restore.sh` | `f2a38f4dc40f00158d837f51009d01183163aca508d1b8b62f95c2c15a4b6ebb` |
| `deploy/backup/parse-binlog-start_test.sh` | `a362319c4b05b520d796d7fcaeee0000fee192287cd5e458d635e6a5e566965d` |

## Commands (summary)

```bash
# Throwaway project — do NOT use shared project `mkt`
export COMPOSE_PROJECT_NAME=mkt-r6-restore
ENV_FILE=/workspace/r6-restore-drill/env
docker compose --env-file "$ENV_FILE" -f deploy/docker-compose.yml \
  -f /workspace/r6-restore-drill/compose.override-name.yml up -d mysql
# seed r6_drill_markers + r6_drill_ledger (balance=100); FLUSH LOGS x3
BACKUP_DIR=/workspace/r6-restore-drill/backups ENV_FILE=... ./deploy/backup/backup.sh
# -> 20261011T075608Z / BINLOG_START mysql-bin.000007:157
BINLOG_ARCHIVE_DIR=/workspace/r6-restore-drill/archive ./deploy/backup/ship-binlog.sh
# re-ship: copied=0 skipped_existing=6 (idempotent)
# insert A (balance=150); FLUSH LOGS; ship
# T_UTC=2026-10-11 07:56:16
# insert B (balance=200); FLUSH LOGS; ship
# corrupt archive size -> ship exit 1 (fail loud)
# restore without RESTORE_I_ACCEPT_DATA_LOSS -> exit 2
# move companion bins aside; restore with BINLOG_ARCHIVE_DIR + STOP_DATETIME=T
TZ=UTC RESTORE_I_ACCEPT_DATA_LOSS=YES STOP_DATETIME='2026-10-11 07:56:16' \
  BINLOG_ARCHIVE_DIR=/workspace/r6-restore-drill/archive ENV_FILE=... \
  ./deploy/backup/restore.sh /workspace/r6-restore-drill/backups/20261011T075608Z
BINLOG_RETENTION_COUNT=3 ./deploy/backup/ship-binlog.sh
bash deploy/backup/parse-binlog-start_test.sh
docker compose ... down -v   # destroy throwaway volumes only
```

Backup stamp: `/workspace/r6-restore-drill/backups/20261011T075608Z`  
`BINLOG_START`: `mysql-bin.000007` / `157`  
`STOP_DATETIME`: `2026-10-11 07:56:16` UTC  
Dump/binlog bytes stayed under `/workspace/r6-restore-drill/` (**not** committed).

## Outcomes

| Check | Result |
|---|---|
| Target container | `mkt-r6-restore-mysql-1` only (safety gate refused unexpected name) |
| Shared project `mkt` | Untouched (still healthy before/after) |
| `ship-binlog.sh` skips current open file | yes |
| Idempotent re-ship | `copied=0 skipped_existing=6` |
| Fail loud on size mismatch | exit 1: `refusing to overwrite (would corrupt chain)` |
| Restore refuse gate | exit 2 |
| Restore used `BINLOG_ARCHIVE_DIR` (companion bins aside) | `using BINLOG_ARCHIVE_DIR=...`; start `mysql-bin.000007:157` |
| PITR `STOP_DATETIME` | `2026-10-11 07:56:16` UTC |
| Marker A after restore | **present** |
| Marker B after restore | **absent** |
| Ledger balance | **150** (A; not 200 from B) |
| Fixture `parse-binlog-start_test.sh` | PASSED |
| Retention `BINLOG_RETENTION_COUNT=3` | purged oldest; kept 3 |
| Restore wall time | ~7s |
| Verdict | **PITR_OK** |
| Teardown | `mkt-r6-restore` container + volumes removed |

## Checklist bind (honest)

| R31 row | Bind |
|---|---|
| 在隔离环境完成指定时间点恢复… | **PASS** → tick; evidence this file |
| 全量备份与连续 binlog 归档**已调度**… | Scripts + ship/checksum/archive **proven**; production cron/sidecar still **operator-owned** (examples only) → **leave unchecked** |
| 真实浏览器 / 容量 PASS | **Not claimed** (capacity overall FAIL; browser empty) |
| R6 / RC | Still **进行中** / **not RC** |

## Gaps / follow-ups

1. **Production schedule** still operator-owned; repo ships cron/sidecar **examples** only (`deploy/backup/schedule/`).
2. **COMPOSE_PROJECT_NAME** required when compose file has `name: mkt` — drills must not resolve to shared stack.
3. Host `mysqlbinlog` must be Oracle MySQL 8 (documented in F16 drills).
4. Dump/binlog bytes not committed (PII / size).
5. App-level post-restore admin/portal smoke **not** re-run in this throwaway-mysql-only slice (deploy journey already evidenced on shared `mkt` stack in prior R6 PRs).

## Not done

- No restore against any shared/team database.
- No capacity re-run / threshold change / DEC-006 reopen.
- No browser row claim; no RC claim.
