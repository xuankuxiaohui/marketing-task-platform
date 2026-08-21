package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.risk.command.RiskListItemCreateCommand;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.it.RiskITSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R13.4: repeated CLAIM checks on a blacklisted user write one hit each, never an instance.
 */
@Testcontainers
class RiskRejectIdempotentIT {

    @Container
    static final MySQLContainer<?> MYSQL = RiskITSupport.mysql();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void tenRejectedClaimsLeaveZeroInstancesAndTenHits() throws Exception {
        try (RiskITSupport env = RiskITSupport.start(MYSQL, Instant.parse("2026-08-19T12:00:00Z"))) {
            UserContext.set(new UserPrincipal(1L, "admin", "op"));
            env.tx.executeWithoutResult(status -> env.lists.add(new RiskListItemCreateCommand(
                    RiskDimension.USER, RiskListType.BLACK, "8801", "bot", null, false, null)));
            List<RiskVerdict> verdicts = new ArrayList<>(10);
            for (int i = 0; i < 10; i++) {
                verdicts.add(env.port.check(RiskScene.CLAIM, new RiskSubject(8801L, "203.0.113.8", "dev-8801", null)));
            }
            assertThat(verdicts).hasSize(10);
            assertThat(verdicts).allMatch(v -> v.action() == RiskAction.REJECT);
            Integer instances = env.jdbc.queryForObject("SELECT COUNT(*) FROM task_instance", Integer.class);
            Integer hits = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM risk_hit_log WHERE user_id = 8801 AND rule_code = 'USER:BLACK'",
                    Integer.class);
            assertThat(instances).isZero();
            assertThat(hits).isEqualTo(10);
        }
    }
}
