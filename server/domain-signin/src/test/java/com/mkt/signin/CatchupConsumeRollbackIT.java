package com.mkt.signin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.signin.support.SigninErrorCodes;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Catchup CONSUME failure rolls back the sgn_record insert (R21.3). */
@Testcontainers
class CatchupConsumeRollbackIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void consumeFailureLeavesNoRecordAndNoConsume() throws Exception {
        try (SigninITSupport env = new SigninITSupport(MYSQL)) {
            long activityId = env.tx.execute(status -> env.publishDaily());
            env.rewards.consumeFail = new BusinessException(SigninErrorCodes.CATCHUP_NOT_ALLOWED, "积分余额不足");
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.portal.catchup(activityId, 9L, "2026-08-19")))
                    .isInstanceOf(BusinessException.class);
            Integer count = env.jdbc.queryForObject("SELECT COUNT(*) FROM sgn_record", Integer.class);
            assertThat(count).isZero();
            assertThat(env.rewards.consumes).isEmpty();
        }
    }
}
