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
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.command.ReconImportLineCommand;
import com.mkt.reward.command.ReconReviewCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.FulfillFailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconResults;
import com.mkt.reward.domain.ReconReviewStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.response.ReconItemView;
import com.mkt.reward.response.ReconMatchResponse;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardGrantSettings;
import com.mkt.reward.support.RewardRuntimeSettings;
import com.mkt.reward.testsupport.CategoryFixtures;
import com.mkt.reward.testsupport.MemoryGrantRecordStore;
import com.mkt.reward.testsupport.MemoryPrizeCategoryStore;
import com.mkt.reward.testsupport.MemoryPrizeStore;
import com.mkt.reward.testsupport.MemoryReconBatchStore;
import com.mkt.reward.testsupport.MemoryReconItemStore;
import com.mkt.reward.testsupport.MemorySnapshotPrizeLookup;
import com.mkt.reward.testsupport.MemoryStockLogStore;
import com.mkt.reward.testsupport.RecordingPointsPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ReconAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T04:00:00Z");
    private static final LocalDate BILL = LocalDate.of(2026, 8, 19);

    private MemoryPrizeStore prizes;
    private MemoryPrizeCategoryStore categories;
    private MemoryGrantRecordStore grants;
    private MemoryReconItemStore items;
    private GrantAppService grant;
    private FulfillmentService fulfillment;
    private ReconAppService recon;
    private RewardRuntimeSettings settings;
    private Clock clock;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(7L, "admin", "op"));
        prizes = new MemoryPrizeStore();
        categories = new MemoryPrizeCategoryStore();
        grants = new MemoryGrantRecordStore();
        MemoryStockLogStore logs = new MemoryStockLogStore();
        MemoryReconBatchStore batches = new MemoryReconBatchStore();
        items = new MemoryReconItemStore();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        categories.seed(CategoryFixtures.alipay());
        categories.seed(CategoryFixtures.points());
        PrizeAppService prizesApp = new PrizeAppService(prizes, categories, logs, new MemorySnapshotPrizeLookup(), clock);
        EventPublisher events = new EventPublisher(new MemoryOutboxStore(), OutboxProducer.PORTAL);
        settings = new RewardRuntimeSettings();
        fulfillment = new FulfillmentService(
                new RecordingPointsPort(), grants, prizes, categories, events, settings, clock);
        grant = new GrantAppService(
                prizes,
                categories,
                grants,
                logs,
                new StubUsers(),
                new StubRisk(),
                events,
                new GrantFailureLedger(grants, events, clock, new RewardGrantSettings(), null),
                fulfillment,
                null,
                clock);
        recon = new ReconAppService(
                batches, items, grants, prizes, categories, grant, fulfillment, settings, clock);
        enableAlipay(prizesApp, "red_recon", 50);
        enablePoints(prizesApp);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void matchExhaustsFourResults() {
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity arrived = pendingGrant(prizeId, "s-a", "alipay-red:a", GrantRecordStatuses.FULFILL_ARRIVED, 100);
        GrantRecordEntity sending = pendingGrant(prizeId, "s-s", "alipay-red:s", GrantRecordStatuses.FULFILL_SENDING, 100);
        GrantRecordEntity failed = pendingGrant(prizeId, "s-f", "alipay-red:f", GrantRecordStatuses.FULFILL_FAILED, 80);
        failed.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        grants.update(failed);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.importLines(
                batch.id(),
                new ReconImportCommand(List.of(
                        new ReconImportLineCommand("alipay-red:a", 100, null),
                        new ReconImportLineCommand("alipay-red:x", 50, null),
                        new ReconImportLineCommand("alipay-red:f", 90, null))));
        ReconMatchResponse matched = recon.match(batch.id());
        assertThat(matched.matchedCount()).isEqualTo(1);
        assertThat(matched.platformOnly()).isEqualTo(1);
        assertThat(matched.channelOnly()).isEqualTo(1);
        assertThat(matched.amountMismatch()).isEqualTo(1);
        assertThat(matched.platformCount()).isEqualTo(3);
        assertThat(matched.channelCount()).isEqualTo(3);
        assertThat(items.listAll(batch.id())).hasSize(4);
        assertThat(grants.getById(arrived.getId()).getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_MATCHED);
        assertThat(grants.getById(sending.getId()).getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_DIFF);
        assertThat(grants.getById(failed.getId()).getReconStatus()).isEqualTo(GrantRecordStatuses.RECON_DIFF);
    }

    @Test
    void timeoutUnreviewedRefusesRefulfillAndManualGrant() {
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity failed = pendingGrant(prizeId, "s-t", "alipay-red:t", GrantRecordStatuses.FULFILL_FAILED, 100);
        failed.setFulfillFailReason(FulfillFailReasons.TIMEOUT);
        grants.update(failed);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        ReconItemView item = recon.items(batch.id(), ReconResults.PLATFORM_ONLY, null, null).records().getFirst();
        assertThat(item.reviewStatus()).isEqualTo(ReconReviewStatuses.PENDING_REVIEW);
        assertThatThrownBy(() -> recon.action(item.id(), new ReconActionCommand(ReconActions.REFULFILL, "x", null, null)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_REVIEW_REQUIRED);
        assertThatThrownBy(() -> recon.action(
                        item.id(), new ReconActionCommand(ReconActions.MANUAL_GRANT, "x", 9L, prizeId)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_REVIEW_REQUIRED);
        assertThat(grants.getById(failed.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(49);
    }

    @Test
    void autoPathNeverManualGrant() {
        settings.setAutoRefulfillEnabled(true);
        long prizeId = prizes.getByCode("red_recon").getId();
        prizes.getById(prizeId).setReconActionPolicy(com.mkt.reward.domain.ReconPolicies.AUTO);
        GrantRecordEntity failed = pendingGrant(prizeId, "s-au", "alipay-red:au", GrantRecordStatuses.FULFILL_FAILED, 100);
        failed.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        grants.update(failed);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        List.copyOf(TransactionSynchronizationManager.getSynchronizations())
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        assertThat(grants.all())
                .filteredOn(row -> "MANUAL_GRANT".equals(row.getGrantSource()))
                .isEmpty();
        assertThat(grants.getById(failed.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_SENDING);
        ReconItemView item = recon.items(batch.id(), ReconResults.PLATFORM_ONLY, null, null).records().getFirst();
        assertThat(item.action()).isEqualTo(ReconActions.REFULFILL);
        assertThatThrownBy(() -> recon.action(
                        item.id(), new ReconActionCommand(ReconActions.MANUAL_GRANT, "x", 9L, prizeId)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_ACTION_DONE);
    }

    @Test
    void autoOffDoesNotRefulfill() {
        settings.setAutoRefulfillEnabled(false);
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity failed = pendingGrant(prizeId, "s-off", "alipay-red:off", GrantRecordStatuses.FULFILL_FAILED, 100);
        failed.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        grants.update(failed);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        assertThat(grants.getById(failed.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
    }

    @Test
    void manualGrantClosesOriginalAndCallbackStaysFailed() {
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity original =
                pendingGrant(prizeId, "s-mg", "alipay-red:mg", GrantRecordStatuses.FULFILL_FAILED, 100);
        original.setFulfillFailReason(FulfillFailReasons.CHANNEL_REJECT);
        grants.update(original);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        ReconItemView item = recon.items(batch.id(), ReconResults.PLATFORM_ONLY, null, null).records().getFirst();
        recon.action(item.id(), new ReconActionCommand(ReconActions.MANUAL_GRANT, "补发", 9L, prizeId));
        GrantRecordEntity closed = grants.getById(original.getId());
        assertThat(closed.getFulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(closed.getFulfillFailReason()).isEqualTo(FulfillFailReasons.MANUAL);
        assertThat(closed.getFulfillmentRef()).isEqualTo("alipay-red:mg");
        var callback = fulfillment.callback("alipay-red:mg", "SUCCESS", null);
        assertThat(callback.superseded()).isTrue();
        assertThat(callback.fulfillmentStatus()).isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(grants.getById(original.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(48);
    }

    @Test
    void rejectedReviewOnlyAbsorb() {
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity failed = pendingGrant(prizeId, "s-rj", "alipay-red:rj", GrantRecordStatuses.FULFILL_FAILED, 100);
        failed.setFulfillFailReason(FulfillFailReasons.TIMEOUT);
        grants.update(failed);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        ReconItemView item =
                recon.items(batch.id(), null, ReconReviewStatuses.PENDING_REVIEW, null).records().getFirst();
        recon.review(item.id(), new ReconReviewCommand("REJECT", "渠道已出款"));
        assertThatThrownBy(() -> recon.action(item.id(), new ReconActionCommand(ReconActions.REFULFILL, "x", null, null)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_ACTION_FORBIDDEN);
        assertThatThrownBy(() -> recon.action(
                        item.id(), new ReconActionCommand(ReconActions.MANUAL_GRANT, "x", 9L, prizeId)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_ACTION_FORBIDDEN);
        recon.action(item.id(), new ReconActionCommand(ReconActions.ABSORB, "认领", null, null));
        assertThat(grants.getById(failed.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(49);
    }

    @Test
    void sendingUnreviewedRefusesRefulfill() {
        long prizeId = prizes.getByCode("red_recon").getId();
        pendingGrant(prizeId, "s-sn", "alipay-red:sn", GrantRecordStatuses.FULFILL_SENDING, 100);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        ReconItemView item = recon.items(batch.id(), ReconResults.PLATFORM_ONLY, null, null).records().getFirst();
        assertThat(item.reviewStatus()).isEqualTo(ReconReviewStatuses.PENDING_REVIEW);
        assertThatThrownBy(() -> recon.action(item.id(), new ReconActionCommand(ReconActions.REFULFILL, "x", null, null)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_REVIEW_REQUIRED);
        assertThatThrownBy(() -> recon.action(
                        item.id(), new ReconActionCommand(ReconActions.MANUAL_GRANT, "x", 9L, prizeId)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_REVIEW_REQUIRED);
    }

    @Test
    void callbackFailedUnlistedIsForbidden() {
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity failed =
                pendingGrant(prizeId, "s-cb", "alipay-red:cb", GrantRecordStatuses.FULFILL_FAILED, 100);
        failed.setFulfillFailReason(FulfillFailReasons.CALLBACK_FAILED);
        grants.update(failed);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        ReconItemView item = recon.items(batch.id(), ReconResults.PLATFORM_ONLY, null, null).records().getFirst();
        assertThatThrownBy(() -> recon.action(item.id(), new ReconActionCommand(ReconActions.REFULFILL, "x", null, null)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_ACTION_FORBIDDEN);
    }

    @Test
    void duplicateImportRejected() {
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        assertThatThrownBy(() -> recon.importLines(
                        batch.id(),
                        new ReconImportCommand(List.of(
                                new ReconImportLineCommand("alipay-red:dup", 1, null),
                                new ReconImportLineCommand("alipay-red:dup", 2, null)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void simulatedAndPointsStayOutOfPlatformSet() {
        long alipay = prizes.getByCode("red_recon").getId();
        GrantRecordEntity sim = pendingGrant(alipay, "s-sim", "alipay-red:sim", GrantRecordStatuses.FULFILL_ARRIVED, 100);
        sim.setSimulated(1);
        grants.update(sim);
        long pointsId = prizes.getByCode("pts_recon").getId();
        grant.grant(pointsId, 9L, GrantSource.TASK_STEP, "s-pts", GrantContext.defaults());
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        ReconMatchResponse matched = recon.match(batch.id());
        assertThat(matched.platformCount()).isZero();
    }

    @Test
    void secondActionRejected() {
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity arrived =
                pendingGrant(prizeId, "s-2", "alipay-red:2", GrantRecordStatuses.FULFILL_ARRIVED, 100);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        ReconItemView item = recon.items(batch.id(), ReconResults.PLATFORM_ONLY, null, null).records().getFirst();
        recon.action(item.id(), new ReconActionCommand(ReconActions.ABSORB, "认领", null, null));
        assertThatThrownBy(() -> recon.action(item.id(), new ReconActionCommand(ReconActions.ABSORB, "再", null, null)))
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RECON_ACTION_DONE);
        assertThat(arrived.getId()).isPositive();
    }

    @Test
    void reviewConfirmDoesNotAutoGrant() {
        long prizeId = prizes.getByCode("red_recon").getId();
        GrantRecordEntity failed =
                pendingGrant(prizeId, "s-rv", "alipay-red:rv", GrantRecordStatuses.FULFILL_FAILED, 100);
        failed.setFulfillFailReason(FulfillFailReasons.TIMEOUT);
        grants.update(failed);
        ReconBatchResponse batch = recon.create(new ReconBatchCreateCommand(BuiltinCategories.ALIPAY_RED, BILL));
        recon.match(batch.id());
        ReconItemView item = recon.items(batch.id(), null, ReconReviewStatuses.PENDING_REVIEW, null).records().getFirst();
        recon.review(item.id(), new ReconReviewCommand("CONFIRM", "已核渠未出款"));
        assertThat(grants.getById(failed.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_FAILED);
        recon.action(item.id(), new ReconActionCommand(ReconActions.REFULFILL, "重试", null, null));
        assertThat(grants.getById(failed.getId()).getFulfillmentStatus())
                .isEqualTo(GrantRecordStatuses.FULFILL_SENDING);
    }

    private GrantRecordEntity pendingGrant(
            long prizeId, String sourceId, String ref, String fulfillment, int costFen) {
        long id = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, sourceId, GrantContext.defaults()).recordId();
        GrantRecordEntity row = grants.getById(id);
        row.setFulfillmentStatus(fulfillment);
        row.setFulfillmentRef(ref);
        row.setCostFen(costFen);
        row.setReconStatus(GrantRecordStatuses.RECON_PENDING);
        if (GrantRecordStatuses.FULFILL_ARRIVED.equals(fulfillment)) {
            row.setFulfilledAt(row.getGrantedAt());
        }
        grants.update(row);
        return row;
    }

    private static void enableAlipay(PrizeAppService prizesApp, String code, int stock) {
        var created = prizesApp.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                BuiltinCategories.ALIPAY_RED,
                Map.of("faceFen", 100),
                null,
                stock,
                0,
                0,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
                null,
                null));
        prizesApp.enable(created.id(), new PrizeConfirmCommand(true));
    }

    private static void enablePoints(PrizeAppService prizesApp) {
        var created = prizesApp.create(new PrizeSaveCommand(
                "pts_recon",
                "pts",
                null,
                null,
                BuiltinCategories.POINTS,
                Map.of("points", 10),
                null,
                10,
                0,
                0,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
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
