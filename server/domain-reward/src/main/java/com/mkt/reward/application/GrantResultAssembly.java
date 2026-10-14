package com.mkt.reward.application;

import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.PrizeCost;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Result assembly extracted from {@link GrantAppService} (R2-3).
 * Move-only: appendSuccess / fillBase / toResult semantics unchanged.
 */
@Component
public class GrantResultAssembly {

    private final EventPublisher events;
    private final PrizeStore prizes;

    @Autowired
    public GrantResultAssembly(ObjectProvider<EventPublisher> events, PrizeStore prizes) {
        this(events.getIfAvailable(), prizes);
    }

    GrantResultAssembly(EventPublisher events, PrizeStore prizes) {
        this.events = events;
        this.prizes = prizes;
    }

    void fillBase(
            GrantRecordEntity record,
            long prizeId,
            PrizeEntity prize,
            long userId,
            GrantSource grantSource,
            String sourceId,
            PrizeCost cost,
            GrantContext ctx,
            LocalDateTime now) {
        record.setPrizeId(prize != null && prize.getId() != null ? prize.getId() : prizeId);
        record.setPrizeCode(prize == null ? "missing" : prize.getCode());
        record.setCategoryCode(prize == null ? "MISSING" : prize.getCategoryCode());
        record.setFaceFen(cost.faceFen());
        record.setCostFen(cost.costFen());
        record.setReconStatus(GrantRecordStatuses.RECON_NONE);
        record.setUserId(userId);
        record.setGrantSource(grantSource.name());
        record.setSourceId(sourceId);
        if (record.getFulfillmentStatus() == null) {
            record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_NONE);
        }
        if (record.getRetryCount() == null) {
            record.setRetryCount(0);
        }
        record.setSimulated(ctx != null && ctx.simulated() ? 1 : 0);
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(now);
        }
        record.setUpdatedAt(now);
    }

    void appendSuccess(GrantRecordEntity record, GrantSource source) {
        if (events == null) {
            return;
        }
        events.append(
                EventCodes.REWARD_GRANT_SUCCESS,
                "rwd_grant_record",
                String.valueOf(record.getId()),
                Map.of(
                        "recordId", record.getId(),
                        "prizeId", record.getPrizeId(),
                        "grantSource", source.name(),
                        "userId", record.getUserId(),
                        "simulated", simulated(record)));
    }

    GrantResult toResult(GrantRecordEntity row, boolean hitIdempotent) {
        PrizeEntity prize = prizes.getByIdIncludingDeleted(row.getPrizeId());
        String prizeName = prize != null ? prize.getName() : row.getPrizeCode();
        return new GrantResult(
                row.getId() == null ? 0L : row.getId(),
                GrantStatus.valueOf(row.getStatus()),
                FulfillmentStatus.valueOf(row.getFulfillmentStatus()),
                row.getPrizeId(),
                hitIdempotent,
                prizeName);
    }

    private static boolean simulated(GrantRecordEntity row) {
        return row.getSimulated() != null && row.getSimulated() == 1;
    }
}
