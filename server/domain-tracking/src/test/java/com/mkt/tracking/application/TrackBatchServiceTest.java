package com.mkt.tracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.time.MutableClock;
import com.mkt.tracking.command.TrackBatchCommand;
import com.mkt.tracking.command.TrackEventCommand;
import com.mkt.tracking.command.TrackIdentity;
import com.mkt.tracking.domain.DisabledEventPolicy;
import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.domain.UnregisteredPolicy;
import com.mkt.tracking.response.TrackBatchResponse;
import com.mkt.tracking.support.TrackDropCounters;
import com.mkt.tracking.support.TrackErrorCodes;
import com.mkt.tracking.support.TrackSettings;
import com.mkt.tracking.testsupport.MemoryEventLogStore;
import com.mkt.tracking.testsupport.MemoryEventMetadataStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackBatchServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private final MemoryEventLogStore logs = new MemoryEventLogStore();
    private final MemoryEventMetadataStore metadata = new MemoryEventMetadataStore()
            .put("page.view", MetadataStatus.ENABLED)
            .put("task.card.exposure", MetadataStatus.ENABLED)
            .put("old.event.code", MetadataStatus.DISABLED);
    private final TrackSettings settings = new TrackSettings();
    private final TrackDropCounters drops = new TrackDropCounters();
    private TrackBatchService service;

    @BeforeEach
    void setUp() {
        service = new TrackBatchService(
                logs,
                metadata,
                new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock),
                settings,
                drops,
                clock);
    }

    @Test
    void partialAcceptDropsMalformedAndKeepsLegal() {
        TrackBatchResponse result = service.ingest(
                new TrackBatchCommand(
                        List.of(
                                new TrackEventCommand("page.view", Map.of("route", "/home"), "t1"),
                                new TrackEventCommand("NOT A CODE", Map.of(), "t2"),
                                new TrackEventCommand("task.card.exposure", Map.of("taskId", 1, "taskCode", "a"), "t3")),
                        "WEB",
                        "1.0.0"),
                new TrackIdentity(11L, "dev-1", "203.0.113.9"));

        assertThat(result.accepted()).isEqualTo(2);
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(drops.malformed()).isEqualTo(1);
        assertThat(logs.rows()).hasSize(1);
        assertThat(logs.rows().get(0).getSource()).isEqualTo("CLIENT");
        assertThat(logs.rows().get(0).getUserId()).isEqualTo(11L);
        assertThat(logs.rows().get(0).getDeviceId()).isEqualTo("dev-1");
        assertThat(logs.rows().get(0).getBatchSize()).isEqualTo(2);
        assertThat(logs.rows().get(0).getRegistered()).isEqualTo(1);
        assertThat(logs.rows().get(0).getEvents()).contains("page.view").contains("task.card.exposure");
    }

    @Test
    void overflowRejectsWholeBatch() {
        settings.setBatchMaxSize(2);
        List<TrackEventCommand> events = new ArrayList<>();
        events.add(new TrackEventCommand("page.view", Map.of(), null));
        events.add(new TrackEventCommand("page.view", Map.of(), null));
        events.add(new TrackEventCommand("page.view", Map.of(), null));
        assertThatThrownBy(() -> service.ingest(
                        new TrackBatchCommand(events, "WEB", "1.0.0"), new TrackIdentity(null, "dev", "1.1.1.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TrackErrorCodes.BATCH_OVERFLOW);
        assertThat(logs.rows()).isEmpty();
    }

    @Test
    void unregisteredAcceptMarksRow() {
        TrackBatchResponse result = service.ingest(
                new TrackBatchCommand(
                        List.of(new TrackEventCommand("unknown.event.code", Map.of(), null)), "WEB", "1.0.0"),
                new TrackIdentity(null, "dev-anon", "198.51.100.7"));
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(logs.rows().get(0).getRegistered()).isEqualTo(0);
        assertThat(logs.rows().get(0).getUserId()).isNull();
        assertThat(logs.rows().get(0).getDeviceId()).isEqualTo("dev-anon");
    }

    @Test
    void unregisteredRejectDropsWithoutInsert() {
        settings.setUnregisteredPolicy(UnregisteredPolicy.REJECT);
        TrackBatchResponse result = service.ingest(
                new TrackBatchCommand(
                        List.of(new TrackEventCommand("unknown.event.code", Map.of(), null)), "WEB", "1.0.0"),
                new TrackIdentity(2L, "d", "1.1.1.1"));
        assertThat(result.accepted()).isZero();
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(drops.unregistered()).isEqualTo(1);
        assertThat(logs.rows()).isEmpty();
    }

    @Test
    void disabledDropCountDoesNotInsert() {
        TrackBatchResponse result = service.ingest(
                new TrackBatchCommand(
                        List.of(new TrackEventCommand("old.event.code", Map.of(), null)), "WEB", "1.0.0"),
                new TrackIdentity(2L, "d", "1.1.1.1"));
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(drops.disabled()).isEqualTo(1);
        assertThat(logs.rows()).isEmpty();
    }

    @Test
    void disabledKeepInserts() {
        settings.setDisabledEventPolicy(DisabledEventPolicy.KEEP);
        TrackBatchResponse result = service.ingest(
                new TrackBatchCommand(
                        List.of(new TrackEventCommand("old.event.code", Map.of(), null)), "WEB", "1.0.0"),
                new TrackIdentity(2L, "d", "1.1.1.1"));
        assertThat(result.accepted()).isEqualTo(1);
        assertThat(logs.rows()).hasSize(1);
    }

    @Test
    void rateLimitRejectsWholeBatch() {
        SlidingWindowRateLimiter limiter = org.mockito.Mockito.mock(SlidingWindowRateLimiter.class);
        org.mockito.Mockito.when(limiter.tryAcquire(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(false);
        TrackBatchService limited = new TrackBatchService(logs, metadata, limiter, settings, drops, clock);
        TrackBatchCommand cmd =
                new TrackBatchCommand(List.of(new TrackEventCommand("page.view", Map.of(), null)), "WEB", "1.0.0");
        assertThatThrownBy(() -> limited.ingest(cmd, new TrackIdentity(9L, "dev", "1.1.1.1")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TrackErrorCodes.BATCH_RATE_LIMITED);
        assertThat(logs.rows()).isEmpty();
    }
}
