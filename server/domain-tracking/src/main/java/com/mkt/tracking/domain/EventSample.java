package com.mkt.tracking.domain;

/**
 * Deterministic debug-query sampling: {@code id % 100 < ratio} (R29.3). Ratio 100 includes all
 * rows. Matches SQL {@code MOD(id, 100) < ratio}.
 */
public final class EventSample {

    private EventSample() {}

    public static boolean include(long id, int ratioPercent) {
        int ratio = Math.min(100, Math.max(0, ratioPercent));
        if (ratio >= 100) {
            return true;
        }
        if (ratio < 1) {
            return false;
        }
        return Math.floorMod(id, 100) < ratio;
    }
}
