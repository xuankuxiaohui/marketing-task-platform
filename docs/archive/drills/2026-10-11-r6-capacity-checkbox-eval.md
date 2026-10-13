# R6 capacity 勾评估（DEC-006 清单行）— after #152 — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `cb794f2` (Merge #152; admin-list p1 formal 5m PASS) |
| Branch | `docs/r6-capacity-checkbox-eval-2026-10-11` |
| Date (Asia/Shanghai) | 2026-10-11 ~23:32–23:45 CST |
| Prior architect note | [#152 comment](https://github.com/xuankuxiaohui/marketing-task-platform/pull/152#issuecomment-6110511678)：下一片开容量勾评估；由本 PR 决定是否勾「满足 DEC-006」行；第二勾「场景+业务回归」另议；仍非 RC |
| DEC-006 thresholds | **unchanged** — no value edits; DEC **not** reopened |
| Capacity checklist | **both boxes stay unchecked** in this PR (default recommendation) |
| RC / R6 | **非 RC** / **非 R6 通过** — R6 stays 进行中 |

## Purpose

Architect (#152): after admin-list p1 PASS → open **容量勾评估** (docs): summarize already-green formal evidence for list / advance / complete / risk / ad / track / admin-list-p1; **explicitly list remaining gaps** (Outbox backlog/recovery numbers, cold/hot split, `run-full.sh` residual, p1 seed captcha, etc.); this eval PR **decides whether** to tick the DEC-006 capacity row — default **leave unchecked** if gaps remain.

**This slice is docs-only.** No re-seed, no DB wipe, no migration change, no k6 re-run, no threshold value change.

## Already-green formal evidence (cite)

| Scenario | Gate (DEC-006) | Formal result | Evidence / tip |
|---|---|---|---|
| **list** | 500 QPS; P95≤300ms; dropped rate&lt;0.05; checks/http_fail | **PASS** (~500/s; P95≈4.34ms; dropped **0**; checks 100%; http_fail 0%) | [list-dropped-residual](2026-10-11-r6-capacity-list-dropped-residual.md) · #147 tip / master `152734c` |
| **advance** | 300 QPS; progress/callback P95≤200ms; dropped&lt;0.05; business | **PASS** (~300/s; progress P95≈60.6ms; callback P95≈41.7ms; dropped **0**; checks 100%) | [advance-dropped](2026-10-11-r6-capacity-advance-dropped.md) · #148 |
| **complete** | grant P95≤500ms; business / first_ok / exhausted | **PASS** (grant P95≈498.1ms; checks 100%; http_fail 0%; first_ok 4049; exhausted 0) | [complete](2026-10-11-r6-capacity-complete.md) · #149 evidence base `977edc1` |
| **risk-on/off** | ΔP95≤20ms; **both** variants HTTP 200 + code===0 (403 never OK) | **PASS** (on≈381.1ms; off≈385.1ms; **Δ≈−4.0ms**; checks 100%; http_fail 0%) | [risk-on](2026-10-11-r6-capacity-risk-on.md) · #150 tip `b359c9b` |
| **ad** | 300 QPS; P95≤100ms; dropped&lt;0.05 | **PASS** (~300/s; P95≈1.84ms; dropped **0**; checks 100%) | [ad](2026-10-11-r6-capacity-ad.md) · #151 tip `efbe303` |
| **track** | ≥6000 eps; P95≤100ms; drop&lt;0.1% | **PASS** (accepted ≈6000.02/s; P95≈11.4ms; drop 0; checks 100%) — from initial P0 suite | [dec006](2026-10-11-r6-capacity-dec006.md) · #139 era (not re-run after later cuts; still the standing track PASS) |
| **admin-list p1** | p1 data; instances/definitions/grants P95≤800ms | **PASS** (instances≈699.8ms; definitions≈99.6ms; grants≈200.8ms; checks 100%; live users≈1.01M / instances≈515k / events≈5.79M) | [admin-list-p1](2026-10-11-r6-capacity-admin-list-p1.md) · #152 tip `c7fc87d` → master `cb794f2` |

**Topology note (common):** bot box Compose `mkt` — portal×2 + admin×1 via nginx; isolated throwaway volume; DEC-006 machine/pool baselines per report headers. Threshold **values** never changed across these cuts.

## Remaining gaps (block ticking 「满足 DEC-006」)

### 1. Outbox backlog / recovery numbers (DEC-006 × DEC-002)

DEC-006 requires evidence to include **Outbox 积压** and pins **数字门禁** for steady backlog, oldest-event lag, and recovery drain (DEC-002 closed mechanism only; numbers live here).

| Observed | Status |
|---|---|
| Initial P0 suite noted `sys_outbox` PENDING **267110** ([dec006](2026-10-11-r6-capacity-dec006.md)) | Snapshot only — **not** a steady-state / recovery gate |
| Formal scenario reports after list→admin-list-p1 | **Do not** record Outbox backlog / oldest lag / recovery drain numbers |
| Dedicated Outbox recovery / drain drill under load | **Not run** |

**Gap:** no accepted numeric Outbox capacity gate + no recovery-empty evidence. Blocks a clean DEC-006 capacity-row tick.

### 2. Cold vs hot path split (DEC-006 冷热 / 热点)

DEC-006: reports must **分列** cold-start vs hot steady-state; hotspot ~20/80; must not report hot-only.

| Observed | Status |
|---|---|
| List residual / hot-path work documented warm→formal and some cold notes | Warm/formal PASS exists; **not** a full cold-vs-hot **分列** capacity claim for every scenario |
| Idle cold list sample in initial P0 (~234 ms cold / ~115–170 ms warm) | Idle-only; not under gate QPS |
| Ad / advance / complete / risk / track / admin-list-p1 | Primarily **warm→formal** (hot steady); cold column absent or incomplete |

**Gap:** DEC-006 cold/hot 分列 not satisfied as a suite-level evidence package.

### 3. `run-full.sh` residual

[`perf/run-full.sh`](../../perf/run-full.sh) is the P1 full gate (seed p1 → list → advance → complete → track → admin-list → ad → risk-on/off delta). **Never executed** end-to-end this R6 day.

What exists instead: **per-scenario** formal 5m PASSes (mostly p0 seed; admin-list alone at live p1 archive scale). That is stronger than zero evidence, but is **not** a single `run-full.sh` soak with one seed snapshot and one report directory.

**Gap:** full-suite residual / single-shot P1 orchestration evidence missing.

### 4. p1 seed captcha incompleteness

Official `seed.py --scale p1` re-run for #152 hit **`captcha missing in redis for portal`** at ~**1020/2000** portal login users (`SEED_EXIT=1`). Live MySQL already had p1 **archive** scale (users≈1.01M / instances≈515k / events≈5.79M); admin-list does not need portal tokens — but DEC-006 p1 text still calls for ~**2000 login users** + Redis session capacity separate from static profiles.

**Gap:** p1 login-session completeness / Redis session capacity not cleanly demonstrated; seed honesty already recorded on [#152](https://github.com/xuankuxiaohui/marketing-task-platform/pull/152).

### 5. Other leftovers (from DEC-006 / checklist / drills)

| Item | Note |
|---|---|
| Second checklist box 「容量场景与正常/异常业务回归」 | Architect: **另议** — not decided in this eval |
| Suite-level re-run after later cuts | track PASS is from early P0; list/advance/complete/risk/ad/admin-list-p1 were sequential fail-stop cuts — **no** single post-#152 re-soak of the whole matrix |
| Resource / pool / Outbox fields in every `perf/reports/` JSON | Partial across reports; DEC-006 evidence checklist asks for commit, resources, topology, data scale, cold/hot, duration, split throughput, P95, business errors, drops, lock waits, **Outbox backlog** |
| Prometheus / production CA / scheduled backup | Separate R31 rows — **out of scope** for this capacity-row decision, but still open on the go-live checklist |
| complete grant P95≈498ms | PASS but **near** 500ms ceiling — headroom thin (not a fail; flag for architect) |

## Recommendation (default)

**Leave unchecked:**

1. `- [ ] 性能报告…满足 DEC-006…` — **do not tick**. Green per-scenario formal 5m evidence is strong, but **Outbox numeric/recovery**, **cold/hot 分列**, **`run-full.sh` residual**, and **p1 seed captcha / login-session completeness** remain open. Ticking now would over-claim DEC-006 evidence口径.
2. `- [ ] 容量场景与正常/异常业务回归…` — **do not tick** (architect: 另议).

Still **非 RC** / **非 R6 通过**. No threshold value changes. No auto-advance into RC.

### Suggested next gap to close (ask architect)

**Default if architect wants one next slice:** **Outbox backlog / recovery numbers** (measure steady PENDING + oldest lag + controlled drain/recovery under load; pin numbers to DEC-006 without reopening DEC-002 mechanism). Alternatives: cold/hot 分列 package, or `run-full.sh` once p1 seed captcha/login path is honest.

## Explicit non-claims

1. **Not** a capacity checklist tick (either box).
2. **Not** DEC-006 reopen / k6 threshold **value** change.
3. **Not** RC / R6 通过 / full `run-full.sh` PASS.
4. **Not** a new seed / wipe / migration / perf re-run in this PR.
5. **Not** a claim that early track PASS was re-validated after #147–#152 code/docs cuts.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 — both capacity-related boxes **remain empty**; this eval linked. R6 stays **已开 / 进行中** / **非 RC**.
