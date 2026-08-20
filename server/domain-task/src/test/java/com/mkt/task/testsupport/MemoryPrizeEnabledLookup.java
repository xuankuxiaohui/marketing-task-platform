package com.mkt.task.testsupport;

import com.mkt.task.application.PrizeEnabledLookup;
import java.util.HashSet;
import java.util.Set;

public final class MemoryPrizeEnabledLookup implements PrizeEnabledLookup {

    public final Set<Long> enabled = new HashSet<>();
    public boolean allowAll = true;

    @Override
    public boolean enabled(long prizeId) {
        return allowAll || enabled.contains(prizeId);
    }
}
