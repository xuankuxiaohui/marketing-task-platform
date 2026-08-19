package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.risk.it.RiskITSupport;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R26.4: same-request grant (elapsed=null) skips R-e; existing instance under threshold is rejected.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class ReElapsedScopeIT {

    @Container
    static final MySQLContainer<?> MYSQL = RiskITSupport.mysql();

    @Container
    static final GenericContainer<?> REDIS = RiskITSupport.redis();

    @Test
    void sameRequestGrantSkipsReAndExistingInstanceIsRejected() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (RiskITSupport env = RiskITSupport.start(MYSQL, REDIS, now)) {
            env.jdbc.update("UPDATE risk_rule_config SET enabled=0 WHERE rule_code <> 'R-e'");
            env.jdbc.update("UPDATE risk_rule_config SET threshold=5, window_seconds=NULL WHERE rule_code='R-e'");

            RiskSubject instantGrant = new RiskSubject(9001L, "192.0.2.8", "dev-9001", null);
            assertThat(env.port.check(RiskScene.GRANT, instantGrant).action()).isEqualTo(RiskAction.PASS);
            Integer reHits = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM risk_hit_log WHERE rule_code='R-e'", Integer.class);
            assertThat(reHits).isZero();

            RiskSubject slowEnough = new RiskSubject(9001L, "192.0.2.8", "dev-9001", 5L);
            assertThat(env.port.check(RiskScene.GRANT, slowEnough).action()).isEqualTo(RiskAction.PASS);

            RiskSubject tooFast = new RiskSubject(9001L, "192.0.2.8", "dev-9001", 4L);
            assertThat(env.port.check(RiskScene.GRANT, tooFast).action()).isEqualTo(RiskAction.REJECT);
            Integer after = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM risk_hit_log WHERE rule_code='R-e' AND action_result='REJECTED'",
                    Integer.class);
            assertThat(after).isEqualTo(1);
            Integer recorded = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_outbox WHERE event_code='risk.hit.recorded'", Integer.class);
            assertThat(recorded).isEqualTo(1);
            String payload = env.jdbc.queryForObject(
                    "SELECT payload FROM sys_outbox WHERE event_code='risk.hit.recorded'", String.class);
            assertThat(payload).contains("\"hitId\":").doesNotContain("\"hitId\":null");

            Integer grants = env.jdbc.queryForObject("SELECT COUNT(*) FROM rwd_grant_record", Integer.class);
            Integer stockLogs = env.jdbc.queryForObject("SELECT COUNT(*) FROM rwd_stock_log", Integer.class);
            Integer points = env.jdbc.queryForObject("SELECT COUNT(*) FROM pnt_transaction", Integer.class);
            assertThat(grants).isZero();
            assertThat(stockLogs).isZero();
            assertThat(points).isZero();
        }
    }
}
