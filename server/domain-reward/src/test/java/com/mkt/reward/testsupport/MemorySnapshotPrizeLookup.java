package com.mkt.reward.testsupport;

import com.mkt.reward.application.SnapshotPrizeLookup;
import java.util.HashSet;
import java.util.Set;

public final class MemorySnapshotPrizeLookup implements SnapshotPrizeLookup {

    public final Set<Long> referenced = new HashSet<>();
    public int affected;
    public int inFlight;

    @Override
    public int affectedPublishedTaskCount(long prizeId) {
        return referenced.contains(prizeId) ? affected : 0;
    }

    @Override
    public boolean referencedByAnySnapshot(long prizeId) {
        return referenced.contains(prizeId);
    }

    @Override
    public int inFlightInstanceCount(long prizeId) {
        return referenced.contains(prizeId) ? inFlight : 0;
    }
}
