package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.FulfillmentModes;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.RewardTargets;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardRuntimeSettings;
import com.mkt.reward.testsupport.CategoryFixtures;
import com.mkt.reward.testsupport.MemoryGrantRecordStore;
import com.mkt.reward.testsupport.MemoryPrizeCategoryStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import com.mkt.reward.testsupport.RecordingPointsPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * HC-1 Top3 / T3: dedicated unit coverage for {@link FulfillmentService} (start / callback / confirm / retry /
 * tick / third-party stub dispatch / events). Test-only; no main-code change. FulfillmentOpsTest keeps the
 * original happy-path callback cases.
 */
class FulfillmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.parse("2026-08-19T00:00:00");

    private final AtomicLong seq = new AtomicLong(1);
    private MemoryGrantRecordStore grants;
    private MemoryPrizeStore prizes;
    private MemoryPrizeCategoryStore categories;
    private MemoryOutboxStore outbox;
    private RecordingPointsPort points;
    private RewardRuntimeSettings settings;
    private FulfillmentService fulfillment;

    @BeforeEach
    void setUp() {
        // EventPublisher needs an "actual" transaction; synchronization stays off so the stub ref writes inline
        // unless a test opts in.
        TransactionSynchronizationManager.setActualTransactionActive(true);
        grants = new MemoryGrantRecordStore();
        prizes = new MemoryPrizeStore();
        categories = new MemoryPrizeCategoryStore();
        categories.seed(CategoryFixtures.alipay());
        categories.seed(CategoryFixtures.points());
        categories.seed(CategoryFixtures.physical());
        outbox = new MemoryOutboxStore();
        points = new RecordingPointsPort();
        settings = new RewardRuntimeSettings();
        settings.setFulfillRetryMax(3);
        settings.setFulfillRetryIntervalSeconds(60);
        fulfillment = new FulfillmentService(
                points,
                grants,
                prizes,
                categories,
                new EventPublisher(outbox, OutboxProducer.PORTAL),
                settings,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    // ---- start ----

    @Test
    void startInstantPointsEarnsAndArrives() {
        PrizeEntity prize = prize(BuiltinCategories.POINTS, RewardTargets.PLATFORM, FulfillmentModes.INSTANT,
                "{\"points\":50}");
        prize.setExpireHours(24);
        GrantRecordEntity record = record(prize, GrantRecordStatuses.FULFILL_NONE, null);
        record.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        record.setRetryCount(2);

        fulfillment.start(record, prize, categories.getByCode(BuiltinCategories.POINTS));

        assertThat(points.calls()).containsExactly(new RecordingPointsPort.EarnCall(
                9L, 50, NOW.plusSeconds(24 * 3600), "TASK_STEP", record.getSourceId(), false));
        assertThat(record.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
        assertThat(record.getFulfilledAt()).isEqualTo(NOW_UTC);
        assertThat(record.getFulfillFailReason()).isNull();
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_NONE);
    }

    @Test
    void startInstantPointsWithoutExpirePassesNullExpireAndSimulatedFlag() {
        PrizeEntity prize = prize(BuiltinCategories.POINTS, RewardTargets.PLATFORM, FulfillmentModes.INSTANT,
                "{\"points\":\"7\"}");
        GrantRecordEntity record = record(prize, GrantRecordStatuses.FULFILL_NONE, null);
        record.setSimulated(1);

        fulfillment.start(record, prize, null);

        assertThat(points.calls()).singleElement().satisfies(call -> {
            assertThat(call.points()).isEqualTo(7);
            assertThat(call.expireAt()).isNull();
            assertThat(call.simulated()).isTrue();
        });
    }

    @Test
    void startInstantPointsWithoutPositiveAmountIsRetryable() {
        PrizeEntity prize = prize(BuiltinCategories.POINTS, RewardTargets.PLATFORM, FulfillmentModes.INSTANT,
                "{\"points\":0}");
        GrantRecordEntity record = record(prize, GrantRecordStatuses.FULFILL_NONE, null);

        assertThatThrownBy(() -> fulfillment.start(record, prize, null))
                .isInstanceOf(RetryableGrantException.class)
                .extracting(ex -> ((RetryableGrantException) ex).reason())
                .isEqualTo(RetryableGrantReason.SYSTEM_ERROR);
        assertThat(points.calls()).isEmpty();
    }

    @Test
    void startInstantPointsPortFailureIsWrappedAsRetryable() {
        points.failWith = new IllegalStateException("db down");
        PrizeEntity prize = prize(BuiltinCategories.POINTS, RewardTargets.PLATFORM, FulfillmentModes.INSTANT,
                "{\"points\":5}");
        GrantRecordEntity record = record(prize, GrantRecordStatuses.FULFILL_NONE, null);

        assertThatThrownBy(() -> fulfillment.start(record, prize, null))
                .isInstanceOf(RetryableGrantException.class)
                .extracting(ex -> ((RetryableGrantException) ex).reason())
                .isEqualTo(RetryableGrantReason.SYSTEM_ERROR);
        assertThat(record.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_NONE);
    }

    @Test
    void startInstantWithoutPointsPortStillArrives() {
        FulfillmentService noPoints = new FulfillmentService(
                null, grants, prizes, categories, null, settings, Clock.fixed(NOW, ZoneOffset.UTC));
        PrizeEntity prize = prize(BuiltinCategories.POINTS, RewardTargets.PLATFORM, FulfillmentModes.INSTANT,
                "{\"points\":5}");
        GrantRecordEntity record = record(prize, GrantRecordStatuses.FULFILL_NONE, null);

        noPoints.start(record, prize, null);

        assertThat(record.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
    }

    @Test
    void startInstantNonPointsSkipsEarnAndMarksReconWhenCategoryRequires() {
        PrizeEntity prize = prize(BuiltinCategories.ALIPAY_RED, RewardTargets.THIRD_PARTY, FulfillmentModes.INSTANT,
                null);
        GrantRecordEntity record = record(prize, GrantRecordStatuses.FULFILL_NONE, null);

        fulfillment.start(record, prize, categories.getByCode(BuiltinCategories.ALIPAY_RED));

        assertThat(points.calls()).isEmpty();
        assertThat(record.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
        assertThat(record.getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_PENDING);
    }

    @Test
    void startAsyncEntersSendingDueNow() {
        PrizeEntity prize = prize(BuiltinCategories.ALIPAY_RED, RewardTargets.THIRD_PARTY, FulfillmentModes.ASYNC,
                null);
        GrantRecordEntity record = record(prize, GrantRecordStatuses.FULFILL_NONE, null);
        record.setRetryCount(4);
        record.setFulfillFailReason(FulfillFailReasons.ADAPTER_ERROR);

        fulfillment.start(record, prize, categories.getByCode(BuiltinCategories.ALIPAY_RED));

        assertThat(record.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_SENDING);
        assertThat(record.getNextFulfillRetryAt()).isEqualTo(NOW_UTC);
        assertThat(record.getRetryCount()).isZero();
        assertThat(record.getFulfillFailReason()).isNull();
        assertThat(record.getFulfilledAt()).isNull();
        assertThat(record.getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_NONE);
    }

    // ---- third-party stub dispatch ----

    @Test
    void armStubWritesRefImmediatelyWithoutTxSynchronization() {
        PrizeEntity prize = alipayPrize(null);
        GrantRecordEntity record = insert(prize, GrantRecordStatuses.FULFILL_SENDING, null);

        fulfillment.armThirdPartyStub(record, prize, categories.getByCode(BuiltinCategories.ALIPAY_RED));

        assertThat(grants.getById(record.getId()).getFulfillmentRef()).isEqualTo("alipay-red:stub-" + record.getId());
    }

    @Test
    void armStubPrefersTypeParamsAdapterOverCategory() {
        PrizeEntity prize = alipayPrize("{\"adapterCode\":\"custom\"}");
        GrantRecordEntity record = insert(prize, GrantRecordStatuses.FULFILL_SENDING, null);

        fulfillment.armThirdPartyStub(record, prize, categories.getByCode(BuiltinCategories.ALIPAY_RED));

        assertThat(grants.getById(record.getId()).getFulfillmentRef()).isEqualTo("custom:stub-" + record.getId());
    }

    @Test
    void armStubFallsBackToStubAdapter() {
        PrizeEntity prize = alipayPrize(null);
        GrantRecordEntity record = insert(prize, GrantRecordStatuses.FULFILL_SENDING, null);

        fulfillment.armThirdPartyStub(record, prize, null);

        assertThat(grants.getById(record.getId()).getFulfillmentRef()).isEqualTo("stub:stub-" + record.getId());
    }

    @Test
    void armStubIsNoopForPlatformPrizeOrExistingRef() {
        PrizeEntity physical = prize(BuiltinCategories.PHYSICAL, RewardTargets.PLATFORM, FulfillmentModes.ASYNC, null);
        GrantRecordEntity platformRow = insert(physical, GrantRecordStatuses.FULFILL_SENDING, null);
        fulfillment.armThirdPartyStub(platformRow, physical, categories.getByCode(BuiltinCategories.PHYSICAL));
        assertThat(grants.getById(platformRow.getId()).getFulfillmentRef()).isNull();

        PrizeEntity alipay = alipayPrize(null);
        GrantRecordEntity armed = insert(alipay, GrantRecordStatuses.FULFILL_SENDING, "existing-ref");
        fulfillment.armThirdPartyStub(armed, alipay, categories.getByCode(BuiltinCategories.ALIPAY_RED));
        assertThat(grants.getById(armed.getId()).getFulfillmentRef()).isEqualTo("existing-ref");

        fulfillment.armThirdPartyStub(armed, null, null);
        assertThat(grants.getById(armed.getId()).getFulfillmentRef()).isEqualTo("existing-ref");
    }

    @Test
    void armStubDefersRefUntilAfterCommitInsideTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        PrizeEntity prize = alipayPrize(null);
        GrantRecordEntity record = insert(prize, GrantRecordStatuses.FULFILL_SENDING, null);

        fulfillment.armThirdPartyStub(record, prize, categories.getByCode(BuiltinCategories.ALIPAY_RED));
        assertThat(grants.getById(record.getId()).getFulfillmentRef()).isNull();

        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        assertThat(syncs).hasSize(1);
        syncs.forEach(TransactionSynchronization::afterCommit);
        assertThat(grants.getById(record.getId()).getFulfillmentRef()).isEqualTo("alipay-red:stub-" + record.getId());
    }

    // ---- events ----

    @Test
    void emitArrivedOnlyForArrivedRecords() {
        PrizeEntity prize = alipayPrize(null);
        GrantRecordEntity sending = insert(prize, GrantRecordStatuses.FULFILL_SENDING, "ref-s");
        fulfillment.emitArrived(sending);
        assertThat(outbox.all()).isEmpty();

        GrantRecordEntity arrived = insert(prize, GrantRecordStatuses.FULFILL_ARRIVED, "ref-a");
        fulfillment.emitArrived(arrived);
        assertThat(outbox.all()).singleElement().satisfies(event -> {
            assertThat(event.eventCode()).isEqualTo(EventCodes.REWARD_FULFILL_ARRIVED);
            assertThat(event.payload()).contains("\"fulfillmentRef\":\"ref-a\"").doesNotContain("fulfillFailReason");
        });
    }

    @Test
    void emitArrivedWithoutPublisherIsSilent() {
        FulfillmentService noEvents = new FulfillmentService(points, grants, null, Clock.fixed(NOW, ZoneOffset.UTC));
        GrantRecordEntity arrived = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_ARRIVED, "ref-a");

        noEvents.emitArrived(arrived);

        assertThat(outbox.all()).isEmpty();
    }

    // ---- callback / confirm ----

    @Test
    void callbackRejectsInvalidRef() {
        assertErrorCode(() -> fulfillment.callback(null, "SUCCESS", null), CommonErrorCodes.PARAM_INVALID);
        assertErrorCode(() -> fulfillment.callback("  ", "SUCCESS", null), CommonErrorCodes.PARAM_INVALID);
        assertErrorCode(() -> fulfillment.callback("x".repeat(65), "SUCCESS", null), CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void callbackTrimsRefAndRejectsUnknownResult() {
        GrantRecordEntity row = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-trim");

        assertErrorCode(() -> fulfillment.callback(" ref-trim ", "PENDING", null), CommonErrorCodes.PARAM_INVALID);
        assertThat(fulfillment.callback(" ref-trim ", "SUCCESS", null).fulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
        assertThat(grants.getById(row.getId()).getFulfilledAt()).isEqualTo(NOW_UTC);
    }

    @Test
    void successCallbackMarksReconAndEmitsArrivedEvent() {
        GrantRecordEntity row = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-ok");

        var response = fulfillment.callback("ref-ok", "SUCCESS", null);

        assertThat(response.superseded()).isFalse();
        GrantRecordEntity fresh = grants.getById(row.getId());
        assertThat(fresh.getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_PENDING);
        assertThat(outbox.all()).extracting(OutboxRecord::eventCode)
                .containsExactly(EventCodes.REWARD_FULFILL_ARRIVED);
    }

    @Test
    void failedCallbackSchedulesRetryBeforeMax() {
        GrantRecordEntity row = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-f");

        assertThat(fulfillment.callback("ref-f", "FAILED", FulfillFailReasons.ADAPTER_ERROR).fulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_SENDING);

        GrantRecordEntity fresh = grants.getById(row.getId());
        assertThat(fresh.getRetryCount()).isEqualTo(1);
        assertThat(fresh.getNextFulfillRetryAt()).isEqualTo(NOW_UTC.plusSeconds(60));
        assertThat(fresh.getFulfillFailReason()).isNull();
        assertThat(outbox.all()).isEmpty();
    }

    @Test
    void failedCallbackMapsReservedOrUnknownReasonToCallbackFailed() {
        settings.setFulfillRetryMax(1);
        GrantRecordEntity manual = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-m");
        GrantRecordEntity timeout = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-t");
        GrantRecordEntity unknown = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-u");
        GrantRecordEntity none = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-n");

        fulfillment.callback("ref-m", "FAILED", FulfillFailReasons.MANUAL);
        fulfillment.callback("ref-t", "FAILED", FulfillFailReasons.TIMEOUT);
        fulfillment.callback("ref-u", "FAILED", "SOMETHING_ELSE");
        fulfillment.callback("ref-n", "FAILED", null);

        for (GrantRecordEntity row : List.of(manual, timeout, unknown, none)) {
            GrantRecordEntity fresh = grants.getById(row.getId());
            assertThat(fresh.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
            assertThat(fresh.getFulfillFailReason()).isEqualTo(FulfillFailReasons.CALLBACK_FAILED);
        }
        assertThat(outbox.all()).hasSize(4).allSatisfy(event -> {
            assertThat(event.eventCode()).isEqualTo(EventCodes.REWARD_FULFILL_FAILED);
            assertThat(event.payload()).contains("\"fulfillFailReason\":\"CALLBACK_FAILED\"");
        });
    }

    @Test
    void finalFailureOnNonReconCategoryLeavesReconNone() {
        settings.setFulfillRetryMax(1);
        PrizeEntity physical = prize(BuiltinCategories.PHYSICAL, RewardTargets.PLATFORM, FulfillmentModes.ASYNC, null);
        GrantRecordEntity row = insert(physical, GrantRecordStatuses.FULFILL_SENDING, "ref-p");

        fulfillment.callback("ref-p", "FAILED", FulfillFailReasons.CHANNEL_REJECT);

        GrantRecordEntity fresh = grants.getById(row.getId());
        assertThat(fresh.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(fresh.getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_NONE);
    }

    @Test
    void callbackOnArrivedIsIdempotentForFailedResultToo() {
        GrantRecordEntity row = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_ARRIVED, "ref-done");

        var response = fulfillment.callback("ref-done", "FAILED", FulfillFailReasons.CHANNEL_REJECT);

        assertThat(response.fulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
        assertThat(response.superseded()).isFalse();
        assertThat(grants.getById(row.getId()).getRetryCount()).isZero();
        assertThat(outbox.all()).isEmpty();
    }

    @Test
    void callbackOnNonSendingRowIsRejected() {
        GrantRecordEntity failed = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_FAILED, "ref-ff");
        failed.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        assertErrorCode(() -> fulfillment.callback("ref-ff", "SUCCESS", null), RewardErrorCodes.FULFILL_NOT_SENDING);

        GrantRecordEntity notGranted = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-ng");
        notGranted.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
        assertErrorCode(() -> fulfillment.callback("ref-ng", "SUCCESS", null), RewardErrorCodes.FULFILL_NOT_SENDING);
    }

    @Test
    void confirmMissingRecordIsNotFound() {
        assertErrorCode(() -> fulfillment.confirm(999L), CommonErrorCodes.NOT_FOUND);
    }

    // ---- retry ----

    @Test
    void retryMissingRecordIsNotFound() {
        assertErrorCode(() -> fulfillment.retry(999L), CommonErrorCodes.NOT_FOUND);
    }

    @Test
    void retryRejectsNonFailedOrManualRows() {
        GrantRecordEntity sending = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-1");
        assertErrorCode(() -> fulfillment.retry(sending.getId()), RewardErrorCodes.FULFILL_NOT_RETRYABLE);

        GrantRecordEntity manual = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_FAILED, "ref-2");
        manual.setFulfillFailReason(FulfillFailReasons.MANUAL);
        assertErrorCode(() -> fulfillment.retry(manual.getId()), RewardErrorCodes.FULFILL_NOT_RETRYABLE);

        GrantRecordEntity notGranted = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_FAILED, "ref-3");
        notGranted.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
        assertErrorCode(() -> fulfillment.retry(notGranted.getId()), RewardErrorCodes.FULFILL_NOT_RETRYABLE);
    }

    @Test
    void retryResetsFailedRowToSendingAndArmsStub() {
        PrizeEntity prize = alipayPrize(null);
        GrantRecordEntity row = insert(prize, GrantRecordStatuses.FULFILL_FAILED, null);
        row.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        row.setRetryCount(3);

        var response = fulfillment.retry(row.getId());

        assertThat(response.fulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_SENDING);
        assertThat(response.superseded()).isFalse();
        GrantRecordEntity fresh = grants.getById(row.getId());
        assertThat(fresh.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_SENDING);
        assertThat(fresh.getFulfillFailReason()).isNull();
        assertThat(fresh.getRetryCount()).isZero();
        assertThat(fresh.getNextFulfillRetryAt()).isEqualTo(NOW_UTC);
        assertThat(fresh.getFulfillmentRef()).isEqualTo("alipay-red:stub-" + row.getId());
    }

    @Test
    void retryLosingCasIsNotRetryable() {
        GrantRecordEntity row = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_FAILED, "ref-cas");
        row.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        // Concurrent writer already moved the persisted fulfillment off FULFILL_FAILED; in-memory view is stale.
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_SENDING);
        grants.update(row);
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_FAILED);

        assertErrorCode(() -> fulfillment.retry(row.getId()), RewardErrorCodes.FULFILL_NOT_RETRYABLE);
    }

    // ---- tick ----

    @Test
    void tickArmsDueRetries() {
        GrantRecordEntity due = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, null);
        due.setNextFulfillRetryAt(NOW_UTC.minusMinutes(1));
        GrantRecordEntity later = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, null);
        later.setNextFulfillRetryAt(NOW_UTC.plusMinutes(5));

        assertThat(fulfillment.tick()).isEqualTo(1);

        assertThat(grants.getById(due.getId()).getFulfillmentRef()).isEqualTo("alipay-red:stub-" + due.getId());
        assertThat(grants.getById(later.getId()).getFulfillmentRef()).isNull();
    }

    @Test
    void tickTimeoutEmitsFailedEvent() {
        GrantRecordEntity row = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-old");
        row.setUpdatedAt(NOW_UTC.minusHours(settings.sendingTimeoutHours() + 1L));

        assertThat(fulfillment.tick()).isEqualTo(1);

        GrantRecordEntity fresh = grants.getById(row.getId());
        assertThat(fresh.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(fresh.getFulfillFailReason()).isEqualTo(FulfillFailReasons.TIMEOUT);
        assertThat(fresh.getUpdatedAt()).isEqualTo(NOW_UTC);
        assertThat(outbox.all()).singleElement().satisfies(event -> {
            assertThat(event.eventCode()).isEqualTo(EventCodes.REWARD_FULFILL_FAILED);
            assertThat(event.payload()).contains("\"fulfillFailReason\":\"TIMEOUT\"");
        });
    }

    @Test
    void tickMarksCrossDaySendingReconPendingOnlyForReconCategories() {
        // Business day (UTC+8) starts 2026-08-18T16:00Z for NOW; granted before that is cross-day.
        LocalDateTime yesterday = LocalDateTime.parse("2026-08-18T10:00:00");
        GrantRecordEntity alipay = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-x");
        alipay.setGrantedAt(yesterday);
        PrizeEntity physical = prize(BuiltinCategories.PHYSICAL, RewardTargets.PLATFORM, FulfillmentModes.ASYNC, null);
        GrantRecordEntity platform = insert(physical, GrantRecordStatuses.FULFILL_SENDING, null);
        platform.setGrantedAt(yesterday);
        GrantRecordEntity today = insert(alipayPrize(null), GrantRecordStatuses.FULFILL_SENDING, "ref-y");
        today.setGrantedAt(LocalDateTime.parse("2026-08-18T17:00:00"));

        assertThat(fulfillment.tick()).isEqualTo(1);

        assertThat(grants.getById(alipay.getId()).getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_PENDING);
        assertThat(grants.getById(platform.getId()).getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_NONE);
        assertThat(grants.getById(today.getId()).getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_NONE);
    }

    @Test
    void reconFallsBackToRecordCategoryWhenPrizeMissing() {
        settings.setFulfillRetryMax(1);
        GrantRecordEntity row = record(null, GrantRecordStatuses.FULFILL_SENDING, "ref-orphan");
        row.setPrizeId(4242L);
        row.setCategoryCode(BuiltinCategories.ALIPAY_RED);
        grants.insert(row);

        fulfillment.callback("ref-orphan", "FAILED", FulfillFailReasons.CHANNEL_REJECT);

        assertThat(grants.getById(row.getId()).getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_PENDING);
    }

    // ---- helpers ----

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, Object code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(code);
    }

    private PrizeEntity alipayPrize(String typeParams) {
        return prize(BuiltinCategories.ALIPAY_RED, RewardTargets.THIRD_PARTY, FulfillmentModes.ASYNC, typeParams);
    }

    private PrizeEntity prize(String categoryCode, String target, String mode, String typeParams) {
        PrizeEntity prize = new PrizeEntity();
        prize.setCode("p-" + seq.getAndIncrement());
        prize.setCategoryCode(categoryCode);
        prize.setRewardTarget(target);
        prize.setFulfillmentMode(mode);
        prize.setTypeParams(typeParams);
        prizes.insert(prize);
        return prize;
    }

    private GrantRecordEntity insert(PrizeEntity prize, String fulfillmentStatus, String ref) {
        GrantRecordEntity row = record(prize, fulfillmentStatus, ref);
        grants.insert(row);
        return row;
    }

    private GrantRecordEntity record(PrizeEntity prize, String fulfillmentStatus, String ref) {
        GrantRecordEntity row = new GrantRecordEntity();
        if (prize != null) {
            row.setPrizeId(prize.getId());
            row.setPrizeCode(prize.getCode());
            row.setCategoryCode(prize.getCategoryCode());
        }
        row.setCostFen(100);
        row.setReconStatus(GrantRecordStatuses.RECON_NONE);
        row.setUserId(9L);
        row.setGrantSource("TASK_STEP");
        row.setSourceId("src-" + seq.getAndIncrement());
        row.setStatus(GrantRecordStatuses.GRANTED);
        row.setFulfillmentStatus(fulfillmentStatus);
        row.setFulfillmentRef(ref);
        row.setRetryCount(0);
        row.setSimulated(0);
        row.setGrantedAt(NOW_UTC);
        row.setCreatedAt(NOW_UTC);
        row.setUpdatedAt(NOW_UTC);
        return row;
    }
}
