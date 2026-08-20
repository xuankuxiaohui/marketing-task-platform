package com.mkt.reward.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.FailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.support.RewardGrantSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * REQUIRES_NEW leave-trace for retryable grant failures (design §5.6.2). Unique exception to RL-05.
 */
@Service
public class GrantFailureLedger {

    private final GrantRecordStore grants;
    private final EventPublisher events;
    private final Clock clock;
    private final RewardGrantSettings settings;
    private final TransactionTemplate requiresNew;

    @Autowired
    public GrantFailureLedger(
            GrantRecordStore grants,
            ObjectProvider<EventPublisher> events,
            Clock clock,
            RewardGrantSettings settings,
            ObjectProvider<PlatformTransactionManager> txms) {
        this(grants, events.getIfAvailable(), clock, settings, txms.getIfAvailable());
    }

    public GrantFailureLedger(
            GrantRecordStore grants,
            EventPublisher events,
            Clock clock,
            RewardGrantSettings settings,
            PlatformTransactionManager txm) {
        this.grants = grants;
        this.events = events;
        this.clock = clock;
        this.settings = settings;
        if (txm == null) {
            this.requiresNew = null;
        } else {
            TransactionTemplate nested = new TransactionTemplate(txm);
            nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            nested.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            this.requiresNew = nested;
        }
    }

    public GrantRecordEntity leaveRetryPending(GrantRecordEntity draft, boolean incrementRetry) {
        if (requiresNew != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            return requiresNew.execute(status -> persist(draft, incrementRetry));
        }
        return persist(draft, incrementRetry);
    }

    private GrantRecordEntity persist(GrantRecordEntity draft, boolean incrementRetry) {
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        GrantRecordEntity existing =
                grants.getByIdempotent(draft.getGrantSource(), draft.getSourceId(), draft.getPrizeId());
        GrantRecordEntity row = existing == null ? draft : existing;
        int retry = row.getRetryCount() == null ? 0 : row.getRetryCount();
        if (incrementRetry) {
            retry = retry + 1;
        } else if (existing == null) {
            retry = 0;
        }
        row.setRetryCount(retry);
        if (incrementRetry && retry >= settings.retryMax()) {
            row.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
            row.setNextRetryAt(null);
        } else {
            row.setStatus(GrantRecordStatuses.RETRY_PENDING);
            row.setNextRetryAt(now.plusSeconds(settings.retryIntervalSeconds()));
        }
        row.setFailReason(draft.getFailReason());
        row.setUpdatedAt(now);
        if (row.getFulfillmentStatus() == null) {
            row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        }
        if (row.getReconStatus() == null) {
            row.setReconStatus(GrantRecordStatuses.RECON_NONE);
        }
        if (row.getCostFen() == null) {
            row.setCostFen(0);
        }
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        if (existing == null) {
            try {
                grants.insert(row);
            } catch (DuplicateKeyException ex) {
                GrantRecordEntity raced =
                        grants.getByIdempotent(draft.getGrantSource(), draft.getSourceId(), draft.getPrizeId());
                if (raced != null) {
                    return raced;
                }
                throw ex;
            }
        } else {
            grants.update(row);
        }
        appendFailed(row);
        return row;
    }

    public void markPermanent(GrantRecordEntity draft) {
        if (requiresNew != null && TransactionSynchronizationManager.isActualTransactionActive()) {
            requiresNew.execute(status -> {
                persistPermanentRow(draft);
                return null;
            });
            return;
        }
        persistPermanentRow(draft);
    }

    private void persistPermanentRow(GrantRecordEntity draft) {
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        GrantRecordEntity existing =
                grants.getByIdempotent(draft.getGrantSource(), draft.getSourceId(), draft.getPrizeId());
        GrantRecordEntity row = existing == null ? draft : existing;
        row.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
        row.setFailReason(draft.getFailReason());
        row.setUpdatedAt(now);
        if (row.getRetryCount() == null) {
            row.setRetryCount(0);
        }
        if (row.getFulfillmentStatus() == null) {
            row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        }
        if (row.getReconStatus() == null) {
            row.setReconStatus(GrantRecordStatuses.RECON_NONE);
        }
        if (row.getCostFen() == null) {
            row.setCostFen(0);
        }
        if (row.getCreatedAt() == null) {
            row.setCreatedAt(now);
        }
        if (existing == null) {
            grants.insert(row);
        } else {
            grants.update(row);
        }
        appendFailed(row);
    }

    private void appendFailed(GrantRecordEntity row) {
        if (events == null) {
            return;
        }
        String reason = row.getFailReason() == null ? FailReasons.SYSTEM_ERROR : row.getFailReason();
        events.append(
                EventCodes.REWARD_GRANT_FAILED,
                "rwd_grant_record",
                String.valueOf(row.getId()),
                Map.of(
                        "recordId", row.getId(),
                        "prizeId", row.getPrizeId(),
                        "failReason", reason));
    }
}
