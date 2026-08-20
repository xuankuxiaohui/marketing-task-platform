package com.mkt.contract;

/** Cross-domain port provided by domain-reward (design §2.2.3). */
public interface RewardPort {

    GrantResult grant(long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx);

    UserRewardSummary userSummary(long userId);

    /** D-13 read-only: whether {@code rwd_prize} is ENABLED and not deleted. */
    boolean prizeEnabled(long prizeId);
}
