package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.support.RewardErrorCodes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** C-7 / R19.1: concurrent claim of one WON record; CAS path, lock degraded.
 * Exactly one GRANTED row; losers are claim.conflict or idempotent GRANTED. */
@Testcontainers
class PrizeClaimExactlyOnceIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentClaimExactlyOneGranted() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            long prizeId = env.enableManualCoupon("cpn_c7_" + System.nanoTime(), 5, 24);
            long recordId = env.tx.execute(status -> env.grant.grant(
                            prizeId, 9L, GrantSource.TASK_STEP, "step-c7", GrantContext.defaults()))
                    .recordId();
            int n = 64;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            AtomicInteger success = new AtomicInteger();
            List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(20, TimeUnit.SECONDS);
                        env.tx.executeWithoutResult(status -> env.claims.claim(recordId, 9L));
                        success.incrementAndGet();
                    } catch (BusinessException ex) {
                        if (ex.errorCode() != RewardErrorCodes.CLAIM_CONFLICT
                                && ex.errorCode() != RewardErrorCodes.CLAIM_NOT_WON) {
                            unexpected.add(ex);
                        }
                    } catch (Throwable ex) {
                        unexpected.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(40, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(unexpected).isEmpty();
            Integer granted = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE id=? AND status='GRANTED'",
                    Integer.class,
                    recordId);
            assertThat(granted).isEqualTo(1);
            String fulfillment = env.jdbc.queryForObject(
                    "SELECT fulfillment_status FROM rwd_grant_record WHERE id=?", String.class, recordId);
            assertThat(fulfillment).isEqualTo(GrantRecordStatuses.FULFILL_ARRIVED);
            assertThat(success.get()).isBetween(1, n);
        }
    }
}
