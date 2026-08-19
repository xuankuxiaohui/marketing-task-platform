package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.risk.it.RiskITSupport;
import com.mkt.risk.support.RiskFallbackPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R26.3: Redis pause → default allow + fallback counter; reject policy refuses.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class RuleFallbackIT {

    @Container
    static final MySQLContainer<?> MYSQL = RiskITSupport.mysql();

    @Container
    static final GenericContainer<?> REDIS = RiskITSupport.redis();

    @Test
    void pausedRedisIsObservableAndHonorsPolicy() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (RiskITSupport env = RiskITSupport.start(MYSQL, REDIS, now)) {
            RiskSubject subject = new RiskSubject(8001L, "198.51.100.8", "dev-8001", null);
            REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
            try {
                assertThat(env.fallbackSettings.policy()).isEqualTo(RiskFallbackPolicy.ALLOW);
                assertThat(env.port.check(RiskScene.CLAIM, subject).action()).isEqualTo(RiskAction.PASS);
                assertThat(env.fallbackProbe.fallbackCount()).isEqualTo(1);

                env.fallbackSettings.setPolicy(RiskFallbackPolicy.REJECT);
                assertThat(env.port.check(RiskScene.CLAIM, subject).action()).isEqualTo(RiskAction.REJECT);
                assertThat(env.fallbackProbe.fallbackCount()).isEqualTo(2);
            } finally {
                REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
            }
        }
    }
}
