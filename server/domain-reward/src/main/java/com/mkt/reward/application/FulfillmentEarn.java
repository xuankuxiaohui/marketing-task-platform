package com.mkt.reward.application;

import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.domain.TypeParams;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.points.PointsPort;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fulfillment earn helpers extracted from {@link FulfillmentService} (R13-6).
 * Move-only: earnPoints semantics unchanged.
 */
@Component
public class FulfillmentEarn {

    private final PointsPort points;

    @Autowired
    public FulfillmentEarn(ObjectProvider<PointsPort> points) {
        this(points.getIfAvailable());
    }

    public FulfillmentEarn(PointsPort points) {
        this.points = points;
    }

    void earnPoints(GrantRecordEntity record, PrizeEntity prize, Instant now) {
        if (points == null) {
            return;
        }
        Integer amount = TypeParams.positiveInt(RewardJson.map(prize.getTypeParams()), TypeParams.POINTS);
        if (amount == null) {
            throw new RetryableGrantException(RetryableGrantReason.SYSTEM_ERROR);
        }
        Instant expireAt = prize.getExpireHours() == null
                ? null
                : now.plus(Duration.ofHours(prize.getExpireHours()));
        try {
            points.earn(
                    record.getUserId(),
                    amount,
                    expireAt,
                    record.getGrantSource(),
                    record.getSourceId(),
                    record.getSimulated() != null && record.getSimulated() == 1);
        } catch (RuntimeException ex) {
            throw new RetryableGrantException(RetryableGrantReason.SYSTEM_ERROR);
        }
    }
}
