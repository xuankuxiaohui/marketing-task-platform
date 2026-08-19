package com.mkt.admin.arch.fixture.clock.illegal;

import java.time.Instant;

/** Deliberate AT-C01 violation. */
public final class CallsInstantNow {

    public Instant now() {
        return Instant.now();
    }
}
