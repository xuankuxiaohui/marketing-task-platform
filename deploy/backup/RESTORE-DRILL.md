# 备份恢复演练

依据 R31.3，最终需要全量备份、连续 binlog 归档和指定时间点恢复。项目未上线；**脚本正确性已按静态审阅对齐，本轮未对共享库执行备份或恢复**。真实隔离演练须按下列步骤取证后才能勾选上线清单；**2026-10-10 已在 bot box 对 throwaway Docker MySQL 完成一次隔离取证**（见 [drills/2026-10-10-f16-isolated-restore.md](drills/2026-10-10-f16-isolated-restore.md)）；**同日另见连续 shipping 演练** [drills/2026-10-10-f16-continuous-binlog-ship.md](drills/2026-10-10-f16-continuous-binlog-ship.md)（full → ship → A/B → PITR）。共享/团队库仍禁止。

## 脚本行为（F16 对齐后）

| 入口 | 行为 | 仍须运行时验证 |
|---|---|---|
| [backup.sh](backup.sh) | `mysqldump --master-data=2` 全量；解析 CHANGE MASTER / CHANGE REPLICATION SOURCE 写出 `BINLOG_START`（`file\tpos`）；复制当时可见 binlog；生成 `SHA256SUMS`；单文件复制失败即失败 | 全量调度与校验；**连续归档见 ship-binlog.sh**（本脚本不是连续 shipping） |
| [ship-binlog.sh](ship-binlog.sh) | **独立**连续归档：仅复制已轮转 binlog → `BINLOG_ARCHIVE_DIR`；跳过 `*.index` 与当前正在写入文件；已存在则按 size/sha 校验且不覆盖；失败即退出；可选按天/数量保留；写 `INVENTORY.tsv` | 生产 cron/sidecar 调度；归档介质权限 |
| [restore.sh](restore.sh) | 要求 `RESTORE_I_ACCEPT_DATA_LOSS=YES`；校验 `SHA256SUMS`；导入全量后**仅从备份记录的 binlog 文件+位点**起用 `mysqlbinlog --start-position` 重放，可设 `STOP_DATETIME`；可选 `BINLOG_ARCHIVE_DIR`（连续归档优先）；缺少起点或归档链不含起点文件则拒绝 | 隔离目标库、完整归档链、PITR 业务核对 |

恢复**不会**再从第一份 `mysql-bin.*` 盲目重放（旧缺口：重复应用 dump 已包含的事件）。没有可解析起点时脚本直接失败，而不是猜测。

## 连续 binlog 归档（与全量分离）

- 全量仍由 [backup.sh](backup.sh) 产出 `BINLOG_START`；**不要**把连续 shipping 塞进 `backup.sh`。
- [ship-binlog.sh](ship-binlog.sh) 定时增量拷贝已轮转文件；调度样例见 [schedule/](schedule/) 与 [README.md](README.md)。
- PITR：选早于 T 的全量 + `BINLOG_ARCHIVE_DIR` 中覆盖 A/B 事件的归档链，再设 `STOP_DATETIME`。
- 默认示例不指向共享库；演练使用 throwaway `ENV_FILE`。


## 演练设计（隔离环境，勿对本机共享库执行）

1. 准备与源库隔离的恢复实例，记录源库/目标库、版本、时区与备份保留策略；确认不会覆盖开发共享库或其他项目数据。
2. 在时间点 `T0` 取得完整备份并记录 binlog 文件与位置（见备份目录 `BINLOG_START`）；此后持续归档 binlog。
3. 写入标记 A，选定目标时间 `T`，再写入标记 B。记录准确时间与提交顺序，保证 `T0 < A < T < B`。
4. 选择 **早于 T** 的全量备份，校验 `SHA256SUMS` 及完整归档链。设置 `RESTORE_I_ACCEPT_DATA_LOSS=YES` 后还原；脚本从其记录的坐标回放到 T。
5. 验证 A 存在、B 不存在，并核对任务实例、发奖记录、积分余额与流水的一致性。不得只检查数据库能启动。
6. 启动应用验证关键查询/业务流程，记录恢复耗时与丢失窗口，对照规格要求验收。

全量备份若晚于 T，之后再设置 binlog 停止时间不能撤销全量里已有的数据。原流程“在目标时间之后再备份并恢复到目标时间”不再作为演练方法。

## 静态取证（脚本行为；另见隔离演练表）

| 检查 | 结果 |
|---|---|
| `restore.sh` 是否读取 `BINLOG_START` / dump 内 CHANGE MASTER\|SOURCE | 是 |
| 无起点时是否拒绝重放 | 是（exit 1） |
| 无 `RESTORE_I_ACCEPT_DATA_LOSS=YES` 时是否拒绝执行 | 是（exit 2） |
| 是否对本仓库共享/开发库执行 restore/backup | **否**（仅 throwaway Docker MySQL） |

解析自检（fixture，不连库）见同目录 [parse-binlog-start_test.sh](parse-binlog-start_test.sh)。

## 记录与保管

备份含个人信息，使用独立介质与权限；dump、binlog、真实配置和密钥不能入库。校验结果与演练报告保存在受控位置，在[上线清单](../R31-go-live-checklist.md)引用。

| 提交 | 环境 / 隔离目标 | 全量时间与坐标 | 目标 T | 校验与业务结果 | 恢复耗时 / 数据窗口 | 执行人 / 日期 |
|---|---|---|---|---|---|---|
| 15af760 + backup.sh stdin/index fix (this drill PR) | bot box throwaway Docker MySQL `mkt_platform_f16_drill` (down -v after) | 20261010T125738Z / `mysql-bin.000005:157` | 2026-10-10 12:57:54 UTC | A present, B absent, ledger=150 (**PITR_OK**); fixture PASSED; refuse gate exit 2 | ~5s restore; loss window = events after T | Grok Bot / 2026-10-10 CST; evidence: [drills/2026-10-10-f16-isolated-restore.md](drills/2026-10-10-f16-isolated-restore.md) |
| 84fc506 + ship-binlog.sh (this PR) | bot box throwaway Docker MySQL `mkt_platform_f16_ship` (down -v after) | 20261010T151223Z / `mysql-bin.000007:157` | 2026-10-10 15:12:43 UTC | A present, B absent, ledger=150 (**PITR_OK** via `BINLOG_ARCHIVE_DIR`); idempotent ship; fail-loud size mismatch; retention count=3; fixture PASSED; refuse exit 2 | ~15s wall for A/B/ship/restore | Grok Bot / 2026-10-10 CST; evidence: [drills/2026-10-10-f16-continuous-binlog-ship.md](drills/2026-10-10-f16-continuous-binlog-ship.md) |
