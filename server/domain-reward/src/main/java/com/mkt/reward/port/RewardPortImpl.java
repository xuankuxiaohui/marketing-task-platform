package com.mkt.reward.port;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserRewardSummary;
import com.mkt.reward.application.PrizeStore;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.PrizeEntity;

/** RewardPort: prizeEnabled in task 32; grant/userSummary in 33/35. */
public class RewardPortImpl implements RewardPort {

    private final PrizeStore prizes;

    public RewardPortImpl(PrizeStore prizes) {
        this.prizes = prizes;
    }

    @Override
    public GrantResult grant(long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx) {
        throw new UnsupportedOperationException("RewardPort.grant is task 33");
    }

    @Override
    public UserRewardSummary userSummary(long userId) {
        return new UserRewardSummary(0L, new PrizeSummary(0L, 0L));
    }

    @Override
    public boolean prizeEnabled(long prizeId) {
        PrizeEntity prize = prizes.getById(prizeId);
        return prize != null && PrizeStatuses.ENABLED.equals(prize.getStatus()) && !prize.deletedFlag();
    }
}
