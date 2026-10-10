package com.mkt.admin.arch.fixture.clock.illegal;

import java.time.LocalDate;

/** Deliberate AT-C01 violation: wall-clock LocalDate.now() bypasses injected Clock. */
public final class CallsLocalDateNow {

    public LocalDate today() {
        return LocalDate.now();
    }
}
