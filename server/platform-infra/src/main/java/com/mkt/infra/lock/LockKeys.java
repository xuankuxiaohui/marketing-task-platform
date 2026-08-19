package com.mkt.infra.lock;

/** Closed lock key family (design §3.10). */
public final class LockKeys {

    private LockKeys() {
    }

    public static String lock(String scope) {
        return "lock:" + scope;
    }

    public static String sched(String name) {
        return "sched:" + name;
    }

    public static String rwdClaim(long recordId) {
        return "lock:rwd-claim:" + recordId;
    }

    public static String outboxRelay(String app) {
        return "outbox:relay:" + app;
    }
}
