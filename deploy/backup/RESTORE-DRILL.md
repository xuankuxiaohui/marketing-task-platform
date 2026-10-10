# 备份恢复演练

依据 R31.3，最终需要全量备份、连续 binlog 归档和指定时间点恢复。项目未上线；**脚本正确性已按静态审阅对齐，本轮未对共享库执行备份或恢复**。真实隔离演练仍须按下列步骤取证后才能勾选上线清单。

## 脚本行为（F16 对齐后）

| 入口 | 行为 | 仍须运行时验证 |
|---|---|---|
| [backup.sh](backup.sh) | `mysqldump --master-data=2` 全量；解析 CHANGE MASTER / CHANGE REPLICATION SOURCE 写出 `BINLOG_START`（`file\tpos`）；复制当时可见 binlog；生成 `SHA256SUMS`；单文件复制失败即失败 | 一次文件复制 ≠ 连续归档；活动 binlog 是否完整、归档调度是否独立于本脚本 |
| [restore.sh](restore.sh) | 要求 `RESTORE_I_ACCEPT_DATA_LOSS=YES`；校验 `SHA256SUMS`；导入全量后**仅从备份记录的 binlog 文件+位点**起用 `mysqlbinlog --start-position` 重放，可设 `STOP_DATETIME`；缺少起点或归档链不含起点文件则拒绝 | 隔离目标库、完整归档链、PITR 业务核对 |

恢复**不会**再从第一份 `mysql-bin.*` 盲目重放（旧缺口：重复应用 dump 已包含的事件）。没有可解析起点时脚本直接失败，而不是猜测。

## 演练设计（隔离环境，勿对本机共享库执行）

1. 准备与源库隔离的恢复实例，记录源库/目标库、版本、时区与备份保留策略；确认不会覆盖开发共享库或其他项目数据。
2. 在时间点 `T0` 取得完整备份并记录 binlog 文件与位置（见备份目录 `BINLOG_START`）；此后持续归档 binlog。
3. 写入标记 A，选定目标时间 `T`，再写入标记 B。记录准确时间与提交顺序，保证 `T0 < A < T < B`。
4. 选择 **早于 T** 的全量备份，校验 `SHA256SUMS` 及完整归档链。设置 `RESTORE_I_ACCEPT_DATA_LOSS=YES` 后还原；脚本从其记录的坐标回放到 T。
5. 验证 A 存在、B 不存在，并核对任务实例、发奖记录、积分余额与流水的一致性。不得只检查数据库能启动。
6. 启动应用验证关键查询/业务流程，记录恢复耗时与丢失窗口，对照规格要求验收。

全量备份若晚于 T，之后再设置 binlog 停止时间不能撤销全量里已有的数据。原流程“在目标时间之后再备份并恢复到目标时间”不再作为演练方法。

## 本轮静态取证（未执行恢复）

| 检查 | 结果 |
|---|---|
| `restore.sh` 是否读取 `BINLOG_START` / dump 内 CHANGE MASTER\|SOURCE | 是 |
| 无起点时是否拒绝重放 | 是（exit 1） |
| 无 `RESTORE_I_ACCEPT_DATA_LOSS=YES` 时是否拒绝执行 | 是（exit 2） |
| 是否对本仓库共享/开发库执行 restore/backup | **否** |

解析自检（fixture，不连库）见同目录 [parse-binlog-start_test.sh](parse-binlog-start_test.sh)。

## 记录与保管

备份含个人信息，使用独立介质与权限；dump、binlog、真实配置和密钥不能入库。校验结果与演练报告保存在受控位置，在[上线清单](../R31-go-live-checklist.md)引用。

| 提交 | 环境 / 隔离目标 | 全量时间与坐标 | 目标 T | 校验与业务结果 | 恢复耗时 / 数据窗口 | 执行人 / 日期 |
|---|---|---|---|---|---|---|
| 待执行 | | | | | | |
