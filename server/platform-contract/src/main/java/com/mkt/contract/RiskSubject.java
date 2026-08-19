package com.mkt.contract;

/** Subject of {@link RiskCheckPort#check} (design §2.2.3). */
public record RiskSubject(Long userId, String ip, String deviceId, Long elapsedSeconds) {

    public RiskSubject {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("ip is required");
        }
    }
}
