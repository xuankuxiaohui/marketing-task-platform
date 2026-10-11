# R6 isolated restore (PITR) evidence — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `04fa5fd437d3d313eb5ff21f0b81f8eea921449b` (merge #139; R6 capacity P0 evidence) |
| Branch | `docs/r6-restore-deploy-2026-10-11` |
| Executor | Grok Bot on bot box (no CloudAgent; user forced box-local clone/edit/push) |
| Date (Asia/Shanghai) | 2026-10-11 ~15:53–15:55 CST |
| Isolation | Throwaway Docker Compose **MySQL only** (`mysql:8.0`), project **`mkt-r6-restore`** (`COMPOSE_PROJECT_NAME` override), DB `mkt_platform_r6_restore`, volume removed after drill |
| Shared/prod DB touched | **No** |
| Concurrent `mkt` stack | **Left UP** (full capacity stack untouched; separate project/network/volume) |
| Verdict | **PITR_OK** |
| R6 overall | **已开 / 进行中** — restore/deploy slice; **not** RC; capacity checklist row stays unchecked/FAIL |

## Goal

Per [RESTORE-DRILL.md](../RESTORE-DRILL.md) and F16 template [2026-10-10-f16-isolated-restore.md](2026-10-10-f16-isolated-restore.md): isolated backup → restore from recorded `BINLOG_START` → PITR with `STOP_DATETIME` → business marker check (A present, B absent, ledger correct).

**Out of scope this PR**: k6 seed/load, capacity threshold/DEC changes, live failed-deploy execution (see companion deploy-failure doc), merge/RC claim.

## Environment

| Item | Detail |
|---|---|
| Host | Debian bot box; Docker Engine; `mysqlbinlog` Ver **8.0.46** (Oracle MySQL) |
| Compose | `deploy/docker-compose.yml` service `mysql` only (`up -d mysql`) |
| Project name | `mkt-r6-restore` via `COMPOSE_PROJECT_NAME` (overrides compose top-level `name: mkt`) |
| Env file | `/workspace/r6-restore-drill/env` (throwaway credentials; **outside repo**; not committed) |
| MySQL | 8.0; `log_bin=ON`; `binlog_format=ROW`; `server-id=1`; TZ `+00:00` |
| Backup dir | `/workspace/r6-restore-drill/backups/` (local; not committed) |

## Script SHAs (tip `04fa5fd`)

| Path | SHA256 |
|---|---|
| `deploy/backup/backup.sh` | `76c7f0bda75d0b2c4913cddfd7d0fef536e27eebc9739c96e6ba0239c2bb00cc` |
| `deploy/backup/restore.sh` | `f2a38f4dc40f00158d837f51009d01183163aca508d1b8b62f95c2c15a4b6ebb` |
| `deploy/backup/parse-binlog-start_test.sh` | `a362319c4b05b520d796d7fcaeee0000fee192287cd5e458d635e6a5e566965d` |
| `deploy/backup/ship-binlog.sh` | `98faab30e12b00d2ecd160fc200fc16557c21e56048a814ee75fb01372118848` |
| `deploy/backup/RESTORE-DRILL.md` | `4b428f5dfce4dfb8c8c72c1a131cafc396aad2a34b0ed5d62a5beded3a13f16f` |

## Commands (summary)

```bash
git fetch origin && git checkout master && git pull   # -> 04fa5fd
git checkout -b docs/r6-restore-deploy-2026-10-11

export COMPOSE_PROJECT_NAME=mkt-r6-restore
export ENV_FILE=/workspace/r6-restore-drill/env   # throwaway; not committed

docker compose --env-file "$ENV_FILE" -f deploy/docker-compose.yml up -d mysql
# seed r6_restore_markers + r6_restore_ledger (balance=100)

BACKUP_DIR=/workspace/r6-restore-drill/backups ENV_FILE="$ENV_FILE" \
  ./deploy/backup/backup.sh
# -> 20261011T075420Z / BINLOG_START mysql-bin.000004:157

# insert marker A (balance=150); T_UTC='2026-10-11 07:54:33'; insert marker B (balance=200)
# FLUSH LOGS; refresh companion binlogs into backup dir; regenerate SHA256SUMS

./deploy/backup/restore.sh <backup>   # refuse gate -> exit 2

TZ=UTC RESTORE_I_ACCEPT_DATA_LOSS=YES STOP_DATETIME='2026-10-11 07:54:33' \
  ENV_FILE="$ENV_FILE" ./deploy/backup/restore.sh /workspace/r6-restore-drill/backups/20261011T075420Z

bash deploy/backup/parse-binlog-start_test.sh   # PASSED

COMPOSE_PROJECT_NAME=mkt-r6-restore docker compose --env-file "$ENV_FILE" \
  -f deploy/docker-compose.yml down -v   # throwaway only; leave project `mkt` UP
```

Backup stamp: `/workspace/r6-restore-drill/backups/20261011T075420Z`  
`BINLOG_START`: `mysql-bin.000004` / `157`

Timeline (UTC): A @ `07:54:31` → T @ `07:54:33` → B @ `07:54:35`

## Outcomes

| Check | Result |
|---|---|
| Fixture `parse-binlog-start_test.sh` | **PASSED** |
| Restore without `RESTORE_I_ACCEPT_DATA_LOSS=YES` | exit **2** (refused) |
| SHA256SUMS verify before restore | OK (full.sql + BINLOG_START + mysql-bin.*) |
| Restore used start file+pos (not first binlog blind replay) | `binlog start: file=mysql-bin.000004 pos=157` |
| PITR `STOP_DATETIME` | `2026-10-11 07:54:33` UTC (`TZ=UTC` for host mysqlbinlog) |
| Marker A after restore | **present** |
| Marker B after restore | **absent** |
| Ledger balance | **150** (A’s update; not 200 from B) |
| Verdict | **PITR_OK** |
| Restore wall time | ~5s |
| Teardown | `mkt-r6-restore` container + `mkt-r6-restore_mysql-data` (+ prometheus volume) removed |
| `mkt` Compose project | **still UP** (prometheus/nginx/portal×2/admin/mysql/redis healthy) |

## Gaps / follow-ups

1. Point-in-time companion binlog refresh after A/B is still a drill step; production continuous shipping remains [ship-binlog.sh](../ship-binlog.sh) (already evidenced 2026-10-10).
2. `--master-data` deprecation warning from mysqldump 8; scripts still parse CHANGE MASTER and CHANGE REPLICATION SOURCE.
3. Host tooling: Oracle MySQL 8 `mysqlbinlog` required (already on this bot box PATH).
4. No restore against any shared/team database.

## Not done

- No merge of this evidence PR (await architect).
- No RC / capacity PASS claim.
