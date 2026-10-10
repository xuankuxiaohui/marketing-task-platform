# F16 isolated restore drill evidence — 2026-10-10

| Field | Value |
|---|---|
| Repo tip (master synced) | `15af760fbd742a89598ae1312b743d8b92dcd3ce` (merge #120; after #118/#119) |
| Executor | Grok Bot on bot box (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-10 ~20:52–21:05 CST |
| Isolation | Throwaway Docker Compose MySQL only (`mysql:8.0`), project `mkt`, DB `mkt_platform_f16_drill`, volume removed after drill |
| Shared/prod DB touched | **No** |

## Goal

Per [RESTORE-DRILL.md](../RESTORE-DRILL.md): isolated backup → restore from recorded `BINLOG_START` / CHANGE MASTER|SOURCE → PITR with `STOP_DATETIME` → business marker check (A present, B absent).

## Environment

| Item | Detail |
|---|---|
| Host | Debian 13 bot box; Docker Engine 26.1.5 installed for this drill (`dockerd` started manually; systemd policy-rc.d blocks service start) |
| Compose | `deploy/docker-compose.yml` service `mysql` only (`up -d mysql`) |
| Env file | `deploy/.env.f16-drill` (throwaway credentials; **not committed**; deleted after teardown) |
| MySQL | 8.0.46; `log_bin=ON`; `binlog_format=ROW`; `server-id=1`; TZ `+00:00` |
| Host `mysqlbinlog` | Official **MySQL 8.0.46** via `docker run --entrypoint mysqlbinlog mysql:8.0-debian` shim on PATH. MariaDB 11.8 client can *decode* but emits `check_constraint_checks` that MySQL 8 rejects on replay. |

## Script SHAs

| Path | SHA256 (at drill / this PR) |
|---|---|
| `deploy/backup/restore.sh` (unchanged from #118) | `087d7c83a3fd68d12cd4782a9df98922a723b3be5979161321ad78018231d63e` |
| `deploy/backup/parse-binlog-start_test.sh` | `a362319c4b05b520d796d7fcaeee0000fee192287cd5e458d635e6a5e566965d` |
| `deploy/backup/RESTORE-DRILL.md` | `b03edafaab351515335855da284c8499b5cbae310d4833b462f7f977f6fed704` |
| `deploy/backup/backup.sh` **after** stdin/index fix (this PR) | `76c7f0bda75d0b2c4913cddfd7d0fef536e27eebc9739c96e6ba0239c2bb00cc` |
| `deploy/backup/backup.sh` at master tip before fix | `1444916c9fa55360ef15bb56cc92d0cca34680394e365eea12f40b78d4c6e8b5` |

## Commands run (summary)

```bash
git checkout master && git pull origin master   # -> 15af760
# install docker.io docker-compose; start dockerd; pull mysql:8.0 + mysql:8.0-debian
docker compose --env-file deploy/.env.f16-drill -f deploy/docker-compose.yml up -d mysql
# seed f16_drill_markers + f16_drill_ledger
BACKUP_DIR=/workspace/f16-drill-backups ENV_FILE=deploy/.env.f16-drill ./deploy/backup/backup.sh
# insert marker A; T=2026-10-10 12:57:54 UTC; insert marker B; refresh binlog copies into archive
# refuse gate:
./deploy/backup/restore.sh <backup>   # exit 2 without RESTORE_I_ACCEPT_DATA_LOSS
TZ=UTC RESTORE_I_ACCEPT_DATA_LOSS=YES STOP_DATETIME='2026-10-10 12:57:54' \
  ENV_FILE=deploy/.env.f16-drill ./deploy/backup/restore.sh /workspace/f16-drill-backups/20261010T125738Z
bash deploy/backup/parse-binlog-start_test.sh   # PASSED
docker compose ... down -v   # destroy throwaway volume
```

Backup stamp: `/workspace/f16-drill-backups/20261010T125738Z`  
`BINLOG_START`: `mysql-bin.000005` / `157`  
Dump comment: `-- CHANGE MASTER TO MASTER_LOG_FILE='mysql-bin.000005', MASTER_LOG_POS=157;`

## Outcomes

| Check | Result |
|---|---|
| Fixture `parse-binlog-start_test.sh` | PASSED |
| Restore without `RESTORE_I_ACCEPT_DATA_LOSS=YES` | exit 2 (refused) |
| SHA256SUMS verify before restore | OK |
| Restore used start file+pos (not first binlog blind replay) | `binlog start: file=mysql-bin.000005 pos=157` |
| PITR `STOP_DATETIME` | `2026-10-10 12:57:54` UTC (`TZ=UTC` for host mysqlbinlog) |
| Marker A after restore | present |
| Marker B after restore | **absent** |
| Ledger balance | 150 (A’s update; not 200 from B) |
| Verdict | **PITR_OK** |
| Restore wall time | ~5s (dump + 2 binlog files) |
| Teardown | container + `mkt_mysql-data` volume removed |

## Gaps / follow-ups

1. **`backup.sh` stdin bug (fixed in this PR)**: `docker compose exec` without `</dev/null` stole the `while read` stdin; only the first binlog was archived. Restored files would miss `BINLOG_START`’s file → restore would correctly refuse incomplete chain. Fix: `</dev/null` on `cat`; also skip `*.index`.
2. **Host tooling**: stock Debian `default-mysql-client` (MariaDB) is insufficient for replay into MySQL 8. Need MySQL 8 `mysqlbinlog` (documented here; not yet packaged in repo scripts).
3. **`--master-data` deprecation**: mysqldump 8.0 warns to use `--source-data`; scripts still parse both CHANGE MASTER and CHANGE REPLICATION SOURCE.
4. **Point-in-time companion copy ≠ continuous archive**: after T0 backup, A/B events required refreshing binlog files into the backup dir (drill step); production still needs an independent binlog shipping job (called out in RESTORE-DRILL.md).
5. **TLS / browser / full app stack**: not in scope this round (per task).
6. **SHA256SUMS self-hash**: regenerate with explicit file list (do not include `SHA256SUMS` in its own checksum set).

## Not done

- No merge of this evidence PR (await architect).
- No restore against any shared/team database.
