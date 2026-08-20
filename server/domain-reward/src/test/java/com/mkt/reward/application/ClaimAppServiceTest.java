package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.infra.lock.LockAcquire;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.response.ClaimResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardGrantSettings;
import com.mkt.reward.support.RewardRuntimeSettings;
import com.mkt.reward.testsupport.CategoryFixtures;
import com.mkt.reward.testsupport.MemoryGrantRecordStore;
import com.mkt.reward.testsupport.MemoryPrizeCategoryStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import com.mkt.reward.testsupport.MemorySnapshotPrizeLookup;
import com.mkt.reward.testsupport.MemoryStockLogStore;
import com.mkt.reward.testsupport.RecordingPointsPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ClaimAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryPrizeStore prizes;
    private MemoryPrizeCategoryStore categories;
    private MemoryGrantRecordStore grants;
    private GrantAppService grant;
    private ClaimAppService claims;
    private RewardRuntimeSettings settings;
    private Clock clock;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        prizes = new MemoryPrizeStore();
        categories = new MemoryPrizeCategoryStore();
        grants = new MemoryGrantRecordStore();
        MemoryStockLogStore logs = new MemoryStockLogStore();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        categories.seed(CategoryFixtures.alipay());
        categories.seed(CategoryFixtures.points());
        categories.seed(CategoryFixtures.coupon());
        PrizeAppService prizesApp = new PrizeAppService(prizes, categories, logs, new MemorySnapshotPrizeLookup(), clock);
        EventPublisher events = new EventPublisher(new MemoryOutboxStore(), OutboxProducer.PORTAL);
        GrantFailureLedger ledger =
                new GrantFailureLedger(grants, events, clock, new RewardGrantSettings(), null);
        FulfillmentService fulfillment = new FulfillmentService(
                new RecordingPointsPort(), grants, prizes, categories, events, new RewardRuntimeSettings(), clock);
        grant = new GrantAppService(
                prizes, categories, grants, logs, new StubUsers(), new StubRisk(), events, ledger, fulfillment, null, clock);
        settings = new RewardRuntimeSettings();
        claims = new ClaimAppService(grants, prizes, categories, fulfillment, (PlatformLock) null, settings, clock);
        enableCoupon(prizesApp);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void claimWonStartsFulfillment() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-c", GrantContext.defaults()).recordId();
        ClaimResponse response = claims.claim(recordId, 9L);
        assertThat(response.status()).isEqualTo(GrantRecordStatuses.GRANTED);
        assertThat(response.fulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
        GrantRecordEntity row = grants.getById(recordId);
        assertThat(row.getClaimedAt()).isNotNull();
        assertThat(row.getGrantedAt()).isNotNull();
    }

    @Test
    void claimRetryPendingIsAllowed() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-rp", GrantContext.defaults()).recordId();
        GrantRecordEntity row = grants.getById(recordId);
        row.setStatus(GrantRecordStatuses.RETRY_PENDING);
        row.setRetryCount(1);
        row.setNextRetryAt(row.getExpireAt());
        grants.update(row);
        ClaimResponse response = claims.claim(recordId, 9L);
        assertThat(response.status()).isEqualTo(GrantRecordStatuses.GRANTED);
    }

    @Test
    void claimKeepsExpireFlipWhenBusinessExceptionLeaves() throws Exception {
        Transactional tx = ClaimAppService.class
                .getMethod("claim", long.class, long.class)
                .getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.noRollbackFor()).contains(BusinessException.class);
    }

    @Test
    void expiredWonIsRejected() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-ex", GrantContext.defaults()).recordId();
        GrantRecordEntity row = grants.getById(recordId);
        row.setExpireAt(row.getCreatedAt().minusHours(1));
        grants.update(row);
        assertThatThrownBy(() -> claims.claim(recordId, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.CLAIM_EXPIRED);
        assertThat(grants.getById(recordId).getStatus()).isEqualTo(GrantRecordStatuses.EXPIRED);
    }

    @Test
    void grantedIsIdempotent() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-id", GrantContext.defaults()).recordId();
        claims.claim(recordId, 9L);
        ClaimResponse again = claims.claim(recordId, 9L);
        assertThat(again.status()).isEqualTo(GrantRecordStatuses.GRANTED);
    }

    @Test
    void otherUserCannotClaim() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-ot", GrantContext.defaults()).recordId();
        assertThatThrownBy(() -> claims.claim(recordId, 8L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void busyLockIsConflict() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-lk", GrantContext.defaults()).recordId();
        PlatformLock busy = org.mockito.Mockito.mock(PlatformLock.class);
        org.mockito.Mockito.when(busy.tryClaimLock(recordId)).thenReturn(LockAcquire.BUSY);
        ClaimAppService locked = new ClaimAppService(
                grants,
                prizes,
                categories,
                new FulfillmentService(new RecordingPointsPort(), grants, null, clock),
                busy,
                settings,
                clock);
        assertThatThrownBy(() -> locked.claim(recordId, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.CLAIM_CONFLICT);
    }

    @Test
    void claimingTimeoutRollsBackToWon() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-to", GrantContext.defaults()).recordId();
        GrantRecordEntity row = grants.getById(recordId);
        row.setStatus(GrantRecordStatuses.CLAIMING);
        row.setUpdatedAt(row.getCreatedAt().minusSeconds(60));
        grants.update(row);
        assertThat(claims.rollbackClaimingTimeout()).isEqualTo(1);
        GrantRecordEntity fresh = grants.getById(recordId);
        assertThat(fresh.getStatus()).isEqualTo(GrantRecordStatuses.WON);
        assertThat(fresh.getRetryCount()).isEqualTo(1);
    }

    @Test
    void expireDueFlipsWonAndRetryPending() {
        long prizeId = prizes.getByCode("cpn_claim").getId();
        long recordId = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-ed", GrantContext.defaults()).recordId();
        GrantRecordEntity row = grants.getById(recordId);
        row.setExpireAt(row.getCreatedAt().minusMinutes(1));
        grants.update(row);
        assertThat(claims.expireDue()).isEqualTo(1);
        assertThat(grants.getById(recordId).getStatus()).isEqualTo(GrantRecordStatuses.EXPIRED);
    }

    private void enableCoupon(PrizeAppService prizesApp) {
        var created = prizesApp.create(new PrizeSaveCommand(
                "cpn_claim",
                "coupon",
                null,
                null,
                BuiltinCategories.COUPON,
                java.util.Map.of(),
                null,
                20,
                0,
                0,
                null,
                null,
                null,
                "MANUAL",
                null,
                24,
                null,
                null));
        prizesApp.enable(created.id(), new PrizeConfirmCommand(true));
    }

    private static final class StubUsers implements UserAttributePort {
        @Override
        public UserAttributes attributes(long userId) {
            return new UserAttributes("GD", "user", "1", 1, List.of(), Instant.EPOCH, AccountStatus.ACTIVE);
        }

        @Override
        public UserAttributes lockAndGet(long userId) {
            return attributes(userId);
        }
    }

    private static final class StubRisk implements RiskCheckPort {
        @Override
        public RiskVerdict check(RiskScene scene, RiskSubject subject) {
            return new RiskVerdict(RiskAction.PASS);
        }

        @Override
        public UserRiskSummary userSummary(long userId) {
            return new UserRiskSummary(0L, List.of());
        }
    }
}
