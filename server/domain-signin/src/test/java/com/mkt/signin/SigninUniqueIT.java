package com.mkt.signin;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.GrantSource;
import com.mkt.signin.response.SigninActionResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * C-9 / R21.1: 64 threads same user same UTC+8 day → one sgn_record and one SIGNIN_DAY grant.
 */
@Testcontainers
class SigninUniqueIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentCheckinCollapsesToOneRecordAndOneGrant() throws Exception {
        try (SigninITSupport env = new SigninITSupport(MYSQL)) {
            long activityId = env.tx.execute(status -> env.publishDaily());
            int n = 64;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            List<SigninActionResponse> responses = Collections.synchronizedList(new ArrayList<>());
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(8, TimeUnit.SECONDS);
                        responses.add(env.tx.execute(status -> env.portal.checkin(activityId, 9L)));
                    } catch (Throwable ex) {
                        errors.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(errors).isEmpty();
            assertThat(responses).hasSize(n);
            long winners = responses.stream().filter(row -> !row.alreadySigned()).count();
            assertThat(winners).isEqualTo(1);
            Set<Long> ids = new HashSet<>();
            for (SigninActionResponse response : responses) {
                assertThat(response.recordId()).isPositive();
                ids.add(response.recordId());
            }
            assertThat(ids).hasSize(1);
            Integer count = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sgn_record WHERE activity_id = ? AND user_id = 9",
                    Integer.class,
                    activityId);
            assertThat(count).isEqualTo(1);
            assertThat(env.rewards.uniqueGrants()).hasSize(1);
            assertThat(env.rewards.uniqueGrants().get(0).source()).isEqualTo(GrantSource.SIGNIN_DAY);
            assertThat(env.rewards.uniqueGrants().get(0).sourceId()).isEqualTo(activityId + ":9:1");
        }
    }
}
