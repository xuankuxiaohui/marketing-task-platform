package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.entity.PntTransactionEntity;
import com.mkt.reward.support.PointsErrorCodes;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Points ledger apply extracted from {@link PointsAppService} (R18-2).
 * Move-only: apply / newRow semantics unchanged.
 * Transactions stay on the service methods that call {@code apply}.
 */
@Component
public class PointsApply {

    private final PointsStore store;
    private final Clock clock;

    @Autowired
    public PointsApply(PointsStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    long apply(
            long userId,
            String type,
            long amount,
            String bizSource,
            String bizId,
            Instant expireAt,
            String remark,
            int simulated,
            boolean created) {
        int affected = store.applyDelta(userId, amount);
        if (affected != 1) {
            PntAccountEntity existing = store.getByUserId(userId);
            if (existing == null) {
                throw new BusinessException(PointsErrorCodes.ACCOUNT_NOT_FOUND);
            }
            throw new BusinessException(PointsErrorCodes.INSUFFICIENT_BALANCE);
        }
        PntAccountEntity account = store.getByUserId(userId);
        long after = account == null || account.getBalance() == null ? 0L : account.getBalance();
        PntTransactionEntity row = newRow(
                userId, type, amount, after, bizSource, bizId, expireAt, remark, simulated);
        store.insertTx(row);
        if (PointTypes.EARN.equals(type) && created && row.getId() != null) {
            return row.getId();
        }
        return after;
    }

    PntTransactionEntity newRow(
            long userId,
            String type,
            long amount,
            long balanceAfter,
            String bizSource,
            String bizId,
            Instant expireAt,
            String remark,
            int simulated) {
        PntTransactionEntity row = new PntTransactionEntity();
        row.setUserId(userId);
        row.setType(type);
        row.setAmount(amount);
        row.setBalanceAfter(balanceAfter);
        row.setBizSource(bizSource);
        row.setBizId(bizId);
        row.setExpireAt(RewardTime.toUtc(expireAt));
        row.setRemark(remark);
        row.setSimulated(simulated);
        row.setCreatedAt(RewardTime.toUtc(clock.instant()));
        return row;
    }
}
