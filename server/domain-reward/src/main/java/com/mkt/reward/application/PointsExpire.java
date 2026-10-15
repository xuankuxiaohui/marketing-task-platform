package com.mkt.reward.application;

import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.PointTypes;
import com.mkt.reward.entity.PntAccountEntity;
import com.mkt.reward.entity.PntTransactionEntity;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Points expiry extracted from {@link PointsAppService} (R18-1).
 * Move-only: expireDue / expireOne / EXPIRE_BATCH semantics unchanged.
 * Per-row {@code REQUIRES_NEW} stays here.
 * Ledger rows are built by {@link PointsApply#newRow} (R18-2); no service back-reference.
 */
@Component
public class PointsExpire {

    static final int EXPIRE_BATCH = 5000;

    private final PointsStore store;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final PointsApply pointsApply;

    @Autowired
    public PointsExpire(
            PointsStore store, ObjectProvider<PlatformTransactionManager> txm, Clock clock, PointsApply pointsApply) {
        this(store, txm.getIfAvailable(), clock, pointsApply);
    }

    public PointsExpire(PointsStore store, PlatformTransactionManager txm, Clock clock, PointsApply pointsApply) {
        this.store = store;
        this.clock = clock;
        this.pointsApply = pointsApply;
        if (txm == null) {
            this.tx = null;
        } else {
            TransactionTemplate template = new TransactionTemplate(txm);
            template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            this.tx = template;
        }
    }

    int expireDue() {
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
        store.insertTx(pointsApply.newRow(
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
}
