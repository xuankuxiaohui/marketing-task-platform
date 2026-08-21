package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.time.MutableClock;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R19.2: expired WON cannot be claimed. */
@Testcontainers
class PrizeExpireIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void claimAfterExpireIsRejected() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        try (RewardITSupport env = new RewardITSupport(MYSQL, clock)) {
            long prizeId = env.enableManualCoupon("cpn_ex_" + System.nanoTime(), 3, 1);
            long recordId = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "step-ex", GrantContext.defaults()))
                    .recordId();
            clock.setInstant(clock.instant().plus(Duration.ofHours(2)));
            assertThatThrownBy(() -> env.claims.claim(recordId, 9L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(RewardErrorCodes.CLAIM_EXPIRED);
            String status = env.jdbc.queryForObject(
                    "SELECT status FROM rwd_grant_record WHERE id=?", String.class, recordId);
            assertThat(status).isEqualTo("EXPIRED");
        }
    }

    @Test
    void expireSchedulerThenClaimRejected() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        try (RewardITSupport env = new RewardITSupport(MYSQL, clock)) {
            long prizeId = env.enableManualCoupon("cpn_exs_" + System.nanoTime(), 3, 1);
            long recordId = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "step-exs", GrantContext.defaults()))
                    .recordId();
            clock.setInstant(clock.instant().plus(Duration.ofHours(2)));
            assertThat(env.claims.expireDue()).isEqualTo(1);
            String flipped = env.jdbc.queryForObject(
                    "SELECT status FROM rwd_grant_record WHERE id=?", String.class, recordId);
            assertThat(flipped).isEqualTo("EXPIRED");
            assertThatThrownBy(() -> env.claims.claim(recordId, 9L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(RewardErrorCodes.CLAIM_EXPIRED);
        }
    }
}
