# Capacity: admin-list confirm after #178 merge (2026-10-12)

**Status:** post-merge formal admin-list 5m **PASS** (勾空; 非 RC).  
**Merge tip:** `5695191` (Merge #178 ← tip `24e799a`).  
**Locks:** Tomcat120; Redisson≤32/1s/1; portal **1536M not raised**; **禁降 Δ 门禁**.

## Setup
- Same throwaway Compose `mkt`; reused `perf/state.json`; **no** scale reseed.
- After merge: `mvn -f server/pom.xml -pl admin-app -am package -DskipTests`; `docker compose … build/up --force-recreate admin-app`; settle → healthy; warm 30s → formal 5m.
- Caps confirmed: `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xss512k`; admin/portal memcg **1536M**.

## Formal admin-list 5m (**PASS**)

| Metric | Pre-merge relief (#178 tip) | Post-merge confirm | Gate |
|---|---|---|---|
| admin-instances P95 | 118.07 ms | **116.62 ms** | ≤800 |
| admin-definitions P95 | 92.59 ms | **92.55 ms** | ≤800 |
| admin-grants P95 | 208.37 ms | **283.51 ms** | ≤800 |
| checks | 100% | **100%** (75464) | — |
| http_fail | 0% | **0%** (0/37732) | ==0 |
| Result | PASS | **PASS** | — |

Warm 30s instances P95≈111.34 ms. Wall (CST): warm 12:52:21–12:52:53; formal 12:52:54–12:57:56.

Logs: `/workspace/r6-capacity-logs/admin-list-confirm-after-178/`.

## Non-claims
1. **Not** capacity checklist tick (勾仍空; 非 RC).
2. **Not** DEC-006 threshold value change / Δ gate lower / 1536M raise.
3. **Not** full run-full retest (targeted admin-list / instances stage only).
