package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.support.RewardErrorCodes;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R18.2: risk reject leaves grant/stock/points untouched. */
@Testcontainers
class GrantZeroSideEffectIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void riskRejectLeavesThreeTablesUnchanged() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            long prizeId = env.enableAlipay("red_zse_" + System.nanoTime(), 7, 0, 0, 50);
            env.risk.reject = true;
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.grant.grant(
                            prizeId, 44L, GrantSource.TASK_STEP, "step-zse", GrantContext.defaults())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(RewardErrorCodes.RISK_BLOCKED_GENERIC);
            Integer grants = env.jdbc.queryForObject("SELECT COUNT(*) FROM rwd_grant_record", Integer.class);
            Integer remaining = env.jdbc.queryForObject(
                    "SELECT remaining_stock FROM rwd_prize WHERE id=?", Integer.class, prizeId);
            Integer logs = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_stock_log WHERE prize_id=?", Integer.class, prizeId);
            assertThat(grants).isZero();
            assertThat(remaining).isEqualTo(7);
            assertThat(logs).isZero();
            assertThat(env.points.calls()).isEmpty();
        }
    }
}
