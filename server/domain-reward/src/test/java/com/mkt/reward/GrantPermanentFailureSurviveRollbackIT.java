package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.reward.domain.FailReasons;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** DEC-004: markPermanent REQUIRES_NEW survives parent rollback. */
@Testcontainers
class GrantPermanentFailureSurviveRollbackIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void permanentFailureRemainsAfterParentRollback() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        try (RewardITSupport env = new RewardITSupport(MYSQL, clock)) {
            GrantRecordEntity draft = new GrantRecordEntity();
            draft.setUserId(9L);
            draft.setPrizeId(1L);
            draft.setGrantSource("TASK_STEP");
            draft.setSourceId("dec004-perm-1");
            draft.setFailReason(FailReasons.SYSTEM_ERROR);
            draft.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

            env.tx.execute(status -> {
                env.failures.markPermanent(draft);
                status.setRollbackOnly();
                return null;
            });

            Integer count = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE source_id = ? AND status = ?",
                    Integer.class,
                    "dec004-perm-1",
                    GrantRecordStatuses.PERMANENT_FAILED);
            assertThat(count).isEqualTo(1);
        }
    }
}
