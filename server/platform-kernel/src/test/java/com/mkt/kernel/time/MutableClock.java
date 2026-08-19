package com.mkt.kernel.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Test clock with {@link #setInstant(Instant)} (design D-03). Published via kernel test-jar. */
public final class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant instant) {
        this(instant, ZoneOffset.UTC);
    }

    public MutableClock(Instant instant, ZoneId zone) {
        this(new AtomicReference<>(Objects.requireNonNull(instant, "instant")), Objects.requireNonNull(zone, "zone"));
    }

    private MutableClock(AtomicReference<Instant> instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.instant.set(Objects.requireNonNull(instant, "instant"));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, Objects.requireNonNull(zone, "zone"));
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
