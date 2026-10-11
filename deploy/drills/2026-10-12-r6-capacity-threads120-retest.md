# R6 capacity — threads=120 revert retest after #160 strip — **FAIL** — 2026-10-12

| Field | Value |
|---|---|
| Master base | `f6ff7e3` (#159); #160 evidence-only (no Tomcat160 on master) |
| Executor | Grok Bot on **bot box** (Compose `mkt`; no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-12 ~04:02–04:17 CST |
| Architect | #160 不合卫生包；`mkt` 回退 threads=**120** → list 复测近PASS → 再 CPU/热路径；本切片 **近PASS 未恢复** → **未**开 CPU 刀 |
| Isolation | throwaway `mkt` |
| DEC-006 | **unchanged** |
| Memory | **1536M** unchanged |
| Tomcat | **120** (accept 200, min-spare 25, `-Xss512k`, keepalive 64) |
| Capacity checklist | **boxes stay empty** |
| R6 / RC | **进行中 / 非 RC** |

## mkt revert confirmation

| Item | Value |
|---|---|
| `SERVER_TOMCAT_THREADS_MAX` | **120** (both portals) |
| accept / min-spare | 200 / 25 |
| `JAVA_TOOL_OPTIONS` | MaxRAMPercentage=75 + G1 + `-Xss512k` |
| nginx keepalive | **64** |
| Image | existing #159 portal image (lean catalog) |
| Action | `compose up -d --force-recreate` portal×2 + admin + nginx |

## vs c0f4795 near-PASS

| Gate | c0f4795 | list1 (cold) | list2 (after 60s settle) |
|---|---|---|---|
| ~500 QPS | ≈499.7 | ≈423.9 | ≈342.8 |
| P95 ≤300ms | ≈88.9 **PASS** | ≈107 **PASS** | ≈155 **PASS** |
| http_fail==0 | 0 **PASS** | ≈0.04% **FAIL** | ≈0.04% **FAIL** |
| checks >0.99 | 100% **PASS** | ≈99.96% **PASS** | ≈99.96% **PASS** |
| dropped &lt;0.05/s | ≈0.304 **FAIL-only** | ≈**36.9**/s **FAIL** | ≈**80.4**/s **FAIL** |
| peak mem | ≈1.46 GiB | ≈1.48/1.49 | ≈1.46/1.49 |
| OOM / Restarts | false / 0 | false / 0 | false / 0 |
| k6 exit | (thresholds) | **99** | **99** |

**Verdict:** near-PASS **not** restored. list2 worse on QPS/dropped after settle. Fail-stop — **CPU/hot-path cut not started**.

### Observed symptoms (list1)

- RedisResponseTimeoutException (GET satoken session; 1000ms)
- nginx upstream prematurely closed / timed out → temporary upstream disable
- Broken pipe on client abort

## CPU hot-path

**Not started** (architect precondition: restore near-PASS first).

## Artifacts

| Artifact | Location |
|---|---|
| Aggregate | [perf/reports/2026-10-12/list-threads120-retest.json](../../perf/reports/2026-10-12/list-threads120-retest.json) |
| Host logs | `/workspace/r6-capacity-logs/cpu-hot-path/` |

## Explicit non-claims

1. Not capacity tick. 2. Not RC. 3. No DEC-006 reopen. 4. No thread raise. 5. No 1536M raise. 6. No Tomcat160 land. 7. Honest FAIL OK.
