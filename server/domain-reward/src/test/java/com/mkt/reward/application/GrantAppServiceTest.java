package com.mkt.reward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.BypassRule;
import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.PermanentGrantException;
import com.mkt.contract.PermanentGrantReason;
import com.mkt.contract.RetryableGrantException;
import com.mkt.contract.RetryableGrantReason;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.command.ManualGrantCommand;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.domain.BuiltinCategories;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.support.RewardErrorCodes;
import com.mkt.reward.support.RewardGrantSettings;
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
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class GrantAppServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private MemoryPrizeStore prizes;
    private MemoryPrizeCategoryStore categories;
    private MemoryGrantRecordStore grants;
    private MemoryStockLogStore logs;
    private MemoryOutboxStore outbox;
    private RecordingPointsPort points;
    private StubRisk risk;
    private StubUsers users;
    private PrizeAppService prizesApp;
    private GrantAppService grant;
    private Clock clock;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        prizes = new MemoryPrizeStore();
        categories = new MemoryPrizeCategoryStore();
        grants = new MemoryGrantRecordStore();
        logs = new MemoryStockLogStore();
        outbox = new MemoryOutboxStore();
        points = new RecordingPointsPort();
        risk = new StubRisk();
        users = new StubUsers();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        categories.seed(CategoryFixtures.alipay());
        categories.seed(CategoryFixtures.points());
        categories.seed(CategoryFixtures.coupon());
        prizesApp = new PrizeAppService(prizes, categories, logs, new MemorySnapshotPrizeLookup(), clock);
        EventPublisher events = new EventPublisher(outbox, OutboxProducer.PORTAL);
        RewardGrantSettings settings = new RewardGrantSettings();
        GrantFailureLedger ledger = new GrantFailureLedger(grants, events, clock, settings, null);
        FulfillmentService fulfillment = new FulfillmentService(points, grants, events, clock);
        grant = new GrantAppService(
                prizes,
                categories,
                grants,
                logs,
                users,
                risk,
                events,
                ledger,
                fulfillment,
                null,
                clock);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void autoGrantWritesGrantedAndSendingForAsync() {
        long prizeId = enableAlipay("red_g", 3);
        GrantResult result = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "step-1", GrantContext.defaults());
        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
        assertThat(result.fulfillmentStatus()).isEqualTo(FulfillmentStatus.SENDING);
        assertThat(result.hitIdempotent()).isFalse();
        GrantRecordEntity row = grants.getById(result.recordId());
        assertThat(row.getCostFen()).isEqualTo(100);
        assertThat(row.getFaceFen()).isEqualTo(100);
        assertThat(outbox.claimBatch("portal", NOW, 10))
                .anyMatch(r -> EventCodes.REWARD_GRANT_SUCCESS.equals(r.eventCode()));
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(2);
    }

    @Test
    void instantPointsCallsEarnAndArrives() {
        long prizeId = enablePoints("pts_g", 5, 10);
        GrantResult result = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "step-p", GrantContext.defaults());
        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
        assertThat(result.fulfillmentStatus()).isEqualTo(FulfillmentStatus.ARRIVED);
        assertThat(points.calls()).hasSize(1);
        assertThat(points.calls().get(0).points()).isEqualTo(10);
    }

    @Test
    void grantedShortCircuits() {
        long prizeId = enableAlipay("red_id", 3);
        GrantResult first = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s", GrantContext.defaults());
        GrantResult again = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s", GrantContext.defaults());
        assertThat(again.hitIdempotent()).isTrue();
        assertThat(again.recordId()).isEqualTo(first.recordId());
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(2);
    }

    @Test
    void retryPendingDoesNotShortCircuit() {
        long prizeId = enableAlipay("red_rp", 1);
        prizes.getById(prizeId).setRemainingStock(0);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-rp", GrantContext.defaults()))
                .isInstanceOf(RetryableGrantException.class)
                .extracting(ex -> ((RetryableGrantException) ex).reason())
                .isEqualTo(RetryableGrantReason.STOCK_INSUFFICIENT);
        GrantRecordEntity pending = grants.getByIdempotent("TASK_STEP", "s-rp", prizeId);
        assertThat(pending.getStatus()).isEqualTo(GrantRecordStatuses.RETRY_PENDING);
        assertThat(pending.getNextRetryAt()).isNotNull();
        prizes.getById(prizeId).setRemainingStock(1);
        GrantResult result = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-rp", GrantContext.defaults());
        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
        assertThat(result.hitIdempotent()).isFalse();
    }

    @Test
    void inactiveUserIsPermanentInvalid() {
        users.status = AccountStatus.DISABLED;
        long prizeId = enableAlipay("red_u", 2);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-u", GrantContext.defaults()))
                .isInstanceOf(PermanentGrantException.class)
                .extracting(ex -> ((PermanentGrantException) ex).reason())
                .isEqualTo(PermanentGrantReason.USER_INVALID);
        assertThat(grants.getByIdempotent("TASK_STEP", "s-u", prizeId).getStatus())
                .isEqualTo(GrantRecordStatuses.PERMANENT_FAILED);
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(2);
    }

    @Test
    void riskRejectHasZeroSideEffects() {
        risk.reject = true;
        long prizeId = enableAlipay("red_rk", 4);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-rk", GrantContext.defaults()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.RISK_BLOCKED_GENERIC);
        assertThat(grants.all()).isEmpty();
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(4);
        assertThat(points.calls()).isEmpty();
    }

    @Test
    void grantRiskUsesClientIpNotDummy() {
        long prizeId = enableAlipay("red_ip", 2);
        GrantContext ctx = GrantContext.defaults().withClient("203.0.113.9", "dev-9");
        grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-ip", ctx);
        assertThat(risk.last.ip()).isEqualTo("203.0.113.9");
        assertThat(risk.last.deviceId()).isEqualTo("dev-9");
        grant.grant(prizeId, 8L, GrantSource.TASK_STEP, "s-ip2", GrantContext.defaults());
        assertThat(risk.last.ip()).isNull();
    }

    @Test
    void regionMissRejected() {
        long prizeId = enableAlipayLimited("red_reg", 2, 0, 0, List.of("BJ"), null, null);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-reg", GrantContext.defaults()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.GRANT_COMBO_INVALID);
        assertThat(grants.all()).isEmpty();
    }

    @Test
    void manualGrantBypassesRegion() {
        UserContext.set(new UserPrincipal(7L, "admin", "op"));
        long prizeId = enableAlipayLimited("red_mg", 2, 0, 0, List.of("BJ"), null, null);
        var response = grant.manualGrant(new ManualGrantCommand(9L, prizeId, "补发", List.of("REGION")));
        assertThat(response.status()).isEqualTo(GrantStatus.GRANTED.name());
        GrantRecordEntity row = grants.getById(response.recordId());
        assertThat(row.getGrantSource()).isEqualTo(GrantSource.MANUAL_GRANT.name());
        assertThat(logs.all().get(logs.all().size() - 1).getChangeType()).isEqualTo("MANUAL_GRANT");
    }

    @Test
    void retryRejectsWon() {
        long prizeId = enableManual("cpn_w", 2);
        GrantResult result = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-w", GrantContext.defaults());
        assertThat(result.status()).isEqualTo(GrantStatus.WON);
        assertThatThrownBy(() -> grant.retry(result.recordId()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.GRANT_NOT_RETRYABLE);
    }

    @Test
    void usersNeverQueriedDirectly() {
        long prizeId = enableAlipay("red_attr", 1);
        grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-attr", GrantContext.defaults());
        assertThat(users.attributeCalls).isEqualTo(1);
        assertThat(users.lockCalls).isZero();
    }

    @Test
    void retryPendingIgnoresOwnClaimLimitSlot() {
        long prizeId = enableAlipayLimited("red_lim", 5, 1, 0, null, null, null);
        prizes.getById(prizeId).setRemainingStock(0);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-lim", GrantContext.defaults()))
                .isInstanceOf(RetryableGrantException.class);
        prizes.getById(prizeId).setRemainingStock(2);
        GrantResult result = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-lim", GrantContext.defaults());
        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
    }

    @Test
    void retryMaxTurnsPermanent() {
        RewardGrantSettings tight = new RewardGrantSettings();
        tight.setRetryMax(1);
        EventPublisher events = new EventPublisher(outbox, OutboxProducer.PORTAL);
        GrantFailureLedger ledger = new GrantFailureLedger(grants, events, clock, tight, null);
        FulfillmentService fulfillment = new FulfillmentService(points, grants, events, clock);
        grant = new GrantAppService(
                prizes, categories, grants, logs, users, risk, events, ledger, fulfillment, null, clock);
        long prizeId = enableAlipay("red_max", 1);
        prizes.getById(prizeId).setRemainingStock(0);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-max", GrantContext.defaults()))
                .isInstanceOf(RetryableGrantException.class);
        assertThat(grants.getByIdempotent("TASK_STEP", "s-max", prizeId).getStatus())
                .isEqualTo(GrantRecordStatuses.RETRY_PENDING);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-max", GrantContext.defaults()))
                .isInstanceOf(RetryableGrantException.class);
        assertThat(grants.getByIdempotent("TASK_STEP", "s-max", prizeId).getStatus())
                .isEqualTo(GrantRecordStatuses.PERMANENT_FAILED);
    }

    @Test
    void prizeDisabledIsPermanent() {
        long prizeId = enableAlipay("red_dis", 2);
        prizes.getById(prizeId).setStatus(com.mkt.reward.domain.PrizeStatuses.DISABLED);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-dis", GrantContext.defaults()))
                .isInstanceOf(PermanentGrantException.class)
                .extracting(ex -> ((PermanentGrantException) ex).reason())
                .isEqualTo(PermanentGrantReason.PRIZE_DISABLED);
        assertThat(grants.getByIdempotent("TASK_STEP", "s-dis", prizeId).getFailReason())
                .isEqualTo("PRIZE_DISABLED");
        assertThat(prizes.getById(prizeId).getRemainingStock()).isEqualTo(2);
    }

    @Test
    void manualGrantStockInsufficient() {
        UserContext.set(new UserPrincipal(7L, "admin", "op"));
        long prizeId = enableAlipay("red_stk", 1);
        prizes.getById(prizeId).setRemainingStock(0);
        assertThatThrownBy(() -> grant.manualGrant(new ManualGrantCommand(9L, prizeId, "补发", List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RewardErrorCodes.STOCK_INSUFFICIENT);
        assertThat(grants.all()).isEmpty();
    }

    @Test
    void instantCouponArrivesWithoutPoints() {
        long prizeId = enableCouponAuto("cpn_auto", 3);
        GrantResult result = grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-cpn", GrantContext.defaults());
        assertThat(result.status()).isEqualTo(GrantStatus.GRANTED);
        assertThat(result.fulfillmentStatus()).isEqualTo(FulfillmentStatus.ARRIVED);
        assertThat(points.calls()).isEmpty();
    }

    @Test
    void retryableFailureWritesGrantFailedEvent() {
        long prizeId = enableAlipay("red_fail", 1);
        prizes.getById(prizeId).setRemainingStock(0);
        assertThatThrownBy(() -> grant.grant(prizeId, 9L, GrantSource.TASK_STEP, "s-fail", GrantContext.defaults()))
                .isInstanceOf(RetryableGrantException.class);
        assertThat(outbox.claimBatch("portal", NOW, 10))
                .anyMatch(r -> EventCodes.REWARD_GRANT_FAILED.equals(r.eventCode()));
    }

    private long enableAlipay(String code, int stockQty) {
        return enableAlipayLimited(code, stockQty, 0, 0, null, null, null);
    }

    private long enablePoints(String code, int stockQty, int pointsAmount) {
        var created = prizesApp.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                BuiltinCategories.POINTS,
                Map.of("points", pointsAmount),
                null,
                stockQty,
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
        return created.id();
    }

    private long enableCouponAuto(String code, int stockQty) {
        var created = prizesApp.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                BuiltinCategories.COUPON,
                Map.of(),
                null,
                stockQty,
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
        return created.id();
    }

    private long enableManual(String code, int stockQty) {
        var created = prizesApp.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                BuiltinCategories.COUPON,
                Map.of(),
                null,
                stockQty,
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
        return created.id();
    }

    private long enableAlipayLimited(
            String code,
            int stockQty,
            int daily,
            int total,
            List<String> region,
            List<String> level,
            List<String> tag) {
        var created = prizesApp.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                BuiltinCategories.ALIPAY_RED,
                Map.of("faceFen", 100),
                null,
                stockQty,
                daily,
                total,
                region,
                level,
                tag,
                "AUTO",
                null,
                null,
                null,
                null));
        prizesApp.enable(created.id(), new PrizeConfirmCommand(true));
        return created.id();
    }

    private static final class StubUsers implements UserAttributePort {
        AccountStatus status = AccountStatus.ACTIVE;
        int attributeCalls;
        int lockCalls;

        @Override
        public UserAttributes attributes(long userId) {
            attributeCalls++;
            return new UserAttributes("GD", "user", "1", 1, List.of("vip"), Instant.EPOCH, status);
        }

        @Override
        public UserAttributes lockAndGet(long userId) {
            lockCalls++;
            return attributes(userId);
        }
    }

    private static final class StubRisk implements RiskCheckPort {
        boolean reject;
        RiskSubject last;

        @Override
        public RiskVerdict check(RiskScene scene, RiskSubject subject) {
            last = subject;
            return new RiskVerdict(reject ? RiskAction.REJECT : RiskAction.PASS);
        }

        @Override
        public UserRiskSummary userSummary(long userId) {
            return new UserRiskSummary(0L, List.of());
        }
    }
}
