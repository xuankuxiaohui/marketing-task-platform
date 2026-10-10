# F16 continuous binlog shipping drill evidence — 2026-10-10

| Field | Value |
|---|---|
| Repo tip (master synced) | `84fc506245e6b650f303f8bbbe24fff250c28386` |
| Branch | `feat/p4-f16-continuous-binlog-ship` |
| Executor | Grok Bot on bot box (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-10 ~23:10–23:13 CST |
| Isolation | Throwaway Docker Compose MySQL only (`mysql:8.0`), project `mkt`, DB `mkt_platform_f16_ship`, volume removed after drill |
| Shared/prod DB touched | **No** |

## Goal

Architect F16 continuous shipping: **separate** archive job (not stuffed into `backup.sh`) → retention + inventory → schedule samples → short drill **full backup → continuous ship → write A/B → PITR** using `BINLOG_ARCHIVE_DIR`.

## Environment

| Item | Detail |
|---|---|
| Host | Debian bot box; Docker Engine 26.1.5; `dockerd` already running |
| Compose | `deploy/docker-compose.yml` service `mysql` only |
| Env file | `/workspace/f16-ship-drill/env` (throwaway; **outside repo**; deleted/not committed) |
| MySQL | 8.0.46; `log_bin=ON`; `binlog_format=ROW`; `server-id=1`; TZ `+00:00` |
| Host `mysqlbinlog` | MySQL 8.0.46 (`mysqlbinlog Ver 8.0.46`) |

## Script SHAs (this PR)

| Path | SHA256 |
|---|---|
| `deploy/backup/ship-binlog.sh` | `98faab30e12b00d2ecd160fc200fc16557c21e56048a814ee75fb01372118848` |
| `deploy/backup/restore.sh` (BINLOG_ARCHIVE_DIR merge) | `f2a38f4dc40f00158d837f51009d01183163aca508d1b8b62f95c2c15a4b6ebb` |
| `deploy/backup/backup.sh` (unchanged this PR) | `76c7f0bda75d0b2c4913cddfd7d0fef536e27eebc9739c96e6ba0239c2bb00cc` |
| `deploy/backup/README.md` | `af4d1f5ae59cb67c047e65847c33696743640e70b12395809ba3887a7e9ed01f` |
| `deploy/backup/RESTORE-DRILL.md` | `16fd9f3ca722e6acbae3da3f2727ff6dff186e11fe3af038c4c049fe84cb25b9` |
| Schedule samples | `deploy/backup/schedule/cron.example`, `deploy/backup/schedule/docker-compose.ship-sidecar.example.yml` |

## Commands (summary)

```bash
git checkout -B feat/p4-f16-continuous-binlog-ship origin/master  # 84fc506
# throwaway ENV_FILE under /workspace/f16-ship-drill/env
docker compose --env-file ... -f deploy/docker-compose.yml up -d mysql
# seed markers/ledger; FLUSH LOGS x3
BACKUP_DIR=/workspace/f16-ship-drill/backups ENV_FILE=... ./deploy/backup/backup.sh
# -> 20261010T151223Z / BINLOG_START mysql-bin.000007:157
BINLOG_ARCHIVE_DIR=/workspace/f16-ship-drill/archive ./deploy/backup/ship-binlog.sh  # copy rotated; skip current
# re-run: copied=0 skipped_existing=6 (idempotent)
# insert A (balance=150); FLUSH LOGS; ship  # archives 000007
# T_UTC=2026-10-10 15:12:43
# insert B (balance=200); FLUSH LOGS; ship  # archives 000008
# corrupt archive size -> ship exits 1 (fail loud)
# restore without RESTORE_I_ACCEPT_DATA_LOSS -> exit 2
# move companion bins aside; restore with BINLOG_ARCHIVE_DIR + STOP_DATETIME=T
TZ=UTC RESTORE_I_ACCEPT_DATA_LOSS=YES STOP_DATETIME='2026-10-10 15:12:43' \
  BINLOG_ARCHIVE_DIR=/workspace/f16-ship-drill/archive ENV_FILE=... \
  ./deploy/backup/restore.sh /workspace/f16-ship-drill/backups/20261010T151223Z
BINLOG_RETENTION_COUNT=3 ./deploy/backup/ship-binlog.sh  # keep 3 newest
bash deploy/backup/parse-binlog-start_test.sh
docker compose ... down -v
```

## Outcomes

| Check | Result |
|---|---|
| `ship-binlog.sh` skips current open file | yes (`mysql-bin.000007` then `000008`/`000009`) |
| Skips `*.index` | yes (not copied) |
| Idempotent re-ship | `copied=0 skipped_existing=6` |
| Fail loud on size mismatch | exit 1: `refusing to overwrite (would corrupt chain)` |
| `INVENTORY.tsv` | name/size/sha256/mtime_utc present |
| Retention `BINLOG_RETENTION_COUNT=3` | purged oldest; kept `000006`–`000008` |
| Fixture `parse-binlog-start_test.sh` | PASSED |
| Restore refuse gate | exit 2 |
| Restore used `BINLOG_ARCHIVE_DIR` (companion bins removed) | `using BINLOG_ARCHIVE_DIR=...`; start `mysql-bin.000007:157` |
| PITR `STOP_DATETIME` | `2026-10-10 15:12:43` UTC |
| Marker A after restore | **present** |
| Marker B after restore | **absent** |
| Ledger balance | **150** (A; not 200 from B) |
| Verdict | **PITR_OK** (continuous ship path) |
| Teardown | container + `mkt_mysql-data` removed |

## Gaps / follow-ups

1. **Production schedule** still operator-owned; repo only ships cron/sidecar **examples** with `ISOLATED=YES` / placeholder `ENV_FILE` (never default to shared DBs).
2. **Sidecar image** is illustrative (`docker:27-cli`); host cron is the supported path until an image with bash+compose is standardized.
3. **TLS / browser / shared-library restore / DEC-005/006** — out of scope (per task).
4. **`--master-data` deprecation** — mysqldump 8 warns; scripts still parse MASTER and SOURCE forms.
5. Dump/binlog bytes from this drill stayed under `/workspace/f16-ship-drill/` (not committed).

## Not done

- No merge of this PR (await architect).
- No restore against any shared/team database.
