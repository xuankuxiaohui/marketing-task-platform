# list alloc/response hygiene formal

Date: 2026-10-12 ~05:43–05:49 CST
Base master: 35eae57 (#163 merged)
Product: top-K PendingCard + VisibilityEvaluator.visible + ALLOW singleton +
compileCached + rewardPreview cache + cycle-key memo. Skip-renew NOT landed.
Caps locked: Redisson 32/1s/1, Tomcat120, portal 1536M, Redis 0.5CPU/256M.
Verdict: FAIL (Java heap space; portal2 Restarts=3; Redis idle).
