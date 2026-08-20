package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.contract.RetryableGrantException;
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

/** C-6 / R18.1: one (source, sourceId, prize) at most one GRANTED/WON row. */
@Testcontainers
class GrantExactlyOnceIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentSameKeyAtMostOneGranted() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            long prizeId = env.enableAlipay("red_c6_" + System.nanoTime(), 20, 0, 0, 50);
            String sourceId = "step-c6";
            int n = 40;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            AtomicInteger granted = new AtomicInteger();
            List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(20, TimeUnit.SECONDS);
                        env.tx.executeWithoutResult(status -> env.grant.grant(
                                prizeId, 88L, GrantSource.TASK_STEP, sourceId, GrantContext.defaults()));
                        granted.incrementAndGet();
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
            Integer rows = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE grant_source=? AND source_id=? AND prize_id=?",
                    Integer.class,
                    "TASK_STEP",
                    sourceId,
                    prizeId);
            assertThat(rows).isEqualTo(1);
            Integer remaining = env.jdbc.queryForObject(
                    "SELECT remaining_stock FROM rwd_prize WHERE id=?", Integer.class, prizeId);
            assertThat(remaining).isEqualTo(19);
            assertThat(granted.get()).isEqualTo(n);
        }
    }

    @Test
    void retryPendingThenConcurrentRetryStillOneGranted() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            long prizeId = env.enableAlipay("red_c6r_" + System.nanoTime(), 5, 0, 0, 50);
            env.jdbc.update("UPDATE rwd_prize SET remaining_stock = 0 WHERE id=?", prizeId);
            String sourceId = "step-c6r";
            assertThatThrownBy(() -> env.tx.executeWithoutResult(status -> env.grant.grant(
                            prizeId, 7L, GrantSource.TASK_STEP, sourceId, GrantContext.defaults())))
                    .isInstanceOf(RetryableGrantException.class);
            Integer pending = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE source_id=? AND status='RETRY_PENDING'",
                    Integer.class,
                    sourceId);
            assertThat(pending).isEqualTo(1);
            env.jdbc.update("UPDATE rwd_prize SET remaining_stock = 5 WHERE id=?", prizeId);
            int n = 20;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(20, TimeUnit.SECONDS);
                        env.tx.executeWithoutResult(status -> env.grant.grant(
                                prizeId, 7L, GrantSource.TASK_STEP, sourceId, GrantContext.defaults()));
                    } catch (Throwable ignored) {
                        // retryable/permanent losers are fine
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(40, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            Integer granted = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE source_id=? AND status='GRANTED'",
                    Integer.class,
                    sourceId);
            assertThat(granted).isEqualTo(1);
            Integer total = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM rwd_grant_record WHERE source_id=?", Integer.class, sourceId);
            assertThat(total).isEqualTo(1);
        }
    }

    @Test
    void instantPointsEarnsOnceUnderContention() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            long prizeId = env.enablePoints("pts_c6_" + System.nanoTime(), 10, 8);
            String sourceId = "step-pts";
            int n = 16;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(20, TimeUnit.SECONDS);
                        env.tx.executeWithoutResult(status -> env.grant.grant(
                                prizeId, 3L, GrantSource.TASK_STEP, sourceId, GrantContext.defaults()));
                    } catch (Throwable ignored) {
                        // ignore
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(done.await(40, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(env.points.calls()).hasSize(1);
            String fulfillment = env.jdbc.queryForObject(
                    "SELECT fulfillment_status FROM rwd_grant_record WHERE source_id=?",
                    String.class,
                    sourceId);
            assertThat(fulfillment).isEqualTo("ARRIVED");
        }
    }
}
