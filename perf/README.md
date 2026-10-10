# 性能验证

这里存放 k6 场景、容量种子与报告生成入口。旧规格中的性能数字已退役，现有脚本阈值仅是当前配置，不自动成为新的验收目标；执行前按 [DEC-006](../docs/decisions.md#dec-006性能目标及证据口径) 确认负载、数据和资源口径。本文未提供当前版本的容量通过结论。

## 入口与环境

| 入口 | 内容 |
|---|---|
| [run-p0.sh](run-p0.sh) | 列表、推进/回调、级联发奖、埋点、后台查询、风控增量 |
| [run-full.sh](run-full.sh) | 上述场景加广告、容量校验、热查询 EXPLAIN |
| [seed/seed.py](seed/seed.py) | 创建测试用户、任务、奖品、实例和事件；调整测试环境限流配置 |
| [seed/explain_hot.py](seed/explain_hot.py) / [explain.sql](seed/explain.sql) | 采集热路径执行计划 |

运行器依赖 Bash、Docker、Python、JDK 26 与 Maven，使用 `deploy/.env` 和测试 Compose。种子会写数据库、修改配置、创建可用会话，只能在专用可重建的性能环境使用。当前本机没有 Docker，不在本机或共享开发库执行。

在具备条件的测试环境，从仓库根运行：

```bash
DURATION=5m bash perf/run-p0.sh
SEED_SCALE=p1 DURATION=5m bash perf/run-full.sh
```

这些是运行入口，不代表现有脚本已达到验收质量。压测属于发布验收，不进例行 PR CI；`run-full.sh` 在 GitHub Actions 中默认拒绝执行，人工性能作业需要显式配置。

## 测量缺口（F14 本轮已修脚本断言；容量数字仍待 DEC-006）

- [advance.js](advance.js) **本轮**：按 VU 分区消费单步 callback（每实例至多一次真实推进），耗尽后回退到大目标 progress 的持续写入；`check` / `advance_business_ok` / `advance_data_exhausted` / `dropped_iterations` 入门槛。随机复用已完成 callback 不再当作持续推进。
- [complete.js](complete.js) **本轮**：对用户 × 级联任务笛卡尔积线性消费（不再取模重放）；耗尽则 `grant_data_exhausted` 失败；`grant_first_ok` / `grant_business_ok` / `checks` 入门槛，区分首次真实发奖与耗尽后的失败路径。
- [track.js](track.js) **本轮**：业务 `check`（含 accepted>0）与 `track_business_ok` / `checks` 入门槛；`track_accepted` 设结构性下限（`count>0`/`rate>0`）避免零成功；`dropped_iterations` 入门槛。满配 events/s 数字仍按 DEC-006 确认，不在此脚本自动升格为验收目标。
- 恒定到达率场景（advance / track / list / ad）**本轮**已加 `dropped_iterations` 结构性门槛（`rate<0.05`）。验收仍须同时证明负载实际达到、业务结果正确和调度完整。
- 监控规则存在不等于指标已接入。`TrackDropCounters` **已**注册 Micrometer `mkt.track.drop`（Prometheus `mkt_track_drop_total`，见 F15）；[alerts.yml](../deploy/prometheus/alerts.yml) 的埋点比例仍可能把事件数和请求数相加，需统一计量单位并验证实际暴露数据。

## 数据规模与查询审查

`p0` 种子准备 1000 个列表任务和 200 个登录用户；`p1` 使用 2000 个登录用户，并增加约 100 万用户、50 万实例、500 万事件的容量数据。静态用户档案不等于同等规模的活跃会话，Redis 会话容量应单独验证。

重点审查发布任务列表的全量读取与逐任务查询、任务实例排序、无用户筛选的积分流水排序、事件查询的分区裁剪，以及广告缓存命中。索引名称和 SQL 与设计一致，并不能推导 P95 或吞吐达标；须在目标数据量下收集实际执行计划、查询次数、扫描行数与耗时，再决定是否修改查询或索引。

## 证据记录

报告默认写入 `perf/reports/<UTC 日期>/`，包括 `*.summary.json`；全量运行还生成 `capacity.json` 和 `slow-query-explain.txt`。报告与 `perf/state.json` 不应混淆：后者含会话与签名密钥，不得作为公开附件。

每次报告至少记录：提交/脏工作区情况、日期、机器及容器资源、节点数、数据库/Redis 拓扑、数据量、冷热缓存、加压时长、实际有效吞吐、P95、业务错误、丢弃/未调度量、锁等待和 Outbox 积压。恢复场景另记录停机期间积压及恢复后的排空过程。

历史 Aviator 记录来自 [AviatorSmokeTest.cachedEvalP99UnderOneMs](../spike/6-aviator/src/test/java/com/mkt/spike/aviator/AviatorSmokeTest.java)：缓存编译表达式 `province() == 'GD' && userLevel() >= 3`，采样 10,000 次，断言 P99 < 1ms。旧文档曾记“通过”，但本轮没有核验其执行环境和原始报告；它既不是当前测试通过证明，也不代表带 IO 的业务规则延迟。

完成性能验证后，将报告引用填入[首次上线清单](../deploy/R31-go-live-checklist.md)。
