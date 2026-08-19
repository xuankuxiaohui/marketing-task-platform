package com.mkt.tracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.query.TrackDebugQuery;
import com.mkt.tracking.response.TrackDebugEventResponse;
import com.mkt.tracking.support.TrackErrorCodes;
import com.mkt.tracking.support.TrackSettings;
import com.mkt.tracking.testsupport.MemoryEventLogStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrackDebugQueryServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
    private final MemoryEventLogStore logs = new MemoryEventLogStore();
    private final TrackSettings settings = new TrackSettings();
    private TrackDebugQueryService service;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(7L, "admin", "op"));
        settings.setQuerySampleRatioPercent(100);
        service = new TrackDebugQueryService(
                logs, new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock), settings);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void filtersByCodeUserSourceTimeAndExpandsBatchJson() {
        insert(1L, "CLIENT", "page.view", 11L, "dev-a",
                "[{\"code\":\"page.view\",\"props\":{\"route\":\"/home\"}},{\"code\":\"page.leave\",\"props\":{}}]");
        insert(2L, "SERVER", "auth.login.success", 11L, null,
                "[{\"code\":\"auth.login.success\",\"props\":{}}]");
        insert(3L, "CLIENT", "page.view", 22L, "dev-b", "[{\"code\":\"page.view\",\"props\":{}}]");

        PageData<TrackDebugEventResponse> byLeave = service.query(new TrackDebugQuery(
                "page.leave", null, "client", null, null, null, PageQuery.of(1, 20)));
        assertThat(byLeave.total()).isEqualTo(1);
        assertThat(byLeave.records().get(0).id()).isEqualTo(1L);
        assertThat(byLeave.records().get(0).events()).extracting(item -> item.code())
                .contains("page.leave");

        PageData<TrackDebugEventResponse> byUser = service.query(
                new TrackDebugQuery(null, 22L, null, null, null, null, PageQuery.of(1, 20)));
        assertThat(byUser.total()).isEqualTo(1);
        assertThat(byUser.records().get(0).userId()).isEqualTo(22L);

        PageData<TrackDebugEventResponse> bySource = service.query(
                new TrackDebugQuery(null, null, "SERVER", null, null, null, PageQuery.of(1, 20)));
        assertThat(bySource.total()).isEqualTo(1);
        assertThat(bySource.records().get(0).source()).isEqualTo("SERVER");
    }

    @Test
    void samplingDoesNotMutateRows() {
        settings.setQuerySampleRatioPercent(1);
        for (long id = 1; id <= 200; id++) {
            insert(id, "CLIENT", "page.view", 1L, "dev", "[{\"code\":\"page.view\",\"props\":{}}]");
        }
        int before = logs.rows().size();
        String hash = logs.rows().get(0).getEvents();
        PageData<TrackDebugEventResponse> page = service.query(
                new TrackDebugQuery(null, null, null, null, null, null, PageQuery.of(1, 100)));
        assertThat(logs.rows()).hasSize(before);
        assertThat(logs.rows().get(0).getEvents()).isEqualTo(hash);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.records()).hasSize(2);
    }

    @Test
    void invalidSourceRejected() {
        assertThatThrownBy(() -> service.query(
                        new TrackDebugQuery(null, null, "MOBILE", null, null, null, PageQuery.of(1, 20))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
        assertThat(logs.rows()).isEmpty();
    }

    @Test
    void independentRateLimitDoesNotInsert() {
        SlidingWindowRateLimiter limiter = mock(SlidingWindowRateLimiter.class);
        when(limiter.tryAcquire(any(), anyString(), anyInt(), anyInt())).thenReturn(false);
        TrackDebugQueryService limited = new TrackDebugQueryService(logs, limiter, settings);
        insert(1L, "CLIENT", "page.view", 1L, "dev", "[{\"code\":\"page.view\",\"props\":{}}]");
        assertThatThrownBy(() -> limited.query(
                        new TrackDebugQuery(null, null, null, null, null, null, PageQuery.of(1, 20))))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TrackErrorCodes.QUERY_RATE_LIMITED);
        assertThat(logs.rows()).hasSize(1);
    }

    private void insert(long id, String source, String code, Long userId, String deviceId, String events) {
        EvtEventLogEntity row = new EvtEventLogEntity();
        row.setId(id);
        row.setSource(source);
        row.setEventCode(code);
        row.setUserId(userId);
        row.setDeviceId(deviceId);
        row.setEvents(events);
        row.setBatchSize(1);
        row.setRegistered(1);
        row.setSimulated(0);
        row.setServerTime(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        logs.insert(row);
    }
}
