package com.mkt.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.activity.domain.HitRules;
import com.mkt.activity.response.ParticipateResponse;
import com.mkt.contract.GrantSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * C-10 / R22.1: global daily quota 10, 128 threads concurrent participate.
 * Exactly 10 PASS; excess REJECT with hit_rule persisted.
 */
@Testcontainers
class ActivityQuotaIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentParticipateHonorsGlobalDailyQuota() throws Exception {
        try (ActivityITSupport env = new ActivityITSupport(MYSQL)) {
            long activityId = env.tx.execute(status -> env.publishLimited(10));
            int n = 128;
            for (int i = 1; i <= n; i++) {
                env.users.put(i, "110000", Instant.parse("2026-08-01T00:00:00Z"));
            }
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            List<ParticipateResponse> responses = Collections.synchronizedList(new ArrayList<>());
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 1; i <= n; i++) {
                final long userId = i;
                pool.submit(() -> {
                    try {
                        barrier.await(8, TimeUnit.SECONDS);
                        responses.add(env.tx.execute(status -> env.portal.participate(activityId, userId)));
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(errors).isEmpty();
            assertThat(responses).hasSize(n);
            long pass = responses.stream().filter(row -> HitRules.PASS.equals(row.result())).count();
            long reject = responses.stream().filter(row -> HitRules.REJECT.equals(row.result())).count();
            assertThat(pass).isEqualTo(10);
            assertThat(reject).isEqualTo(118);
            Integer passRows = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM act_participation WHERE activity_id = ? AND result = 'PASS'",
                    Integer.class,
                    activityId);
            Integer rejectRows = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM act_participation WHERE activity_id = ? AND result = 'REJECT' AND hit_rule = 'GLOBAL_DAILY'",
                    Integer.class,
                    activityId);
            assertThat(passRows).isEqualTo(10);
            assertThat(rejectRows).isEqualTo(118);
            assertThat(env.rewards.uniqueGrants()).hasSize(10);
            assertThat(env.rewards.uniqueGrants().get(0).source()).isEqualTo(GrantSource.ACTIVITY_PARTICIPATION);
        }
    }
}
