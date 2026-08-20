package com.mkt.task.application;

/** Read-only prize enablement for publish validation (R12.3). Does not import reward types. */
public interface PrizeEnabledLookup {

    boolean enabled(long prizeId);
}
