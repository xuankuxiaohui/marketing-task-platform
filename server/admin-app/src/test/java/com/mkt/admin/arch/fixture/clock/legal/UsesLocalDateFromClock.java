package com.mkt.admin.arch.fixture.clock.legal;

import java.time.Clock;
import java.time.LocalDate;

/** Legal: calendar date from injected Clock. */
public final class UsesLocalDateFromClock {

    public LocalDate today(Clock clock) {
        return LocalDate.now(clock);
    }
}
