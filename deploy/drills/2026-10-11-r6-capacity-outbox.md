# R6 capacity Outbox backlog / recovery numbers — after #153 — 2026-10-11

| Field | Value |
|---|---|
| Repo tip (master base) | `f147abd` (Merge #153; capacity 勾评估 — both boxes empty) |
| Branch | `docs/r6-capacity-outbox-2026-10-11` |
| Executor | Grok Bot on **bot box** (no CloudAgent) |
| Date (Asia/Shanghai) | 2026-10-11 ~23:53 → 2026-10-12 ~00:08 CST |
| Prior | [#153](https://github.com/xuankuxiaohui/marketing-task-platform/pull/153) / [checkbox-eval](2026-10-11-r6-capacity-checkbox-eval.md) next gap: **Outbox backlog / recovery numbers** |
| Isolation | Compose project `mkt` (throwaway; **not** prod / shared DB) |
| Shared/prod DB touched | **No** |
| DEC-002 mechanism | **unchanged / not reopened** — batch **100** / fixedDelay **5s** / lock-hold budget **2s** / **no MQ** |
| DEC-006 | numeric Outbox evidence only; **thresholds for other scenes unchanged** |
| Capacity checklist | **both boxes stay empty** — this slice does **not** tick capacity |
| R6 / RC | **进行中 / 非 RC** |

## Goal

Measure and pin **candidate DEC-006 Outbox numeric gates** from honest evidence:

1. **稳态 PENDING** under keep-up write load (relay ticking)
2. **最老延迟** (oldest PENDING age)
3. **可控排空** (controlled recovery drain after known backlog)

Fail-stop. **Do not** reopen DEC-002. **Do not** tick capacity. **Not** RC.

## Topology

| Item | Detail |
|---|---|
| Compose | `deploy/docker-compose.yml` + `deploy/.env`; project **`mkt`** |
| Apps | **portal×2 + admin×1** via nginx (required dual-app OutboxRelay / D-11) |
| Base | host `http://127.0.0.1:18080` → nginx |
| MySQL / Redis | isolated `mkt` volumes; MySQL `max_connections` 151; app limits portal/admin 2.0 CPU / 1536M |
| OutboxRelay (DEC-002 closed) | `DEFAULT_BATCH_SIZE=100`; `fixedDelay=5000`; `lock-hold-budget=2s`; producer filter `admin|portal` |

## Commands (summary)

```bash
# After merge #153
git fetch && git checkout master && git pull --ff-only
# master tip f147abddaaf3558631b97c648bc39b208bb4b257

COMPOSE=(docker compose --env-file deploy/.env -f deploy/docker-compose.yml -p mkt)
"${COMPOSE[@]}" ps   # portal×2 + admin + mysql + redis + nginx healthy

# Sampler (PENDING/DEAD by producer + oldest age vs UTC_TIMESTAMP)
# → /workspace/r6-capacity-logs/outbox/sample_outbox.sh  (box-local)

# --- Controlled recovery ---
"${COMPOSE[@]}" stop portal-app-1 portal-app-2 admin-app
# INSERT 10000 PENDING portal rows (task.step.complete → EVT_EVENT_LOG only)
# INSERT…WITH RECURSIVE seq … (cte_max_recursion_depth=100000)
"${COMPOSE[@]}" start portal-app-1 portal-app-2 admin-app
# poll PENDING until 0

# --- Steady write load (controlled append; relay running) ---
# Phase A: BATCH=50 / 1s ≈ 50/s × 60s  (below measured drain capacity)
# Phase B: BATCH=200 / 1s ≈ 200/s × 60s (above capacity — climb, not plateau)
```

Host logs / CSVs (box-local, **not** committed): `/workspace/r6-capacity-logs/outbox/` (`samples2.csv`, `samples_a50.csv`, `samples_b200.csv`, `drain2.log`, …).

## 1) Controlled recovery / drain

| Step | PENDING (portal) | oldest_age_s | Note |
|---|---|---|---|
| Inject (apps stopped) | **10000** | 0 | synthetic `task.step.complete` / producer=`portal` |
| Apps healthy (resume) | **9900** | 30 | ~100 drained during startup health window |
| t+0s | 9900 | 30 | |
| t+47s | 6000 | 77 | |
| t+104s | 1500 | 134 | |
| t+119s | **0** | 0 | **DRAIN_OK** |
| Residual DEAD | **0** | — | no DEAD for portal/admin |

| Metric | Value |
|---|---|
| Inject N | 10000 |
| Time-to-drain after apps healthy | **119 s** |
| Effective drain rate | **≈83.2 rows/s** (9900/119) |
| Max oldest lag during this drain | **144 s** (FIFO; last rows) |
| Infra | stack remained healthy; fail-stop not triggered |

**Verdict (recovery):** PASS vs candidate gate below (≤180 s for 10k).

## 2) Steady PENDING + oldest lag (controlled write load)

Advance@300 was attempted first but **not** used for Outbox steady numbers: stale seed → 100% business HTTP fail, and progress-heavy advance rarely emits `sys_outbox` (step-complete only). Per architect wording, used **controlled write load that appends outbox** instead (same consumer path: `task.step.complete` → `EvtEventLogWriter`).

### Phase A — keep-up ≈50 append/s × 60s (relay running)

| Metric | Measured |
|---|---|
| Samples | 46 (≈1 Hz) |
| PENDING min / median / max | **23 / 150 / 800** |
| PENDING last-third avg | **≈231** |
| oldest_age_s min / median / max | **0 / 2 / 19** |
| oldest last-third avg | **≈4.6 s** |
| After inject stop → PENDING=0 | **≈3 s** |
| DEAD | **0** |

**Reading:** under keep-up load below measured drain capacity (~83/s), PENDING stays **low hundreds** with **second-scale** oldest lag (sawtooth from 5s fixedDelay + batch).

### Phase B — overload ≈200 append/s × 60s (document ceiling; not a steady gate)

| Metric | Measured |
|---|---|
| PENDING min / median / max | **200 / 1172 / 4538** |
| PENDING last-third avg | **≈3178** (climbing — **no** plateau in 60s) |
| oldest_age_s max | **28 s** |
| After inject stop → 0 | **≈22 s** (from ~4500) |

**Reading:** produce ≫ ~83/s drain → backlog **accumulates** (expected). Early P0 snapshot PENDING **267110** fits this class (infra saturation / produce≫drain), **not** a keep-up steady gate.

## 3) Proposed DEC-006 Outbox numeric gates (**proposal from this run** — architect pins finals)

| Gate | Proposed number | This-run evidence | vs proposal |
|---|---|---|---|
| Steady PENDING (keep-up; portal+admin; healthy `mkt` dual portal) | **peak ≤ 1000**; last-third avg ≤ 500 | Phase A peak 800 / last-third ≈231 | **PASS** |
| Steady oldest PENDING age (keep-up) | **≤ 30 s** | Phase A max 19 s / median 2 s | **PASS** |
| Recovery drain | **10k** portal PENDING → **0** within **≤ 180 s** after apps healthy; **DEAD residual = 0** | 119 s; DEAD 0 | **PASS** |
| Overload behavior (informational) | No soft fail — backlog may climb if append ≫ ~80/s effective drain | Phase B climb to 4538 in 60s | documented |

**Not proposed as gates:** early P0 snapshot 267110; Phase B peak; FIFO oldest age during a single burst drain (144 s) — use recovery wall-clock instead.

## Explicit non-claims

1. **Not** a capacity checklist tick (either box).
2. **Not** DEC-002 reopen / batch/budget/delay change / MQ.
3. **Not** DEC-006 reopen for list/advance/complete/… threshold **values**.
4. **Not** RC / R6 通过 / `run-full.sh` PASS.
5. **Not** a claim that advance@300 formal was Outbox-green this cut (seed stale; progress path low emit).
6. **Not** cold/hot 分列 package / p1 captcha completeness (still open gaps from checkbox-eval).
7. Host CSVs/logs under `/workspace/r6-capacity-logs/outbox/` stay **box-local**.

## Checklist binding

See [R31-go-live-checklist.md](../R31-go-live-checklist.md) §数据与运行保障 — Outbox backlog/recovery evidence linked; **capacity boxes remain empty**. R6 stays **已开 / 进行中** / **非 RC**.

## Corroboration (same box / concurrent executor pass)

Independent stop→inject→restart pass on the same Compose `mkt` while this evidence was being finalized:

| Item | Value |
|---|---|
| Idle steady (6×5s, no load) | PENDING **0**; oldest **0** |
| Controlled inject (relay stopped) | **20000** portal `task.step.complete` (+6 stray auth.register → peak **20006**); engineered oldest **60 s**; **no drain** while apps stopped (15s hold) |
| Restart + concurrent ad ~300/s | mid-window **9806 → 0** in **~106 s** (≈**92 eps**); DEAD **0** |
| Final idle 30s | PENDING **0** |

Consistent with primary ~83 eps / 10k@119s recovery numbers above. Ad concurrent run had tiny startup http_fail/dropped (not an ad capacity claim). Host log: `/workspace/r6-capacity-logs/outbox-backlog-recovery.log` (box-local).

