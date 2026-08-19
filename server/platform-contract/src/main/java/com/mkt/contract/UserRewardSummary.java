package com.mkt.contract;

/** Read-only reward facade (D-13). */
public record UserRewardSummary(long pointsBalance, PrizeSummary prizeSummary) {
}
