package com.mkt.contract;

/** Task instance counters for R5.6 (D-13). */
public record InstanceCounts(long inProgressInstanceCount, long historyInstanceCount) {
}
