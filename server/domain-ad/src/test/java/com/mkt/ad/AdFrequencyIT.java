package com.mkt.ad;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.ad.response.PortalAdPositionView;
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
 * C-11 / R30.1: daily impression cap 10, 64 threads concurrent pull of the same slot.
 * Served copies of the material ≤ 10.
 */
@Testcontainers
class AdFrequencyIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void concurrentPullDoesNotExceedDailyCap() throws Exception {
        try (AdITSupport env = new AdITSupport(MYSQL)) {
            env.settings.setDailyImpressionLimit(10);
            long materialId = env.tx.execute(status -> env.publishImage("home_image", 20));
            int n = 64;
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            CyclicBarrier barrier = new CyclicBarrier(n);
            CountDownLatch done = new CountDownLatch(n);
            List<Integer> served = Collections.synchronizedList(new ArrayList<>());
            List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        barrier.await(8, TimeUnit.SECONDS);
                        PortalAdPositionView view = env.portal.pull("home_image", 9L, null, "WEB");
                        long hits = view.materials().stream()
                                .filter(row -> row.materialId() == materialId)
                                .count();
                        served.add((int) hits);
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
            assertThat(served).hasSize(n);
            int total = served.stream().mapToInt(Integer::intValue).sum();
            assertThat(total).isLessThanOrEqualTo(10);
            assertThat(total).isEqualTo(10);
        }
    }
}
