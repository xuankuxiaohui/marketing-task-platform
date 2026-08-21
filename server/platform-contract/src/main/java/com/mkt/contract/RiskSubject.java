package com.mkt.contract;

/** Subject of {@link RiskCheckPort#check} (design §2.2.3). Missing IP skips R-c / R-f. */
public record RiskSubject(Long userId, String ip, String deviceId, Long elapsedSeconds, boolean simulated) {

    public RiskSubject {
        ip = ip == null || ip.isBlank() ? null : ip;
        deviceId = deviceId == null || deviceId.isBlank() ? null : deviceId;
    }

    public RiskSubject(Long userId, String ip, String deviceId, Long elapsedSeconds) {
        this(userId, ip, deviceId, elapsedSeconds, false);
    }
}
