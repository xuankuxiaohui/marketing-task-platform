package com.mkt.tracking.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.tracking.application.TrackDebugQueryService;
import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.support.TrackSettings;
import com.mkt.tracking.testsupport.MemoryEventLogStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TrackDebugAdminControllerTest {

    private MockMvc mvc;
    private MemoryEventLogStore logs;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
        logs = new MemoryEventLogStore();
        EvtEventLogEntity row = new EvtEventLogEntity();
        row.setId(100L);
        row.setSource("CLIENT");
        row.setEventCode("page.view");
        row.setUserId(9L);
        row.setDeviceId("dev-9");
        row.setEvents("[{\"code\":\"page.view\",\"props\":{\"route\":\"/home\"}}]");
        row.setBatchSize(1);
        row.setRegistered(1);
        row.setSimulated(0);
        row.setServerTime(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        logs.insert(row);
        TrackSettings settings = new TrackSettings();
        settings.setQuerySampleRatioPercent(100);
        TrackDebugQueryService service = new TrackDebugQueryService(
                logs, new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock), settings);
        mvc = MockMvcBuilders.standaloneSetup(new TrackDebugAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void debugQueryIsGetAndDoesNotChangeStore() throws Exception {
        int before = logs.rows().size();
        mvc.perform(get("/admin/track/events/debug")
                        .param("eventCode", "page.view")
                        .param("userId", "9")
                        .param("source", "client")
                        .param("deviceId", "dev-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].eventCode").value("page.view"));
        assertThatUnchanged(before);
    }

    private void assertThatUnchanged(int before) {
        org.assertj.core.api.Assertions.assertThat(logs.rows()).hasSize(before);
    }
}
