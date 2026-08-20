package com.mkt.reward.support;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/** Unique sourceId for MANUAL_GRANT (design §4.5). */
public final class GrantSourceIds {

    private static final AtomicLong SEQ = new AtomicLong();

    private GrantSourceIds() {}

    public static String nextManual(Clock clock) {
        long ts = clock.instant().toEpochMilli();
        long n = SEQ.incrementAndGet();
        return ts + "-" + n;
    }
}
