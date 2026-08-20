package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.outbox.OutboxRecord;
import com.mkt.infra.outbox.OutboxStore;
import com.mkt.kernel.PageData;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.response.TaskStartResponse;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R13.3: list/detail do not write tracking and stay up when EventPublisher is down;
 * start still 200 with a working outbox (client track is a different endpoint).
 */
@Testcontainers
class TrackingNonBlockingIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Test
    void listDetailAndStartSurviveTrackingOutage() throws Exception {
        EventPublisher throwing = new EventPublisher(new ThrowingOutboxStore(), OutboxProducer.PORTAL);
        try (ClaimITSupport broken =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), throwing)) {
            long taskId = broken.publishLegal("nb_list");
            assertThatCode(() -> {
                        PageData<TaskCardView> page = broken.portal.list(9L, null, 1, 20);
                        assertThat(page.records()).isNotEmpty();
                        TaskDetailResponse detail = broken.portal.detail(taskId, 9L, "WEB");
                        assertThat(detail.status()).isEqualTo("NOT_STARTED");
                    })
                    .doesNotThrowAnyException();
        }
        try (ClaimITSupport healthy =
                new ClaimITSupport(MYSQL, ClaimITSupport.activeUsers(), ClaimITSupport.passRisk(), null)) {
            long taskId = healthy.publishLegal("nb_start");
            TaskStartResponse started = healthy.tx.execute(
                    status -> healthy.claims.start(taskId, 9L, "203.0.113.1", null, "WEB"));
            assertThat(started.instanceId()).isPositive();
        }
    }

    private static final class ThrowingOutboxStore implements OutboxStore {
        @Override
        public long insert(String eventCode, String producer, String aggregateType, String aggregateId, String payload) {
            throw new IllegalStateException("track writer down");
        }

        @Override
        public java.util.List<OutboxRecord> claimBatch(String producer, java.time.Instant now, int limit) {
            throw new IllegalStateException("track writer down");
        }

        @Override
        public void delete(long id) {
            throw new IllegalStateException("track writer down");
        }

        @Override
        public void markRetry(long id, int retryCount, java.time.Instant nextRetryAt) {
            throw new IllegalStateException("track writer down");
        }

        @Override
        public void markDead(long id, int retryCount) {
            throw new IllegalStateException("track writer down");
        }

        @Override
        public int countPending(String producer) {
            throw new IllegalStateException("track writer down");
        }
    }
}
