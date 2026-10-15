package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.reward.command.PointsAdjustCommand;
import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.query.PointsAccountQuery;
import com.mkt.reward.query.PointsTransactionQuery;
import com.mkt.reward.response.PointsAccountView;
import com.mkt.reward.response.PointsBalanceResponse;
import com.mkt.reward.response.PointsPortalTxView;
import com.mkt.reward.response.PointsTransactionView;
import com.mkt.reward.support.PointsErrorCodes;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Points ledger (design §5.8 / R20). */
@Service
public class PointsAppService {

    private final PointsStore store;
    private final PointsExpire pointsExpire;
    private final PointsApply pointsApply;
    private final PointsReads pointsReads;

    @Autowired
    public PointsAppService(
            PointsStore store, PointsExpire pointsExpire, PointsApply pointsApply, PointsReads pointsReads) {
        this.store = store;
        this.pointsExpire = pointsExpire;
        this.pointsApply = pointsApply;
        this.pointsReads = pointsReads;
    }

    public PointsAppService(PointsStore store, PlatformTransactionManager txm, Clock clock) {
        PointsApply apply = new PointsApply(store, clock);
        this.store = store;
        this.pointsExpire = new PointsExpire(store, txm, clock, apply);
        this.pointsApply = apply;
        this.pointsReads = new PointsReads(store);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long earn(long userId, int points, Instant expireAt, String sourceType, String sourceId) {
        return earn(userId, points, expireAt, sourceType, sourceId, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long earn(
            long userId, int points, Instant expireAt, String sourceType, String sourceId, boolean simulated) {
        if (points <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        store.insertIgnoreAccount(userId);
        return pointsApply.apply(
                userId, PointTypes.EARN, points, sourceType, sourceId, expireAt, null, simulated ? 1 : 0, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long consume(long userId, int points, String sourceType, String sourceId, String remark) {
        if (points <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return pointsApply.apply(
                userId, PointTypes.CONSUME, -points, sourceType, sourceId, null, remark, 0, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long reverse(long userId, int points, String sourceType, String sourceId, String remark) {
        return reverse(userId, points, sourceType, sourceId, remark, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long reverse(
            long userId, int points, String sourceType, String sourceId, String remark, boolean simulated) {
        if (points <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return pointsApply.apply(
                userId, PointTypes.REVERSAL, -points, sourceType, sourceId, null, remark, simulated ? 1 : 0, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsBalanceResponse adjust(PointsAdjustCommand command) {
        if (command.userId() == null || command.amount() == null || command.amount() == 0L) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException(PointsErrorCodes.REASON_REQUIRED);
        }
        long amount = command.amount();
        boolean credit = amount > 0;
        if (credit) {
            store.insertIgnoreAccount(command.userId());
        }
        long after = pointsApply.apply(
                command.userId(),
                PointTypes.ADJUST,
                amount,
                null,
                null,
                null,
                command.reason().trim(),
                0,
                credit);
        return new PointsBalanceResponse(after);
    }

    /** Lazy-create on C-end / admin balance read (R20.1). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PointsBalanceResponse balance(long userId) {
        store.insertIgnoreAccount(userId);
        return new PointsBalanceResponse(balanceOrZero(userId));
    }

    /** D-13: missing account is 0, no insert. */
    public long balanceOrZero(long userId) {
        PntAccountEntity account = store.getByUserId(userId);
        return account == null || account.getBalance() == null ? 0L : account.getBalance();
    }

    /**
     * Scheduler path: one REQUIRES_NEW transaction per earn row (DEC-004).
     * Not the earn/grant path — does not change grant atomicity.
     */
    public int expireDue() {
        return pointsExpire.expireDue();
    }

    public PageData<PointsAccountView> pageAccounts(PointsAccountQuery query) {
        return pointsReads.pageAccounts(query);
    }

    public PageData<PointsTransactionView> pageTransactions(PointsTransactionQuery query) {
        return pointsReads.pageTransactions(query);
    }

    public PageData<PointsPortalTxView> pagePortal(long userId, String type, Integer page, Integer pageSize) {
        return pointsReads.pagePortal(userId, type, page, pageSize);
    }
}
