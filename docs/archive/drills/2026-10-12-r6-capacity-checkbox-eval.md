# R6 capacity 勾评估（DEC-006 清单行）— after run-full-after-#175 PASS — 2026-10-12

| Field | Value |
|---|---|
| Repo tip (master base) | `ab02562` (Merge #175; cooled risk-delta retest + noseed EOF fix) |
| Run tip under test | same `ab02562` on throwaway `mkt` (live #173 product) |
| Date (Asia/Shanghai) | 2026-10-12 11:29–12:13 CST |
| DEC-006 thresholds | **unchanged** — 禁降 Δ gate; 禁抬 1536M |
| Capacity checklist | **both boxes stay unchecked** in this PR (default; architect must say to tick) |
| RC / R6 | **非 RC** / **非 R6 通过** — R6 stays 进行中 |

## Purpose

Architect (~11:09 CST + steering): after cooled risk-delta PASS and full **run-full 不重seed PASS-ALL**, open **容量勾评估**. 勾 may still stay empty — **do not tick unless architect says**.

## Fresh suite evidence (single run-full)

Cite: [run-full-after-175](../capacity/2026-10-12-run-full-after-175.md) · logs `/workspace/r6-capacity-logs/run-full-after-175/` · tip `ab02562`.

| Scenario | Gate (DEC-006) | This run-full | Result |
|---|---|---|---|
| **list** | 500 QPS; P95≤300ms; dropped rate&lt;0.05 | ~465.6/s; P95 **95.59ms**; dropped **2** (≈0.006/s); checks 100%; http_fail 0 | **PASS** |
| **advance** | 300 QPS; P95≤200ms; dropped&lt;0.05 | progress P95 **106.15ms**; callback **12.46ms**; dropped **0**; checks 100% | **PASS** |
| **complete** | grant P95≤500ms | grant P95 **477.37ms**; checks 100%; scenario http_fail 0% | **PASS** |
| **track** | ≥6000 eps; P95≤100ms; drop&lt;0.1% | P95 **31.41ms**; dropped **0**; checks 100% | **PASS** |
| **admin-list** | P95≤800ms (p1-scale live) | instances **795.78ms**; definitions 98.6; grants 296.34; checks 100% | **PASS** (near 800 ceiling) |
| **ad** | 300 QPS; P95≤100ms; dropped&lt;0.05 | P95 **2.12ms**; dropped **3** (≈0.01/s); checks 100% | **PASS** |
| **risk-on/off** | ΔP95≤20ms; both HTTP 200+code0 | on **485.53** / off **477.82** / **Δ≈7.70ms**; checks 100%; http_fail 0 | **PASS** |

**Also closed since prior 勾评估 (#153 era):** Outbox backlog/recovery numbers ([outbox](2026-10-11-r6-capacity-outbox.md)); cold/hot 分列 ([cold-hot](2026-10-12-r6-capacity-cold-hot.md)); **`run-full` residual** (this PASS-ALL via `perf/run-full-noseed.sh`, same p1-scale state, no reseed).

## Remaining gaps / cautions (why default 勾空)

| Item | Note |
|---|---|
| admin-list instances P95≈795.78ms | PASS but **near** 800ms ceiling — thin headroom |
| complete grant P95≈477ms | PASS with more headroom than early ~498ms era; still watch |
| Second checklist box 「容量场景与正常/异常业务回归」 | Architect: historically 另议 — **do not tick** here |
| Env miss on first start | missing `state.json` mount — **not** a gate FAIL; restored and re-ran |
| Product stack | live #173 dual-IN+V9+TTL3s singleflight; no new product knife this slice |
| Prometheus / prod CA / scheduled backup | Separate R31 rows — out of scope |

## Recommendation (default)

**Leave unchecked:**

1. `- [ ] 性能报告…满足 DEC-006…` — **do not tick** unless architect explicitly says. Suite is now green in one run-full, and prior Outbox/cold-hot gaps have evidence — but checklist tick is **architect-owned**; admin-list near-ceiling + second-box 另议 remain.
2. `- [ ] 容量场景与正常/异常业务回归…` — **do not tick** (另议).

Still **非 RC** / **非 R6 通过**. No threshold value changes. No 1536M raise.

## Explicit non-claims

1. **Not** a capacity checklist tick (either box) unless architect follows up.
2. **Not** DEC-006 reopen / k6 threshold **value** change.
3. **Not** RC / R6 通过.
4. **Not** a scale reseed / wipe / migration / product knife in this PR.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 — both capacity-related boxes **remain empty**; this eval linked. R6 stays **已开 / 进行中** / **非 RC**.
