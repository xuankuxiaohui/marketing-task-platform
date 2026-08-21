# 慢查询复盘（任务 49 / NFR 性能 8）

对照 design §2.6 容量假设与 §7.8 种子规模：门户用户 100 万、日新增实例 50 万、日事件 500 万（峰值 3000 events/s，`evt_event_log` 按月 RANGE 分区）。本文件是静态索引对照；staging 跑 `perf/run-full.sh` 后把 `EXPLAIN` 落到 `perf/reports/<日期>/slow-query-explain.txt`（gitignore，签署项）。

**不进例行 PR CI。** 未改 V1–V4，未新增 Flyway 索引（design §3 已列的热路径索引足够支撑 k6 门槛；缺口只记账）。

## 容量种子

| 口径 | 落库 | 做法 |
|------|------|------|
| 100 万用户档案 | `sys_portal_user` ≥ 1e6 | `perf_seq` 批量 `INSERT`（`capu*`，不登录） |
| k6 登录态用户池 | HTTP 注册 2000 | 列表 / 领取 / 广告频控用 |
| 50 万实例 | `task_instance` ≥ 5e5 | 已有 `bulk_instances` |
| 500 万事件 | `evt_event_log` ≥ 5e6 行 | 当月分区追加；峰值口径 3000 eps，k6 性能 5 按 2 倍余量 6000 eps |
| 广告位 | `home_banner` / `home_popup` / `home_float` / `app_splash` / `home_image` | 性能 6 |

`perf_seq` 只存在于跑过 p1 种子的 staging 库，不是产品表，不进 Flyway。

## 热路径 × 索引

| NFR / 入口 | SQL（mapper / 种子 EXPLAIN） | 设计索引 | 预期 |
|------------|------------------------------|----------|------|
| 性能 1 列表 | `task_definition` `deleted=0 AND status='PUBLISHED' ORDER BY sort_weight, id` | `idx_status_sort (status, deleted, sort_weight)` | range/ref；1000 条已发布任务全量读后内存过滤（既有列表语义） |
| 性能 2 推进 | `task_instance_step` CAS + `uk_dedup (instance_id, step_code, report_id)` | 乐观锁 + 去重唯一 | 点查，无全表 |
| 性能 3 发奖 | 实例 uk + `rwd_prize` 库存 SQL（无 version 列） | `uk_user_task_cycle` / 库存语句听 §5.7 | 单事务 P95 门槛 500 ms；CI 常驻级联预算仍是任务 29 路径 |
| 性能 4 风控 | 规则配置点查 `uk_rule`；计数走 Redis | 不扫 `risk_hit_log` | 增量 ≤ 20 ms |
| 性能 5 埋点 | `INSERT evt_event_log` PK `(id, server_time)` | 月分区；只增 | 3000 eps 峰值、6000 eps 验收 |
| 性能 6 广告 | `ad_position.uk_code` + `ad_position_material.idx_position` | L2 `ad:position` TTL 60s | 热路径命中缓存，P95 ≤ 100 ms @ 300 QPS |
| 性能 7 后台实例 | `SELECT … FROM task_instance ORDER BY id DESC LIMIT 20` | PRIMARY | 逆序主键，50 万行可过 800 ms |
| 性能 7 后台定义 | `task_definition` `ORDER BY id DESC LIMIT 20` | PRIMARY + `uk_code` | 千级行 |
| 性能 7 积分流水 | `pnt_transaction ORDER BY created_at DESC, id DESC LIMIT 20` | `idx_user_time (user_id, created_at)` | **无 user_id 时用不上最左列**，现网种子流水很少；若流水涨到十万级再考虑 design 未列的 `(created_at, id)`，本任务不加 V8 |
| C 端我的任务 | `user_id + status` | `idx_user_status (user_id, status, created_at)` | range |
| 过期扫描 | `status + expire_at` | `idx_expire` | 调度，非 k6 |
| 事件调试 | `event_code` / `user_id` / `server_time` | `idx_code_time` / `idx_user_time` + 分区裁剪 | `JSON_CONTAINS(events, …)` 无法走列索引；R29.1 管理端调试，不是列表热路径 |
| 日聚合 | `server_time` 窗 + `JSON_TABLE` | 分区键 = `server_time` | 批任务，不是交互 P95 |

## 结论

1. **k6 热路径索引与 design §3 一致**，性能 1–7 不因 100 万用户 / 50 万实例 / 500 万事件缺主键或唯一约束。
2. **事件表靠月分区**支撑 3000 eps 峰值写入与按日扫描；种子会补当前月起 3 个未来分区（与调度 8 同口径）。
3. **不发明索引**：积分后台无筛选列表、调试 `JSON_CONTAINS` 是已知形态，未打进 V1–V7。若 staging `run-full.sh` 性能 7 积分项 P95 破 800 ms，再开独立任务按 design 补索引，不在本任务改 DDL。
4. **Redis 4GB（R31.5）** 按 100 万双账号会话估算；本种子不给 100 万行登录，k6 只用 2000 token。会话容量仍是上线清单生产项，不是 k6 门槛。

复验命令（staging compose，portal ×2）：

```text
SEED_SCALE=p1 DURATION=5m bash perf/run-full.sh
```

报告：`perf/reports/<日期>/`（`capacity.json`、各 script `*.summary.json`、`slow-query-explain.txt`）。
