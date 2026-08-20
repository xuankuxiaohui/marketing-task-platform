package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.kernel.BusinessException;
import com.mkt.reward.command.PointsAdjustCommand;
import com.mkt.reward.support.PointsErrorCodes;
import java.time.Instant;
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

/**
 * C-8 / R20.1: concurrent adjust(-1) plus expire scan never goes negative;
 * rejected debits leave no ledger row.
 */
@Testcontainers
class PointsNonNegativeIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentAdjustAndExpireKeepBalanceNonNegative() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            for (int round = 0; round < 20; round++) {
                runRound(env, round);
            }
        }
    }

    private static void runRound(RewardITSupport env, int round) throws Exception {
        long userId = 10_000L + round;
        env.tx.executeWithoutResult(status -> env.pointsLedger.earn(
                userId, 100, Instant.parse("2020-01-01T00:00:00Z"), "TASK_STEP", "seed-" + round));
        int n = 128;
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CyclicBarrier barrier = new CyclicBarrier(n + 1);
        CountDownLatch done = new CountDownLatch(n + 1);
        AtomicInteger successDebits = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    barrier.await(8, TimeUnit.SECONDS);
                    env.tx.executeWithoutResult(
                            status -> env.pointsLedger.adjust(new PointsAdjustCommand(userId, -1L, "c8")));
                    successDebits.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.errorCode() == PointsErrorCodes.INSUFFICIENT_BALANCE) {
                        rejected.incrementAndGet();
                    } else {
                        unexpected.add(ex);
                    }
                } catch (RuntimeException | java.util.concurrent.BrokenBarrierException
                        | java.util.concurrent.TimeoutException
                        | InterruptedException ex) {
                    unexpected.add(ex);
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        pool.submit(() -> {
            try {
                barrier.await(8, TimeUnit.SECONDS);
                env.pointsLedger.expireDue();
            } catch (RuntimeException | java.util.concurrent.BrokenBarrierException
                    | java.util.concurrent.TimeoutException
                    | InterruptedException ex) {
                unexpected.add(ex);
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        assertThat(unexpected).isEmpty();

        Long balance = env.jdbc.queryForObject(
                "SELECT balance FROM pnt_account WHERE user_id = ?", Long.class, userId);
        assertThat(balance).isNotNull().isGreaterThanOrEqualTo(0L);
        Long debitSum = env.jdbc.queryForObject(
                "SELECT COALESCE(SUM(-amount), 0) FROM pnt_transaction WHERE user_id = ? AND amount < 0",
                Long.class,
                userId);
        assertThat(debitSum).isNotNull();
        assertThat(balance).isEqualTo(100L - debitSum);
        Long debitRows = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM pnt_transaction WHERE user_id = ? AND amount < 0", Long.class, userId);
        assertThat(rejected.get() + successDebits.get()).isEqualTo(n);
        assertThat(debitRows).isGreaterThanOrEqualTo((long) successDebits.get());
        Long negative = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM pnt_account WHERE user_id = ? AND balance < 0", Long.class, userId);
        assertThat(negative).isZero();
    }
}
