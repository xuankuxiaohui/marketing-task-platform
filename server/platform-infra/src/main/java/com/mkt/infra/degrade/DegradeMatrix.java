package com.mkt.infra.degrade;

import java.util.EnumMap;
import java.util.Map;

/** Closed §6.8 matrix. Session / nonce reject; rate-limit allow. */
public final class DegradeMatrix {

    private static final Map<DegradeComponent, DegradeAction> ACTIONS = new EnumMap<>(DegradeComponent.class);

    static {
        ACTIONS.put(DegradeComponent.SESSION, DegradeAction.REJECT);
        ACTIONS.put(DegradeComponent.RATE_LIMIT, DegradeAction.ALLOW);
        ACTIONS.put(DegradeComponent.CACHE, DegradeAction.L1_OR_DB);
        ACTIONS.put(DegradeComponent.DISTRIBUTED_LOCK, DegradeAction.SKIP_ROUND);
        ACTIONS.put(DegradeComponent.CLAIM_LOCK, DegradeAction.FALLBACK_CAS);
        ACTIONS.put(DegradeComponent.RISK, DegradeAction.POLICY);
        ACTIONS.put(DegradeComponent.OUTBOX_RELAY, DegradeAction.SKIP_ROUND);
        ACTIONS.put(DegradeComponent.NONCE, DegradeAction.REJECT);
        ACTIONS.put(DegradeComponent.TRACKING, DegradeAction.ALLOW);
    }

    private DegradeMatrix() {
    }

    public static DegradeAction action(DegradeComponent component) {
        return ACTIONS.get(component);
    }

    public static boolean rejectOnFailure(DegradeComponent component) {
        return action(component) == DegradeAction.REJECT;
    }

    public static boolean allowOnFailure(DegradeComponent component) {
        return action(component) == DegradeAction.ALLOW;
    }
}
