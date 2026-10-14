package com.mkt.reward.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fulfillment event helpers extracted from {@link FulfillmentService} (R13-3).
 * Move-only: emitArrived / append semantics unchanged.
 */
@Component
public class FulfillmentEvents {

    private final EventPublisher events;

    @Autowired
    public FulfillmentEvents(ObjectProvider<EventPublisher> events) {
        this(events.getIfAvailable());
    }

    public FulfillmentEvents(EventPublisher events) {
        this.events = events;
    }

    void emitArrived(GrantRecordEntity record) {
        if (GrantRecordStatuses.FULFILL_ARRIVED.equals(record.getFulfillmentStatus())) {
            append(EventCodes.REWARD_FULFILL_ARRIVED, record);
        }
    }

    void append(String eventCode, GrantRecordEntity record) {
        if (events == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordId", record.getId());
        payload.put("prizeId", record.getPrizeId());
        payload.put("userId", record.getUserId());
        payload.put("simulated", record.getSimulated() != null && record.getSimulated() == 1);
        if (EventCodes.REWARD_FULFILL_ARRIVED.equals(eventCode)) {
            payload.put("fulfillmentRef", record.getFulfillmentRef());
        } else {
            payload.put("fulfillFailReason", record.getFulfillFailReason());
        }
        events.append(eventCode, "rwd_grant_record", String.valueOf(record.getId()), payload);
    }
}
