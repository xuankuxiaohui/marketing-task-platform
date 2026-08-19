package com.mkt.tracking.support;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drop metrics for R28.5 / R28.8. */
public final class TrackDropCounters {

    private static final Logger log = LoggerFactory.getLogger(TrackDropCounters.class);

    private final AtomicLong malformed = new AtomicLong();
    private final AtomicLong unregistered = new AtomicLong();
    private final AtomicLong disabled = new AtomicLong();

    public void addMalformed(int n) {
        if (n > 0) {
            malformed.addAndGet(n);
            log.warn("track drop malformed count={}", n);
        }
    }

    public void addUnregistered(int n) {
        if (n > 0) {
            unregistered.addAndGet(n);
            log.warn("track drop unregistered count={}", n);
        }
    }

    public void addDisabled(int n) {
        if (n > 0) {
            disabled.addAndGet(n);
            log.warn("track drop disabled count={}", n);
        }
    }

    public long malformed() {
        return malformed.get();
    }

    public long unregistered() {
        return unregistered.get();
    }

    public long disabled() {
        return disabled.get();
    }

    public long totalDropped() {
        return malformed.get() + unregistered.get() + disabled.get();
    }
}
