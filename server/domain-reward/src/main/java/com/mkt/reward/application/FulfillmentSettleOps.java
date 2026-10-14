package com.mkt.reward.application;

import com.mkt.contract.event.EventCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.response.FulfillmentCallbackResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardRuntimeSettings;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fulfillment settle helpers extracted from {@link FulfillmentService} (R13-2).
 * Move-only: settleSending / failAttempt / failTimeout / arrive semantics unchanged.
 */
@Component
public class FulfillmentSettleOps {

    private final GrantRecordStore grants;
    private final FulfillmentLookups lookups;
    private final FulfillmentEvents fulfillmentEvents;
    private final RewardRuntimeSettings settings;
    private final Clock clock;

    @Autowired
    public FulfillmentSettleOps(
            GrantRecordStore grants,
            FulfillmentLookups lookups,
            FulfillmentEvents fulfillmentEvents,
            RewardRuntimeSettings settings,
            Clock clock) {
        this.grants = grants;
        this.lookups = lookups;
        this.fulfillmentEvents = fulfillmentEvents;
        this.settings = settings == null ? new RewardRuntimeSettings() : settings;
        this.clock = clock;
    }

    FulfillmentCallbackResponse settleSending(GrantRecordEntity row, boolean success, String failReason) {
        if (GrantRecordStatuses.FULFILL_ARRIVED.equals(row.getFulfillmentStatus())) {
            return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_ARRIVED, false);
        }
        if (GrantRecordStatuses.FULFILL_FAILED.equals(row.getFulfillmentStatus())
                && FulfillFailReasons.MANUAL.equals(row.getFulfillFailReason())) {
            return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_FAILED, true);
        }
        if (!GrantRecordStatuses.GRANTED.equals(row.getStatus())
                || !GrantRecordStatuses.FULFILL_SENDING.equals(row.getFulfillmentStatus())) {
            throw new BusinessException(RewardErrorCodes.FULFILL_NOT_SENDING);
        }
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        if (success) {
            arrive(row, now);
            int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_SENDING);
            if (affected != 1) {
                GrantRecordEntity fresh = grants.getById(row.getId());
                return settleSending(fresh, true, null);
            }
            fulfillmentEvents.emitArrived(row);
            return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_ARRIVED, false);
        }
        return failAttempt(row, now, failReason);
    }

    private FulfillmentCallbackResponse failAttempt(GrantRecordEntity row, LocalDateTime now, String failReason) {
        String reason = lookups.mappedFailReason(failReason);
        int retry = row.getRetryCount() == null ? 0 : row.getRetryCount();
        retry = retry + 1;
        row.setRetryCount(retry);
        row.setUpdatedAt(now);
        if (retry >= settings.fulfillRetryMax()) {
            row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_FAILED);
            row.setFulfillFailReason(reason);
            if (lookups.reconRequired(row)) {
                row.setReconStatus(GrantRecordStatuses.RECON_PENDING);
            }
            int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_SENDING);
            if (affected != 1) {
                return settleSending(grants.getById(row.getId()), false, failReason);
            }
            fulfillmentEvents.append(EventCodes.REWARD_FULFILL_FAILED, row);
            return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_FAILED, false);
        }
        row.setNextFulfillRetryAt(now.plusSeconds(settings.fulfillRetryIntervalSeconds()));
        int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_SENDING);
        if (affected != 1) {
            return settleSending(grants.getById(row.getId()), false, failReason);
        }
        return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_SENDING, false);
    }

    void failTimeout(GrantRecordEntity row, LocalDateTime now) {
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_FAILED);
        row.setFulfillFailReason(FulfillFailReasons.TIMEOUT);
        row.setUpdatedAt(now);
        if (lookups.reconRequired(row)) {
            row.setReconStatus(GrantRecordStatuses.RECON_PENDING);
        }
        int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_SENDING);
        if (affected == 1) {
            fulfillmentEvents.append(EventCodes.REWARD_FULFILL_FAILED, row);
        }
    }

    private void arrive(GrantRecordEntity row, LocalDateTime now) {
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_ARRIVED);
        row.setFulfilledAt(now);
        row.setFulfillFailReason(null);
        row.setUpdatedAt(now);
        if (lookups.reconRequired(row)) {
            row.setReconStatus(GrantRecordStatuses.RECON_PENDING);
        }
    }
}
