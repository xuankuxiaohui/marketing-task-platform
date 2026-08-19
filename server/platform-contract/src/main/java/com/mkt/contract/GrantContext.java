package com.mkt.contract;

import java.util.List;

/**
 * Grant extras. {@code simulated} defaults to false (design §2.2.3).
 */
public record GrantContext(
        String reason,
        List<BypassRule> bypassRules,
        Long operatorId,
        boolean simulated,
        Long elapsedSeconds) {

    public GrantContext {
        bypassRules = bypassRules == null ? List.of() : List.copyOf(bypassRules);
    }

    public static GrantContext defaults() {
        return new GrantContext(null, List.of(), null, false, null);
    }
}
