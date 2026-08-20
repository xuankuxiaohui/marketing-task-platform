package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class FulfillmentOpsTest {

    private MemoryGrantRecordStore grants;
    private FulfillmentService fulfillment;
    private RewardRuntimeSettings settings;
    private Clock clock;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        grants = new MemoryGrantRecordStore();
        MemoryPrizeStore prizes = new MemoryPrizeStore();
        MemoryPrizeCategoryStore categories = new MemoryPrizeCategoryStore();
        categories.seed(CategoryFixtures.alipay());
        clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        settings = new RewardRuntimeSettings();
        settings.setFulfillRetryMax(2);
        fulfillment = new FulfillmentService(
                new RecordingPointsPort(),
                grants,
                prizes,
                categories,
                new EventPublisher(new MemoryOutboxStore(), OutboxProducer.PORTAL),
                settings,
                clock);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void successCallbackArrivesOnce() {
        GrantRecordEntity row = sending("ref-1");
        assertThat(fulfillment.callback("ref-1", "SUCCESS", null).fulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
        assertThat(fulfillment.callback("ref-1", "SUCCESS", null).fulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
        assertThat(grants.getById(row.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
    }

    @Test
    void failedCallbackRetriesThenFails() {
        sending("ref-2");
        assertThat(fulfillment.callback("ref-2", "FAILED", FulfillFailReasons.CHANNEL_REJECT).fulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_SENDING);
        assertThat(fulfillment.callback("ref-2", "FAILED", FulfillFailReasons.CHANNEL_REJECT).fulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(grants.getByFulfillmentRef("ref-2").getFulfillFailReason())
                .isEqualTo(FulfillFailReasons.CHANNEL_REJECT);
        assertThat(grants.getByFulfillmentRef("ref-2").getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_PENDING);
    }

    @Test
    void unknownRefIsNotFound() {
        assertThatThrownBy(() -> fulfillment.callback("missing", "SUCCESS", null))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.FULFILL_NOT_FOUND);
    }

    @Test
    void timeoutTickWritesTimeoutAndPendingRecon() {
        GrantRecordEntity row = sending("ref-t");
        row.setUpdatedAt(LocalDateTime.parse("2026-08-17T00:00:00"));
        grants.update(row);
        fulfillment.tick();
        GrantRecordEntity fresh = grants.getById(row.getId());
        assertThat(fresh.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(fresh.getFulfillFailReason()).isEqualTo(FulfillFailReasons.TIMEOUT);
        assertThat(fresh.getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_PENDING);
    }

    @Test
    void confirmArrives() {
        GrantRecordEntity row = sending("ref-c");
        assertThat(fulfillment.confirm(row.getId()).fulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
    }

    @Test
    void manualCloseRejectsConfirm() {
        GrantRecordEntity row = sending("ref-m");
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_FAILED);
        row.setFulfillFailReason(FulfillFailReasons.MANUAL);
        grants.update(row);
        assertThat(fulfillment.confirm(row.getId()).superseded()).isTrue();
        assertThat(grants.getById(row.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
    }

    private GrantRecordEntity sending(String ref) {
        GrantRecordEntity row = new GrantRecordEntity();
        row.setPrizeId(1L);
        row.setPrizeCode("red");
        row.setCategoryCode("ALIPAY_RED");
        row.setCostFen(100);
        row.setReconStatus(GrantRecordStatuses.RECON_NONE);
        row.setUserId(9L);
        row.setGrantSource("TASK_STEP");
        row.setSourceId(ref);
        row.setStatus(GrantRecordStatuses.GRANTED);
        row.setFulfillmentStatus(GrantRecordStatuses.FULFILL_SENDING);
        row.setFulfillmentRef(ref);
        row.setRetryCount(0);
        row.setSimulated(0);
        LocalDateTime now = LocalDateTime.parse("2026-08-19T00:00:00");
        row.setGrantedAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        grants.insert(row);
        return row;
    }
}
