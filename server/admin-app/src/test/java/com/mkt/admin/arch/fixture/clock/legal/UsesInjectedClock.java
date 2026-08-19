package com.mkt.admin.arch.fixture.clock.legal;

import java.time.Clock;
import java.time.Instant;

/** Legal: time comes from injected Clock. */
public final class UsesInjectedClock {

    public Instant now(Clock clock) {
        return Instant.now(clock);
    }
}
