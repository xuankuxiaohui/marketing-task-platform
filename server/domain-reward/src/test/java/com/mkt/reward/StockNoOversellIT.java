package com.mkt.reward;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.GrantSource;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.command.StockReplenishCommand;
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

/** C-4 / R17.1: remaining + successful grants = total_stock under contention. */
@Testcontainers
class StockNoOversellIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentConsumeNeverOversellsAndLogChainHolds() throws Exception {
        try (RewardITSupport env = new RewardITSupport(MYSQL)) {
            for (int round = 0; round < 20; round++) {
                runRound(env, round);
            }
        }
    }

    private static void runRound(RewardITSupport env, int round) throws Exception {
        String code = "red_c4_" + round + "_" + System.nanoTime();
        long prizeId = env.enableAlipay(code, 50, 0, 0, 100);
        int n = 200;
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CyclicBarrier barrier = new CyclicBarrier(n);
        CountDownLatch done = new CountDownLatch(n);
        AtomicInteger success = new AtomicInteger();
        List<Long> grantIds = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    barrier.await(20, TimeUnit.SECONDS);
                    env.tx.executeWithoutResult(status -> {
                        var result = env.stock.consume(prizeId, 1000L + idx, GrantSource.TASK_STEP, "s-" + idx);
                        grantIds.add(result.grantRecordId());
                    });
                    success.incrementAndGet();
                } catch (BusinessException ex) {
                    if (ex.errorCode() != RewardErrorCodes.STOCK_INSUFFICIENT) {
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
        assertThat(success.get()).isEqualTo(50);
        Integer remaining = env.jdbc.queryForObject(
                "SELECT remaining_stock FROM rwd_prize WHERE id = ?", Integer.class, prizeId);
        Integer total = env.jdbc.queryForObject(
                "SELECT total_stock FROM rwd_prize WHERE id = ?", Integer.class, prizeId);
        Integer granted = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM rwd_grant_record WHERE prize_id = ? AND status IN ('GRANTED','WON','CLAIMING','RETRY_PENDING')",
                Integer.class,
                prizeId);
        assertThat(remaining).isZero();
        assertThat(total).isEqualTo(50);
        assertThat(granted).isEqualTo(50);
        assertThat(granted + remaining).isEqualTo(total);
        assertLogChain(env, prizeId);

        env.tx.executeWithoutResult(
                status -> env.prizes.replenish(prizeId, new StockReplenishCommand(10, "回补")));
        Long restoreId = grantIds.get(0);
        env.tx.executeWithoutResult(status -> env.stock.restore(prizeId, restoreId));
        Integer remainingAfter = env.jdbc.queryForObject(
                "SELECT remaining_stock FROM rwd_prize WHERE id = ?", Integer.class, prizeId);
        Integer totalAfter = env.jdbc.queryForObject(
                "SELECT total_stock FROM rwd_prize WHERE id = ?", Integer.class, prizeId);
        Integer grantedAfter = env.jdbc.queryForObject(
                "SELECT COUNT(*) FROM rwd_grant_record WHERE prize_id = ? AND status IN ('GRANTED','WON','CLAIMING','RETRY_PENDING')",
                Integer.class,
                prizeId);
        assertThat(totalAfter).isEqualTo(60);
        assertThat(grantedAfter + remainingAfter).isEqualTo(totalAfter);
        assertLogChain(env, prizeId);
    }

    private static void assertLogChain(RewardITSupport env, long prizeId) {
        List<int[]> rows = env.jdbc.query(
                "SELECT before_value, amount, after_value FROM rwd_stock_log WHERE prize_id = ? ORDER BY id",
                (rs, i) -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)},
                prizeId);
        assertThat(rows).isNotEmpty();
        Integer prevAfter = null;
        for (int[] row : rows) {
            assertThat(row[2]).isEqualTo(row[0] + row[1]);
            if (prevAfter != null) {
                assertThat(row[0]).isEqualTo(prevAfter);
            }
            prevAfter = row[2];
        }
    }
}
