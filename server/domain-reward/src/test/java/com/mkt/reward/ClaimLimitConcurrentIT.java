package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.time.MutableClock;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** C-5 / R17.2: daily=2 then total=3 under the prize row lock. */
@Testcontainers
class ClaimLimitConcurrentIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentSameUserHonorsDailyThenTotal() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        try (RewardITSupport env = new RewardITSupport(MYSQL, clock)) {
            for (int round = 0; round < 20; round++) {
                runRound(env, clock, round);
            }
        }
    }

    private static void runRound(RewardITSupport env, MutableClock clock, int round) throws Exception {
        clock.setInstant(Instant.parse("2026-08-19T00:00:00Z"));
        String code = "red_c5_" + round + "_" + System.nanoTime();
        long prizeId = env.enableAlipay(code, 100, 2, 3, 50);
        long userId = 7000L + round;
        Burst first = burst(env, prizeId, userId, 64, "d");
        assertThat(first.unexpected).isEmpty();
        assertThat(first.success.get()).isEqualTo(2);
        assertThat(first.dailyRejections.get()).isEqualTo(62);
        assertThat(first.totalRejections.get()).isZero();

        clock.setInstant(Instant.parse("2026-08-20T00:00:00Z"));
        Burst second = burst(env, prizeId, userId, 64, "t");
        assertThat(second.unexpected).isEmpty();
        assertThat(second.success.get()).isEqualTo(1);
        assertThat(second.totalRejections.get()).isEqualTo(63);
        Integer granted = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM rwd_grant_record WHERE prize_id = ? AND user_id = ? AND status IN ('GRANTED','WON','CLAIMING','RETRY_PENDING')",
                Integer.class,
                prizeId,
                userId);
        assertThat(granted).isEqualTo(3);
    }

    private static Burst burst(RewardITSupport env, long prizeId, long userId, int n, String prefix)
            throws InterruptedException {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CyclicBarrier barrier = new CyclicBarrier(n);
        CountDownLatch done = new CountDownLatch(n);
        Burst burst = new Burst();
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    barrier.await(20, TimeUnit.SECONDS);
                    env.tx.executeWithoutResult(status -> env.stock.consume(
                            prizeId, userId, GrantSource.TASK_STEP, prefix + "-" + idx + "-" + System.nanoTime()));
                    burst.success.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.errorCode() == RewardErrorCodes.CLAIM_LIMIT_EXCEEDED) {
                        if (ex.getMessage() != null && ex.getMessage().contains("当日")) {
                            burst.dailyRejections.incrementAndGet();
                        } else {
                            burst.totalRejections.incrementAndGet();
                        }
                    } else {
                        burst.unexpected.add(ex);
                    }
                } catch (Throwable ex) {
                    burst.unexpected.add(ex);
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(done.await(40, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        return burst;
    }

    private static final class Burst {
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger dailyRejections = new AtomicInteger();
        final AtomicInteger totalRejections = new AtomicInteger();
        final List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
    }
}
