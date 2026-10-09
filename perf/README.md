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

## 在用于验收前必须修正的测量缺口

- [advance.js](advance.js) 随机复用实例，没有业务结果断言。种子仅准备 100 个单步 callback 实例，完成后继续请求不能代表持续有效推进；progress 的大目标场景需单独统计有效进度写入。
- [complete.js](complete.js) 对有限用户 × 任务组合循环取模，可能测到幂等重放；`check` 也没有对应的失败阈值。应区分首次真实发奖与重放，数据耗尽要使场景失败。
- [track.js](track.js) 记录 `track_accepted`，但没有有效 events/s 门槛；业务失败的 `check` 未进入阈值。只按发起速率或 HTTP 成功不能证明事件吞吐。
- 恒定到达率场景未设置 `dropped_iterations` 门槛，调度器未发出的请求可能让延迟看起来合格。验收需同时证明负载实际达到、业务结果正确和调度完整。
- 监控规则存在不等于指标已接入。`TrackDropCounters` 当前为内存计数；[alerts.yml](../deploy/prometheus/alerts.yml) 的埋点比例把事件数和请求数相加，需统一计量单位并验证实际暴露数据。

## 数据规模与查询审查

`p0` 种子准备 1000 个列表任务和 200 个登录用户；`p1` 使用 2000 个登录用户，并增加约 100 万用户、50 万实例、500 万事件的容量数据。静态用户档案不等于同等规模的活跃会话，Redis 会话容量应单独验证。

重点审查发布任务列表的全量读取与逐任务查询、任务实例排序、无用户筛选的积分流水排序、事件查询的分区裁剪，以及广告缓存命中。索引名称和 SQL 与设计一致，并不能推导 P95 或吞吐达标；须在目标数据量下收集实际执行计划、查询次数、扫描行数与耗时，再决定是否修改查询或索引。

## 证据记录

报告默认写入 `perf/reports/<UTC 日期>/`，包括 `*.summary.json`；全量运行还生成 `capacity.json` 和 `slow-query-explain.txt`。报告与 `perf/state.json` 不应混淆：后者含会话与签名密钥，不得作为公开附件。

每次报告至少记录：提交/脏工作区情况、日期、机器及容器资源、节点数、数据库/Redis 拓扑、数据量、冷热缓存、加压时长、实际有效吞吐、P95、业务错误、丢弃/未调度量、锁等待和 Outbox 积压。恢复场景另记录停机期间积压及恢复后的排空过程。

历史 Aviator 记录来自 [AviatorSmokeTest.cachedEvalP99UnderOneMs](../spike/6-aviator/src/test/java/com/mkt/spike/aviator/AviatorSmokeTest.java)：缓存编译表达式 `province() == 'GD' && userLevel() >= 3`，采样 10,000 次，断言 P99 < 1ms。旧文档曾记“通过”，但本轮没有核验其执行环境和原始报告；它既不是当前测试通过证明，也不代表带 IO 的业务规则延迟。

完成性能验证后，将报告引用填入[首次上线清单](../deploy/R31-go-live-checklist.md)。
