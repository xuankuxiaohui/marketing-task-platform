package com.mkt.tracking.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Drop metrics for R28.5 / R28.8. Exposes Micrometer {@code mkt.track.drop} (Prometheus {@code mkt_track_drop_total}). */
public final class TrackDropCounters {

    private static final Logger log = LoggerFactory.getLogger(TrackDropCounters.class);

    static final String METRIC_NAME = "mkt.track.drop";
    static final String TAG_REASON = "reason";
    static final String REASON_MALFORMED = "malformed";
    static final String REASON_UNREGISTERED = "unregistered";
    static final String REASON_DISABLED = "disabled";

    private final AtomicLong malformed = new AtomicLong();
    private final AtomicLong unregistered = new AtomicLong();
    private final AtomicLong disabled = new AtomicLong();

    private final Counter malformedCounter;
    private final Counter unregisteredCounter;
    private final Counter disabledCounter;

    public TrackDropCounters(MeterRegistry registry) {
        this.malformedCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_REASON, REASON_MALFORMED)
                .register(registry);
        this.unregisteredCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_REASON, REASON_UNREGISTERED)
                .register(registry);
        this.disabledCounter = Counter.builder(METRIC_NAME)
                .tag(TAG_REASON, REASON_DISABLED)
                .register(registry);
    }

    public void addMalformed(int n) {
        if (n > 0) {
            malformed.addAndGet(n);
            malformedCounter.increment(n);
            log.warn("track drop malformed count={}", n);
        }
    }

    public void addUnregistered(int n) {
        if (n > 0) {
            unregistered.addAndGet(n);
            unregisteredCounter.increment(n);
            log.warn("track drop unregistered count={}", n);
        }
    }

    public void addDisabled(int n) {
        if (n > 0) {
            disabled.addAndGet(n);
            disabledCounter.increment(n);
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
