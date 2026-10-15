package com.mkt.reward.application;

import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.FulfillmentModes;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.TypeParams;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeCategoryEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.points.PointsPort;
import com.mkt.reward.response.FulfillmentCallbackResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardRuntimeSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** startFulfillment on GRANTED (design §5.6.3). P0 third-party adapters are stubs. */
@Service
public class FulfillmentService {

    private final PointsPort points;
    private final GrantRecordStore grants;
    private final FulfillmentLookups lookups;
    private final FulfillmentSettleOps settleOps;
    private final FulfillmentEvents fulfillmentEvents;
    private final FulfillmentDispatch dispatch;
    private final FulfillmentTickOps tickOps;
    private final Clock clock;

    @Autowired
    public FulfillmentService(
            ObjectProvider<PointsPort> points,
            GrantRecordStore grants,
            FulfillmentLookups lookups,
            FulfillmentSettleOps settleOps,
            FulfillmentEvents fulfillmentEvents,
            FulfillmentDispatch dispatch,
            FulfillmentTickOps tickOps,
            Clock clock) {
        this(
                points.getIfAvailable(),
                grants,
                lookups,
                settleOps,
                fulfillmentEvents,
                dispatch,
                tickOps,
                clock);
    }

    public FulfillmentService(PointsPort points, GrantRecordStore grants, EventPublisher events, Clock clock) {
        this(points, grants, (PrizeStore) null, (PrizeCategoryStore) null, events, new RewardRuntimeSettings(), clock);
    }

    public FulfillmentService(
            PointsPort points,
            GrantRecordStore grants,
            PrizeStore prizes,
            PrizeCategoryStore categories,
            EventPublisher events,
            RewardRuntimeSettings settings,
            Clock clock) {
        this(points, grants, new FulfillmentLookups(prizes, categories), events, settings, clock);
    }

    private FulfillmentService(
            PointsPort points,
            GrantRecordStore grants,
            FulfillmentLookups lookups,
            EventPublisher events,
            RewardRuntimeSettings settings,
            Clock clock) {
        FulfillmentEvents fulfillmentEvents = new FulfillmentEvents(events);
        RewardRuntimeSettings resolved = settings == null ? new RewardRuntimeSettings() : settings;
        FulfillmentSettleOps settleOps =
                new FulfillmentSettleOps(grants, lookups, fulfillmentEvents, resolved, clock);
        FulfillmentDispatch dispatch = new FulfillmentDispatch(grants);
        FulfillmentTickOps tickOps =
                new FulfillmentTickOps(grants, lookups, settleOps, dispatch, resolved, clock);
        this(points, grants, lookups, settleOps, fulfillmentEvents, dispatch, tickOps, clock);
    }

    private FulfillmentService(
            PointsPort points,
            GrantRecordStore grants,
            FulfillmentLookups lookups,
            FulfillmentSettleOps settleOps,
            FulfillmentEvents fulfillmentEvents,
            FulfillmentDispatch dispatch,
            FulfillmentTickOps tickOps,
            Clock clock) {
        this.points = points;
        this.grants = grants;
        this.lookups = lookups;
        this.settleOps = settleOps;
        this.fulfillmentEvents = fulfillmentEvents;
        this.dispatch = dispatch;
        this.tickOps = tickOps;
        this.clock = clock;
    }

    public void start(GrantRecordEntity record, PrizeEntity prize, PrizeCategoryEntity category) {
        Instant now = clock.instant();
        record.setFulfillFailReason(null);
        if (FulfillmentModes.INSTANT.equals(prize.getFulfillmentMode())) {
            if (BuiltinCategories.POINTS.equals(prize.getCategoryCode())) {
                earnPoints(record, prize, now);
            }
            record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_ARRIVED);
            record.setFulfilledAt(RewardTime.toUtc(now));
            if (category != null && category.reconRequiredFlag()) {
                record.setReconStatus(GrantRecordStatuses.RECON_PENDING);
            }
            record.setRetryCount(0);
            return;
        }
        record.setRetryCount(0);
        record.setFulfillmentStatus(GrantRecordStatuses.FULFILL_SENDING);
        record.setNextFulfillRetryAt(RewardTime.toUtc(now));
    }

    public void armThirdPartyStub(GrantRecordEntity record, PrizeEntity prize, PrizeCategoryEntity category) {
        dispatch.armThirdPartyStub(record, prize, category);
    }

    public void emitArrived(GrantRecordEntity record) {
        fulfillmentEvents.emitArrived(record);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FulfillmentCallbackResponse confirm(long recordId) {
        GrantRecordEntity row = grants.getById(recordId);
        if (row == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return settleOps.settleSending(row, true, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FulfillmentCallbackResponse callback(String fulfillmentRef, String result, String failReason) {
        if (fulfillmentRef == null || fulfillmentRef.isBlank() || fulfillmentRef.length() > 64) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
        }
        GrantRecordEntity row = grants.getByFulfillmentRef(fulfillmentRef.trim());
        if (row == null) {
            throw new BusinessException(RewardErrorCodes.FULFILL_NOT_FOUND);
        }
        if ("SUCCESS".equals(result)) {
            return settleOps.settleSending(row, true, null);
        }
        if ("FAILED".equals(result)) {
            return settleOps.settleSending(row, false, failReason);
        }
        throw new BusinessException(CommonErrorCodes.PARAM_INVALID);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FulfillmentCallbackResponse retry(long recordId) {
        GrantRecordEntity row = grants.getById(recordId);
        if (row == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        if (!GrantRecordStatuses.GRANTED.equals(row.getStatus())
                || !GrantRecordStatuses.FULFILL_FAILED.equals(row.getFulfillmentStatus())
                || FulfillFailReasons.MANUAL.equals(row.getFulfillFailReason())) {
            throw new BusinessException(RewardErrorCodes.FULFILL_NOT_RETRYABLE);
        }
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_SENDING);
        row.setFulfillFailReason(null);
        row.setRetryCount(0);
        row.setNextFulfillRetryAt(now);
        row.setUpdatedAt(now);
        int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_FAILED);
        if (affected != 1) {
            throw new BusinessException(RewardErrorCodes.FULFILL_NOT_RETRYABLE);
        }
        PrizeEntity prize = lookups.prizeOf(row);
        PrizeCategoryEntity category = lookups.categoryOf(prize);
        if (prize != null) {
            dispatch.armThirdPartyStub(row, prize, category);
        }
        return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_SENDING, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int tick() {
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        int ran = 0;
        ran += tickOps.retryDue(now);
        ran += tickOps.timeoutDue(now);
        ran += tickOps.crossDayPending(now);
        return ran;
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
                    record.getSourceId(),
                    record.getSimulated() != null && record.getSimulated() == 1);
        } catch (RuntimeException ex) {
            throw new RetryableGrantException(RetryableGrantReason.SYSTEM_ERROR);
        }
    }
}
