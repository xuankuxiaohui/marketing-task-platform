package com.mkt.ad.domain;

/** Redis freq / popup keys (design §3.11.5). */
public final class AdFreqKeys {

    private AdFreqKeys() {}

    public static String daily(Long userId, String deviceId, long materialId, String dayKey) {
        if (userId != null) {
            return "ad:freq:" + userId + ":" + materialId + ":" + dayKey;
        }
        return "ad:freq:dev:" + subject(deviceId) + ":" + materialId + ":" + dayKey;
    }

    public static String popup(Long userId, String deviceId) {
        String subject = userId != null ? String.valueOf(userId) : subject(deviceId);
        return "ad:popup:cd:" + subject;
    }

    public static boolean hasSubject(Long userId, String deviceId) {
        return userId != null || (deviceId != null && !deviceId.isBlank());
    }

    private static String subject(String deviceId) {
        return deviceId == null || deviceId.isBlank() ? "missing" : deviceId.trim();
    }
}
