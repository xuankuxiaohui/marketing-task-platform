package com.mkt.identity.application;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserRewardSummary;

/** Placeholder when domain-reward is not on the classpath. */
public class RewardPortStub implements RewardPort {

    @Override
    public GrantResult grant(long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx) {
        throw new UnsupportedOperationException("RewardPort is not assembled");
    }

    @Override
    public UserRewardSummary userSummary(long userId) {
        return new UserRewardSummary(0L, new PrizeSummary(0L, 0L));
    }

    @Override
    public boolean prizeEnabled(long prizeId) {
        return false;
    }
}
