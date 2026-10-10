# Backup, continuous binlog shipping, restore (F16 / R31.3)

| Script | Role |
|---|---|
| [backup.sh](backup.sh) | Daily **full** dump (`mysqldump --master-data=2`) + companion binlog copy + `BINLOG_START` + `SHA256SUMS`. Point-in-time companion only — **not** continuous shipping. |
| [ship-binlog.sh](ship-binlog.sh) | **Separate** archive job: incrementally copy *rotated* binlogs → `BINLOG_ARCHIVE_DIR`; skip `*.index` and the file still being written; fail on size/sha mismatch; idempotent; optional retention; writes `INVENTORY.tsv`. |
| [restore.sh](restore.sh) | Import full dump, then replay from recorded `BINLOG_START` (never guesses). Optional `BINLOG_ARCHIVE_DIR` merges continuous archive (preferred on name collision). Requires `RESTORE_I_ACCEPT_DATA_LOSS=YES`. |
| [parse-binlog-start_test.sh](parse-binlog-start_test.sh) | Fixture-only parser self-check (no DB). |

Drill procedure and evidence: [RESTORE-DRILL.md](RESTORE-DRILL.md), [drills/](drills/).

## Continuous shipping

```bash
# Prefer a dedicated ENV_FILE for drills — never point at shared/team DBs by accident.
ENV_FILE=deploy/.env \
BINLOG_ARCHIVE_DIR=/var/backups/mkt/binlog-archive \
BINLOG_RETENTION_DAYS=7 \
BINLOG_RETENTION_COUNT=64 \
  ./deploy/backup/ship-binlog.sh
```

Behaviour:

1. Reads current open binlog via `SHOW BINARY LOG STATUS` (fallback `SHOW MASTER STATUS`).
2. Lists `mysql-bin.*` / `binlog.*` in the compose MySQL datadir; skips `*.index` and the open file.
3. For each rotated file: if missing → copy + `.sha256`; if present with same size → skip; if size/sha disagrees → **exit 1** (refuses to overwrite a corrupt chain).
4. Rewrites `INVENTORY.tsv` (`name`, `size`, `sha256`, `mtime_utc`).
5. Optional retention by days and/or count (oldest first).

Link to restore: full backup still owns `BINLOG_START`; after T0, keep running `ship-binlog.sh` so the archive covers A/B events for PITR. Restore:

```bash
BINLOG_ARCHIVE_DIR=/var/backups/mkt/binlog-archive \
RESTORE_I_ACCEPT_DATA_LOSS=YES \
STOP_DATETIME='2026-10-10 12:00:00' \
  ./deploy/backup/restore.sh deploy/backups/<stamp>
```

## Schedule samples (defaults never touch shared DBs)

Examples only — operators must set `ENV_FILE` / compose project to an **isolated** MySQL. See [schedule/](schedule/).

- Cron: [schedule/cron.example](schedule/cron.example)
- Compose sidecar: [schedule/docker-compose.ship-sidecar.example.yml](schedule/docker-compose.ship-sidecar.example.yml)

## Safety

- Do **not** run `restore.sh` or drills against shared/dev/prod without isolation + `RESTORE_I_ACCEPT_DATA_LOSS=YES`.
- Dump, binlog, and real credentials stay off git; archive dirs are local/ops media.
- Host `mysqlbinlog` for MySQL 8 replay must be Oracle MySQL 8 (MariaDB client can emit statements MySQL 8 rejects). See drill notes under `drills/`.
