package com.mkt.admin.arch.fixture.clock.illegal;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Deliberate AT-C01 violation: LocalDateTime.now(ZoneId) is not covered by no-arg-only rules. */
public final class CallsLocalDateTimeNowZoneId {

    public LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
