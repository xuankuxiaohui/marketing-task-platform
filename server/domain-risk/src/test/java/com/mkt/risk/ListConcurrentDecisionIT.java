package com.mkt.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RiskListType;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.risk.command.RiskListItemCreateCommand;
import com.mkt.risk.command.RiskListItemImportCommand;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskListKeys;
import com.mkt.risk.entity.RiskListItemEntity;
import com.mkt.risk.it.RiskITSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * C-12: no lost window / ghost projection; concurrent import and remove stay in {before, after}.
 * Requires Docker; leave for CI.
 */
@Testcontainers
class ListConcurrentDecisionIT {

    private static final int ROUNDS = 20;
    private static final int DECISIONS_PER_ROUND = 200;

    @Container
    static final MySQLContainer<?> MYSQL = RiskITSupport.mysql();

    @Container
    static final GenericContainer<?> REDIS = RiskITSupport.redis();

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void noThirdStateWhileMutating() throws Exception {
        Instant start = Instant.parse("2026-08-19T11:00:00Z");
        try (RiskITSupport env = RiskITSupport.start(MYSQL, REDIS, start)) {
            UserContext.set(new UserPrincipal(1L, "admin", "op"));
            assertLostWindowClosed(env);
            assertGhostProjectionIgnored(env);

            for (int round = 1; round <= ROUNDS; round++) {
                long t0 = System.nanoTime();
                runRound(env, 8000L + round);
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
                assertThat(elapsedMs).isLessThan(5_000L);
            }
        }
    }

    private static void assertLostWindowClosed(RiskITSupport env) throws Exception {
        env.tx.executeWithoutResult(status -> env.lists.add(new RiskListItemCreateCommand(
                RiskDimension.USER, RiskListType.BLACK, "600", "lag", null, false, null)));
        String key = RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "600");
        env.kv.unlink(key);
        RiskSubject subject = new RiskSubject(600L, "10.0.0.2", "dev-600", null);
        assertThat(env.lookup.decide(RiskScene.CLAIM, subject)).isEqualTo(ListDecision.REJECT);
        assertThat(env.kv.get(key)).isNotBlank();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ListDecision>> futures = new ArrayList<>(64);
            for (int i = 0; i < 64; i++) {
                futures.add(pool.submit(() -> env.lookup.decide(RiskScene.CLAIM, subject)));
            }
            for (Future<ListDecision> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(ListDecision.REJECT);
            }
        }
        RiskListItemEntity row = env.listStore.getByUk("USER", "BLACK", "600");
        env.tx.executeWithoutResult(status -> env.lists.remove(row.getId(), "cleanup"));
    }

    private static void assertGhostProjectionIgnored(RiskITSupport env) {
        String key = RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "601");
        env.kv.set(key, RiskListKeys.PERMANENT);
        RiskSubject subject = new RiskSubject(601L, "10.0.0.3", "dev-601", null);
        assertThat(env.lookup.decide(RiskScene.CLAIM, subject)).isEqualTo(ListDecision.PASS);
        assertThat(env.kv.get(key)).isNull();
    }

    private static void runRound(RiskITSupport env, long userId) throws Exception {
        String value = String.valueOf(userId);
        RiskSubject subject = new RiskSubject(userId, "10.1.1.1", "dev-" + userId, null);
        CyclicBarrier barrier = new CyclicBarrier(3);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<List<ListDecision>> decided = pool.submit(() -> {
                barrier.await();
                List<ListDecision> results = new ArrayList<>(DECISIONS_PER_ROUND);
                for (int i = 0; i < DECISIONS_PER_ROUND; i++) {
                    results.add(env.lookup.decide(RiskScene.CLAIM, subject));
                }
                return results;
            });
            Future<?> importer = pool.submit(() -> {
                UserContext.set(new UserPrincipal(1L, "admin", "op"));
                try {
                    barrier.await();
                    for (int i = 0; i < 20; i++) {
                        env.tx.executeWithoutResult(status -> env.lists.importItems(new RiskListItemImportCommand(
                                RiskDimension.USER, RiskListType.BLACK, value, "c12-import")));
                    }
                } finally {
                    UserContext.clear();
                }
                return null;
            });
            Future<?> remover = pool.submit(() -> {
                UserContext.set(new UserPrincipal(1L, "admin", "op"));
                try {
                    barrier.await();
                    for (int i = 0; i < 20; i++) {
                        env.tx.executeWithoutResult(status -> {
                            RiskListItemEntity row = env.listStore.getByUk("USER", "BLACK", value);
                            if (row != null) {
                                env.lists.remove(row.getId(), "c12-remove");
                            }
                        });
                    }
                } finally {
                    UserContext.clear();
                }
                return null;
            });
            List<ListDecision> results = decided.get(8, TimeUnit.SECONDS);
            importer.get(8, TimeUnit.SECONDS);
            remover.get(8, TimeUnit.SECONDS);
            assertThat(results).hasSize(DECISIONS_PER_ROUND);
            assertThat(results).allMatch(d -> d == ListDecision.REJECT || d == ListDecision.PASS);

            env.projection.reconcile(RiskDimension.USER, RiskListType.BLACK, value);
            RiskListItemEntity finalRow = env.listStore.getByUk("USER", "BLACK", value);
            ListDecision settled = env.lookup.decide(RiskScene.CLAIM, subject);
            if (finalRow == null) {
                assertThat(settled).isEqualTo(ListDecision.PASS);
            } else {
                assertThat(settled).isEqualTo(ListDecision.REJECT);
            }
        }
    }
}
