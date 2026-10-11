# 性能验证

这里存放 k6 场景、容量种子与报告生成入口。旧规格中的性能数字已退役。[DEC-006](../docs/decisions.md#dec-006性能目标及证据口径已关闭) **已关闭**：证据口径与场景数值已升格为**正式门禁**。本批（R6 容量前半）已将 `perf/*.js` 注释/门槛说明与 DEC-006 对齐；**数值默认值本就匹配门禁表，未改阈值**。**可复现容量报告（P0）已于 2026-10-11 在 bot box Compose 跑出**：见 [deploy/drills/2026-10-11-r6-capacity-dec006.md](../deploy/drills/2026-10-11-r6-capacity-dec006.md) 与 [reports/2026-10-11/EVIDENCE.md](reports/2026-10-11/EVIDENCE.md)。**整体 FAIL**（list/advance/complete/risk-on）；**track PASS**；admin-list PASS（p0 数据）。本文**不**提供容量通过结论，**不**宣称 RC；p1/`run-full.sh` 未跑。

## DEC-006 正式门禁（脚本默认已对齐）

| 场景 | 门禁 | 脚本 |
|---|---|---|
| 门户列表 | **500 QPS**，双实例 **P95≤300ms** | [list.js](list.js) |
| internal 推进/回调 | **300 QPS**，**P95≤200ms** | [advance.js](advance.js) |
| 级联发奖链 | **P95≤500ms** | [complete.js](complete.js) |
| 埋点满批 | **≥6000 events/s**，**P95≤100ms**，丢弃率 **<0.1%** | [track.js](track.js)（`TRACK_EPS` 驱动到达率；`track_accepted` 保留结构性下限） |
| 广告位 | **300 QPS**，**P95≤100ms** | [ad.js](ad.js) |
| 后台典型列表（p1） | **P95≤800ms** | [admin-list.js](admin-list.js) |
| 风控 on vs off | 领取/发奖 **P95 差值≤20ms** | [risk-delta.js](risk-delta.js) + runner 比较 |
| 恒定到达率 | `dropped_iterations` **rate<0.05** | list / advance / track / ad |
| 业务失败 | ≈0（F14 结构性门槛） | 各场景 `checks` / `*_business_ok` |
| 稳态时长 | 默认 **5m**（`DURATION`） | [lib.js](lib.js) |
| 拓扑 | portal×2 + admin×1 经 nginx；**不得**单实例外推 | Compose + 报告须记录 |

## 入口与环境

| 入口 | 内容 |
|---|---|
| [run-p0.sh](run-p0.sh) | 列表、推进/回调、级联发奖、埋点、后台查询、风控增量（DEC-006 门禁） |
| [run-full.sh](run-full.sh) | 上述场景加广告、容量校验、热查询 EXPLAIN |
| [seed/seed.py](seed/seed.py) | 创建测试用户、任务、奖品、实例和事件；调整测试环境限流配置 |
| [seed/explain_hot.py](seed/explain_hot.py) / [explain.sql](seed/explain.sql) | 采集热路径执行计划 |

运行器依赖 Bash、Docker、Python、JDK 26 与 Maven，使用 `deploy/.env` 和测试 Compose。种子会写数据库、修改配置、创建可用会话，只能在专用可重建的性能环境使用。当前本机没有 Docker，不在本机或共享开发库执行。

在具备条件的测试环境，从仓库根运行：

```bash
DURATION=5m bash perf/run-p0.sh
SEED_SCALE=p1 DURATION=5m bash perf/run-full.sh
```

这些是运行入口，不代表已有可复现容量通过报告。压测属于发布验收，不进例行 PR CI；`run-full.sh` 在 GitHub Actions 中默认拒绝执行，人工性能作业需要显式配置。种子与 `run-*.sh` / k6 实跑需**显式授权**（R6 开启本身不等于授权本执行器跑 seed）。

## 测量缺口（F14 结构性断言已合；DEC-006 门禁脚本已对齐；可复现报告待跑）

- [advance.js](advance.js) / [complete.js](complete.js) / [track.js](track.js) / [list.js](list.js) / [ad.js](ad.js) / [admin-list.js](admin-list.js) / [risk-delta.js](risk-delta.js)：注释与门槛说明已指向 **已关闭的 DEC-006 正式门禁**；QPS/P95/`dropped_iterations` 默认与门禁表一致。
- [track.js](track.js)：业务 `check` 与 `track_business_ok` / `checks` 入门槛；`track_accepted` 保留结构性下限（`count>0`/`rate>0`）；到达率由 `TRACK_EPS`（默认 6000）驱动。**满配 ≥6000 events/s 须在可复现报告中证明有效 accepted 吞吐**（本批不加硬 eps 计数门槛，避免无实跑时误伤）。
- 恒定到达率场景（advance / track / list / ad）`dropped_iterations` **rate<0.05** 为 DEC-006 门禁。验收仍须同时证明负载实际达到、业务结果正确和调度完整。
- **P0 已跑（2026-10-11）**：证据见 [deploy/drills/2026-10-11-r6-capacity-dec006.md](../deploy/drills/2026-10-11-r6-capacity-dec006.md)；原始 `perf/reports/2026-10-11/*.summary.json` 本机生成（gitignored）。**整体未达 DEC-006**。list 路径补强见 [list-path](../deploy/drills/2026-10-11-r6-capacity-list-path.md)；#142 后再测仍 FAIL；hot-path 补强见 [list-hotpath](../deploy/drills/2026-10-11-r6-capacity-list-hotpath.md)（勾仍空；**未**改 DEC-006 阈值）。**list 正式 5m after #144（FAIL dropped）**：见 [list-5m-after-144](../deploy/drills/2026-10-11-r6-capacity-list-5m-after-144.md)（勾仍空；**未**改阈值）。**list residual dropped 补强（#147）**：见 [list-dropped-residual](../deploy/drills/2026-10-11-r6-capacity-list-dropped-residual.md)（勾仍空；**未**改门禁数值）。**advance residual dropped 补强（#148）**：见 [advance-dropped](../deploy/drills/2026-10-11-r6-capacity-advance-dropped.md)（正式 5m advance **PASS** dropped 0；勾仍空）。**complete 补强（本 PR）**：见 [complete](../deploy/drills/2026-10-11-r6-capacity-complete.md)（warm→formal；正式 5m grant P95≈498ms **PASS**；勾仍空；**未**改门禁数值）。**Outbox 积压/恢复（#154；勾仍空）**：见 [outbox](../deploy/drills/2026-10-11-r6-capacity-outbox.md)（稳态 PENDING + 最老延迟 + 10k 排空；不重开 DEC-002）。**冷热分列（#155；勾仍空）**：见 [cold-hot](../deploy/drills/2026-10-12-r6-capacity-cold-hot.md)（list+ad 冷|热；master `960e373`→`18798ae`；**未**改门禁数值）。**run-full residual（本 PR；勾仍空）**：见 [run-full](../deploy/drills/2026-10-12-r6-capacity-run-full.md)（p1 captcha→**2000** OK；`run-full.sh` **FAIL at list**；fail-stop；**未**改门禁数值；cite Outbox/cold-hot）。**沉降后 list 单场景复测（本 PR；勾仍空）**：见 [list-settle](../deploy/drills/2026-10-12-r6-capacity-list-settle.md)（不叠 bulk；list formal **FAIL**；P95≈176ms OK；http_fail/checks/dropped 越门；fail-stop；**未**改门禁数值）。**重建至 DEC-006 p1 后 list（本 PR；勾仍空）**：见 [rebuild-list](../deploy/drills/2026-10-12-r6-capacity-rebuild-list.md)（`mkt` `down -v` + p1 seed tokenUsers=2000；沉降后 list formal **FAIL**；P95≈1580ms；http_fail≈1.64%；checks≈98.4%；dropped≈83.4/s；fail-stop；**未**改门禁数值）。**list-under-p1 内存/热路径补强（本 PR；勾仍空）**：见 [list-under-p1-mem](../deploy/drills/2026-10-12-r6-capacity-list-under-p1-mem.md)（1536M 不抬；Tomcat 120 + Xss512k + G1 + catalog metadata-only；OOM cleared；list formal **FAIL** 仅 dropped≈0.304/s；P95/http_fail/checks PASS；fail-stop；**未**改门禁数值）。**clear residual dropped 实验（#160 证据-only；勾仍空）**：见 [clear-dropped](../deploy/drills/2026-10-12-r6-capacity-clear-dropped.md)（Tomcat160+加重 prealloc **FAIL** 回退；**产品配置不落地**；master 仍 threads=120；**未**改门禁）。**threads=120 复测（本 PR；勾仍空）**：见 [threads120-retest](../deploy/drills/2026-10-12-r6-capacity-threads120-retest.md)（回退 Tomcat120 后 list formal **未**恢复 c0f4795 near-PASS；CPU/热路径 **未**开；fail-stop；**未**改门禁）。admin-list p1 formal 5m 已 PASS（见 [admin-list-p1](../deploy/drills/2026-10-11-r6-capacity-admin-list-p1.md)）；勾仍空。无通过报告不能承诺容量。- 监控规则存在不等于指标已接入。`TrackDropCounters` **已**注册 Micrometer `mkt.track.drop`（Prometheus `mkt_track_drop_total`，见 F15）；[alerts.yml](../deploy/prometheus/alerts.yml) 的埋点比例仍可能把事件数和请求数相加，需统一计量单位并验证实际暴露数据。

## 数据规模与查询审查

`p0` 种子准备 1000 个列表任务和 200 个登录用户；`p1` 使用 2000 个登录用户，并增加约 100 万用户、50 万实例、500 万事件的容量数据。静态用户档案不等于同等规模的活跃会话，Redis 会话容量应单独验证。

重点审查发布任务列表的全量读取与逐任务查询、任务实例排序、无用户筛选的积分流水排序、事件查询的分区裁剪，以及广告缓存命中。索引名称和 SQL 与设计一致，并不能推导 P95 或吞吐达标；须在目标数据量下收集实际执行计划、查询次数、扫描行数与耗时，再决定是否修改查询或索引。

## 证据记录

报告默认写入 `perf/reports/<UTC 日期>/`，包括 `*.summary.json`；全量运行还生成 `capacity.json` 和 `slow-query-explain.txt`。报告与 `perf/state.json` 不应混淆：后者含会话与签名密钥，不得作为公开附件。

每次报告至少记录：提交/脏工作区情况、日期、机器及容器资源、节点数、数据库/Redis 拓扑、数据量、冷热缓存、加压时长、实际有效吞吐、P95、业务错误、丢弃/未调度量、锁等待和 Outbox 积压。恢复场景另记录停机期间积压及恢复后的排空过程。拓扑须为 **portal×2 + admin×1 经 nginx**，不得用单实例结果外推。

历史 Aviator 记录来自 [AviatorSmokeTest.cachedEvalP99UnderOneMs](../spike/6-aviator/src/test/java/com/mkt/spike/aviator/AviatorSmokeTest.java)：缓存编译表达式 `province() == 'GD' && userLevel() >= 3`，采样 10,000 次，断言 P99 < 1ms。旧文档曾记“通过”，但本轮没有核验其执行环境和原始报告；它既不是当前测试通过证明，也不代表带 IO 的业务规则延迟。

完成性能验证后，将报告引用填入[首次上线清单](../deploy/R31-go-live-checklist.md)。
