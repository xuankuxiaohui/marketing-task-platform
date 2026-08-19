package com.mkt.contract;

/** Prize counts for {@link UserRewardSummary} (design §2.2.3). */
public record PrizeSummary(long won, long granted) {
}
