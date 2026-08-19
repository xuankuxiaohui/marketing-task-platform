package com.mkt.tracking.domain;

public final class MetadataStatuses {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";

    private MetadataStatuses() {}

    public static boolean valid(String status) {
        return ENABLED.equals(status) || DISABLED.equals(status);
    }

    public static MetadataStatus of(String status) {
        if (status == null) {
            return MetadataStatus.MISSING;
        }
        if (DISABLED.equals(status)) {
            return MetadataStatus.DISABLED;
        }
        return MetadataStatus.ENABLED;
    }
}
