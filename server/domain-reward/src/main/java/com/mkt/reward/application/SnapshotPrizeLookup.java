package com.mkt.reward.application;

/** Read-only scan of published snapshots / in-flight instances that reference a prize (R17.7). */
public interface SnapshotPrizeLookup {

    int affectedPublishedTaskCount(long prizeId);

    boolean referencedByAnySnapshot(long prizeId);

    int inFlightInstanceCount(long prizeId);
}
