# Capacity evidence: settle → isolated advance after #186 — FAIL (勾空; 非 RC) (2026-10-12)

**Result:** After merge **#186** (`091d367`, tip `e9f8ced`) on same throwaway `mkt` (**no reseed**): true-settle attempt → isolated `advance.js` warm→formal **5m FAIL** (dropped + callback P95). Capacity勾空; 非 RC. Do **not** tick. Opens **progress 产品刀** evidence track (`/internal/task/progress` hotspots). Do **not** raise thresholds / Redisson / Tomcat200–250.

## Context
- Master merge: **`091d367`** = Merge #186 (skip-renew + instances TTL 5s)
- Prior run-full under tip `21fc981`: FAIL-STOP at advance (progress P95≈218.11ms>200; dropped≈0.0627/s)
- Architect next: 同 mkt 沉降后孤立 advance — PASS续run-full；仍FAIL再开 progress 产品刀（先取证 `/internal/task/progress` 热点）
- Isolation: compose `mkt`; **no** `seed.py --scale`; reused `perf/state.json` (p1)
- Caps locked: Tomcat120; portal 1536M **not raised**; Redisson ≤32/1s/1; Δ≤20 **not lowered**; daily limit **not raised**; **禁 revert #178**; **禁抬阈值 / Redisson加码 / Tomcat200–250**

## Isolated advance formal 5m — FAIL

Settle: 36×20s poll → **SETTLE_TIMEOUT proceeding** (MySQL oscillated ~0–100% from admin `mtr_*` JSON aggs + outbox/`evt_event_log` load). Portal1 stayed &lt;15% most samples. Proceeded per runbook.

| metric | run-full advance (under #186 tip) | **isolated settle advance** | gate |
|---|---|---|---|
| result | FAIL | **FAIL** | — |
| progress P95 `http_req_duration{name:/internal/task/progress,scenario:advance}` | ≈**218.11 ms** ✗ | ≈**101.23 ms** ✓ | ≤200 |
| callback P95 `…/callback…` | ≈176.85 ms ✓ | ≈**307.81 ms** ✗ | ≤200 |
| `dropped_iterations{scenario:advance}` | ≈0.0627/s (21) ✗ / overall 0.122/s (41) | ≈**0.1134/s (38)** ✗ | rate&lt;0.05 |
| checks | 100% | **100%** | — |
| http_fail | 0% | **0%** | — |
| progress_advance_ok | — | 89830 | count&gt;0 ✓ |
| callback_real_advance | — | 132 | count&gt;0 ✓ |

k6 error: `thresholds on metrics 'dropped_iterations{scenario:advance}, http_req_duration{name:/internal/task/callback,scenario:advance}' have been crossed`

Host logs: `/workspace/r6-capacity-logs/advance-settle-after-186/` (`advance-formal-5m.log`, `advance-settle-after-186.summary.json`, `verdict.txt`, `settle-poll.txt`).

**Note:** Isolated cool-ish run **cleared progress P95** (101≤200) vs run-full stack (218), but **callback P95 + dropped** still FAIL. Residual remains advance-family; architect-directed next knife stays **progress** hotspot first.

## `/internal/task/progress` hotspot evidence (先取证)

Path: `TaskInternalController.progress` → `TaskStepAppService.progress` → `StepEngine.progress`.

Observed per-request / co-load hotspots (code + live processlist/logs during settle+advance):

1. **`instances.getByIdForUpdate(instanceId)`** — every progress takes instance row lock (`TaskStepAppService.locate` / progress entry). Concurrent progress on overlapping pools serializes on InnoDB row locks.
2. **Per call loads:** `snapshotOf` + `attrs(userId)` + `crowds(userId)` + `rejectIfFrozen` before engine (see `progress-app-hotspots.txt`).
3. **`StepEngine.progress`:** idempotent report insert (`task_progress_report`) + CAS progress + event/outbox publish under same TX window (snippet in `stepengine-progress-snippet.txt`).
4. **Co-load during formal:** admin metrics `INSERT INTO mtr_task_funnel_d … JSON_TABLE(evt_event_log)` and continuous `INSERT INTO evt_event_log` held MySQL near 100% through settle; during advance portal logs showed **OutboxRelay Lock wait timeout** on `evt_event_log` insert (`outbox-lock-waits-count.txt`: 6+6 across portal1/2 in 30m window).
5. **Processlist samples during settle/advance:** `risk_hit_log` COUNT; `task_instance` / step SELECTs; `evt_event_log` INSERT updates — compete with progress FOR UPDATE.

**Not applied (banned):** threshold raise; Redisson bump; Tomcat 200–250; 1536M raise; Δ lower; daily-limit raise; revert #178.

## Disposition
- **勾空; 非 RC.** Do **not** tick either capacity box.
- Isolated advance **FAIL** → **progress 产品刀** track opened with hotspot evidence above; **ask architect which product cut** (do not invent scope beyond evidence). Candidate themes from evidence: shorten FOR UPDATE hold / reduce attrs+crowds+snapshot churn / isolate evt/outbox contention with progress — **architect picks**.
- **run-full-noseed not continued** (FAIL branch).
- Caps unchanged.

## Artifacts
- `/workspace/r6-capacity-logs/advance-settle-after-186/`
- Merge SHA noted: `091d367` (`merge-sha.txt`)
