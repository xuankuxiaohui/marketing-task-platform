package com.mkt.task.application;

/** Read-only prize enablement for publish validation (R12.3) via {@link com.mkt.contract.RewardPort}. */
public interface PrizeEnabledLookup {

    boolean enabled(long prizeId);
}
