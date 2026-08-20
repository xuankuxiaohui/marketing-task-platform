package com.mkt.task.testsupport;

import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.PermanentGrantReason;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserRewardSummary;
import java.util.ArrayList;
import java.util.List;

/** RewardPort stand-in for task 29; real grant is task 40. */
public final class MemoryRewardPort implements RewardPort {

    public enum Behavior {
        GRANTED,
        WON,
        RETRYABLE,
        PERMANENT,
        RETRY_PENDING_STATUS
    }

    public Behavior behavior = Behavior.GRANTED;
    public final List<GrantCall> calls = new ArrayList<>();

    public record GrantCall(long prizeId, long userId, GrantSource source, String sourceId, GrantContext ctx) {}

    @Override
    public GrantResult grant(long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx) {
        calls.add(new GrantCall(prizeId, userId, grantSource, sourceId, ctx));
        return switch (behavior) {
            case GRANTED -> new GrantResult(1L, GrantStatus.GRANTED, FulfillmentStatus.ARRIVED, prizeId, false);
            case WON -> new GrantResult(2L, GrantStatus.WON, FulfillmentStatus.NONE, prizeId, false);
            case RETRYABLE -> throw new RetryableGrantException(RetryableGrantReason.STOCK_INSUFFICIENT);
            case PERMANENT -> throw new PermanentGrantException(PermanentGrantReason.PRIZE_DISABLED);
            case RETRY_PENDING_STATUS ->
                new GrantResult(3L, GrantStatus.RETRY_PENDING, FulfillmentStatus.NONE, prizeId, false);
        };
    }

    @Override
    public UserRewardSummary userSummary(long userId) {
        return new UserRewardSummary(0L, new PrizeSummary(0, 0));
    }

    @Override
    public boolean prizeEnabled(long prizeId) {
        return true;
    }

    @Override
    public long consume(long userId, int points, String sourceType, String sourceId, String remark) {
        return 0L;
    }
}
