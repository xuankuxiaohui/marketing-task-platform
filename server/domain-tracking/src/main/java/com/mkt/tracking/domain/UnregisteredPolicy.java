package com.mkt.tracking.domain;

/** Appendix A {@code track.unregistered-policy}. */
public enum UnregisteredPolicy {
    ACCEPT,
    REJECT;

    public static UnregisteredPolicy fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return ACCEPT;
        }
        return "reject".equalsIgnoreCase(raw.trim()) ? REJECT : ACCEPT;
    }
}
