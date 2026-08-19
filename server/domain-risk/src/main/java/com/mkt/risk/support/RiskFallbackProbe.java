package com.mkt.risk.support;

import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fallback / hit-write failure counters (R26.3 / §5.9). Not an Outbox event — D-05 has no fallback code. */
@Component
public final class RiskFallbackProbe {

    private static final Logger log = LoggerFactory.getLogger(RiskFallbackProbe.class);

    private final AtomicLong fallbacks = new AtomicLong();
    private final AtomicLong hitWriteFailures = new AtomicLong();

    public void onFallback(RiskFallbackPolicy policy, Exception cause) {
        fallbacks.incrementAndGet();
        log.warn("risk fallback policy={} cause={}", policy, cause.toString());
    }

    public void onHitWriteFailure(Exception cause) {
        hitWriteFailures.incrementAndGet();
        log.warn("risk hit persist failed; business continues. cause={}", cause.toString());
    }

    public long fallbackCount() {
        return fallbacks.get();
    }

    public long hitWriteFailureCount() {
        return hitWriteFailures.get();
    }
}
