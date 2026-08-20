package com.mkt.reward.application;

import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.FulfillmentModes;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.RewardTargets;
import com.mkt.reward.domain.TypeParams;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.points.PointsPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** startFulfillment on GRANTED (design §5.6.3). P0 third-party adapters are stubs. */
@Service
public class FulfillmentService {

    private final PointsPort points;
    private final GrantRecordStore grants;
    private final EventPublisher events;
    private final Clock clock;

    @Autowired
    public FulfillmentService(
            ObjectProvider<PointsPort> points,
            GrantRecordStore grants,
            ObjectProvider<EventPublisher> events,
            Clock clock) {
        this(points.getIfAvailable(), grants, events.getIfAvailable(), clock);
    }

    public FulfillmentService(PointsPort points, GrantRecordStore grants, EventPublisher events, Clock clock) {
        this.points = points;
        this.grants = grants;
        this.events = events;
        this.clock = clock;
    }

    public void start(GrantRecordEntity record, PrizeEntity prize, PrizeCategoryEntity category) {
        Instant now = clock.instant();
        if (FulfillmentModes.INSTANT.equals(prize.getFulfillmentMode())) {
            if (BuiltinCategories.POINTS.equals(prize.getCategoryCode())) {
                earnPoints(record, prize, now);
            }
            record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_ARRIVED);
            record.setFulfilledAt(RewardTime.toUtc(now));
            if (category != null && category.reconRequiredFlag()) {
                record.setReconStatus(GrantRecordStatuses.RECON_PENDING);
            }
            return;
        }
        record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_SENDING);
        record.setNextFulfillRetryAt(RewardTime.toUtc(now));
    }

    public void armThirdPartyStub(GrantRecordEntity record, PrizeEntity prize, PrizeCategoryEntity category) {
        dispatchAfterCommit(record, prize, category);
    }

    public void emitArrived(GrantRecordEntity record) {
        if (GrantRecordStatuses.FULFILL_ARRIVED.equals(record.getFulfillmentStatus())) {
            append(EventCodes.REWARD_FULFILL_ARRIVED, record);
        }
    }

    private void earnPoints(GrantRecordEntity record, PrizeEntity prize, Instant now) {
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
                    record.getSourceId());
        } catch (RuntimeException ex) {
            throw new RetryableGrantException(RetryableGrantReason.SYSTEM_ERROR);
        }
    }

    private void dispatchAfterCommit(
            GrantRecordEntity record, PrizeEntity prize, PrizeCategoryEntity category) {
        if (!RewardTargets.THIRD_PARTY.equals(prize.getRewardTarget())) {
            return;
        }
        String adapter = TypeParams.text(RewardJson.map(prize.getTypeParams()), TypeParams.ADAPTER_CODE);
        if (adapter == null && category != null) {
            adapter = category.getAdapterCode();
        }
        if (adapter == null || adapter.isBlank()) {
            adapter = "stub";
        }
        String ref = adapter + ":stub-" + record.getId();
        Runnable write = () -> grants.updateFulfillmentRef(record.getId(), ref);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write.run();
                }
            });
        } else {
            write.run();
        }
    }

    private void append(String eventCode, GrantRecordEntity record) {
        if (events == null) {
            return;
        }
        events.append(
                eventCode,
                "rwd_grant_record",
                String.valueOf(record.getId()),
                Map.of(
                        "recordId", record.getId(),
                        "prizeId", record.getPrizeId(),
                        "fulfillmentStatus", record.getFulfillmentStatus()));
    }
}
