package com.mkt.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.infra.outbox.ConsumerDirection;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.infra.outbox.OutboxRoutes;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.kernel.time.MutableClock;
import com.mkt.risk.domain.RiskCntKeys;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.support.RiskCntWindow;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskCntConsumerTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private final MemoryKeyValueStore kv = new MemoryKeyValueStore();
    private final RiskCntWindow window = new RiskCntWindow(kv);
    private final RiskCntConsumer consumer = new RiskCntConsumer(window, clock);

    @Test
    void directionIsRiskCnt() {
        assertThat(consumer.direction()).isEqualTo(ConsumerDirection.RISK_CNT);
    }

    @Test
    void completeWritesRaAndSkipsSimulated() {
        consumer.consume(row(OutboxRoutes.TASK_INSTANCE_COMPLETE, Map.of("userId", 8, "instanceId", 100)));
        consumer.consume(row(
                OutboxRoutes.TASK_INSTANCE_COMPLETE,
                Map.of("userId", 8, "instanceId", 101, "simulated", true)));
        assertThat(count(RiskRuleCode.RA, RiskCntKeys.DIM_USER, "8")).isEqualTo(1);
    }

    @Test
    void grantWritesRb() {
        consumer.consume(row(OutboxRoutes.REWARD_GRANT_SUCCESS, Map.of("userId", 8, "recordId", 200)));
        assertThat(count(RiskRuleCode.RB, RiskCntKeys.DIM_USER, "8")).isEqualTo(1);
    }

    @Test
    void authWritesRcAndRdEvenWhenSimulated() {
        consumer.consume(row(
                OutboxRoutes.AUTH_LOGIN_SUCCESS,
                Map.of("userId", 8, "ip", "10.0.0.8", "deviceId", "Dev-1", "simulated", true)));
        assertThat(count(RiskRuleCode.RC, RiskCntKeys.DIM_IP, "10.0.0.8")).isEqualTo(1);
        assertThat(count(RiskRuleCode.RD, RiskCntKeys.DIM_DEVICE, "dev-1")).isEqualTo(1);
    }

    @Test
    void neverWritesRe() {
        consumer.consume(row(OutboxRoutes.TASK_INSTANCE_COMPLETE, Map.of("userId", 8, "instanceId", 1)));
        assertThat(kv.zcount("risk:cnt:R-e:USER:8", Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY)).isZero();
    }

    private long count(RiskRuleCode rule, String dim, String value) {
        return window.count(rule, dim, value, 3600, clock.instant().toEpochMilli());
    }

    private OutboxRecord row(String eventCode, Map<String, Object> payload) {
        return new OutboxRecord(
                1L,
                eventCode,
                "portal",
                "agg",
                "1",
                JsonUtil.toJson(payload),
                "PENDING",
                0,
                null,
                clock.instant());
    }
}
