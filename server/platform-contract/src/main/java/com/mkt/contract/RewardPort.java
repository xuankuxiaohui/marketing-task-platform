package com.mkt.contract;

/** Cross-domain port provided by domain-reward (design §2.2.3 / §3.11.1). */
public interface RewardPort {

    GrantResult grant(long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx);

    UserRewardSummary userSummary(long userId);

    /** D-13 read-only: whether {@code rwd_prize} is ENABLED and not deleted. */
    boolean prizeEnabled(long prizeId);

    /**
     * Same-transaction points debit for P1 catchup (design §3.11.1). Returns balance after.
     * {@code points == 0} is a no-op.
     */
    long consume(long userId, int points, String sourceType, String sourceId, String remark);
}
