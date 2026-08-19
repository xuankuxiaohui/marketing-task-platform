package com.mkt.tracking.domain;

/** Appendix A {@code track.disabled-event-policy}. */
public enum DisabledEventPolicy {
    DROP_COUNT,
    KEEP;

    public static DisabledEventPolicy fromConfig(String raw) {
        if (raw == null || raw.isBlank()) {
            return DROP_COUNT;
        }
        return "keep".equalsIgnoreCase(raw.trim()) ? KEEP : DROP_COUNT;
    }
}
