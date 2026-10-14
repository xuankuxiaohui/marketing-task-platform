package com.mkt.reward.application;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.PrizeCost;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Persistence helpers extracted from {@link GrantAppService} (R2-4).
 * Move-only: leaveRetry / persistPermanent / persistSuccess semantics unchanged.
 */
@Component
public class GrantPersistence {

    private final GrantRecordStore grants;
    private final PrizeStore prizes;
    private final GrantResultAssembly assembly;
    private final GrantFailureLedger failures;
    private final Clock clock;

    @Autowired
    public GrantPersistence(
            GrantRecordStore grants,
            PrizeStore prizes,
            GrantResultAssembly assembly,
            GrantFailureLedger failures,
            Clock clock) {
        this.grants = grants;
        this.prizes = prizes;
        this.assembly = assembly;
        this.failures = failures;
        this.clock = clock;
    }

    void persistSuccess(GrantRecordEntity record, String fromStatus) {
        if (record.getId() == null) {
            try {
                grants.insert(record);
            } catch (DuplicateKeyException ex) {
                GrantRecordEntity winner =
                        grants.getByIdempotent(record.getGrantSource(), record.getSourceId(), record.getPrizeId());
                if (winner != null
                        && !GrantRecordStatuses.RETRY_PENDING.equals(winner.getStatus())
                        && !GrantRecordStatuses.PERMANENT_FAILED.equals(winner.getStatus())) {
                    throw new IdempotentHit(winner);
                }
                throw ex;
            }
            return;
        }
        if (GrantRecordStatuses.RETRY_PENDING.equals(fromStatus)
                || GrantRecordStatuses.PERMANENT_FAILED.equals(fromStatus)) {
            int affected = grants.updateIfStatus(record, fromStatus);
            if (affected != 1) {
                GrantRecordEntity winner =
                        grants.getByIdempotent(record.getGrantSource(), record.getSourceId(), record.getPrizeId());
                if (winner != null
                        && (GrantRecordStatuses.GRANTED.equals(winner.getStatus())
                                || GrantRecordStatuses.WON.equals(winner.getStatus()))) {
                    throw new IdempotentHit(winner);
                }
                throw new RetryableGrantException(RetryableGrantReason.SYSTEM_ERROR);
            }
            return;
        }
        grants.update(record);
    }

    void leaveRetry(
            long prizeId,
            long userId,
            GrantSource grantSource,
            String sourceId,
            GrantContext ctx,
            GrantRecordEntity existing,
            String failReason) {
        GrantRecordEntity draft = existing == null ? new GrantRecordEntity() : existing;
        PrizeEntity prize = prizes.getByIdIncludingDeleted(prizeId);
        assembly.fillBase(
                draft,
                prizeId,
                prize,
                userId,
                grantSource,
                sourceId,
                new PrizeCost(0, null),
                ctx,
                RewardTime.toUtc(clock.instant()));
        draft.setFailReason(failReason);
        boolean increment = existing != null && GrantRecordStatuses.RETRY_PENDING.equals(existing.getStatus());
        failures.leaveRetryPending(draft, increment);
    }

    void persistPermanent(
            long prizeId,
            long userId,
            GrantSource grantSource,
            String sourceId,
            GrantContext ctx,
            GrantRecordEntity existing,
            String failReason) {
        GrantRecordEntity draft = existing == null ? new GrantRecordEntity() : existing;
        PrizeEntity prize = prizes.getByIdIncludingDeleted(prizeId);
        assembly.fillBase(
                draft,
                prizeId,
                prize,
                userId,
                grantSource,
                sourceId,
                new PrizeCost(0, null),
                ctx,
                RewardTime.toUtc(clock.instant()));
        draft.setFailReason(failReason);
        failures.markPermanent(draft);
    }

    static final class IdempotentHit extends RuntimeException {
        final GrantRecordEntity winner;

        IdempotentHit(GrantRecordEntity winner) {
            this.winner = winner;
        }
    }
}
