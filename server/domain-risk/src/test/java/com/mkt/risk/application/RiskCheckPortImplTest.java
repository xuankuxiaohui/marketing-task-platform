package com.mkt.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserRiskSummary;
import com.mkt.contract.event.EventCodes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.infra.outbox.OutboxRoutes;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.time.MutableClock;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.support.ListLookup;
import com.mkt.risk.support.RiskCntWindow;
import com.mkt.risk.support.RiskFallbackPolicy;
import com.mkt.risk.support.RiskFallbackProbe;
import com.mkt.risk.support.RiskFallbackSettings;
import com.mkt.risk.support.RiskListProjection;
import com.mkt.risk.testsupport.MemoryRiskHitLogStore;
import com.mkt.risk.testsupport.MemoryRiskListItemStore;
import com.mkt.risk.testsupport.MemoryRuleConfigStore;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RiskCheckPortImplTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private final MemoryKeyValueStore kv = new MemoryKeyValueStore();
    private final MemoryRiskListItemStore lists = new MemoryRiskListItemStore();
    private final MemoryRiskHitLogStore hits = new MemoryRiskHitLogStore();
    private final MemoryRuleConfigStore rules = new MemoryRuleConfigStore();
    private final RiskFallbackSettings fallback = new RiskFallbackSettings();
    private final RiskFallbackProbe probe = new RiskFallbackProbe();
    private final MemoryOutboxStore outbox = new MemoryOutboxStore(clock);
    private final RiskCheckPortImpl port = newPort();

    @Test
    void claimUserBlackIsRejectAndDoesNotWriteCnt() {
        addUserBlack(88L);
        RiskVerdict verdict = port.check(RiskScene.CLAIM, subject(88L, null));
        assertThat(verdict.action()).isEqualTo(RiskAction.REJECT);
        assertThat(hits.countByQuery("USER:BLACK", "LIST", null, 88L, "REJECTED", null, null)).isEqualTo(1);
        assertThat(kv.zcount("risk:cnt:R-f:IP:10.0.0.8", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
                .isZero();
    }

    @Test
    void whitelistSkipsRulesEvenWhenCountWouldReject() {
        addUserWhite(88L);
        rules.replace(new RuleSpec(RiskRuleCode.RF, true, 1, 60L, RiskAction.REJECT));
        kv.zadd("risk:cnt:R-f:IP:10.0.0.8", clock.instant().toEpochMilli(), "pre");
        RiskVerdict verdict = port.check(RiskScene.CLAIM, subject(88L, null));
        assertThat(verdict.action()).isEqualTo(RiskAction.PASS);
        assertThat(hits.countByQuery(null, "RULE", null, 88L, null, null, null)).isZero();
        assertThat(kv.zcount("risk:cnt:R-f:IP:10.0.0.8", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
                .isEqualTo(1);
    }

    @Test
    void registerAndLoginDoNotRunRulesOrWriteCnt() {
        rules.replace(new RuleSpec(RiskRuleCode.RF, true, 1, 60L, RiskAction.REJECT));
        assertThat(port.check(RiskScene.REGISTER, subject(7L, null)).action()).isEqualTo(RiskAction.PASS);
        assertThat(port.check(RiskScene.LOGIN, subject(7L, null)).action()).isEqualTo(RiskAction.PASS);
        assertThat(hits.countByQuery(null, "RULE", null, null, null, null, null)).isZero();
        assertThat(kv.zcount("risk:cnt:R-f:IP:10.0.0.8", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
                .isZero();
    }

    @Test
    void grantSkipsReWhenElapsedNullAndClaimNeverRunsRe() {
        rules.disableAllExcept(RiskRuleCode.RE);
        assertThat(port.check(RiskScene.GRANT, subject(3L, null)).action()).isEqualTo(RiskAction.PASS);
        assertThat(port.check(RiskScene.CLAIM, subject(3L, 1L)).action()).isEqualTo(RiskAction.PASS);
        assertThat(hits.countByQuery("R-e", "RULE", null, null, null, null, null)).isZero();
    }

    @Test
    void grantRejectsWhenElapsedBelowThreshold() {
        rules.disableAllExcept(RiskRuleCode.RE);
        RiskVerdict verdict = port.check(RiskScene.GRANT, subject(3L, 4L));
        assertThat(verdict.action()).isEqualTo(RiskAction.REJECT);
        assertThat(hits.countByQuery("R-e", "RULE", null, 3L, "REJECTED", null, null)).isEqualTo(1);
        assertThat(kv.zcount("risk:cnt:R-e:USER:3", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)).isZero();
    }

    @Test
    void redisDownFallsBackToAllowByDefault() {
        kv.setAvailable(false);
        assertThat(port.check(RiskScene.CLAIM, subject(1L, null)).action()).isEqualTo(RiskAction.PASS);
        assertThat(probe.fallbackCount()).isEqualTo(1);
        fallback.setPolicy(RiskFallbackPolicy.REJECT);
        assertThat(port.check(RiskScene.CLAIM, subject(1L, null)).action()).isEqualTo(RiskAction.REJECT);
        assertThat(probe.fallbackCount()).isEqualTo(2);
    }

    @Test
    void redisDownStillRejectsWhenDbHasUserBlack() {
        addUserBlack(1L);
        kv.setAvailable(false);
        assertThat(port.check(RiskScene.CLAIM, subject(1L, null)).action()).isEqualTo(RiskAction.REJECT);
        assertThat(probe.fallbackCount()).isZero();
    }

    @Test
    void claimIncrementsRf() {
        rules.disableAllExcept(RiskRuleCode.RF);
        rules.replace(new RuleSpec(RiskRuleCode.RF, true, 60, 60L, RiskAction.REJECT));
        assertThat(port.check(RiskScene.CLAIM, new RiskSubject(3L, "203.0.113.10", "dev-rf", null)).action())
                .isEqualTo(RiskAction.PASS);
        assertThat(kv.zcount("risk:cnt:R-f:IP:203.0.113.10", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
                .isEqualTo(1);
    }

    @Test
    void consumerDeviceWriteHitsRdOnMixedCaseCheck() {
        rules.disableAllExcept(RiskRuleCode.RD);
        rules.replace(new RuleSpec(RiskRuleCode.RD, true, 2, 86400L, RiskAction.REJECT));
        RiskCntConsumer consumer = new RiskCntConsumer(new RiskCntWindow(kv), clock);
        consumer.consume(authRow(11L, "10.0.0.8", "Dev-1"));
        consumer.consume(authRow(12L, "10.0.0.8", "DEV-1"));
        assertThat(port.check(RiskScene.CLAIM, new RiskSubject(13L, "10.0.0.8", "Dev-1", null)).action())
                .isEqualTo(RiskAction.REJECT);
        assertThat(hits.countByQuery("R-d", "RULE", "dev-1", 13L, "REJECTED", null, null)).isEqualTo(1);
    }

    @Test
    void hitRecordedGoesToOutboxWithHitId() {
        rules.disableAllExcept(RiskRuleCode.RE);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThat(port.check(RiskScene.GRANT, subject(3L, 1L)).action()).isEqualTo(RiskAction.REJECT);
            assertThat(probe.hitWriteFailureCount()).isZero();
            assertThat(outbox.all()).hasSize(1);
            OutboxRecord row = outbox.all().get(0);
            assertThat(row.eventCode()).isEqualTo(EventCodes.RISK_HIT_RECORDED);
            assertThat(row.payload()).contains("\"hitId\":");
            assertThat(row.payload()).doesNotContain("\"hitId\":null");
            assertThat(row.aggregateId()).isNotEqualTo("null");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void userSummaryDoesNotWriteHits() {
        addUserBlack(5L);
        addUserWhite(5L);
        port.check(RiskScene.CLAIM, subject(5L, null));
        UserRiskSummary summary = port.userSummary(5L);
        assertThat(summary.hitCount()).isEqualTo(1);
        assertThat(summary.listStatus()).containsExactly(RiskListType.BLACK, RiskListType.WHITE);
        assertThat(port.userSummary(9L).hitCount()).isZero();
        assertThat(port.userSummary(9L).listStatus()).isEmpty();
    }

    private OutboxRecord authRow(long userId, String ip, String deviceId) {
        return new OutboxRecord(
                userId,
                OutboxRoutes.AUTH_LOGIN_SUCCESS,
                "portal",
                "user",
                String.valueOf(userId),
                com.mkt.kernel.json.JsonUtil.toJson(Map.of("userId", userId, "ip", ip, "deviceId", deviceId)),
                "PENDING",
                0,
                null,
                clock.instant());
    }

    private RiskCheckPortImpl newPort() {
        RiskListProjection projection = new RiskListProjection(kv, lists, clock);
        EventPublisher publisher = new EventPublisher(outbox, OutboxProducer.ADMIN);
        RiskHitRecorder recorder = new RiskHitRecorder(hits, publisher, clock, probe, null);
        return new RiskCheckPortImpl(
                new ListLookup(projection, clock),
                rules,
                new RiskCntWindow(kv),
                recorder,
                hits,
                lists,
                fallback,
                probe,
                clock);
    }

    private static RiskSubject subject(long userId, Long elapsed) {
        return new RiskSubject(userId, "10.0.0.8", "dev-1", elapsed);
    }

    private void addUserBlack(long userId) {
        RiskListItemEntity row = new RiskListItemEntity();
        row.setDimension(RiskDimension.USER.name());
        row.setListType(RiskListType.BLACK.name());
        row.setListValue(String.valueOf(userId));
        row.setReason("test");
        row.setDenyLogin(0);
        lists.insert(row);
    }

    private void addUserWhite(long userId) {
        RiskListItemEntity row = new RiskListItemEntity();
        row.setDimension(RiskDimension.USER.name());
        row.setListType(RiskListType.WHITE.name());
        row.setListValue(String.valueOf(userId));
        row.setReason("vip");
        row.setDenyLogin(0);
        lists.insert(row);
    }
}
