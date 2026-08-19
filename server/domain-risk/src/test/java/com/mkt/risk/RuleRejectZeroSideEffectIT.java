package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.risk.it.RiskITSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R26.2: rejected claim checks write hits and leave task/reward tables untouched.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class RuleRejectZeroSideEffectIT {

    @Container
    static final MySQLContainer<?> MYSQL = RiskITSupport.mysql();

    @Container
    static final GenericContainer<?> REDIS = RiskITSupport.redis();

    @Test
    void concurrentRejectedClaimsWriteHitsAndZeroInstances() throws Exception {
        try (RiskITSupport env = RiskITSupport.start(MYSQL, REDIS, Instant.parse("2026-08-19T12:00:00Z"))) {
            env.jdbc.update("UPDATE risk_rule_config SET enabled=0 WHERE rule_code <> 'R-f'");
            env.jdbc.update("UPDATE risk_rule_config SET threshold=1, window_seconds=60 WHERE rule_code='R-f'");

            int n = 32;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            List<RiskVerdict> verdicts = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        verdicts.add(env.port.check(
                                RiskScene.CLAIM, new RiskSubject(7001L, "203.0.113.8", "dev-7001", null)));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();

            assertThat(verdicts).hasSize(n);
            assertThat(verdicts).allMatch(v -> v.action() == RiskAction.REJECT);
            Integer instances = env.jdbc.queryForObject("SELECT COUNT(*) FROM task_instance", Integer.class);
            Integer grants = env.jdbc.queryForObject("SELECT COUNT(*) FROM rwd_grant_record", Integer.class);
            Integer hits = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM risk_hit_log WHERE rule_code='R-f' AND hit_type='RULE'", Integer.class);
            assertThat(instances).isZero();
            assertThat(grants).isZero();
            assertThat(hits).isEqualTo(n);
        }
    }
}
