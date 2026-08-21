# 备份恢复演练（R31.3）

每日全量 `mysqldump --single-transaction --master-data=2` + binlog 保留 7 天（`deploy/mysql/conf.d/mkt.cnf`）。介质与生产账号分离；备份含个人信息，按合规控制访问。Redis 会话不备份。

## 演练步骤

1. `cp deploy/.env.example deploy/.env`，填占位后 `bash deploy/backup/backup.sh`
2. 写入一条可识别行（例如后台改字典），记下 UTC 时刻 T
3. 再跑一次 `backup.sh`
4. 停应用流量，`STOP_DATETIME='<T>' bash deploy/backup/restore.sh deploy/backups/<stamp>`
5. 断言：T 之前的数据在，T 之后的写入不在
6. 填下表，归档到运维工单（不要把 dump 提交进 git）

| 日期 (UTC) | 环境 | 操作人 | 备份目录 | 目标时间点 | 结果 | 备注 |
|------------|------|--------|----------|------------|------|------|
| 2026-08-20 | compose | 任务 42 交付 | 脚本已入库，首次真人演练待 staging | — | 待签 | P0 交付物是脚本+步骤；签字在上线清单 |

生产首次签字前必须在 staging 实跑一行。
