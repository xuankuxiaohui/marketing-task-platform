package com.mkt.reward.port;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserRewardSummary;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.GrantRecordStore;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.application.PrizeStore;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.PrizeEntity;

/** RewardPort: grant in task 33; userSummary in task 35 (D-13). */
public class RewardPortImpl implements RewardPort {

    private final GrantAppService grants;
    private final PrizeStore prizes;
    private final PointsAppService points;
    private final GrantRecordStore records;

    public RewardPortImpl(GrantAppService grants, PrizeStore prizes) {
        this(grants, prizes, null, null);
    }

    public RewardPortImpl(
            GrantAppService grants, PrizeStore prizes, PointsAppService points, GrantRecordStore records) {
        this.grants = grants;
        this.prizes = prizes;
        this.points = points;
        this.records = records;
    }

    @Override
    public GrantResult grant(long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx) {
        return grants.grant(prizeId, userId, grantSource, sourceId, ctx);
    }

    @Override
    public UserRewardSummary userSummary(long userId) {
        long balance = points == null ? 0L : points.balanceOrZero(userId);
        long won = records == null ? 0L : records.countByUserStatus(userId, GrantRecordStatuses.WON);
        long granted = records == null ? 0L : records.countByUserStatus(userId, GrantRecordStatuses.GRANTED);
        return new UserRewardSummary(balance, new PrizeSummary(won, granted));
    }

    @Override
    public boolean prizeEnabled(long prizeId) {
        PrizeEntity prize = prizes.getById(prizeId);
        return prize != null && PrizeStatuses.ENABLED.equals(prize.getStatus()) && !prize.deletedFlag();
    }
}
