package com.mkt.reward.application;

import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.convert.RewardTime;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.FulfillmentModes;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.RewardTargets;
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
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** startFulfillment on GRANTED (design §5.6.3). P0 third-party adapters are stubs. */
@Service
public class FulfillmentService {

    static final int BATCH = 100;

    private final PointsPort points;
    private final GrantRecordStore grants;
    private final PrizeStore prizes;
    private final PrizeCategoryStore categories;
    private final EventPublisher events;
    private final RewardRuntimeSettings settings;
    private final Clock clock;

    @Autowired
    public FulfillmentService(
            ObjectProvider<PointsPort> points,
            GrantRecordStore grants,
            ObjectProvider<PrizeStore> prizes,
            ObjectProvider<PrizeCategoryStore> categories,
            ObjectProvider<EventPublisher> events,
            ObjectProvider<RewardRuntimeSettings> settings,
            Clock clock) {
        this(
                points.getIfAvailable(),
                grants,
                prizes.getIfAvailable(),
                categories.getIfAvailable(),
                events.getIfAvailable(),
                settings.getIfAvailable() == null ? new RewardRuntimeSettings() : settings.getIfAvailable(),
                clock);
    }

    public FulfillmentService(PointsPort points, GrantRecordStore grants, EventPublisher events, Clock clock) {
        this(points, grants, null, null, events, new RewardRuntimeSettings(), clock);
    }

    public FulfillmentService(
            PointsPort points,
            GrantRecordStore grants,
            PrizeStore prizes,
            PrizeCategoryStore categories,
            EventPublisher events,
            RewardRuntimeSettings settings,
            Clock clock) {
        this.points = points;
        this.grants = grants;
        this.prizes = prizes;
        this.categories = categories;
        this.events = events;
        this.settings = settings == null ? new RewardRuntimeSettings() : settings;
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
        dispatchAfterCommit(record, prize, category);
    }

    public void emitArrived(GrantRecordEntity record) {
        if (GrantRecordStatuses.FULFILL_ARRIVED.equals(record.getFulfillmentStatus())) {
            append(EventCodes.REWARD_FULFILL_ARRIVED, record);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FulfillmentCallbackResponse confirm(long recordId) {
        GrantRecordEntity row = grants.getById(recordId);
        if (row == null) {
            throw new BusinessException(CommonErrorCodes.NOT_FOUND);
        }
        return settleSending(row, true, null);
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
            return settleSending(row, true, null);
        }
        if ("FAILED".equals(result)) {
            return settleSending(row, false, failReason);
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
        PrizeEntity prize = prizeOf(row);
        PrizeCategoryEntity category = categoryOf(prize);
        if (prize != null) {
            armThirdPartyStub(row, prize, category);
        }
        return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_SENDING, false);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int tick() {
        LocalDateTime now = RewardTime.toUtc(clock.instant());
        int ran = 0;
        ran += retryDue(now);
        ran += timeoutDue(now);
        ran += crossDayPending(now);
        return ran;
    }

    private int retryDue(LocalDateTime now) {
        int ran = 0;
        for (GrantRecordEntity row : grants.listFulfillRetryDue(now, BATCH)) {
            PrizeEntity prize = prizeOf(row);
            PrizeCategoryEntity category = categoryOf(prize);
            if (prize != null) {
                armThirdPartyStub(row, prize, category);
            }
            ran++;
        }
        return ran;
    }

    private int timeoutDue(LocalDateTime now) {
        LocalDateTime cutoff = now.minusHours(settings.sendingTimeoutHours());
        int ran = 0;
        for (GrantRecordEntity row : grants.listSendingTimeout(cutoff, BATCH)) {
            failTimeout(row, now);
            ran++;
        }
        return ran;
    }

    private int crossDayPending(LocalDateTime now) {
        LocalDateTime dayStart = com.mkt.reward.domain.ClaimWindows.dailyStartUtc(clock);
        int ran = 0;
        for (GrantRecordEntity row : grants.listCrossDaySending(dayStart, BATCH)) {
            if (reconRequired(row)) {
                grants.markReconPending(row.getId(), now);
                ran++;
            }
        }
        return ran;
    }

    private FulfillmentCallbackResponse settleSending(GrantRecordEntity row, boolean success, String failReason) {
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
            emitArrived(row);
            return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_ARRIVED, false);
        }
        return failAttempt(row, now, failReason);
    }

