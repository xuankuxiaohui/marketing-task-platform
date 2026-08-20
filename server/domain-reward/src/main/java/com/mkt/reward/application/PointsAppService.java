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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Points ledger (design §5.8 / R20). */
@Service
public class PointsAppService {

    static final int EXPIRE_BATCH = 5000;

    private final PointsStore store;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Autowired
    public PointsAppService(
            PointsStore store, ObjectProvider<PlatformTransactionManager> txm, Clock clock) {
        this(store, txm.getIfAvailable(), clock);
    }

    public PointsAppService(PointsStore store, PlatformTransactionManager txm, Clock clock) {
        this.store = store;
        this.clock = clock;
        if (txm == null) {
            this.tx = null;
        } else {
            TransactionTemplate template = new TransactionTemplate(txm);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.tx = template;
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long earn(long userId, int points, Instant expireAt, String sourceType, String sourceId) {
        if (points <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        store.insertIgnoreAccount(userId);
        return apply(userId, PointTypes.EARN, points, sourceType, sourceId, expireAt, null, 0, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long consume(long userId, int points, String sourceType, String sourceId, String remark) {
        if (points <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return apply(userId, PointTypes.CONSUME, -points, sourceType, sourceId, null, remark, 0, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long reverse(long userId, int points, String sourceType, String sourceId, String remark) {
        if (points <= 0) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        return apply(userId, PointTypes.REVERSAL, -points, sourceType, sourceId, null, remark, 0, false);
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
        long after = apply(
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

    public int expireDue() {
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        int total = 0;
        while (true) {
            List<PntTransactionEntity> batch = store.listExpireDue(now, EXPIRE_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (PntTransactionEntity earn : batch) {
                total += runTx(() -> expireOne(earn));
            }
            if (batch.size() < EXPIRE_BATCH) {
                break;
            }
        }
        return total;
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

    private long apply(
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

    private int expireOne(PntTransactionEntity earn) {
        String bizId = String.valueOf(earn.getId());
        if (store.expireExists(bizId)) {
            return 0;
        }
        PntAccountEntity locked = store.lockByUserId(earn.getUserId());
        if (locked == null) {
            return 0;
        }
        if (store.expireExists(bizId)) {
            return 0;
        }
        long due = earn.getAmount() == null ? 0L : Math.abs(earn.getAmount());
        long current = locked.getBalance() == null ? 0L : locked.getBalance();
        long deduct = Math.min(current, due);
        if (deduct > 0) {
            int affected = store.applyDelta(earn.getUserId(), -deduct);
            if (affected != 1) {
                return 0;
            }
        }
        long after = current - deduct;
        String remark = deduct < due ? "truncated; requested=" + due + " deducted=" + deduct : null;
        store.insertTx(newRow(
                earn.getUserId(),
                PointTypes.EXPIRE,
                -deduct,
                after,
                null,
                bizId,
                null,
                remark,
                earn.getSimulated() == null ? 0 : earn.getSimulated()));
        return 1;
    }

    private int runTx(java.util.function.Supplier<Integer> action) {
        if (tx == null) {
            return action.get();
        }
        Integer value = tx.execute(status -> action.get());
        return value == null ? 0 : value;
    }

    private PntTransactionEntity newRow(
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
