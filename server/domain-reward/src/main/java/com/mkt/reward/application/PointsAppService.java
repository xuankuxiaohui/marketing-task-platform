package com.mkt.reward.application;

import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.reward.command.PointsAdjustCommand;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.entity.PntTransactionEntity;
import com.mkt.reward.query.PointsAccountQuery;
import com.mkt.reward.query.PointsTransactionQuery;
import com.mkt.reward.response.PointsAccountView;
import com.mkt.reward.response.PointsBalanceResponse;
import com.mkt.reward.response.PointsPortalTxView;
import com.mkt.reward.response.PointsTransactionView;
import com.mkt.reward.support.PointsErrorCodes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

    @Autowired
    public PointsAppService(PointsStore store, PointsExpire pointsExpire, PointsApply pointsApply) {
        this.store = store;
        this.pointsExpire = pointsExpire;
        this.pointsApply = pointsApply;
    }

    public PointsAppService(PointsStore store, PlatformTransactionManager txm, Clock clock) {
        PointsApply apply = new PointsApply(store, clock);
        this.store = store;
        this.pointsExpire = new PointsExpire(store, txm, clock, apply);
        this.pointsApply = apply;
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
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = store.countAccounts(query.userId());
        List<PointsAccountView> records = store.listAccounts(query.userId(), page.offset(), page.pageSize()).stream()
                .map(PointsAppService::toAccountView)
                .toList();
        return new PageData<>(total, records);
    }

    public PageData<PointsTransactionView> pageTransactions(PointsTransactionQuery query) {
        String type = normalizeType(query.type());
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        LocalDateTime from = RewardTime.toUtc(query.from());
        LocalDateTime to = RewardTime.toUtc(query.to());
        long total = store.countTx(query.userId(), type, from, to);
        List<PointsTransactionView> records = store.listTx(
                        query.userId(), type, from, to, page.offset(), page.pageSize())
                .stream()
                .map(PointsAppService::toTxView)
                .toList();
        return new PageData<>(total, records);
    }

    public PageData<PointsPortalTxView> pagePortal(long userId, String type, Integer page, Integer pageSize) {
        String normalized = normalizeType(type);
        PageQuery pager = PageQuery.of(page, pageSize);
        long total = store.countTx(userId, normalized, null, null);
        List<PointsPortalTxView> records = store.listTx(userId, normalized, null, null, pager.offset(), pager.pageSize())
                .stream()
                .map(PointsAppService::toPortalView)
                .toList();
        return new PageData<>(total, records);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        if (!PointTypes.closed(type)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return type;
    }

    private static PointsAccountView toAccountView(PntAccountEntity row) {
        return new PointsAccountView(
                row.getId(),
                row.getUserId(),
                row.getBalance() == null ? 0L : row.getBalance(),
                RewardTime.toInstant(row.getCreatedAt()),
                RewardTime.toInstant(row.getUpdatedAt()));
    }

    private static PointsTransactionView toTxView(PntTransactionEntity row) {
        return new PointsTransactionView(
                row.getId(),
                row.getUserId(),
                row.getType(),
                row.getAmount() == null ? 0L : row.getAmount(),
                row.getBalanceAfter() == null ? 0L : row.getBalanceAfter(),
                row.getBizSource(),
                row.getBizId(),
                RewardTime.toInstant(row.getExpireAt()),
                row.getRemark(),
                row.simulatedFlag(),
                RewardTime.toInstant(row.getCreatedAt()));
    }

    private static PointsPortalTxView toPortalView(PntTransactionEntity row) {
        return new PointsPortalTxView(
                row.getType(),
                row.getAmount() == null ? 0L : row.getAmount(),
                row.getBalanceAfter() == null ? 0L : row.getBalanceAfter(),
                row.getBizSource(),
                null,
                row.getRemark(),
                RewardTime.toInstant(row.getCreatedAt()));
    }
}