    private FulfillmentCallbackResponse failAttempt(GrantRecordEntity row, LocalDateTime now, String failReason) {
        String reason = mappedFailReason(failReason);
        int retry = row.getRetryCount() == null ? 0 : row.getRetryCount();
        retry = retry + 1;
        row.setRetryCount(retry);
        row.setUpdatedAt(now);
        if (retry >= settings.fulfillRetryMax()) {
            row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_FAILED);
            row.setFulfillFailReason(reason);
            if (reconRequired(row)) {
                row.setReconStatus(GrantRecordStatuses.RECON_PENDING);
            }
            int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_SENDING);
            if (affected != 1) {
                return settleSending(grants.getById(row.getId()), false, failReason);
            }
            append(EventCodes.REWARD_FULFILL_FAILED, row);
            return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_FAILED, false);
        }
        row.setNextFulfillRetryAt(now.plusSeconds(settings.fulfillRetryIntervalSeconds()));
        int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_SENDING);
        if (affected != 1) {
            return settleSending(grants.getById(row.getId()), false, failReason);
        }
        return new FulfillmentCallbackResponse(GrantRecordStatuses.FULFILL_SENDING, false);
    }

    private void failTimeout(GrantRecordEntity row, LocalDateTime now) {
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_FAILED);
        row.setFulfillFailReason(FulfillFailReasons.TIMEOUT);
        row.setUpdatedAt(now);
        if (reconRequired(row)) {
            row.setReconStatus(GrantRecordStatuses.RECON_PENDING);
        }
        int affected = grants.updateIfFulfillment(row, GrantRecordStatuses.FULFILL_SENDING);
        if (affected == 1) {
            append(EventCodes.REWARD_FULFILL_FAILED, row);
        }
    }

    private void arrive(GrantRecordEntity row, LocalDateTime now) {
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_ARRIVED);
        row.setFulfilledAt(now);
        row.setFulfillFailReason(null);
        row.setUpdatedAt(now);
        if (reconRequired(row)) {
            row.setReconStatus(GrantRecordStatuses.RECON_PENDING);
        }
    }

    private boolean reconRequired(GrantRecordEntity row) {
        PrizeCategoryEntity category = categoryOf(prizeOf(row));
        if (category == null && categories != null && row.getCategoryCode() != null) {
            category = categories.getByCode(row.getCategoryCode());
        }
        return category != null && category.reconRequiredFlag();
    }

    private PrizeEntity prizeOf(GrantRecordEntity row) {
        if (prizes == null || row.getPrizeId() == null) {
            return null;
        }
        return prizes.getByIdIncludingDeleted(row.getPrizeId());
    }

    private PrizeCategoryEntity categoryOf(PrizeEntity prize) {
        if (categories == null || prize == null || prize.getCategoryCode() == null) {
            return null;
        }
        return categories.getByCode(prize.getCategoryCode());
    }

    private static String mappedFailReason(String failReason) {
        if (failReason != null
                && FulfillFailReasons.closed(failReason)
                && !FulfillFailReasons.MANUAL.equals(failReason)
                && !FulfillFailReasons.TIMEOUT.equals(failReason)) {
            return failReason;
        }
        return FulfillFailReasons.CALLBACK_FAILED;
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
        if (prize == null || !RewardTargets.THIRD_PARTY.equals(prize.getRewardTarget())) {
            return;
        }
        if (record.getFulfillmentRef() != null && !record.getFulfillmentRef().isBlank()) {
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordId", record.getId());
        payload.put("prizeId", record.getPrizeId());
        if (EventCodes.REWARD_FULFILL_ARRIVED.equals(eventCode)) {
            payload.put("fulfillmentRef", record.getFulfillmentRef());
        } else {
            payload.put("fulfillFailReason", record.getFulfillFailReason());
        }
        events.append(eventCode, "rwd_grant_record", String.valueOf(record.getId()), payload);
    }
}
