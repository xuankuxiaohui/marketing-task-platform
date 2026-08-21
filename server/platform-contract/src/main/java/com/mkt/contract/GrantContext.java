package com.mkt.contract;

import java.util.List;

/**
 * Grant extras. {@code simulated} defaults to false (design §2.2.3).
 * {@code ip}/{@code deviceId} are required by R26.1 R-f / R-d (demand over closed field list).
 */
public record GrantContext(
        String reason,
        List<BypassRule> bypassRules,
        Long operatorId,
        boolean simulated,
        Long elapsedSeconds,
        String ip,
        String deviceId) {

    public GrantContext {
        bypassRules = bypassRules == null ? List.of() : List.copyOf(bypassRules);
    }

    public GrantContext(
            String reason, List<BypassRule> bypassRules, Long operatorId, boolean simulated, Long elapsedSeconds) {
        this(reason, bypassRules, operatorId, simulated, elapsedSeconds, null, null);
    }

    public static GrantContext defaults() {
        return new GrantContext(null, List.of(), null, false, null, null, null);
    }

    public GrantContext withClient(String clientIp, String clientDeviceId) {
        return new GrantContext(reason, bypassRules, operatorId, simulated, elapsedSeconds, clientIp, clientDeviceId);
    }
}
